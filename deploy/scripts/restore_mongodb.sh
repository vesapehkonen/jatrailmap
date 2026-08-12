#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <archive.gz>" >&2
    exit 2
fi

archive=$1

deploy_dir=/srv/jatrail/deploy
compose_file="$deploy_dir/compose.yaml"
image_env="$deploy_dir/image.env"
override_file=/tmp/jatrail-restore-compose.yaml

if [ ! -f "$archive" ]; then
    echo "Backup archive not found: $archive" >&2
    exit 2
fi

cat > "$override_file" <<'YAML'
services:
  mongo:
    mem_limit: 1g
YAML

echo "Restarting MongoDB with temporary 1 GB memory limit"
docker compose \
    --env-file "$image_env" \
    -f "$compose_file" \
    -f "$override_file" \
    up -d --no-deps --force-recreate mongo

echo "Waiting briefly for MongoDB"
sleep 5

echo "Restoring $archive"
docker exec -i jatrail-mongo-1 sh -c '
    mongorestore \
        --host 127.0.0.1 \
        --username "$(cat /run/secrets/mongo_root_username)" \
        --password "$(cat /run/secrets/mongo_root_password)" \
        --authenticationDatabase admin \
        --drop \
        --numInsertionWorkersPerCollection=1 \
        --archive \
        --gzip
' < "$archive"

echo "Restore completed"

echo "Restarting MongoDB with normal Compose memory limit"
docker compose \
    --env-file "$image_env" \
    -f "$compose_file" \
    up -d --no-deps --force-recreate mongo

echo "Restore finished successfully"
