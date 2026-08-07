from bson import ObjectId

from app.routes.trails import visible_trails_query, with_trail_search


def seed_trails(database):
    user = ObjectId()
    other = ObjectId()
    group = ObjectId()
    database.trails.insert_many(
        [
            {"trailname": "My private route", "location": "Seattle", "userid": user, "access": "private"},
            {"trailname": "Shared ridge", "location": "Portland", "userid": other, "access": "group", "groups": [group]},
            {"trailname": "Public coast", "location": "Astoria", "userid": other, "access": "public"},
            {"trailname": "Hidden route", "location": "Tacoma", "userid": other, "access": "private"},
        ]
    )
    return user, group


def names(database, query):
    return {trail["trailname"] for trail in database.trails.find(query)}


def test_all_visible_scope_includes_owned_shared_and_public(database):
    user, group = seed_trails(database)
    assert names(database, visible_trails_query(user, [group])) == {
        "My private route",
        "Shared ridge",
        "Public coast",
    }


def test_anonymous_and_public_scope_only_include_public(database):
    user, group = seed_trails(database)
    assert names(database, visible_trails_query(None, [])) == {"Public coast"}
    assert names(database, visible_trails_query(user, [group], "public")) == {"Public coast"}


def test_personal_and_shared_scopes_are_separate(database):
    user, group = seed_trails(database)
    assert names(database, visible_trails_query(user, [group], "mine")) == {
        "My private route"
    }
    assert names(database, visible_trails_query(user, [group], "shared")) == {
        "Shared ridge"
    }


def test_search_matches_name_or_location_and_escapes_regex(database):
    user, group = seed_trails(database)
    visible = visible_trails_query(user, [group])
    assert names(database, with_trail_search(visible, "astoria")) == {"Public coast"}
    assert names(database, with_trail_search(visible, ".*")) == set()
