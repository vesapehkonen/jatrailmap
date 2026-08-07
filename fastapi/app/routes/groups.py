import re
from typing import Any

from bson import ObjectId
from bson.errors import InvalidId
from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import HTMLResponse, Response
from fastapi.templating import Jinja2Templates
from pymongo.database import Database

from ..auth import require_csrf, require_user_id
from ..cleanup import remove_group_references
from ..database import get_db
from ..models import GroupCreate, GroupUpdate

router = APIRouter()
templates = Jinja2Templates(directory=str(__file__).rsplit("/routes/", 1)[0] + "/templates")


def oid_or_404(value: str) -> ObjectId:
    try:
        return ObjectId(value)
    except (InvalidId, TypeError) as exc:
        raise HTTPException(status_code=404, detail="Group not found") from exc


def member_ids_or_422(db: Database[Any], values: list[str], owner_id: ObjectId) -> list[ObjectId]:
    try:
        ids = list(dict.fromkeys(ObjectId(value) for value in values if value != str(owner_id)))
    except (InvalidId, TypeError) as exc:
        raise HTTPException(status_code=422, detail="Invalid group member") from exc
    if db.users.count_documents({"_id": {"$in": ids}}) != len(ids):
        raise HTTPException(status_code=422, detail="A selected user does not exist")
    return ids


def owned_group_or_404(db: Database[Any], group_id: ObjectId, owner_id: ObjectId) -> dict[str, Any]:
    group = db.groups.find_one({"_id": group_id, "ownerid": owner_id})
    if group is None:
        raise HTTPException(status_code=404, detail="Group not found")
    return group


@router.get("/groups", response_class=HTMLResponse)
def groups_page(
    request: Request,
    q: str = Query(default="", max_length=100),
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> HTMLResponse:
    groups = list(db.groups.find({"ownerid": user_id}).sort("name", 1))
    for group in groups:
        group["member_strings"] = [str(value) for value in group.get("members", [])]
        group["trail_count"] = db.trails.count_documents({"groups": group["_id"]})
        group["picture_count"] = db.pictures.count_documents({"groups": group["_id"]})
    user_ids: set[ObjectId] = set()
    for group in groups:
        for value in group.get("members", []):
            try:
                user_ids.add(value if isinstance(value, ObjectId) else ObjectId(value))
            except (InvalidId, TypeError):
                continue
    if len(q.strip()) >= 2:
        pattern = re.compile(re.escape(q.strip()), re.IGNORECASE)
        for user in db.users.find(
            {
                "_id": {"$ne": user_id},
                "$or": [
                    {"username": pattern},
                    {"display_name": pattern, "show_name_on_public_trails": True},
                ],
            },
            {"_id": 1},
        ).limit(50):
            user_ids.add(user["_id"])
    users = list(
        db.users.find(
            {"_id": {"$in": list(user_ids), "$ne": user_id}},
            {"username": 1, "display_name": 1, "show_name_on_public_trails": 1},
        ).sort("username", 1)
    )
    for user in users:
        user["member_label"] = (
            user.get("display_name")
            if user.get("show_name_on_public_trails") is True and user.get("display_name")
            else user.get("username", "Unknown user")
        )
    return templates.TemplateResponse(
        request,
        "groups.html",
        {"groups": groups, "users": users, "query": q, "authenticated": True},
    )


@router.post("/api/v1/groups", dependencies=[Depends(require_csrf)], status_code=201)
def create_group(
    update: GroupCreate,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> dict[str, str]:
    members = member_ids_or_422(db, update.members, user_id)
    group_id = db.groups.insert_one(
        {"ownerid": user_id, "members": members, "name": update.name}
    ).inserted_id
    return {"status": "ok", "groupid": str(group_id)}


@router.patch("/api/v1/groups/{group_id}", dependencies=[Depends(require_csrf)])
def update_group(
    group_id: str,
    update: GroupUpdate,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> dict[str, str]:
    oid = oid_or_404(group_id)
    owned_group_or_404(db, oid, user_id)
    members = member_ids_or_422(db, update.members, user_id)
    db.groups.update_one(
        {"_id": oid, "ownerid": user_id}, {"$set": {"name": update.name, "members": members}}
    )
    return {"status": "ok"}


@router.delete(
    "/api/v1/groups/{group_id}", dependencies=[Depends(require_csrf)], status_code=204
)
def delete_group(
    group_id: str,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> Response:
    oid = oid_or_404(group_id)
    owned_group_or_404(db, oid, user_id)
    remove_group_references(db, oid)
    db.groups.delete_one({"_id": oid, "ownerid": user_id})
    return Response(status_code=204)
