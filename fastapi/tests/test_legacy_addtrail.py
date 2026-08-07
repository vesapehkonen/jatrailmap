import base64
import asyncio
import copy
import json

import bcrypt
from bson import ObjectId
from bson.binary import Binary
from starlette.requests import Request

from app.config import Settings
from app.routes.trails import (
    android_upload_trail,
    modern_android_response,
    process_android_upload,
)


def payload(password: str = "secret") -> dict:
    return {"newtrail": [
        {"type": "TrailInfo", "access": "public", "date": "2025-01-01T00:00:00Z", "trailname": "Migration test", "locationname": "Seattle", "description": "A trail"},
        {"type": "UserInfo", "username": "android", "password": password},
        {"type": "LocationCollection", "locations": [{"timestamp": "2025-01-01T00:00:00Z", "loc": {"type": "Point", "coordinates": [-122.0, 47.0, 10.0]}}]},
        {"type": "PictureCollection", "pictures": [{"timestamp": "2025-01-01T00:01:00Z", "filename": "photo.gif", "picturename": "View", "description": "", "loc": {"type": "Point", "coordinates": [-122.0, 47.0, 10.0]}, "file": base64.b64encode(b"GIF89a-test").decode()}]},
    ]}


def response_json(response) -> dict:
    return json.loads(response.body)


def modern_upload(body, database, settings):
    return modern_android_response(process_android_upload(body, database, settings))


def api_request(body: bytes, content_type: str) -> Request:
    sent = False

    async def receive():
        nonlocal sent
        if sent:
            return {"type": "http.request", "body": b"", "more_body": False}
        sent = True
        return {"type": "http.request", "body": body, "more_body": False}

    return Request(
        {
            "type": "http",
            "method": "POST",
            "path": "/api/v1/trails",
            "headers": [(b"content-type", content_type.encode())],
        },
        receive,
    )


def multipart_request(
    manifest: dict, photos: list[tuple[str, bytes]], manifest_as_file: bool = False
) -> Request:
    boundary = "jatrail-test-boundary"
    manifest_disposition = (
        'form-data; name="manifest"; filename="trail.json"'
        if manifest_as_file
        else 'form-data; name="manifest"'
    )
    chunks = [
        f"--{boundary}\r\nContent-Disposition: {manifest_disposition}\r\nContent-Type: application/json\r\n\r\n".encode(),
        json.dumps(manifest).encode(),
        b"\r\n",
    ]
    for filename, content in photos:
        chunks.extend(
            [
                f"--{boundary}\r\nContent-Disposition: form-data; name=\"photos\"; filename=\"{filename}\"\r\nContent-Type: image/jpeg\r\n\r\n".encode(),
                content,
                b"\r\n",
            ]
        )
    chunks.append(f"--{boundary}--\r\n".encode())
    return api_request(b"".join(chunks), f"multipart/form-data; boundary={boundary}")


def test_android_upload_contract_and_existing_collections(database):
    user_id = database.users.insert_one(
        {"username": "android", "password": bcrypt.hashpw(b"secret", bcrypt.gensalt()).decode()}
    ).inserted_id
    response = modern_upload(payload(), database, Settings(_env_file=None))
    body = response_json(response)
    assert response.status_code == 201
    assert body["status"] == "ok"
    assert body["message"] == "Trail uploaded successfully."
    trail = database.trails.find_one({"_id": ObjectId(body["trailid"])})
    assert trail["userid"] == user_id
    assert trail["access"] == "private"
    assert database.locations.count_documents({"trailid": trail["_id"]}) == 1
    assert database.pictures.count_documents({"trailid": trail["_id"]}) == 1
    assert database.images.count_documents({}) == 1
    assert bytes(database.images.find_one({})["img"]) == b"GIF89a-test"


def test_android_upload_wrong_password_uses_versioned_error(database):
    database.users.insert_one(
        {"username": "android", "password": bcrypt.hashpw(b"secret", bcrypt.gensalt()).decode()}
    )
    response = modern_upload(payload("wrong"), database, Settings(_env_file=None))
    assert response.status_code == 401
    assert response_json(response)["error_code"] == "invalid_credentials"
    assert database.trails.count_documents({}) == 0


def create_android_user(database, **fields):
    document = {
        "username": "android",
        "password": bcrypt.hashpw(b"secret", bcrypt.gensalt()).decode(),
    }
    document.update(fields)
    return database.users.insert_one(document).inserted_id


def test_versioned_android_upload_returns_201_and_preserves_documents(database):
    user_id = create_android_user(database)
    response = modern_upload(payload(), database, Settings(_env_file=None))
    body = response_json(response)
    assert response.status_code == 201
    assert body["status"] == "ok"
    trail_id = ObjectId(body["trailid"])
    assert database.trails.find_one({"_id": trail_id})["userid"] == user_id
    assert database.locations.count_documents({"trailid": trail_id}) == 1
    assert database.pictures.count_documents({"trailid": trail_id}) == 1


