from bson import ObjectId

from app.access import TrailAccessPolicy


def test_public_private_and_owner_access(database):
    owner = ObjectId()
    stranger = ObjectId()
    policy = TrailAccessPolicy(database)
    assert policy.can_view({"userid": owner, "access": "public"}, None)
    assert policy.can_view({"userid": owner, "access": "private"}, owner)
    assert not policy.can_view({"userid": owner, "access": "private"}, stranger)
    assert not policy.can_view({"userid": owner, "access": "private"}, None)


def test_group_access_supports_legacy_string_member_ids(database):
    user_id = ObjectId()
    group_id = database.groups.insert_one({"members": [str(user_id)]}).inserted_id
    trail = {"userid": ObjectId(), "access": "group", "groups": [group_id]}
    assert TrailAccessPolicy(database).can_view(trail, user_id)


def test_picture_visibility_cannot_override_private_trail(database):
    trail = {"userid": ObjectId(), "access": "private"}
    picture = {"access": "public"}
    assert not TrailAccessPolicy(database).can_view_picture(trail, picture, None)
