from typing import Any


def public_profile(user: dict[str, Any] | None) -> dict[str, str]:
    user = user or {}
    display_name = ""
    location = ""
    if user.get("show_name_on_public_trails") is True:
        display_name = str(user.get("display_name") or "").strip()
    if user.get("show_location_on_public_trails") is True:
        location = str(user.get("profile_location") or "").strip()
    return {
        "display_name": display_name or "JaTrail member",
        "location": location,
    }
