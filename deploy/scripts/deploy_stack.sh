#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$script_dir/common.sh"

validate_image_tag "$@"
image_tag=$1
"$script_dir/verify_runtime.sh"
select_image "$image_tag"

echo "Pulling MongoDB, Caddy, and FastAPI images"
compose pull mongo caddy web

echo "Starting MongoDB"
compose up -d --no-build --pull never mongo

# The official image performs first-run initialization in its PID 1 entrypoint.
# Wait with lightweight host process inspection until that entrypoint has
# replaced itself with the final mongod. This avoids starting a second mongosh
# while MongoDB's own initialization script is still using one.
mongo_container=$(compose ps -q mongo)
mongod_wait_attempt=1
mongod_wait_attempts=30
while :; do
    mongo_pid=$(docker inspect --format '{{.State.Pid}}' "$mongo_container")
    if [ "$mongo_pid" -gt 0 ] && [ "$(cat "/proc/$mongo_pid/comm")" = "mongod" ]; then
        break
    fi
    if [ "$mongod_wait_attempt" -ge "$mongod_wait_attempts" ]; then
        echo "MongoDB did not finish container initialization" >&2
        exit 1
    fi
    sleep 1
    mongod_wait_attempt=$((mongod_wait_attempt + 1))
done
sleep 2

echo "Checking MongoDB once"
compose exec -T mongo sh -ec '
    mongosh --quiet --host 127.0.0.1 \
        --username "$(cat /run/secrets/mongo_root_username)" \
        --password "$(cat /run/secrets/mongo_root_password)" \
        --authenticationDatabase admin \
        --eval "quit(db.adminCommand({ping: 1}).ok ? 0 : 2)"
'

echo "Synchronizing the MongoDB application account"
compose exec -T mongo bash /docker-entrypoint-initdb.d/01-create-app-user.sh

echo "Starting FastAPI and Caddy"
compose up -d --no-build --pull never --wait web caddy
public_health_check
record_deployment "$image_tag"
compose ps
echo "Stack deployment complete: ghcr.io/vesapehkonen/jatrail:$image_tag"
