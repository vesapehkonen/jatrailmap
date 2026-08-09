from collections.abc import Iterator
from pathlib import Path
from typing import Any

from fastapi import Request
from pymongo import ASCENDING, DESCENDING, MongoClient
from pymongo.database import Database

from .config import Settings


def mongo_client_credentials(settings: Settings) -> dict[str, str]:
    username_file = settings.mongodb_username_file
    password_file = settings.mongodb_password_file
    if bool(username_file) != bool(password_file):
        raise ValueError("MongoDB username and password files must be configured together")
    if not username_file:
        return {}
    username = Path(username_file).read_text(encoding="utf-8").strip()
    password = Path(password_file).read_text(encoding="utf-8").strip()
    if not username or not password:
        raise ValueError("MongoDB credential files must not be empty")
    return {"username": username, "password": password}


def connect(settings: Settings) -> MongoClient[Any]:
    client: MongoClient[Any] = MongoClient(
        settings.mongodb_uri,
        tz_aware=True,
        **mongo_client_credentials(settings),
    )
    client.admin.command("ping")
    return client


def ensure_indexes(db: Database[Any], session_max_age_seconds: int) -> None:
    # Creating an equivalent index is idempotent. Do not drop indexes at application startup.
    db.sessions.create_index(
        [("created", ASCENDING)],
        expireAfterSeconds=session_max_age_seconds,
    )
    db.sessions.create_index("token_digest", unique=True, sparse=True, name="token_digest_unique")
    db.users.create_index("username", unique=True, name="username_unique")
    db.trails.create_index([("access", ASCENDING), ("date", DESCENDING)])
    db.trails.create_index("userid")
    db.locations.create_index([("trailid", ASCENDING), ("timestamp", ASCENDING)])
    db.pictures.create_index("trailid")
    db.pictures.create_index("imageid")
    db.groups.create_index("ownerid")
    db.groups.create_index("members")
    db.admin_audit.create_index([("created", DESCENDING)])
    db.admin_audit.create_index("actorid")
    db.admin_audit.create_index("targetid")
    db.notifications.create_index([("userid", ASCENDING), ("created", DESCENDING)])
    db.password_resets.create_index("token_digest", unique=True, sparse=True)
    db.password_resets.create_index("expires", expireAfterSeconds=0)


def get_db(request: Request) -> Iterator[Database[Any]]:
    yield request.app.state.db
