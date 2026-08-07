import re
import hashlib
import secrets
from datetime import datetime, timedelta, timezone
from typing import Any

from bson import ObjectId
from bson.errors import InvalidId
from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import HTMLResponse, Response
from fastapi.templating import Jinja2Templates
from pymongo.database import Database

from ..auth import require_admin_id, require_csrf, verify_password
from ..cleanup import delete_account_records
from ..config import Settings, get_settings
from ..database import get_db
from ..models import (
    AdminAccountUpdate,
    AdminApprovalUpdate,
    AdminPasswordConfirmation,
    AdminQuotaUpdate,
    AdminRoleUpdate,
    RegistrationSettingsUpdate,
    TrailModeration,
)
from ..quotas import account_storage_bytes, binary_image_size, user_limits
from ..registration import registration_settings

router = APIRouter()
templates = Jinja2Templates(directory=str(__file__).rsplit("/routes/", 1)[0] + "/templates")


def target_user_or_404(db: Database[Any], user_id: str) -> dict[str, Any]:
    try:
        oid = ObjectId(user_id)
    except (InvalidId, TypeError) as exc:
        raise HTTPException(status_code=404, detail="Account not found") from exc
    user = db.users.find_one({"_id": oid})
    if user is None:
        raise HTTPException(status_code=404, detail="Account not found")
    return user


def audit(
    db: Database[Any], actor_id: ObjectId, action: str, target: dict[str, Any], details: Any = None
) -> None:
    db.admin_audit.insert_one(
        {
            "actorid": actor_id,
            "action": action,
            "targetid": target["_id"],
            "target_username": target.get("username", ""),
            "details": details or {},
            "created": datetime.now(timezone.utc),
        }
    )


def confirm_admin_password(db: Database[Any], admin_id: ObjectId, password: str) -> None:
    admin = db.users.find_one({"_id": admin_id}, {"password": 1})
    if admin is None or not verify_password(password, admin.get("password", "")):
        raise HTTPException(status_code=403, detail="Administrator password is incorrect")


def admin_context() -> dict[str, bool]:
    return {"authenticated": True, "admin": True}


def parse_day(value: str, end: bool = False) -> datetime | None:
    if not value:
        return None
    try:
        parsed = datetime.strptime(value, "%Y-%m-%d").replace(tzinfo=timezone.utc)
    except ValueError:
        return None
    return parsed + timedelta(days=1) if end else parsed


@router.get("/admin", response_class=HTMLResponse)
def admin_dashboard(
    request: Request,
    q: str = "",
    db: Database[Any] = Depends(get_db),
    admin_id: ObjectId = Depends(require_admin_id),
    settings: Settings = Depends(get_settings),
) -> HTMLResponse:
    q = q.strip()[:100]
    query: dict[str, Any] = {}
    if q:
        pattern = re.compile(re.escape(q), re.IGNORECASE)
        query = {
            "$or": [
                {"username": pattern},
                {"email": pattern},
                {"display_name": pattern},
                {"profile_location": pattern},
            ]
        }
    users = list(db.users.find(query).sort("username", 1).limit(100))
    for user in users:
        user["trail_count"] = db.trails.count_documents({"userid": user["_id"]})
        user["storage_bytes"] = account_storage_bytes(db, user["_id"])
        user["storage_mb"] = f"{user['storage_bytes'] / (1024 * 1024):.1f}"
        limits = user_limits(user, settings)
        user["limits_mb"] = {
            "account": limits["account_storage_bytes"] // (1024 * 1024),
            "image": limits["image_bytes"] // (1024 * 1024),
            "upload": limits["upload_bytes"] // (1024 * 1024),
            "photos": limits["photos_per_trail"],
        }
    return templates.TemplateResponse(
        request,
        "admin.html",
        {
            **admin_context(),
            "admin_id": admin_id,
            "users": users,
            "q": q,
            "totals": {
                "users": db.users.count_documents({}),
                "trails": db.trails.count_documents({}),
                "photos": db.pictures.count_documents({}),
                "storage_mb": f"{sum(user['storage_bytes'] for user in users) / (1024 * 1024):.1f}",
            },
            "defaults": {
                "account": settings.max_account_storage_bytes // (1024 * 1024),
                "image": settings.max_image_bytes // (1024 * 1024),
                "photos": settings.max_photos_per_trail,
                "upload": settings.max_upload_bytes // (1024 * 1024),
            },
        },
    )


