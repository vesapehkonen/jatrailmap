import pytest
from bson import ObjectId
from fastapi import HTTPException

from app.models import CoordinateUpdate, PictureUpdate, TrailUpdate, VisibilityUpdate
from app.routes.trails import (
    delete_picture,
    delete_trail,
    update_location,
    update_picture,
    update_picture_permissions,
    update_trail,
    update_trail_permissions,
)


def make_trail(database, owner: ObjectId, name: str = "Trail") -> ObjectId:
    return database.trails.insert_one(
        {"userid": owner, "trailname": name, "location": "", "description": "", "access": "private"}
    ).inserted_id


def test_owner_can_update_metadata_and_non_owner_cannot(database):
    owner = ObjectId()
    trail_id = make_trail(database, owner)
    update_trail(str(trail_id), TrailUpdate(trailname="Updated", location="WA", description="Nice"), database, owner)
    assert database.trails.find_one({"_id": trail_id})["trailname"] == "Updated"
    with pytest.raises(HTTPException) as error:
        update_trail(str(trail_id), TrailUpdate(trailname="Bad", location="", description=""), database, ObjectId())
    assert error.value.status_code == 404


def test_location_and_picture_must_belong_to_url_trail(database):
    owner = ObjectId()
    first = make_trail(database, owner, "First")
    second = make_trail(database, owner, "Second")
    location_id = database.locations.insert_one(
        {"trailid": second, "loc": {"type": "Point", "coordinates": [1.0, 2.0, 3.0]}}
    ).inserted_id
    picture_id = database.pictures.insert_one(
        {"trailid": second, "loc": {"type": "Point", "coordinates": [1.0, 2.0, 3.0]}}
    ).inserted_id
    with pytest.raises(HTTPException):
        update_location(str(first), str(location_id), CoordinateUpdate(longitude=3, latitude=4), database, owner)
    with pytest.raises(HTTPException):
        update_picture(str(first), str(picture_id), PictureUpdate(longitude=3, latitude=4), database, owner)
    assert database.locations.find_one({"_id": location_id})["loc"]["coordinates"][:2] == [1.0, 2.0]


def test_picture_location_is_saved(database):
    owner = ObjectId()
    trail_id = make_trail(database, owner)
    picture_id = database.pictures.insert_one(
        {
            "trailid": trail_id,
            "picturename": "View",
            "description": "",
            "loc": {"type": "Point", "coordinates": [1.0, 2.0, 50.0]},
        }
    ).inserted_id
    update_picture(
        str(trail_id),
        str(picture_id),
        PictureUpdate(
            picturename="View",
            description="",
            longitude=-122.25,
            latitude=47.75,
        ),
        database,
        owner,
    )
    coordinates = database.pictures.find_one({"_id": picture_id})["loc"]["coordinates"]
    assert coordinates == [-122.25, 47.75, 50.0]


def test_group_permissions_accept_only_groups_owned_by_user(database):
    owner = ObjectId()
    trail_id = make_trail(database, owner)
    own_group = database.groups.insert_one({"ownerid": owner, "name": "Own"}).inserted_id
    foreign_group = database.groups.insert_one({"ownerid": ObjectId(), "name": "Other"}).inserted_id
    update_trail_permissions(
        str(trail_id), VisibilityUpdate(access="group", groups=[str(own_group)]), database, owner
    )
    assert database.trails.find_one({"_id": trail_id})["groups"] == [own_group]
    with pytest.raises(HTTPException) as error:
        update_trail_permissions(
            str(trail_id), VisibilityUpdate(access="group", groups=[str(foreign_group)]), database, owner
        )
    assert error.value.status_code == 422


def test_picture_delete_removes_image_and_metadata(database):
    owner = ObjectId()
    trail_id = make_trail(database, owner)
    image_id = database.images.insert_one({"img": "data"}).inserted_id
    picture_id = database.pictures.insert_one(
        {"trailid": trail_id, "imageid": image_id, "loc": {"type": "Point", "coordinates": [1, 2]}}
    ).inserted_id
    delete_picture(str(trail_id), str(picture_id), database, owner)
    assert database.pictures.find_one({"_id": picture_id}) is None
    assert database.images.find_one({"_id": image_id}) is None


def test_trail_delete_removes_all_dependents_and_unrelated_data_survives(database):
    owner = ObjectId()
    trail_id = make_trail(database, owner)
    other_id = make_trail(database, owner, "Keep")
    image_id = database.images.insert_one({"img": "data"}).inserted_id
    database.locations.insert_many([{"trailid": trail_id}, {"trailid": other_id}])
    database.pictures.insert_one({"trailid": trail_id, "imageid": image_id})
    delete_trail(str(trail_id), database, owner)
    assert database.trails.find_one({"_id": trail_id}) is None
    assert database.locations.count_documents({"trailid": trail_id}) == 0
    assert database.pictures.count_documents({"trailid": trail_id}) == 0
    assert database.images.find_one({"_id": image_id}) is None
    assert database.trails.find_one({"_id": other_id}) is not None
    assert database.locations.count_documents({"trailid": other_id}) == 1
