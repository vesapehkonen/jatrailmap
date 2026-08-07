import base64
import binascii
import math
import re
import json
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

from bson import ObjectId
from bson.binary import Binary
from bson.errors import InvalidId
from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import HTMLResponse, JSONResponse, Response
from fastapi.templating import Jinja2Templates
from pydantic import ValidationError
from pymongo.database import Database
from starlette.datastructures import UploadFile as StarletteUploadFile

from ..access import TrailAccessPolicy
from ..auth import optional_user_id, require_csrf, require_user_id
from ..config import Settings, get_settings
from ..cleanup import delete_trail_records
from ..database import get_db
from ..geo import friendly_date, friendly_duration, trail_statistics
from ..models import (
    LegacyTrailUpload,
    LocationCollection,
    PictureCollection,
    PictureUpdate,
    TrailInfo,
    TrailUpdate,
    UserInfo,
    VisibilityUpdate,
    CoordinateUpdate,
)
from ..quotas import account_storage_bytes, user_limits
from ..profiles import public_profile
from ..auth import verify_password

router = APIRouter()
templates = Jinja2Templates(directory=str(__file__).rsplit("/routes/", 1)[0] + "/templates")
TRAILS_PER_PAGE = 12


def object_id(value: str) -> ObjectId:
    try:
        return ObjectId(value)
    except (InvalidId, TypeError) as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not found") from exc


def json_value(value: Any) -> Any:
    if isinstance(value, ObjectId):
        return str(value)
    if isinstance(value, datetime):
        return value.isoformat()
    if isinstance(value, dict):
        return {key: json_value(item) for key, item in value.items()}
    if isinstance(value, list):
        return [json_value(item) for item in value]
    return value


def visible_trail_or_404(
    db: Database[Any], trail_id: ObjectId, user_id: ObjectId | None
) -> dict[str, Any]:
    trail = db.trails.find_one({"_id": trail_id})
    if trail is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Trail not found")
    if not TrailAccessPolicy(db).can_view(trail, user_id):
        # Do not disclose the existence of private trails.
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Trail not found")
    return trail


def owned_trail_or_404(
    db: Database[Any], trail_id: ObjectId, user_id: ObjectId
) -> dict[str, Any]:
    trail = db.trails.find_one({"_id": trail_id, "userid": user_id})
    if trail is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Trail not found")
    return trail


def owned_groups_or_422(
    db: Database[Any], group_ids: list[str], user_id: ObjectId, access: str
) -> list[ObjectId]:
    if access != "group":
        return []
    if not group_ids:
        raise HTTPException(status_code=422, detail="Group visibility requires at least one group")
    try:
        object_ids = list(dict.fromkeys(ObjectId(value) for value in group_ids))
    except (InvalidId, TypeError) as exc:
        raise HTTPException(status_code=422, detail="Invalid group") from exc
    found = db.groups.count_documents({"_id": {"$in": object_ids}, "ownerid": user_id})
    if found != len(object_ids):
        raise HTTPException(status_code=422, detail="A selected group is not owned by this user")
    return object_ids


def joined_group_ids(db: Database[Any], user_id: ObjectId) -> list[ObjectId]:
    return [
        group["_id"]
        for group in db.groups.find(
            {"$or": [{"members": user_id}, {"members": str(user_id)}]}, {"_id": 1}
        )
    ]


def visible_trails_query(
    user_id: ObjectId | None, group_ids: list[ObjectId], scope: str = "all"
) -> dict[str, Any]:
    if user_id is None or scope == "public":
        return {"access": "public"}
    if scope == "mine":
        return {"userid": user_id}
    shared = {
        "access": "group",
        "groups": {"$in": group_ids},
        "userid": {"$ne": user_id},
    }
    if scope == "shared":
        return shared
    return {"$or": [{"userid": user_id}, {"access": "public"}, shared]}