@router.get("/admin/audit", response_class=HTMLResponse)
def audit_log_page(
    request: Request,
    q: str = "",
    actor: str = "",
    action: str = "",
    date_from: str = "",
    date_to: str = "",
    db: Database[Any] = Depends(get_db),
    _admin_id: ObjectId = Depends(require_admin_id),
) -> HTMLResponse:
    q = q.strip()[:100]
    actor = actor.strip()[:100]
    clauses: list[dict[str, Any]] = []
    if q:
        pattern = re.compile(re.escape(q), re.IGNORECASE)
        clauses.append({"target_username": pattern})
    if actor:
        actor_pattern = re.compile(re.escape(actor), re.IGNORECASE)
        matching_actor_ids = [
            user["_id"]
            for user in db.users.find({"username": actor_pattern}, {"_id": 1})
        ]
        clauses.append({"actorid": {"$in": matching_actor_ids}})
    if action:
        clauses.append({"action": action})
    created: dict[str, datetime] = {}
    start = parse_day(date_from)
    end = parse_day(date_to, end=True)
    if start:
        created["$gte"] = start
    if end:
        created["$lt"] = end
    if created:
        clauses.append({"created": created})
    query = {"$and": clauses} if clauses else {}
    entries = list(db.admin_audit.find(query).sort("created", -1).limit(250))
    actor_ids = list({entry.get("actorid") for entry in entries if entry.get("actorid")})
    actors = {
        user["_id"]: user.get("username", "Unknown")
        for user in db.users.find({"_id": {"$in": actor_ids}}, {"username": 1})
    }
    for entry in entries:
        entry["actor_name"] = actors.get(entry.get("actorid"), "Deleted administrator")
    return templates.TemplateResponse(
        request,
        "admin_audit.html",
        {
            **admin_context(),
            "entries": entries,
            "actions": sorted(db.admin_audit.distinct("action")),
            "q": q,
            "actor": actor,
            "action": action,
            "date_from": date_from,
            "date_to": date_to,
        },
    )


@router.get("/admin/storage", response_class=HTMLResponse)
def storage_report_page(
    request: Request,
    db: Database[Any] = Depends(get_db),
    _admin_id: ObjectId = Depends(require_admin_id),
    settings: Settings = Depends(get_settings),
) -> HTMLResponse:
    users = list(db.users.find({}, {"username": 1, "quotas": 1}))
    for user in users:
        used = account_storage_bytes(db, user["_id"])
        limit = user_limits(user, settings)["account_storage_bytes"]
        user.update(
            storage_bytes=used,
            storage_mb=f"{used / (1024 * 1024):.1f}",
            storage_limit_mb=limit // (1024 * 1024),
            storage_percent=round((used / limit) * 100) if limit else 100,
        )
    users.sort(key=lambda item: item["storage_bytes"], reverse=True)
    owner_names = {user["_id"]: user.get("username", "Unknown") for user in users}
    trail_records: list[dict[str, Any]] = []
    photo_records: list[dict[str, Any]] = []
    for trail in db.trails.find({}, {"userid": 1, "access": 1, "trailname": 1}):
        trail_size = 0
        for picture in db.pictures.find({"trailid": trail["_id"]}, {"imageid": 1}):
            image = db.images.find_one({"_id": picture.get("imageid")}, {"img": 1})
            size = binary_image_size(image.get("img")) if image else 0
            trail_size += size
            photo_records.append(
                {"imageid": picture.get("imageid"), "size": size, "trailid": trail["_id"]}
            )
        trail_records.append(
            {
                **trail,
                "owner": owner_names.get(trail.get("userid"), "Unknown"),
                "display_name": trail.get("trailname") if trail.get("access") == "public" else "Private trail",
                "size": trail_size,
                "size_mb": f"{trail_size / (1024 * 1024):.1f}",
            }
        )
    trail_records.sort(key=lambda item: item["size"], reverse=True)
    photo_records.sort(key=lambda item: item["size"], reverse=True)
    for photo in photo_records:
        photo["size_mb"] = f"{photo['size'] / (1024 * 1024):.1f}"
    return templates.TemplateResponse(
        request,
        "admin_storage.html",
        {
            **admin_context(),
            "largest_users": users[:20],
            "near_quota": [user for user in users if user["storage_percent"] >= 80],
            "largest_trails": trail_records[:20],
            "largest_photos": photo_records[:20],
        },
    )


