import argparse
import getpass

import bcrypt

from .config import get_settings
from .database import connect, ensure_indexes


def create_admin(username: str) -> None:
    settings = get_settings()
    client = connect(settings)
    try:
        db = client[settings.mongodb_database]
        ensure_indexes(db, settings.session_max_age_seconds)
        if db.users.find_one({"username": username}) is not None:
            raise SystemExit("That username already exists; use the promote command instead.")
        password = getpass.getpass("Admin password: ")
        confirmation = getpass.getpass("Confirm password: ")
        if len(password) < 12:
            raise SystemExit("Admin passwords must contain at least 12 characters.")
        if password != confirmation:
            raise SystemExit("Passwords do not match.")
        db.users.insert_one(
            {
                "username": username,
                "password": bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode(),
                "role": "admin",
                "suspended": False,
                "display_name": "",
                "profile_location": "",
                "show_name_on_public_trails": False,
                "show_location_on_public_trails": False,
                "email": "",
            }
        )
        print(f"Administrator {username!r} created.")
    finally:
        client.close()


def promote_admin(username: str) -> None:
    settings = get_settings()
    client = connect(settings)
    try:
        result = client[settings.mongodb_database].users.update_one(
            {"username": username}, {"$set": {"role": "admin", "suspended": False}}
        )
        if result.matched_count != 1:
            raise SystemExit("Account not found.")
        print(f"Account {username!r} promoted to administrator.")
    finally:
        client.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="Create or promote a JaTrail administrator")
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in ("create", "promote"):
        subparser = subparsers.add_parser(command)
        subparser.add_argument("username")
    args = parser.parse_args()
    if args.command == "create":
        create_admin(args.username)
    else:
        promote_admin(args.username)


if __name__ == "__main__":
    main()
