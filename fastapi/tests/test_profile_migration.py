from app.profile_migration import migrate_user_profiles, profile_update


def test_profile_update_maps_legacy_fields_and_keeps_them_private():
    update = profile_update(
        {
            "fullname": "  Vesa Hiker  ",
            "city": "Portland",
            "state": "Oregon",
            "country": "USA",
        }
    )

    assert update["$set"] == {
        "display_name": "Vesa Hiker",
        "profile_location": "Portland, Oregon, USA",
        "show_name_on_public_trails": False,
        "show_location_on_public_trails": False,
        "email": "",
    }
    assert set(update["$unset"]) == {"fullname", "city", "state", "country"}


def test_migration_preserves_new_values_and_removes_legacy_fields(database):
    user_id = database.users.insert_one(
        {
            "username": "vesa",
            "fullname": "Old Name",
            "city": "Old City",
            "display_name": "Chosen Name",
            "profile_location": "Chosen Location",
            "show_name_on_public_trails": True,
            "show_location_on_public_trails": False,
            "email": "private@example.com",
        }
    ).inserted_id

    first = migrate_user_profiles(database)
    user = database.users.find_one({"_id": user_id})
    assert first.migrated == 1
    assert user["display_name"] == "Chosen Name"
    assert user["profile_location"] == "Chosen Location"
    assert user["show_name_on_public_trails"] is True
    assert "fullname" not in user
    assert "city" not in user

    second = migrate_user_profiles(database)
    assert second.migrated == 0
    assert second.unchanged == 1


def test_migration_dry_run_does_not_write(database):
    user_id = database.users.insert_one(
        {"username": "legacy", "fullname": "Legacy User", "country": "Finland"}
    ).inserted_id

    result = migrate_user_profiles(database, dry_run=True)
    user = database.users.find_one({"_id": user_id})
    assert result.migrated == 1
    assert user["fullname"] == "Legacy User"
    assert "display_name" not in user
