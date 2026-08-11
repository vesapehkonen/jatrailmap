#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$script_dir/common.sh"

backup_dir=${JATRAIL_BACKUP_DIR:-"$jatrail_root/backups"}
retention_days=${JATRAIL_BACKUP_RETENTION_DAYS:-14}

"$script_dir/verify_runtime.sh"
if [ ! -s "$image_env" ]; then
    echo "No deployed image state exists at $image_env" >&2
    exit 2
fi

case "$retention_days" in
    ''|*[!0-9]*)
        echo "JATRAIL_BACKUP_RETENTION_DAYS must be a non-negative integer" >&2
        exit 2
        ;;
esac

mkdir -p -- "$backup_dir"
chmod 700 -- "$backup_dir"
timestamp=$(date -u '+%Y%m%dT%H%M%SZ')
archive="$backup_dir/jatrail-$timestamp.archive.gz"
partial="$archive.partial"

cleanup() { rm -f -- "$partial"; }
trap cleanup EXIT HUP INT TERM

echo "Creating MongoDB backup: $archive"
compose exec -T mongo sh -ec '
    mongodump \
        --host 127.0.0.1:27017 \
        --username "$(cat /run/secrets/mongo_app_username)" \
        --password "$(cat /run/secrets/mongo_app_password)" \
        --authenticationDatabase jatrail \
        --db jatrail \
        --archive \
        --gzip
' > "$partial"

chmod 600 -- "$partial"
mv -- "$partial" "$archive"
trap - EXIT HUP INT TERM

find "$backup_dir" -type f -name 'jatrail-*.archive.gz' \
    -mtime "+$retention_days" -delete
echo "Backup complete: $archive"
