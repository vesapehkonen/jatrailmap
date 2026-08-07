from typing import Any

from bson import ObjectId
from pymongo.database import Database


def same_id(left: Any, right: Any) -> bool:
    return left is not None and right is not None and str(left) == str(right)


class TrailAccessPolicy:
    def __init__(self, db: Database[Any]):
        self.db = db

    def is_owner(self, trail: dict[str, Any], user_id: ObjectId | None) -> bool:
        return same_id(trail.get("userid"), user_id)

    def can_view(self, trail: dict[str, Any], user_id: ObjectId | None) -> bool:
        if self.is_owner(trail, user_id) or trail.get("access") == "public":
            return True
        if user_id is None or trail.get("access") != "group":
            return False
        group_ids = trail.get("groups") or []
        return self.db.groups.find_one(
            {
                "_id": {"$in": group_ids},
                "$or": [{"members": user_id}, {"members": str(user_id)}],
            }
        ) is not None

    def can_view_picture(
        self, trail: dict[str, Any], picture: dict[str, Any], user_id: ObjectId | None
    ) -> bool:
        if not self.can_view(trail, user_id):
            return False
        if self.is_owner(trail, user_id):
            return True
        access = picture.get("access")
        if access in (None, "public"):
            return True
        if access != "group" or user_id is None:
            return False
        return self.db.groups.find_one(
            {
                "_id": {"$in": picture.get("groups") or []},
                "$or": [{"members": user_id}, {"members": str(user_id)}],
            }
        ) is not None
