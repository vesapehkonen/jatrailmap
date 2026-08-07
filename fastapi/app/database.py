from collections.abc import Iterator
from typing import Any

from fastapi import Request
from pymongo import ASCENDING, DESCENDING, MongoClient
from pymongo.database import Database

from .config import Settings


def connect(settings: Settings) -> MongoClient[Any]:
    client: MongoClient[Any] = MongoClient(settings.mongodb_uri, tz_aware=True)
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
