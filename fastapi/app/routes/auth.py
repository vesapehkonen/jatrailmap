import hmac
from typing import Any

from fastapi import APIRouter, Depends, Form, HTTPException, Request, status
from fastapi.responses import JSONResponse, RedirectResponse
from pymongo.database import Database

from ..auth import (
    CSRF_COOKIE,
    SESSION_COOKIE,
    clear_auth_cookies,
    create_session,
    delete_session,
    require_csrf,
    set_auth_cookies,
    verify_password,
)
from ..config import Settings, get_settings
from ..database import get_db

router = APIRouter()


@router.post("/login")
def login(
    request: Request,
    username: str = Form(...),
    password: str = Form(...),
    csrf_token: str = Form(...),
    db: Database[Any] = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> JSONResponse:
    cookie_csrf = request.cookies.get(CSRF_COOKIE, "")
    if not cookie_csrf or not hmac.compare_digest(cookie_csrf, csrf_token):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Invalid CSRF token")
    user = db.users.find_one(
        {"username": username}, {"password": 1, "suspended": 1, "approved": 1}
    )
    if not user or not verify_password(password, user.get("password", "")):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Wrong username or password")
    if user.get("suspended") is True:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Account is suspended")
    if user.get("approved") is False:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Account is awaiting approval")
    delete_session(db, request.cookies.get(SESSION_COOKIE))
    token, session_csrf = create_session(db, user["_id"])
    response = JSONResponse({"status": "ok"})
    set_auth_cookies(response, token, session_csrf, settings)
    return response


@router.post("/logout", dependencies=[Depends(require_csrf)])
def logout(
    request: Request,
    db: Database[Any] = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> RedirectResponse:
    delete_session(db, request.cookies.get(SESSION_COOKIE))
    response = RedirectResponse("/", status_code=status.HTTP_303_SEE_OTHER)
    clear_auth_cookies(response, settings)
    return response