def with_trail_search(query: dict[str, Any], search: str) -> dict[str, Any]:
    if not search:
        return query
    pattern = re.compile(re.escape(search), re.IGNORECASE)
    return {"$and": [query, {"$or": [{"trailname": pattern}, {"location": pattern}]}]}


def prepare_trail_cards(
    db: Database[Any], trails: list[dict[str, Any]], user_id: ObjectId | None
) -> None:
    policy = TrailAccessPolicy(db)
    owner_ids = list({trail.get("userid") for trail in trails if trail.get("userid")})
    owners = {
        owner["_id"]: public_profile(owner)
        for owner in db.users.find(
            {"_id": {"$in": owner_ids}},
            {
                "display_name": 1,
                "profile_location": 1,
                "show_name_on_public_trails": 1,
                "show_location_on_public_trails": 1,
            },
        )
    }
    for trail in trails:
        trail["owner_profile"] = owners.get(trail.get("userid"), public_profile(None))
        trail["display_date"] = friendly_date(trail.get("date"))
        card_locations = list(
            db.locations.find(
                {"trailid": trail["_id"]},
                {"loc.coordinates": 1, "timestamp": 1},
            ).sort("timestamp", 1)
        )
        trail["display_distance"] = (
            f"{trail_statistics(card_locations)['distance_miles']:.1f} mi"
        )
        trail["cover_imageid"] = None
        for picture in db.pictures.find({"trailid": trail["_id"]}).sort("timestamp", 1):
            if policy.can_view_picture(trail, picture, user_id) and picture.get("imageid"):
                trail["cover_imageid"] = picture["imageid"]
                break


@router.get("/", response_class=HTMLResponse)
def index(
    request: Request,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId | None = Depends(optional_user_id),
) -> HTMLResponse:
    context: dict[str, Any] = {"authenticated": user_id is not None}
    if user_id is None:
        trails = list(db.trails.find({"access": "public"}).sort("date", -1).limit(10))
        prepare_trail_cards(db, trails, None)
        context["trails"] = trails
    else:
        group_ids = joined_group_ids(db, user_id)
        own_trails = list(
            db.trails.find(visible_trails_query(user_id, group_ids, "mine")).sort("date", -1)
        )
        shared_trails = list(
            db.trails.find(visible_trails_query(user_id, group_ids, "shared")).sort("date", -1)
        )
        prepare_trail_cards(db, own_trails, user_id)
        prepare_trail_cards(db, shared_trails, user_id)
        context.update({"own_trails": own_trails, "shared_trails": shared_trails})
    return templates.TemplateResponse(
        request, "index.html", context
    )


@router.get("/trails", response_class=HTMLResponse)
def browse_trails(
    request: Request,
    q: str = "",
    scope: str = "public",
    page: int = 1,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId | None = Depends(optional_user_id),
) -> HTMLResponse:
    q = q.strip()[:100]
    allowed_scopes = {"public"} if user_id is None else {"public", "mine", "shared", "all"}
    if scope not in allowed_scopes:
        scope = "public"
    page = max(1, page)
    group_ids = joined_group_ids(db, user_id) if user_id is not None else []
    query = with_trail_search(visible_trails_query(user_id, group_ids, scope), q)
    total = db.trails.count_documents(query)
    total_pages = max(1, math.ceil(total / TRAILS_PER_PAGE))
    page = min(page, total_pages)
    trails = list(
        db.trails.find(query)
        .sort("date", -1)
        .skip((page - 1) * TRAILS_PER_PAGE)
        .limit(TRAILS_PER_PAGE)
    )
    prepare_trail_cards(db, trails, user_id)
    return templates.TemplateResponse(
        request,
        "browse_trails.html",
        {
            "authenticated": user_id is not None,
            "trails": trails,
            "q": q,
            "scope": scope,
            "page": page,
            "total": total,
            "total_pages": total_pages,
        },
    )


