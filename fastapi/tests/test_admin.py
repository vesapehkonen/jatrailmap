import base64
import json

import bcrypt
import pytest
from bson import ObjectId
from bson.binary import Binary
from fastapi import HTTPException
from starlette.requests import Request

from app.auth import require_admin_id
from app.config import Settings
from app.models import (
    AdminAccountUpdate,
    AdminPasswordConfirmation,
    AdminQuotaUpdate,
    AdminRoleUpdate,
    RegistrationSettingsUpdate,
    TrailModeration,
)
from app.quotas import account_storage_bytes, user_limits
from app.routes.admin import (
    parse_day,
    cleanup_orphan_records,
    issue_password_reset,
    maintenance_findings,
    unpublish_public_trail,
    update_admin_role,
    update_account_quotas,
    update_account_status,
    update_registration_settings,
)
from app.routes.accounts import reset_password_with_token, valid_password_reset
from app.routes.trails import modern_android_response, process_android_upload


def android_payload(photo_data: bytes = b"photo", photo_count: int = 1) -> dict:
    picture = {
        "type": "PictureCollection",
        "pictures": [
            {
                "timestamp": f"2025-01-01T00:00:{index:02d}Z",
                "filename": "photo.jpg",
                "picturename": "View",
                "description": "",
                "loc": {"type": "Point", "coordinates": [-122.0, 47.0]},
                "file": base64.b64encode(photo_data).decode(),
            }
            for index in range(photo_count)
        ],
    }
    return {
        "newtrail": [
            {"type": "TrailInfo", "trailname": "Test", "locationname": "", "description": ""},
            {"type": "UserInfo", "username": "user", "password": "password"},
            {"type": "LocationCollection", "locations": []},
            picture,
        ]
    }


def response_json(response):
    return json.loads(response.body)


def upload_response(body, database):
    return modern_android_response(
        process_android_upload(body, database, Settings(_env_file=None))
    )


def create_admin(database):
    return database.users.insert_one(
        {
            "username": "admin",
            "role": "admin",
            "password": bcrypt.hashpw(b"admin-password", bcrypt.gensalt()).decode(),
        }
    ).inserted_id


def test_default_limits_match_first_admin_phase():
    limits = user_limits({}, Settings(_env_file=None))
    assert limits == {
        "account_storage_bytes": 100 * 1024 * 1024,
        "image_bytes": 2 * 1024 * 1024,
        "photos_per_trail": 30,
        "upload_bytes": 50 * 1024 * 1024,
    }


def test_android_upload_enforces_photo_count_override(database):
    database.users.insert_one(
        {
            "username": "user",
            "password": bcrypt.hashpw(b"password", bcrypt.gensalt()).decode(),
            "quotas": {"photos_per_trail": 1},
        }
    )
    response = upload_response(android_payload(photo_count=2), database)
    assert response.status_code == 413
    assert response_json(response)["error_code"] == "photo_count_exceeded"
    assert database.trails.count_documents({}) == 0


def test_android_upload_enforces_image_and_storage_limits(database):
    user_id = database.users.insert_one(
        {
            "username": "user",
            "password": bcrypt.hashpw(b"password", bcrypt.gensalt()).decode(),
            "quotas": {"image_bytes": 3, "account_storage_bytes": 3},
        }
    ).inserted_id
    response = upload_response(android_payload(b"four"), database)
    assert response.status_code == 413
    assert response_json(response)["error_code"] == "photo_too_large"
    assert account_storage_bytes(database, user_id) == 0


def test_suspended_android_account_cannot_upload(database):
    database.users.insert_one(
        {
            "username": "user",
            "password": bcrypt.hashpw(b"password", bcrypt.gensalt()).decode(),
            "suspended": True,
        }
    )
    response = upload_response(android_payload(), database)
    assert response.status_code == 403
    assert response_json(response)["error_code"] == "account_suspended"


def test_admin_dependency_rejects_non_admin(database):
    user_id = database.users.insert_one({"username": "user"}).inserted_id
    with pytest.raises(HTTPException) as error:
        require_admin_id(database, user_id)
    assert error.value.status_code == 403


def test_admin_can_suspend_and_override_quotas_with_audit(database):
    admin_id = create_admin(database)
    user_id = database.users.insert_one({"username": "user"}).inserted_id
    database.sessions.insert_one({"userid": user_id})
    update_account_status(
        str(user_id), AdminAccountUpdate(suspended=True, admin_password="admin-password"), database, admin_id
    )
    assert database.users.find_one({"_id": user_id})["suspended"] is True
    assert database.sessions.count_documents({"userid": user_id}) == 0
    update_account_quotas(
        str(user_id), AdminQuotaUpdate(image_mb=5, photos_per_trail=40, admin_password="admin-password"), database, admin_id
    )
    assert database.users.find_one({"_id": user_id})["quotas"] == {
        "image_bytes": 5 * 1024 * 1024,
        "photos_per_trail": 40,
    }
    assert database.admin_audit.count_documents({"actorid": admin_id, "targetid": user_id}) == 2


def test_admin_cannot_suspend_self(database):
    admin_id = create_admin(database)
    with pytest.raises(HTTPException) as error:
        update_account_status(
            str(admin_id), AdminAccountUpdate(suspended=True, admin_password="admin-password"), database, admin_id
        )
    assert error.value.status_code == 422


