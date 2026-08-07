import hashlib
import hmac
import secrets
from datetime import datetime, timezone
from typing import Any

import bcrypt
from bson import ObjectId
from fastapi import Depends, HTTPException, Request, status
from pymongo.database import Database

from .config import Settings, get_settings
from .database import get_db

SESSION_COOKIE = "token"
CSRF_COOKIE = "csrf_token"


def verify_password(password: str, password_hash: str | bytes) -> bool:
    try:
        encoded_hash = password_hash.encode() if isinstance(password_hash, str) else password_hash
        return bcrypt.checkpw(password.encode(), encoded_hash)
    except (ValueError, TypeError):
        return False


def token_digest(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def create_session(db: Database[Any], user_id: ObjectId) -> tuple[str, str]:
    token = secrets.token_urlsafe(48)
    csrf_token = secrets.token_urlsafe(32)
    # New sessions store only a digest. Legacy plaintext sessions remain readable while migrating.
    db.sessions.insert_one(
        {
            "token_digest": token_digest(token),
            "userid": user_id,
            "csrf_token": csrf_token,
            "created": datetime.now(timezone.utc),
        }
    )
    return token, csrf_token


def find_session(db: Database[Any], token: str | None) -> dict[str, Any] | None:
    if not token:
        return None
    return db.sessions.find_one(
        {"$or": [{"token_digest": token_digest(token)}, {"token": token}]}
    )


def csrf_cookie_update(
    db: Database[Any], session_token: str | None, cookie_token: str | None
) -> str | None:
    session = find_session(db, session_token)
    expected = str(session.get("csrf_token", "")) if session else ""
    current = cookie_token or ""
    if expected:
        return expected if not hmac.compare_digest(current, expected) else None
    return secrets.token_urlsafe(32) if not current else None


def delete_session(db: Database[Any], token: str | None) -> None:
    if token:
        db.sessions.delete_many(
            {"$or": [{"token_digest": token_digest(token)}, {"token": token}]}
        )


def optional_user_id(
    request: Request, db: Database[Any] = Depends(get_db)
) -> ObjectId | None:
    session = find_session(db, request.cookies.get(SESSION_COOKIE))
    if not session:
        return None
    user_id = session.get("userid")
    if db.users.find_one(
        {"_id": user_id, "suspended": {"$ne": True}, "approved": {"$ne": False}}, {"_id": 1}
    ) is None:
        return None
    request.state.is_admin = db.users.count_documents({"_id": user_id, "role": "admin"}) == 1
    return user_id


def require_user_id(user_id: ObjectId | None = Depends(optional_user_id)) -> ObjectId:
    if user_id is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authentication required")
    return user_id


def require_admin_id(
    db: Database[Any] = Depends(get_db),
    user_id: ObjectId = Depends(require_user_id),
) -> ObjectId:
    if db.users.find_one({"_id": user_id, "role": "admin", "suspended": {"$ne": True}}) is None:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Administrator access required")
    return user_id


async def require_csrf(request: Request, db: Database[Any] = Depends(get_db)) -> None:
    session = find_session(db, request.cookies.get(SESSION_COOKIE))
    if not session:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authentication required")
    supplied = request.headers.get("X-CSRF-Token")
    if not supplied:
        form = await request.form()
        supplied = str(form.get("csrf_token", ""))
    cookie_token = request.cookies.get(CSRF_COOKIE, "")
    expected = str(session.get("csrf_token", ""))
    if not expected or not hmac.compare_digest(supplied or "", expected) or not hmac.compare_digest(
        cookie_token, expected
    ):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Invalid CSRF token")


def set_auth_cookies(response: Any, token: str, csrf_token: str, settings: Settings) -> None:
    common = {
        "max_age": settings.session_max_age_seconds,
        "secure": settings.secure_cookies,
        "samesite": "strict",
        "path": "/",
    }
    response.set_cookie(SESSION_COOKIE, token, httponly=True, **common)
    response.set_cookie(CSRF_COOKIE, csrf_token, httponly=False, **common)


def clear_auth_cookies(response: Any, settings: Settings = Depends(get_settings)) -> None:
    response.delete_cookie(SESSION_COOKIE, path="/", secure=settings.secure_cookies, samesite="strict")
    response.delete_cookie(CSRF_COOKIE, path="/", secure=settings.secure_cookies, samesite="strict")
