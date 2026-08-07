from typing import Any

from bson import ObjectId
from pymongo.database import Database


def delete_trail_records(db: Database[Any], trail_id: ObjectId) -> None:
    image_ids = [
        picture["imageid"]
        for picture in db.pictures.find({"trailid": trail_id}, {"imageid": 1})
        if picture.get("imageid") is not None
    ]
    if image_ids:
        db.images.delete_many({"_id": {"$in": image_ids}})
    db.pictures.delete_many({"trailid": trail_id})
    db.locations.delete_many({"trailid": trail_id})
    db.trails.delete_one({"_id": trail_id})


def remove_group_references(db: Database[Any], group_id: ObjectId) -> None:
    for collection in (db.trails, db.pictures):
        for document in collection.find({"groups": group_id}, {"groups": 1, "access": 1}):
            remaining = [value for value in document.get("groups", []) if value != group_id]
            update: dict[str, Any] = {"groups": remaining}
            if document.get("access") == "group" and not remaining:
                update["access"] = "private"
            collection.update_one({"_id": document["_id"]}, {"$set": update})


def delete_account_records(db: Database[Any], user_id: ObjectId) -> None:
    for trail in db.trails.find({"userid": user_id}, {"_id": 1}):
        delete_trail_records(db, trail["_id"])
    for group in db.groups.find({"ownerid": user_id}, {"_id": 1}):
        remove_group_references(db, group["_id"])
        db.groups.delete_one({"_id": group["_id"], "ownerid": user_id})
    db.groups.update_many(
        {"$or": [{"members": user_id}, {"members": str(user_id)}]},
        {"$pull": {"members": {"$in": [user_id, str(user_id)]}}},
    )
    db.sessions.delete_many({"userid": user_id})
    db.notifications.delete_many({"userid": user_id})
    db.password_resets.delete_many({"userid": user_id})
    db.users.delete_one({"_id": user_id})
