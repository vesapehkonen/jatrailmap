#!/usr/bin/env python3
import argparse

from app.config import get_settings
from app.database import connect
from app.profile_migration import migrate_user_profiles


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Migrate legacy JaTrail user profile fields to the current schema"
    )
    parser.add_argument(
        "--dry-run", action="store_true", help="Report changes without updating MongoDB"
    )
    args = parser.parse_args()

    settings = get_settings()
    client = connect(settings)
    try:
        result = migrate_user_profiles(
            client[settings.mongodb_database], dry_run=args.dry_run
        )
    finally:
        client.close()

    mode = "Would migrate" if args.dry_run else "Migrated"
    print(
        f"Scanned {result.scanned} users. {mode} {result.migrated}; "
        f"{result.unchanged} already current."
    )


if __name__ == "__main__":
    main()