@router.get("/admin/users/{user_id}", response_class=HTMLResponse)
def admin_account_detail(
    user_id: str,
    request: Request,
    db: Database[Any] = Depends(get_db),
    _admin_id: ObjectId = Depends(require_admin_id),
    settings: Settings = Depends(get_settings),
) -> HTMLResponse:
    user = target_user_or_404(db, user_id)
    used = account_storage_bytes(db, user["_id"])
    limits = user_limits(user, settings)
    public_trails = list(
        db.trails.find({"userid": user["_id"], "access": "public"}).sort("date", -1)
    )
    actions = list(db.admin_audit.find({"targetid": user["_id"]}).sort("created", -1).limit(20))
    return templates.TemplateResponse(
        request,
        "admin_user.html",
        {
            **admin_context(),
            "user": user,
            "public_trails": public_trails,
            "trail_count": db.trails.count_documents({"userid": user["_id"]}),
            "photo_count": db.pictures.count_documents(
                {"trailid": {"$in": [trail["_id"] for trail in db.trails.find({"userid": user["_id"]}, {"_id": 1})]}}
            ),
            "storage_mb": f"{used / (1024 * 1024):.1f}",
            "storage_limit_mb": limits["account_storage_bytes"] // (1024 * 1024),
            "actions": actions,
        },
    )


@router.get("/admin/moderation", response_class=HTMLResponse)
def moderation_page(
    request: Request,
    q: str = "",
    db: Database[Any] = Depends(get_db),
    _admin_id: ObjectId = Depends(require_admin_id),
) -> HTMLResponse:
    q = q.strip()[:100]
    query: dict[str, Any] = {"access": "public"}
    if q:
        pattern = re.compile(re.escape(q), re.IGNORECASE)
        query = {"$and": [query, {"$or": [{"trailname": pattern}, {"location": pattern}]}]}
    trails = list(db.trails.find(query).sort("date", -1).limit(100))
    owner_ids = list({trail.get("userid") for trail in trails if trail.get("userid")})
    owners = {
        user["_id"]: user.get("username", "Unknown")
        for user in db.users.find({"_id": {"$in": owner_ids}}, {"username": 1})
    }
    for trail in trails:
        trail["owner"] = owners.get(trail.get("userid"), "Unknown")
    return templates.TemplateResponse(
        request,
        "admin_moderation.html",
        {**admin_context(), "trails": trails, "q": q},
    )


@router.patch(
    "/api/v1/admin/users/{user_id}/status", dependencies=[Depends(require_csrf)]
)
def update_account_status(
    user_id: str,
    update: AdminAccountUpdate,
    db: Database[Any] = Depends(get_db),
    admin_id: ObjectId = Depends(require_admin_id),
) -> dict[str, str]:
    confirm_admin_password(db, admin_id, update.admin_password)
    target = target_user_or_404(db, user_id)
    if target["_id"] == admin_id:
        raise HTTPException(status_code=422, detail="You cannot suspend your own account")
    db.users.update_one({"_id": target["_id"]}, {"$set": {"suspended": update.suspended}})
    if update.suspended:
        db.sessions.delete_many({"userid": target["_id"]})
    audit(db, admin_id, "account.suspended" if update.suspended else "account.reactivated", target)
    return {"status": "ok"}


@router.put(
    "/api/v1/admin/users/{user_id}/quotas", dependencies=[Depends(require_csrf)]
)
def update_account_quotas(
    user_id: str,
    update: AdminQuotaUpdate,
    db: Database[Any] = Depends(get_db),
    admin_id: ObjectId = Depends(require_admin_id),
) -> dict[str, str]:
    confirm_admin_password(db, admin_id, update.admin_password)
    target = target_user_or_404(db, user_id)
    mb = 1024 * 1024
    supplied = update.model_dump(exclude_none=True, exclude={"admin_password"})
    quotas: dict[str, int] = {}
    mapping = {
        "account_storage_mb": ("account_storage_bytes", mb),
        "image_mb": ("image_bytes", mb),
        "photos_per_trail": ("photos_per_trail", 1),
        "upload_mb": ("upload_bytes", mb),
    }
    for source, value in supplied.items():
        destination, multiplier = mapping[source]
        quotas[destination] = value * multiplier
    if quotas:
        db.users.update_one({"_id": target["_id"]}, {"$set": {"quotas": quotas}})
    else:
        db.users.update_one({"_id": target["_id"]}, {"$unset": {"quotas": ""}})
    audit(db, admin_id, "account.quotas_updated", target, quotas)
    return {"status": "ok"}


