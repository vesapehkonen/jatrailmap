import hmac
import hashlib
from datetime import datetime, timezone
from typing import Any

import bcrypt
from bson import ObjectId
from fastapi import APIRouter, Depends, Form, HTTPException, Request, status
from fastapi.responses import HTMLResponse, JSONResponse, Response
from fastapi.templating import Jinja2Templates
from pymongo.database import Database
from pymongo.errors import DuplicateKeyError

from ..auth import CSRF_COOKIE, create_session, require_csrf, require_user_id, set_auth_cookies, verify_password
from ..cleanup import delete_account_records
from ..config import Settings, get_settings
from ..database import get_db
from ..models import AccountDelete, PasswordUpdate, ProfileUpdate
from ..registration import registration_settings

router = APIRouter()
templates = Jinja2Templates(directory=str(__file__).rsplit("/routes/", 1)[0] + "/templates")


def current_user_or_401(db: Database[Any], user_id: ObjectId) -> dict[str, Any]:
    user = db.users.find_one({"_id": user_id})
    if user is None:
        raise HTTPException(status_code=401, detail="Authentication required")
    return user


def require_current_password(user: dict[str, Any], password: str) -> None:
    if not verify_password(password, user.get("password", "")):
        raise HTTPException(status_code=403, detail="Current password is incorrect")


@router.get("/register", response_class=HTMLResponse)
def register_page(
    request: Request,
    db: Database[Any] = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> HTMLResponse:
    policy = registration_settings(db, settings)
    return templates.TemplateResponse(
        request, "register.html", {"authenticated": False, "registration": policy}
    )


@router.post("/register")
def register(
    request: Request,
    username: str = Form(..., min_length=1, max_length=200),
    password: str = Form(..., min_length=8, max_length=1024),
    csrf_token: str = Form(...),
    db: Database[Any] = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> JSONResponse:
    cookie_csrf = request.cookies.get(CSRF_COOKIE, "")
    if not cookie_csrf or not hmac.compare_digest(cookie_csrf, csrf_token):
        raise HTTPException(status_code=403, detail="Invalid CSRF token")
    policy = registration_settings(db, settings)
    if not policy["enabled"]:
        raise HTTPException(status_code=403, detail="Account registration is currently closed")
    password_hash = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
    mb = 1024 * 1024
    try:
        user_id = db.users.insert_one(
            {
                "username": username,
                "password": password_hash,
                "display_name": "",
                "profile_location": "",
                "show_name_on_public_trails": False,
                "show_location_on_public_trails": False,
                "email": "",
                "approved": not policy["approval_required"],
                "suspended": False,
                "quotas": {
                    "account_storage_bytes": policy["account_storage_mb"] * mb,
                    "image_bytes": policy["image_mb"] * mb,
                    "photos_per_trail": policy["photos_per_trail"],
                    "upload_bytes": policy["upload_mb"] * mb,
                },
            }
        ).inserted_id
    except DuplicateKeyError as exc:
        raise HTTPException(status_code=409, detail="Username is already in use") from exc
    if policy["approval_required"]:
        return JSONResponse({"status": "pending"}, status_code=201)
    token, session_csrf = create_session(db, user_id)
    response = JSONResponse({"status": "ok"}, status_code=201)
    set_auth_cookies(response, token, session_csrf, settings)
    return response


def valid_password_reset(db: Database[Any], token: str) -> dict[str, Any] | None:
    digest = hashlib.sha256(token.encode()).hexdigest()
    return db.password_resets.find_one(
        {"token_digest": digest, "used": False, "expires": {"$gt": datetime.now(timezone.utc)}}
    )


@router.get("/reset-password/{token}", response_class=HTMLResponse)
def reset_password_page(
    token: str, request: Request, db: Database[Any] = Depends(get_db)
) -> HTMLResponse:
    return templates.TemplateResponse(
        request,
        "reset_password.html",
        {"authenticated": False, "token": token, "valid": valid_password_reset(db, token) is not None},
    )


@router.post("/reset-password/{token}")
def reset_password_with_token(
    token: str,
    request: Request,
    new_password: str = Form(..., min_length=8, max_length=1024),
    csrf_token: str = Form(...),
    db: Database[Any] = Depends(get_db),
) -> JSONResponse:
    cookie_csrf = request.cookies.get(CSRF_COOKIE, "")
    if not cookie_csrf or not hmac.compare_digest(cookie_csrf, csrf_token):
        raise HTTPException(status_code=403, detail="Invalid CSRF token")
    reset = valid_password_reset(db, token)
    if reset is None:
        raise HTTPException(status_code=410, detail="This reset link is invalid or has expired")
    password_hash = bcrypt.hashpw(new_password.encode(), bcrypt.gensalt()).decode()
    result = db.password_resets.update_one(
        {"_id": reset["_id"], "used": False}, {"$set": {"used": True}}
    )
    if result.modified_count != 1:
        raise HTTPException(status_code=410, detail="This reset link has already been used")
    db.users.update_one({"_id": reset["userid"]}, {"$set": {"password": password_hash}})
    db.sessions.delete_many({"userid": reset["userid"]})
    return JSONResponse({"status": "ok"})


@router.get("/account", response_class=HTMLResponse)
def account_page(
    request: Request,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> HTMLResponse:
    user = current_user_or_401(db, user_id)
    notifications = list(db.notifications.find({"userid": user_id}).sort("created", -1).limit(20))
    return templates.TemplateResponse(
        request,
        "account.html",
        {"user": user, "notifications": notifications, "authenticated": True},
    )


@router.patch("/api/v1/account", dependencies=[Depends(require_csrf)])
def update_profile(
    update: ProfileUpdate,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> dict[str, str]:
    user = current_user_or_401(db, user_id)
    require_current_password(user, update.current_password)
    fields = update.model_dump(exclude={"current_password"})
    db.users.update_one({"_id": user_id}, {"$set": fields})
    return {"status": "ok"}


@router.put("/api/v1/account/password", dependencies=[Depends(require_csrf)])
def update_password(
    update: PasswordUpdate,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> dict[str, str]:
    user = current_user_or_401(db, user_id)
    require_current_password(user, update.current_password)
    password_hash = bcrypt.hashpw(update.new_password.encode(), bcrypt.gensalt()).decode()
    db.users.update_one({"_id": user_id}, {"$set": {"password": password_hash}})
    # Invalidate every other login while retaining the session making this request.
    # The caller's token digest is not available here, so require a fresh login consistently.
    db.sessions.delete_many({"userid": user_id})
    return {"status": "ok", "reauthenticate": "true"}


@router.delete("/api/v1/account", dependencies=[Depends(require_csrf)], status_code=204)
def delete_account(
    update: AccountDelete,
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> Response:
    user = current_user_or_401(db, user_id)
    require_current_password(user, update.current_password)
    delete_account_records(db, user_id)
    response = Response(status_code=204)
    response.delete_cookie("token", path="/")
    response.delete_cookie("csrf_token", path="/")
    return response
