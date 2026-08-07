import asyncio

import bcrypt
import pytest
from bson import ObjectId
from fastapi import HTTPException

from app.auth import (
    create_session,
    csrf_cookie_update,
    find_session,
    require_csrf,
    token_digest,
    verify_password,
)


class FakeRequest:
    def __init__(self, token: str, csrf_cookie: str, csrf_form: str):
        self.cookies = {"token": token, "csrf_token": csrf_cookie}
        self.headers = {}
        self._csrf_form = csrf_form

    async def form(self):
        return {"csrf_token": self._csrf_form}


def test_password_check_and_hashed_session(database):
    password_hash = bcrypt.hashpw(b"secret", bcrypt.gensalt()).decode()
    assert verify_password("secret", password_hash)
    assert not verify_password("wrong", password_hash)
    token, csrf = create_session(database, ObjectId())
    session = find_session(database, token)
    assert session["token_digest"] == token_digest(token)
    assert session["csrf_token"] == csrf
    assert "token" not in session


def test_legacy_plaintext_session_remains_readable(database):
    token = "legacy-token"
    database.sessions.insert_one({"token": token, "userid": ObjectId()})
    assert find_session(database, token) is not None


def test_csrf_requires_session_cookie_and_matching_form(database):
    token, csrf = create_session(database, ObjectId())
    asyncio.run(require_csrf(FakeRequest(token, csrf, csrf), database))
    with pytest.raises(HTTPException) as error:
        asyncio.run(require_csrf(FakeRequest(token, csrf, "wrong"), database))
    assert error.value.status_code == 403


def test_csrf_cookie_is_restored_from_active_session(database):
    token, csrf = create_session(database, ObjectId())
    assert csrf_cookie_update(database, token, None) == csrf
    assert csrf_cookie_update(database, token, "stale") == csrf
    assert csrf_cookie_update(database, token, csrf) is None


def test_anonymous_request_gets_csrf_cookie_only_when_missing(database):
    generated = csrf_cookie_update(database, None, None)
    assert generated
    assert csrf_cookie_update(database, None, generated) is None