def test_versioned_android_upload_uses_authentication_http_codes(database):
    create_android_user(database)
    response = modern_upload(payload("wrong"), database, Settings(_env_file=None))
    assert response.status_code == 401
    assert response_json(response)["error_code"] == "invalid_credentials"


def test_versioned_android_upload_reports_photo_count_limit(database):
    create_android_user(database, quotas={"photos_per_trail": 0})
    response = modern_upload(payload(), database, Settings(_env_file=None))
    body = response_json(response)
    assert response.status_code == 413
    assert body["error_code"] == "photo_count_exceeded"
    assert body["details"] == {"photo_count": 1, "limit": 0}


def test_versioned_android_upload_reports_large_photo_details(database):
    create_android_user(database, quotas={"image_bytes": 3})
    response = modern_upload(payload(), database, Settings(_env_file=None))
    body = response_json(response)
    assert response.status_code == 413
    assert body["error_code"] == "photo_too_large"
    assert body["details"]["photo_index"] == 0
    assert body["details"]["filename"] == "photo.gif"
    assert body["details"]["limit_bytes"] == 3


def test_versioned_android_upload_reports_invalid_photo_data(database):
    create_android_user(database)
    body = payload()
    body["newtrail"][3]["pictures"][0]["file"] = "not-base64!"
    response = modern_upload(body, database, Settings(_env_file=None))
    assert response.status_code == 422
    assert response_json(response)["error_code"] == "invalid_photo_data"


def test_versioned_android_upload_reports_account_state(database):
    create_android_user(database, suspended=True)
    response = modern_upload(payload(), database, Settings(_env_file=None))
    assert response.status_code == 403
    assert response_json(response)["error_code"] == "account_suspended"


def test_versioned_android_upload_reports_invalid_payload(database):
    response = modern_upload({"newtrail": []}, database, Settings(_env_file=None))
    assert response.status_code == 422
    assert response_json(response)["error_code"] == "invalid_upload_structure"


def test_versioned_android_upload_reports_combined_upload_limit(database):
    create_android_user(database, quotas={"image_bytes": 100, "upload_bytes": 5})
    response = modern_upload(payload(), database, Settings(_env_file=None))
    body = response_json(response)
    assert response.status_code == 413
    assert body["error_code"] == "upload_too_large"
    assert body["details"]["limit_bytes"] == 5


def test_versioned_android_upload_reports_account_storage_limit(database):
    user_id = create_android_user(
        database,
        quotas={"image_bytes": 100, "upload_bytes": 100, "account_storage_bytes": 15},
    )
    trail_id = database.trails.insert_one({"userid": user_id}).inserted_id
    image_id = database.images.insert_one({"img": Binary(b"existing10")}).inserted_id
    database.pictures.insert_one({"trailid": trail_id, "imageid": image_id})
    response = modern_upload(payload(), database, Settings(_env_file=None))
    body = response_json(response)
    assert response.status_code == 413
    assert body["error_code"] == "account_storage_exceeded"
    assert body["details"]["used_bytes"] == 10
    assert body["details"]["limit_bytes"] == 15


def test_versioned_android_multipart_upload_stores_unchanged_documents(database):
    create_android_user(database)
    manifest = copy.deepcopy(payload())
    manifest["newtrail"][3]["pictures"][0].pop("file")
    photo = b"binary-photo"
    response = asyncio.run(
        android_upload_trail(
            multipart_request(manifest, [("photo.gif", photo)], manifest_as_file=True),
            database,
            Settings(_env_file=None),
        )
    )
    assert response.status_code == 201
    trail_id = ObjectId(response_json(response)["trailid"])
    picture = database.pictures.find_one({"trailid": trail_id})
    assert picture["filename"] == "photo.gif"
    assert bytes(database.images.find_one({"_id": picture["imageid"]})["img"]) == photo


def test_versioned_android_upload_requires_manifest(database):
    response = asyncio.run(
        android_upload_trail(
            api_request(b"--empty--", "multipart/form-data; boundary=empty"),
            database,
            Settings(_env_file=None),
        )
    )
    assert response.status_code == 400
    assert response_json(response)["error_code"] == "missing_manifest"


def test_versioned_android_upload_requires_matching_photo_parts(database):
    manifest = copy.deepcopy(payload())
    manifest["newtrail"][3]["pictures"][0].pop("file")
    response = asyncio.run(
        android_upload_trail(
            multipart_request(manifest, []), database, Settings(_env_file=None)
        )
    )
    assert response.status_code == 422
    assert response_json(response)["error_code"] == "photo_file_count_mismatch"


def test_versioned_android_upload_requires_multipart_content_type(database):
    response = asyncio.run(
        android_upload_trail(
            api_request(b"{}", "application/json"), database, Settings(_env_file=None)
        )
    )
    assert response.status_code == 415
    assert response_json(response)["error_code"] == "unsupported_media_type"
