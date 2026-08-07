from app.profiles import public_profile


def test_public_profile_does_not_expose_login_username_by_default():
    assert public_profile({"username": "private-login"}) == {
        "display_name": "JaTrail member",
        "location": "",
    }


def test_public_profile_shows_only_individually_enabled_fields():
    user = {
        "username": "private-login",
        "display_name": "Vesa",
        "profile_location": "Portland, Oregon",
        "show_name_on_public_trails": True,
        "show_location_on_public_trails": False,
    }
    assert public_profile(user) == {"display_name": "Vesa", "location": ""}
    user["show_location_on_public_trails"] = True
    assert public_profile(user) == {
        "display_name": "Vesa",
        "location": "Portland, Oregon",
    }


def test_empty_enabled_display_name_uses_private_fallback():
    assert public_profile(
        {"display_name": "  ", "show_name_on_public_trails": True}
    )["display_name"] == "JaTrail member"