@router.delete(
    "/api/v1/admin/users/{user_id}", dependencies=[Depends(require_csrf)], status_code=204
)
def admin_delete_account(
    user_id: str,
    update: AdminPasswordConfirmation,
    db: Database[Any] = Depends(get_db),
    admin_id: ObjectId = Depends(require_admin_id),
) -> Response:
    confirm_admin_password(db, admin_id, update.admin_password)
    target = target_user_or_404(db, user_id)
    if target["_id"] == admin_id:
        raise HTTPException(status_code=422, detail="You cannot delete your own admin account")
    audit(db, admin_id, "account.deleted", target)
    delete_account_records(db, target["_id"])
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post(
    "/api/v1/admin/trails/{trail_id}/unpublish", dependencies=[Depends(require_csrf)]
)
def unpublish_public_trail(
    trail_id: str,
    update: TrailModeration,
    db: Database[Any] = Depends(get_db),
    admin_id: ObjectId = Depends(require_admin_id),
) -> dict[str, str]:
    confirm_admin_password(db, admin_id, update.admin_password)
    try:
        oid = ObjectId(trail_id)
    except (InvalidId, TypeError) as exc:
        raise HTTPException(status_code=404, detail="Public trail not found") from exc
    trail = db.trails.find_one({"_id": oid, "access": "public"})
    if trail is None:
        raise HTTPException(status_code=404, detail="Public trail not found")
    now = datetime.now(timezone.utc)
    db.trails.update_one(
        {"_id": oid, "access": "public"},
        {
            "$set": {
                "access": "private",
                "groups": [],
                "moderation": {
                    "reason": update.reason,
                    "administratorid": admin_id,
                    "created": now,
                },
            }
        },
    )
    db.notifications.insert_one(
        {
            "userid": trail.get("userid"),
            "type": "trail_unpublished",
            "title": "A public trail was unpublished",
            "message": f"{trail.get('trailname', 'Your trail')} was made private: {update.reason}",
            "trailid": oid,
            "created": now,
            "read": False,
        }
    )
    audit(
        db,
        admin_id,
        "trail.unpublished",
        {"_id": oid, "username": trail.get("trailname", "")},
        {"reason": update.reason, "ownerid": trail.get("userid")},
    )
    return {"status": "ok"}


@router.get("/admin/settings", response_class=HTMLResponse)
def admin_settings_page(
    request: Request,
    db: Database[Any] = Depends(get_db),
    _admin_id: ObjectId = Depends(require_admin_id),
    settings: Settings = Depends(get_settings),
) -> HTMLResponse:
    return templates.TemplateResponse(
        request,
        "admin_settings.html",
        {**admin_context(), "registration": registration_settings(db, settings)},
    )


@router.put("/api/v1/admin/settings/registration", dependencies=[Depends(require_csrf)])
def update_registration_settings(
    update: RegistrationSettingsUpdate,
    db: Database[Any] = Depends(get_db),
    admin_id: ObjectId = Depends(require_admin_id),
) -> dict[str, str]:
    confirm_admin_password(db, admin_id, update.admin_password)
    mb = 1024 * 1024
    values = {
        "enabled": update.enabled,
        "approval_required": update.approval_required,
        "account_storage_bytes": update.account_storage_mb * mb,
        "image_bytes": update.image_mb * mb,
        "photos_per_trail": update.photos_per_trail,
        "upload_bytes": update.upload_mb * mb,
    }
    db.app_settings.update_one({"_id": "registration"}, {"$set": values}, upsert=True)
    audit(db, admin_id, "registration.settings_updated", {"_id": "registration"}, values)
    return {"status": "ok"}


@router.patch(
    "/api/v1/admin/users/{user_id}/approval", dependencies=[Depends(require_csrf)]
)
def update_account_approval(
    user_id: str,
    update: AdminApprovalUpdate,
    db: Database[Any] = Depends(get_db),
    admin_id: ObjectId = Depends(require_admin_id),
) -> dict[str, str]:
    confirm_admin_password(db, admin_id, update.admin_password)
    target = target_user_or_404(db, user_id)
    db.users.update_one({"_id": target["_id"]}, {"$set": {"approved": update.approved}})
    if not update.approved:
        db.sessions.delete_many({"userid": target["_id"]})
    audit(db, admin_id, "account.approved" if update.approved else "account.unapproved", target)
    return {"status": "ok"}


@router.patch("/api/v1/admin/users/{user_id}/role", dependencies=[Depends(require_csrf)])
def update_admin_role(
    user_id: str,
    update: AdminRoleUpdate,
    db: Database[Any] = Depends(get_db),
    admin_id: ObjectId = Depends(require_admin_id),
) -> dict[str, str]:
    confirm_admin_password(db, admin_id, update.admin_password)
    target = target_user_or_404(db, user_id)
    if update.role == "user" and target.get("role") == "admin":
        active_admins = db.users.count_documents(
            {"role": "admin", "suspended": {"$ne": True}}
        )
        if target.get("suspended") is not True and active_admins <= 1:
            raise HTTPException(status_code=422, detail="The final active administrator cannot be demoted")
    db.users.update_one({"_id": target["_id"]}, {"$set": {"role": update.role}})
    if target["_id"] == admin_id and update.role != "admin":
        db.sessions.delete_many({"userid": admin_id})
    audit(db, admin_id, f"account.role_{update.role}", target)
    return {"status": "ok"}


