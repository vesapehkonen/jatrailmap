import bcrypt
import pytest
from bson import ObjectId
from fastapi import HTTPException

from app.auth import verify_password
from app.cleanup import delete_account_records
from app.models import GroupCreate, GroupUpdate, PasswordUpdate, ProfileUpdate
from app.routes.accounts import update_password, update_profile
from app.routes.groups import create_group, delete_group, update_group


def create_user(database, username: str, password: str = "password123") -> ObjectId:
    return database.users.insert_one(
        {
            "username": username,
            "password": bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode(),
            "fullname": "",
        }
    ).inserted_id


def test_profile_update_uses_authenticated_identity(database):
    owner = create_user(database, "owner")
    other = create_user(database, "other")
    update_profile(
        ProfileUpdate(
            display_name="Owner Name",
            profile_location="Portland, Oregon",
            show_name_on_public_trails=True,
            show_location_on_public_trails=False,
            current_password="password123",
        ),
        database,
        owner,
    )
    profile = database.users.find_one({"_id": owner})
    assert profile["display_name"] == "Owner Name"
    assert profile["profile_location"] == "Portland, Oregon"
    assert profile["show_name_on_public_trails"] is True
    assert profile["show_location_on_public_trails"] is False
    assert database.users.find_one({"_id": other}).get("display_name") is None


def test_password_change_invalidates_sessions(database):
    user_id = create_user(database, "owner")
    database.sessions.insert_many([{"userid": user_id}, {"userid": user_id}])
    update_password(
        PasswordUpdate(current_password="password123", new_password="new-password"),
        database,
        user_id,
    )
    assert verify_password("new-password", database.users.find_one({"_id": user_id})["password"])
    assert database.sessions.count_documents({"userid": user_id}) == 0


def test_group_writes_object_ids_and_only_owner_can_update(database):
    owner = create_user(database, "owner")
    member = create_user(database, "member")
    group_id = ObjectId(
        create_group(GroupCreate(name="Friends", members=[str(member)]), database, owner)["groupid"]
    )
    assert database.groups.find_one({"_id": group_id})["members"] == [member]
    with pytest.raises(HTTPException) as error:
        update_group(
            str(group_id), GroupUpdate(name="Stolen", members=[]), database, ObjectId()
        )
    assert error.value.status_code == 404


def test_deleting_last_group_makes_referenced_content_private(database):
    owner = create_user(database, "owner")
    group_id = database.groups.insert_one({"ownerid": owner, "name": "Friends", "members": []}).inserted_id
    trail_id = database.trails.insert_one(
        {"userid": owner, "access": "group", "groups": [group_id]}
    ).inserted_id
    picture_id = database.pictures.insert_one(
        {"trailid": trail_id, "access": "group", "groups": [group_id]}
    ).inserted_id
    delete_group(str(group_id), database, owner)
    assert database.trails.find_one({"_id": trail_id})["access"] == "private"
    assert database.pictures.find_one({"_id": picture_id})["access"] == "private"


def test_account_deletion_removes_owned_trails_images_groups_and_sessions(database):
    owner = create_user(database, "owner")
    member = create_user(database, "member")
    owned_group = database.groups.insert_one({"ownerid": owner, "members": []}).inserted_id
    foreign_group = database.groups.insert_one(
        {"ownerid": member, "members": [owner, str(owner)]}
    ).inserted_id
    trail_id = database.trails.insert_one(
        {"userid": owner, "access": "group", "groups": [owned_group]}
    ).inserted_id
    image_id = database.images.insert_one({"img": "data"}).inserted_id
    database.locations.insert_one({"trailid": trail_id})
    database.pictures.insert_one({"trailid": trail_id, "imageid": image_id})
    database.sessions.insert_one({"userid": owner})
    delete_account_records(database, owner)
    assert database.users.find_one({"_id": owner}) is None
    assert database.trails.find_one({"_id": trail_id}) is None
    assert database.locations.count_documents({"trailid": trail_id}) == 0
    assert database.pictures.count_documents({"trailid": trail_id}) == 0
    assert database.images.find_one({"_id": image_id}) is None
    assert database.groups.find_one({"_id": owned_group}) is None
    assert database.groups.find_one({"_id": foreign_group})["members"] == []
    assert database.sessions.count_documents({"userid": owner}) == 0
    assert database.users.find_one({"_id": member}) is not None