@router.get("/trail/{trail_id}", response_class=HTMLResponse)
def trail_page(
    trail_id: str,
    request: Request,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId | None = Depends(optional_user_id),
) -> HTMLResponse:
    trail = visible_trail_or_404(db, object_id(trail_id), user_id)
    owner = db.users.find_one(
        {"_id": trail.get("userid")},
        {
            "display_name": 1,
            "profile_location": 1,
            "show_name_on_public_trails": 1,
            "show_location_on_public_trails": 1,
        },
    )
    locations = list(db.locations.find({"trailid": trail["_id"]}).sort("timestamp", 1))
    statistics = trail_statistics(locations)
    policy = TrailAccessPolicy(db)
    photo_count = sum(
        1
        for picture in db.pictures.find({"trailid": trail["_id"]})
        if policy.can_view_picture(trail, picture, user_id)
    )
    return templates.TemplateResponse(
        request,
        "trail.html",
        {
            "trail": trail,
            "owner_profile": public_profile(owner),
            "is_owner": TrailAccessPolicy(db).is_owner(trail, user_id),
            "authenticated": user_id is not None,
            "display_date": friendly_date(trail.get("date")),
            "distance": f"{statistics['distance_miles']:.1f} miles",
            "elapsed": friendly_duration(statistics["elapsed_seconds"]),
            "has_elevation": statistics["elevation_points"] >= 2,
            "elevation_gain": f"{statistics['elevation_gain_feet']:,.0f} ft",
            "minimum_elevation": (
                f"{statistics['minimum_elevation_feet']:,.0f} ft"
                if statistics["minimum_elevation_feet"] is not None
                else "Unavailable"
            ),
            "maximum_elevation": (
                f"{statistics['maximum_elevation_feet']:,.0f} ft"
                if statistics["maximum_elevation_feet"] is not None
                else "Unavailable"
            ),
            "photo_count": photo_count,
        },
    )


@router.get("/api/v1/trails/{trail_id}/track")
def trail_track(
    trail_id: str,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId | None = Depends(optional_user_id),
) -> JSONResponse:
    oid = object_id(trail_id)
    trail = visible_trail_or_404(db, oid, user_id)
    policy = TrailAccessPolicy(db)
    locations = list(db.locations.find({"trailid": oid}).sort("timestamp", 1))
    pictures = [
        picture
        for picture in db.pictures.find({"trailid": oid}).sort("timestamp", 1)
        if policy.can_view_picture(trail, picture, user_id)
    ]
    for item in locations + pictures:
        item["id"] = item["_id"]
    return JSONResponse(json_value({"status": "ok", "locs": locations, "pics": pictures}))


def detect_image_type(data: bytes) -> str:
    if data.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if data.startswith((b"GIF87a", b"GIF89a")):
        return "image/gif"
    if data.startswith(b"RIFF") and data[8:12] == b"WEBP":
        return "image/webp"
    return "application/octet-stream"