@router.post(
    "/api/v1/admin/users/{user_id}/password-reset", dependencies=[Depends(require_csrf)]
)
def issue_password_reset(
    user_id: str,
    update: AdminPasswordConfirmation,
    request: Request,
    db: Database[Any] = Depends(get_db),
    admin_id: ObjectId = Depends(require_admin_id),
) -> dict[str, str]:
    confirm_admin_password(db, admin_id, update.admin_password)
    target = target_user_or_404(db, user_id)
    raw_token = secrets.token_urlsafe(48)
    digest = hashlib.sha256(raw_token.encode()).hexdigest()
    db.password_resets.delete_many({"userid": target["_id"], "used": {"$ne": True}})
    db.password_resets.insert_one(
        {
            "userid": target["_id"],
            "token_digest": digest,
            "created": datetime.now(timezone.utc),
            "expires": datetime.now(timezone.utc) + timedelta(minutes=30),
            "used": False,
            "issued_by": admin_id,
        }
    )
    audit(db, admin_id, "account.password_reset_issued", target)
    return {"status": "ok", "reset_url": str(request.base_url) + f"reset-password/{raw_token}"}


def maintenance_findings(db: Database[Any]) -> dict[str, list[ObjectId]]:
    trail_ids = {trail["_id"] for trail in db.trails.find({}, {"_id": 1})}
    image_ids = {image["_id"] for image in db.images.find({}, {"_id": 1})}
    picture_image_ids = {
        picture.get("imageid")
        for picture in db.pictures.find({}, {"imageid": 1})
        if picture.get("imageid") is not None
    }
    return {
        "orphan_images": list(image_ids - picture_image_ids),
        "orphan_pictures": [
            picture["_id"]
            for picture in db.pictures.find({}, {"trailid": 1, "imageid": 1})
            if picture.get("trailid") not in trail_ids or picture.get("imageid") not in image_ids
        ],
        "orphan_locations": [
            location["_id"]
            for location in db.locations.find({}, {"trailid": 1})
            if location.get("trailid") not in trail_ids
        ],
        "trails_without_owner": [
            trail["_id"]
            for trail in db.trails.find({}, {"userid": 1})
            if db.users.count_documents({"_id": trail.get("userid")}) == 0
        ],
        "trails_without_locations": [
            trail_id
            for trail_id in trail_ids
            if db.locations.count_documents({"trailid": trail_id}) == 0
        ],
    }


@router.get("/admin/maintenance", response_class=HTMLResponse)
def maintenance_page(
    request: Request,
    db: Database[Any] = Depends(get_db),
    _admin_id: ObjectId = Depends(require_admin_id),
) -> HTMLResponse:
    findings = maintenance_findings(db)
    return templates.TemplateResponse(
        request,
        "admin_maintenance.html",
        {**admin_context(), "findings": findings},
    )


@router.post("/api/v1/admin/maintenance/cleanup", dependencies=[Depends(require_csrf)])
def cleanup_orphan_records(
    update: AdminPasswordConfirmation,
    db: Database[Any] = Depends(get_db),
    admin_id: ObjectId = Depends(require_admin_id),
) -> dict[str, Any]:
    confirm_admin_password(db, admin_id, update.admin_password)
    findings = maintenance_findings(db)
    db.pictures.delete_many({"_id": {"$in": findings["orphan_pictures"]}})
    db.locations.delete_many({"_id": {"$in": findings["orphan_locations"]}})
    referenced = {
        picture.get("imageid")
        for picture in db.pictures.find({}, {"imageid": 1})
        if picture.get("imageid") is not None
    }
    now_orphaned_images = [
        image["_id"]
        for image in db.images.find({}, {"_id": 1})
        if image["_id"] not in referenced
    ]
    db.images.delete_many({"_id": {"$in": now_orphaned_images}})
    removed = {
        "pictures": len(findings["orphan_pictures"]),
        "locations": len(findings["orphan_locations"]),
        "images": len(now_orphaned_images),
    }
    audit(db, admin_id, "maintenance.orphans_removed", {"_id": "database"}, removed)
    return {"status": "ok", "removed": removed}
