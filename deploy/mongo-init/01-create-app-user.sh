#!/bin/bash
set -Eeuo pipefail

export JATRAIL_INIT_USERNAME
export JATRAIL_INIT_PASSWORD
JATRAIL_INIT_USERNAME="$(cat /run/secrets/mongo_app_username)"
JATRAIL_INIT_PASSWORD="$(cat /run/secrets/mongo_app_password)"

if [[ -z "$JATRAIL_INIT_USERNAME" || -z "$JATRAIL_INIT_PASSWORD" ]]; then
    echo "JaTrail MongoDB application credentials must not be empty" >&2
    exit 1
fi

mongosh --quiet \
    --host 127.0.0.1 \
    --username "$MONGO_INITDB_ROOT_USERNAME" \
    --password "$MONGO_INITDB_ROOT_PASSWORD" \
    --authenticationDatabase admin \
    "$MONGO_INITDB_DATABASE" <<'JAVASCRIPT'
const username = process.env.JATRAIL_INIT_USERNAME;
const password = process.env.JATRAIL_INIT_PASSWORD;
if (!db.getUser(username)) {
    db.createUser({
        user: username,
        pwd: password,
        roles: [{role: "readWrite", db: db.getName()}]
    });
}
JAVASCRIPT

unset JATRAIL_INIT_USERNAME JATRAIL_INIT_PASSWORD