@router.get("/image/{image_id}")
def image(
    image_id: str,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId | None = Depends(optional_user_id),
) -> Response:
    oid = object_id(image_id)
    picture = db.pictures.find_one({"imageid": oid})
    if picture is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Image not found")
    trail = db.trails.find_one({"_id": picture.get("trailid")})
    if trail is None or not TrailAccessPolicy(db).can_view_picture(trail, picture, user_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Image not found")
    image_doc = db.images.find_one({"_id": oid}, {"img": 1})
    if image_doc is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Image not found")
    stored = image_doc.get("img")
    if not isinstance(stored, (bytes, Binary)):
        raise HTTPException(status_code=500, detail="Stored image is not BSON binary")
    data = bytes(stored)
    return Response(data, media_type=detect_image_type(data), headers={"X-Content-Type-Options": "nosniff"})


@router.get("/trail/{trail_id}/edit", response_class=HTMLResponse)
def edit_trail_page(
    trail_id: str,
    request: Request,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> HTMLResponse:
    trail = owned_trail_or_404(db, object_id(trail_id), user_id)
    groups = list(db.groups.find({"ownerid": user_id}, {"name": 1}).sort("name", 1))
    locations = list(db.locations.find({"trailid": trail["_id"]}).sort("timestamp", 1))
    statistics = trail_statistics(locations)
    return templates.TemplateResponse(
        request,
        "edit_trail.html",
        {
            "trail": trail,
            "groups": groups,
            "authenticated": True,
            "display_date": friendly_date(trail.get("date")),
            "distance": f"{statistics['distance_miles']:.1f} miles",
            "elapsed": friendly_duration(statistics["elapsed_seconds"]),
        },
    )


@router.patch("/api/v1/trails/{trail_id}", dependencies=[Depends(require_csrf)])
def update_trail(
    trail_id: str,
    update: TrailUpdate,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> dict[str, str]:
    oid = object_id(trail_id)
    owned_trail_or_404(db, oid, user_id)
    result = db.trails.update_one(
        {"_id": oid, "userid": user_id}, {"$set": update.model_dump()}
    )
    if result.matched_count != 1:
        raise HTTPException(status_code=404, detail="Trail not found")
    return {"status": "ok"}


@router.patch(
    "/api/v1/trails/{trail_id}/locations/{location_id}",
    dependencies=[Depends(require_csrf)],
)
def update_location(
    trail_id: str,
    location_id: str,
    update: CoordinateUpdate,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> dict[str, str]:
    trail_oid = object_id(trail_id)
    owned_trail_or_404(db, trail_oid, user_id)
    result = db.locations.update_one(
        {"_id": object_id(location_id), "trailid": trail_oid},
        {"$set": {"loc.coordinates.0": update.longitude, "loc.coordinates.1": update.latitude}},
    )
    if result.matched_count != 1:
        raise HTTPException(status_code=404, detail="Location not found")
    return {"status": "ok"}


@router.delete(
    "/api/v1/trails/{trail_id}/locations/{location_id}",
    dependencies=[Depends(require_csrf)],
    status_code=204,
)
def delete_location(
    trail_id: str,
    location_id: str,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> Response:
    trail_oid = object_id(trail_id)
    owned_trail_or_404(db, trail_oid, user_id)
    result = db.locations.delete_one({"_id": object_id(location_id), "trailid": trail_oid})
    if result.deleted_count != 1:
        raise HTTPException(status_code=404, detail="Location not found")
    return Response(status_code=204)


@router.patch(
    "/api/v1/trails/{trail_id}/pictures/{picture_id}",
    dependencies=[Depends(require_csrf)],
)
def update_picture(
    trail_id: str,
    picture_id: str,
    update: PictureUpdate,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> dict[str, str]:
    trail_oid = object_id(trail_id)
    owned_trail_or_404(db, trail_oid, user_id)
    result = db.pictures.update_one(
        {"_id": object_id(picture_id), "trailid": trail_oid},
        {
            "$set": {
                "picturename": update.picturename,
                "description": update.description,
                "loc.coordinates.0": update.longitude,
                "loc.coordinates.1": update.latitude,
            }
        },
    )
    if result.matched_count != 1:
        raise HTTPException(status_code=404, detail="Picture not found")
    return {"status": "ok"}


@router.delete(
    "/api/v1/trails/{trail_id}/pictures/{picture_id}",
    dependencies=[Depends(require_csrf)],
    status_code=204,
)
def delete_picture(
    trail_id: str,
    picture_id: str,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> Response:
    trail_oid = object_id(trail_id)
    owned_trail_or_404(db, trail_oid, user_id)
    picture_oid = object_id(picture_id)
    picture = db.pictures.find_one({"_id": picture_oid, "trailid": trail_oid}, {"imageid": 1})
    if picture is None:
        raise HTTPException(status_code=404, detail="Picture not found")
    # Image first makes retry safe if metadata deletion is interrupted.
    if picture.get("imageid") is not None:
        db.images.delete_one({"_id": picture["imageid"]})
    db.pictures.delete_one({"_id": picture_oid, "trailid": trail_oid})
    return Response(status_code=204)


@router.put(
    "/api/v1/trails/{trail_id}/permissions", dependencies=[Depends(require_csrf)]
)
def update_trail_permissions(
    trail_id: str,
    update: VisibilityUpdate,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> dict[str, str]:
    trail_oid = object_id(trail_id)
    owned_trail_or_404(db, trail_oid, user_id)
    groups = owned_groups_or_422(db, update.groups, user_id, update.access)
    db.trails.update_one(
        {"_id": trail_oid, "userid": user_id},
        {"$set": {"access": update.access, "groups": groups}},
    )
    return {"status": "ok"}


@router.put(
    "/api/v1/trails/{trail_id}/pictures/{picture_id}/permissions",
    dependencies=[Depends(require_csrf)],
)
def update_picture_permissions(
    trail_id: str,
    picture_id: str,
    update: VisibilityUpdate,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> dict[str, str]:
    trail_oid = object_id(trail_id)
    owned_trail_or_404(db, trail_oid, user_id)
    groups = owned_groups_or_422(db, update.groups, user_id, update.access)
    result = db.pictures.update_one(
        {"_id": object_id(picture_id), "trailid": trail_oid},
        {"$set": {"access": update.access, "groups": groups}},
    )
    if result.matched_count != 1:
        raise HTTPException(status_code=404, detail="Picture not found")
    return {"status": "ok"}


@router.delete(
    "/api/v1/trails/{trail_id}", dependencies=[Depends(require_csrf)], status_code=204
)
def delete_trail(
    trail_id: str,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> Response:
    trail_oid = object_id(trail_id)
    owned_trail_or_404(db, trail_oid, user_id)
    delete_trail_records(db, trail_oid)
    return Response(status_code=204)


@dataclass
class AndroidUploadResult:
    ok: bool
    message: str
    http_status: int
    error_code: str | None = None
    details: dict[str, Any] = field(default_factory=dict)
    trail_id: ObjectId | None = None


def process_android_upload(
    payload: dict[str, Any],
    db: Database[Any],
    settings: Settings,
    provided_images: list[bytes] | None = None,
) -> AndroidUploadResult:
    try:
        upload = LegacyTrailUpload.model_validate(payload)
    except ValidationError as exc:
        issues = [
            {"field": ".".join(str(item) for item in error["loc"]), "message": error["msg"]}
            for error in exc.errors(include_url=False, include_input=False)
        ]
        return AndroidUploadResult(
            False, "The trail payload contains invalid fields.", 422, "invalid_payload", {"issues": issues}
        )

    trail_infos = [entry for entry in upload.newtrail if isinstance(entry, TrailInfo)]
    users = [entry for entry in upload.newtrail if isinstance(entry, UserInfo)]
    location_sets = [entry for entry in upload.newtrail if isinstance(entry, LocationCollection)]
    picture_sets = [entry for entry in upload.newtrail if isinstance(entry, PictureCollection)]
    if len(trail_infos) != 1 or len(users) != 1 or len(location_sets) != 1 or len(picture_sets) > 1:
        return AndroidUploadResult(
            False,
            "The upload must contain exactly one TrailInfo, UserInfo, and LocationCollection, and no more than one PictureCollection.",
            422,
            "invalid_upload_structure",
        )

    user_info = users[0]
    user = db.users.find_one(
        {"username": user_info.username},
        {"password": 1, "suspended": 1, "approved": 1, "quotas": 1},
    )
    if not user or not verify_password(user_info.password, user.get("password", "")):
        return AndroidUploadResult(
            False, "The username or password is incorrect.", 401, "invalid_credentials"
        )
    if user.get("suspended") is True:
        return AndroidUploadResult(
            False, "This account is suspended and cannot upload trails.", 403, "account_suspended"
        )
    if user.get("approved") is False:
        return AndroidUploadResult(
            False, "This account is awaiting administrator approval.", 403, "account_pending"
        )

    pictures = picture_sets[0].pictures if picture_sets else []
    if provided_images is not None and len(provided_images) != len(pictures):
        return AndroidUploadResult(
            False,
            "The number of binary photo parts does not match the photo metadata count.",
            422,
            "photo_file_count_mismatch",
            {"photo_metadata_count": len(pictures), "photo_file_count": len(provided_images)},
        )
    limits = user_limits(user, settings)
    if len(pictures) > limits["photos_per_trail"]:
        return AndroidUploadResult(
            False,
            f"This trail contains {len(pictures)} photos; the account limit is {limits['photos_per_trail']}.",
            413,
            "photo_count_exceeded",
            {"photo_count": len(pictures), "limit": limits["photos_per_trail"]},
        )
    decoded_images: list[bytes] = []
    total_bytes = 0
    try:
        for index, picture in enumerate(pictures):
            decoded = (
                provided_images[index]
                if provided_images is not None
                else base64.b64decode(picture.file, validate=True)
            )
            if len(decoded) > limits["image_bytes"]:
                return AndroidUploadResult(
                    False,
                    f"Photo {index + 1} exceeds the maximum photo size.",
                    413,
                    "photo_too_large",
                    {
                        "photo_index": index,
                        "filename": picture.filename,
                        "size_bytes": len(decoded),
                        "limit_bytes": limits["image_bytes"],
                    },
                )
            total_bytes += len(decoded)
            if total_bytes > limits["upload_bytes"]:
                return AndroidUploadResult(
                    False,
                    "The combined decoded photo size exceeds the trail upload limit.",
                    413,
                    "upload_too_large",
                    {"size_bytes": total_bytes, "limit_bytes": limits["upload_bytes"]},
                )
            decoded_images.append(decoded)
    except (binascii.Error, ValueError):
        return AndroidUploadResult(
            False,
            "A photo contains invalid base64 image data.",
            422,
            "invalid_photo_data",
        )
    storage_used = account_storage_bytes(db, user["_id"])
    if storage_used + total_bytes > limits["account_storage_bytes"]:
        return AndroidUploadResult(
            False,
            "Uploading this trail would exceed the account storage quota.",
            413,
            "account_storage_exceeded",
            {
                "used_bytes": storage_used,
                "upload_bytes": total_bytes,
                "limit_bytes": limits["account_storage_bytes"],
            },
        )

    info = trail_infos[0]
    trail_doc = {
        "userid": user["_id"],
        # Preserve the Node server's safe behavior: uploaded trails begin private.
        "access": "private",
        "date": info.date,
        "trailname": info.trailname,
        "location": info.locationname,
        "description": info.description,
    }
    trail_id: ObjectId | None = None
    image_ids: list[ObjectId] = []
    try:
        trail_id = db.trails.insert_one(trail_doc).inserted_id
        location_docs = [
            {"trailid": trail_id, **location.model_dump()}
            for location in location_sets[0].locations
        ]
        if location_docs:
            db.locations.insert_many(location_docs, ordered=True)
        for picture, decoded in zip(pictures, decoded_images, strict=True):
            image_id = db.images.insert_one({"img": Binary(decoded)}).inserted_id
            image_ids.append(image_id)
            metadata = picture.model_dump(exclude={"file"})
            db.pictures.insert_one({"trailid": trail_id, "imageid": image_id, **metadata})
    except Exception:
        # Standalone MongoDB deployments may not support transactions. Compensating cleanup
        # prevents the partial records produced by the old callback chain.
        if trail_id is not None:
            db.locations.delete_many({"trailid": trail_id})
            db.pictures.delete_many({"trailid": trail_id})
            db.trails.delete_one({"_id": trail_id})
        if image_ids:
            db.images.delete_many({"_id": {"$in": image_ids}})
        return AndroidUploadResult(
            False,
            "The server could not store the trail. No partial upload was retained.",
            503,
            "storage_failure",
        )
    return AndroidUploadResult(
        True, "Trail uploaded successfully.", 201, trail_id=trail_id
    )


def modern_android_response(result: AndroidUploadResult) -> JSONResponse:
    if result.ok:
        return JSONResponse(
            {"status": "ok", "message": result.message, "trailid": str(result.trail_id)},
            status_code=result.http_status,
        )
    return JSONResponse(
        {
            "status": "error",
            "error_code": result.error_code,
            "message": result.message,
            "details": result.details,
        },
        status_code=result.http_status,
    )


@router.post("/api/v1/trails")
async def android_upload_trail(
    request: Request,
    db: Database[Any] = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> JSONResponse:
    """Versioned Android upload API with standard HTTP status semantics."""
    content_type = request.headers.get("content-type", "").split(";", 1)[0].strip().lower()
    if content_type != "multipart/form-data":
        return modern_android_response(
            AndroidUploadResult(
                False,
                "Content-Type must be multipart/form-data.",
                415,
                "unsupported_media_type",
            )
        )
    try:
        form = await request.form()
    except (ValueError, UnicodeDecodeError, RuntimeError):
        return modern_android_response(
            AndroidUploadResult(
                False, "The multipart request body could not be parsed.", 400, "malformed_multipart"
            )
        )
    manifest_value = form.get("manifest")
    if isinstance(manifest_value, StarletteUploadFile):
        try:
            manifest_text = (await manifest_value.read()).decode("utf-8")
        except UnicodeDecodeError:
            manifest_text = ""
        finally:
            await manifest_value.close()
    elif isinstance(manifest_value, str):
        manifest_text = manifest_value
    else:
        manifest_text = ""
    if not manifest_text.strip():
        return modern_android_response(
            AndroidUploadResult(
                False,
                "The multipart request must include a JSON manifest field.",
                400,
                "missing_manifest",
            )
        )
    try:
        payload = json.loads(manifest_text)
    except (json.JSONDecodeError, UnicodeDecodeError):
        return modern_android_response(
            AndroidUploadResult(
                False, "The manifest field is not valid JSON.", 400, "malformed_manifest"
            )
        )
    if not isinstance(payload, dict):
        return modern_android_response(
            AndroidUploadResult(
                False, "The manifest JSON must be an object.", 422, "invalid_payload"
            )
        )
    uploads = [
        value
        for key, value in form.multi_items()
        if key == "photos" and isinstance(value, StarletteUploadFile)
    ]
    picture_collections = [
        entry
        for entry in payload.get("newtrail", [])
        if isinstance(entry, dict) and entry.get("type") == "PictureCollection"
    ]
    metadata = picture_collections[0].get("pictures", []) if len(picture_collections) == 1 else []
    if not isinstance(metadata, list) or len(metadata) != len(uploads):
        return modern_android_response(
            AndroidUploadResult(
                False,
                "The number of binary photo parts does not match the photo metadata count.",
                422,
                "photo_file_count_mismatch",
                {
                    "photo_metadata_count": len(metadata) if isinstance(metadata, list) else 0,
                    "photo_file_count": len(uploads),
                },
            )
        )
    image_bytes: list[bytes] = []
    try:
        for index, upload in enumerate(uploads):
            content = await upload.read()
            image_bytes.append(content)
            if isinstance(metadata[index], dict):
                metadata[index]["file"] = base64.b64encode(content).decode()
                if not metadata[index].get("filename"):
                    metadata[index]["filename"] = upload.filename or ""
    finally:
        for upload in uploads:
            await upload.close()
    return modern_android_response(
        process_android_upload(payload, db, settings, provided_images=image_bytes)
    )
