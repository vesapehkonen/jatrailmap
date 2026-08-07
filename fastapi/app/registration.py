from typing import Any

from pymongo.database import Database

from .config import Settings


def registration_settings(db: Database[Any], settings: Settings) -> dict[str, Any]:
    stored = db.app_settings.find_one({"_id": "registration"}) or {}
    return {
        "enabled": stored.get("enabled", True),
        "approval_required": stored.get("approval_required", False),
        "account_storage_mb": stored.get(
            "account_storage_bytes", settings.max_account_storage_bytes
        )
        // (1024 * 1024),
        "image_mb": stored.get("image_bytes", settings.max_image_bytes) // (1024 * 1024),
        "photos_per_trail": stored.get("photos_per_trail", settings.max_photos_per_trail),
        "upload_mb": stored.get("upload_bytes", settings.max_upload_bytes) // (1024 * 1024),
    }
