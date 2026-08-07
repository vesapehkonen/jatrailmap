from typing import Any

from bson import ObjectId
from bson.binary import Binary
from pymongo.database import Database

from .config import Settings


def binary_image_size(value: Any) -> int:
    return len(value) if isinstance(value, (bytes, Binary)) else 0


def account_storage_bytes(db: Database[Any], user_id: ObjectId) -> int:
    trail_ids = [trail["_id"] for trail in db.trails.find({"userid": user_id}, {"_id": 1})]
    if not trail_ids:
        return 0
    image_ids = [
        picture["imageid"]
        for picture in db.pictures.find({"trailid": {"$in": trail_ids}}, {"imageid": 1})
        if picture.get("imageid") is not None
    ]
    return sum(
        binary_image_size(image.get("img"))
        for image in db.images.find({"_id": {"$in": image_ids}}, {"img": 1})
    )


def user_limits(user: dict[str, Any], settings: Settings) -> dict[str, int]:
    overrides = user.get("quotas") or {}
    return {
        "account_storage_bytes": int(
            overrides.get("account_storage_bytes", settings.max_account_storage_bytes)
        ),
        "image_bytes": int(overrides.get("image_bytes", settings.max_image_bytes)),
        "photos_per_trail": int(
            overrides.get("photos_per_trail", settings.max_photos_per_trail)
        ),
        "upload_bytes": int(overrides.get("upload_bytes", settings.max_upload_bytes)),
    }