def test_admin_can_unpublish_public_trail_and_notify_owner(database):
    admin_id = create_admin(database)
    owner_id = database.users.insert_one({"username": "owner"}).inserted_id
    trail_id = database.trails.insert_one(
        {
            "userid": owner_id,
            "trailname": "Public route",
            "access": "public",
            "groups": [ObjectId()],
        }
    ).inserted_id
    assert unpublish_public_trail(
        str(trail_id), TrailModeration(reason="Contains inappropriate material", admin_password="admin-password"), database, admin_id
    ) == {"status": "ok"}
    trail = database.trails.find_one({"_id": trail_id})
    assert trail["access"] == "private"
    assert trail["groups"] == []
    assert trail["moderation"]["reason"] == "Contains inappropriate material"
    notification = database.notifications.find_one({"userid": owner_id})
    assert notification["trailid"] == trail_id
    assert "inappropriate material" in notification["message"]
    assert database.admin_audit.find_one({"action": "trail.unpublished"})["targetid"] == trail_id


def test_admin_cannot_moderate_a_private_trail(database):
    admin_id = create_admin(database)
    trail_id = database.trails.insert_one({"access": "private"}).inserted_id
    with pytest.raises(HTTPException) as error:
        unpublish_public_trail(
            str(trail_id), TrailModeration(reason="Some reason", admin_password="admin-password"), database, admin_id
        )
    assert error.value.status_code == 404


def test_audit_date_filter_parser_rejects_invalid_dates():
    assert parse_day("not-a-date") is None
    assert parse_day("2026-08-06").day == 6
    assert parse_day("2026-08-06", end=True).day == 7


def test_privileged_action_rejects_wrong_admin_password(database):
    admin_id = create_admin(database)
    user_id = database.users.insert_one({"username": "user"}).inserted_id
    with pytest.raises(HTTPException) as error:
        update_account_status(
            str(user_id),
            AdminAccountUpdate(suspended=True, admin_password="wrong"),
            database,
            admin_id,
        )
    assert error.value.status_code == 403


def test_final_active_administrator_cannot_be_demoted(database):
    admin_id = create_admin(database)
    with pytest.raises(HTTPException) as error:
        update_admin_role(
            str(admin_id),
            AdminRoleUpdate(role="user", admin_password="admin-password"),
            database,
            admin_id,
        )
    assert error.value.status_code == 422


def test_admin_can_promote_user_when_an_active_admin_remains(database):
    admin_id = create_admin(database)
    user_id = database.users.insert_one({"username": "user"}).inserted_id
    update_admin_role(
        str(user_id),
        AdminRoleUpdate(role="admin", admin_password="admin-password"),
        database,
        admin_id,
    )
    assert database.users.find_one({"_id": user_id})["role"] == "admin"


def test_registration_settings_are_persisted(database):
    admin_id = create_admin(database)
    update_registration_settings(
        RegistrationSettingsUpdate(
            enabled=False,
            approval_required=True,
            account_storage_mb=120,
            image_mb=3,
            photos_per_trail=40,
            upload_mb=60,
            admin_password="admin-password",
        ),
        database,
        admin_id,
    )
    stored = database.app_settings.find_one({"_id": "registration"})
    assert stored["enabled"] is False
    assert stored["approval_required"] is True
    assert stored["image_bytes"] == 3 * 1024 * 1024


def request_with_csrf(path="/"):
    return Request(
        {
            "type": "http",
            "method": "POST",
            "path": path,
            "scheme": "http",
            "server": ("testserver", 80),
            "headers": [(b"cookie", b"csrf_token=test-csrf")],
        }
    )


def test_admin_issued_password_reset_is_single_use(database):
    admin_id = create_admin(database)
    user_id = database.users.insert_one(
        {"username": "user", "password": bcrypt.hashpw(b"old-password", bcrypt.gensalt()).decode()}
    ).inserted_id
    result = issue_password_reset(
        str(user_id),
        AdminPasswordConfirmation(admin_password="admin-password"),
        request_with_csrf(),
        database,
        admin_id,
    )
    token = result["reset_url"].rsplit("/", 1)[-1]
    assert valid_password_reset(database, token) is not None
    reset_password_with_token(
        token, request_with_csrf(), "new-password", "test-csrf", database
    )
    assert valid_password_reset(database, token) is None
    assert bcrypt.checkpw(
        b"new-password", database.users.find_one({"_id": user_id})["password"].encode()
    )


def test_maintenance_cleanup_removes_only_orphan_children(database):
    admin_id = create_admin(database)
    trail_id = database.trails.insert_one({"userid": admin_id}).inserted_id
    kept_image = database.images.insert_one({"img": Binary(b"hello")}).inserted_id
    database.pictures.insert_one({"trailid": trail_id, "imageid": kept_image})
    orphan_image = database.images.insert_one({"img": Binary(b"hello")}).inserted_id
    orphan_picture = database.pictures.insert_one(
        {"trailid": ObjectId(), "imageid": ObjectId()}
    ).inserted_id
    orphan_location = database.locations.insert_one({"trailid": ObjectId()}).inserted_id
    findings = maintenance_findings(database)
    assert orphan_image in findings["orphan_images"]
    cleanup_orphan_records(
        AdminPasswordConfirmation(admin_password="admin-password"), database, admin_id
    )
    assert database.images.find_one({"_id": kept_image}) is not None
    assert database.images.find_one({"_id": orphan_image}) is None
    assert database.pictures.find_one({"_id": orphan_picture}) is None
    assert database.locations.find_one({"_id": orphan_location}) is None
