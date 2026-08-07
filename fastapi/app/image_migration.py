import base64
import binascii
from dataclasses import dataclass
from typing import Any

from bson.binary import Binary
from pymongo.database import Database


@dataclass
class MigrationResult:
    scanned: int = 0
    migrated: int = 0
    already_binary: int = 0
    invalid: int = 0
    changed_before_update: int = 0


def migrate_images(db: Database[Any], *, dry_run: bool = False) -> MigrationResult:
    result = MigrationResult()
    for image in db.images.find({}, {"img": 1}):
        result.scanned += 1
        value = image.get("img")
        if isinstance(value, bytes):
            result.already_binary += 1
            continue
        if not isinstance(value, str):
            result.invalid += 1
            print(f"Invalid image record {image['_id']}: img is neither Base64 text nor binary")
            continue
        try:
            decoded = base64.b64decode(value, validate=True)
        except (binascii.Error, ValueError):
            result.invalid += 1
            print(f"Invalid image record {image['_id']}: img is not valid Base64")
            continue
        if dry_run:
            result.migrated += 1
            continue
        update = db.images.update_one(
            {"_id": image["_id"], "img": value}, {"$set": {"img": Binary(decoded)}}
        )
        if update.modified_count == 1:
            result.migrated += 1
        else:
            result.changed_before_update += 1
    return result
