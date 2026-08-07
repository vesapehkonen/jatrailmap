import base64

from bson.binary import Binary
from fastapi import HTTPException
import pytest

from app.image_migration import migrate_images
from app.routes.trails import image


def test_migration_converts_base64_and_skips_binary(database):
    raw = b"\x89PNG\r\n\x1a\nimage-data"
    legacy_id = database.images.insert_one(
        {"img": base64.b64encode(raw).decode()}
    ).inserted_id
    binary_id = database.images.insert_one({"img": Binary(b"already")}).inserted_id
    result = migrate_images(database)
    assert result.scanned == 2
    assert result.migrated == 1
    assert result.already_binary == 1
    assert bytes(database.images.find_one({"_id": legacy_id})["img"]) == raw
    assert bytes(database.images.find_one({"_id": binary_id})["img"]) == b"already"
    second = migrate_images(database)
    assert second.migrated == 0
    assert second.already_binary == 2


def test_migration_dry_run_does_not_write(database):
    encoded = base64.b64encode(b"photo").decode()
    image_id = database.images.insert_one({"img": encoded}).inserted_id
    result = migrate_images(database, dry_run=True)
    assert result.migrated == 1
    assert database.images.find_one({"_id": image_id})["img"] == encoded


def test_migration_leaves_invalid_records_unchanged(database):
    image_id = database.images.insert_one({"img": "not-valid-base64!"}).inserted_id
    result = migrate_images(database)
    assert result.invalid == 1
    assert database.images.find_one({"_id": image_id})["img"] == "not-valid-base64!"


def test_image_endpoint_reads_only_binary_storage(database):
    trail_id = database.trails.insert_one({"access": "public"}).inserted_id
    binary_id = database.images.insert_one({"img": Binary(b"GIF89a-image")}).inserted_id
    database.pictures.insert_one(
        {"trailid": trail_id, "imageid": binary_id, "access": "public"}
    )
    response = image(str(binary_id), database, None)
    assert response.body == b"GIF89a-image"
    assert response.media_type == "image/gif"

    legacy_id = database.images.insert_one({"img": base64.b64encode(b"old").decode()}).inserted_id
    database.pictures.insert_one(
        {"trailid": trail_id, "imageid": legacy_id, "access": "public"}
    )
    with pytest.raises(HTTPException) as error:
        image(str(legacy_id), database, None)
    assert error.value.status_code == 500
