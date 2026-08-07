from dataclasses import dataclass
from typing import Any

from pymongo.database import Database


LEGACY_PROFILE_FIELDS = ("fullname", "city", "state", "country")


@dataclass
class ProfileMigrationResult:
    scanned: int = 0
    migrated: int = 0
    unchanged: int = 0


def _text(value: Any) -> str:
    return value.strip() if isinstance(value, str) else ""


def _legacy_location(user: dict[str, Any]) -> str:
    parts: list[str] = []
    for field in ("city", "state", "country"):
        value = _text(user.get(field))
        if value and value.casefold() not in {part.casefold() for part in parts}:
            parts.append(value)
    return ", ".join(parts)


def profile_update(user: dict[str, Any]) -> dict[str, Any]:
    """Return the idempotent update needed to convert one legacy user record."""
    set_fields: dict[str, Any] = {}
    if "display_name" not in user:
        set_fields["display_name"] = _text(user.get("fullname"))
    if "profile_location" not in user:
        set_fields["profile_location"] = _legacy_location(user)
    if "show_name_on_public_trails" not in user:
        set_fields["show_name_on_public_trails"] = False
    if "show_location_on_public_trails" not in user:
        set_fields["show_location_on_public_trails"] = False
    if "email" not in user:
        set_fields["email"] = ""

    update: dict[str, Any] = {}
    if set_fields:
        update["$set"] = set_fields
    legacy_present = {field: "" for field in LEGACY_PROFILE_FIELDS if field in user}
    if legacy_present:
        update["$unset"] = legacy_present
    return update


def migrate_user_profiles(
    db: Database[Any], *, dry_run: bool = False
) -> ProfileMigrationResult:
    result = ProfileMigrationResult()
    for user in db.users.find({}):
        result.scanned += 1
        update = profile_update(user)
        if not update:
            result.unchanged += 1
            continue
        if not dry_run:
            db.users.update_one({"_id": user["_id"]}, update)
        result.migrated += 1
    return result
