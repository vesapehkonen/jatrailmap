#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
deploy_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)

required_files="
$deploy_dir/compose.yaml
$deploy_dir/Caddyfile
$deploy_dir/mongo-init/01-create-app-user.sh
$deploy_dir/jatrail.env
$deploy_dir/caddy.env
$deploy_dir/secrets/mongo_root_username
$deploy_dir/secrets/mongo_root_password
$deploy_dir/secrets/mongo_app_username
$deploy_dir/secrets/mongo_app_password
"

for required_file in $required_files; do
    if [ ! -s "$required_file" ]; then
        echo "Required VPS-local file is missing or empty: $required_file" >&2
        exit 2
    fi
done

command -v docker >/dev/null 2>&1 || { echo "Docker is not installed" >&2; exit 2; }
command -v curl >/dev/null 2>&1 || { echo "curl is not installed" >&2; exit 2; }
docker compose version >/dev/null
docker info >/dev/null

echo "VPS runtime configuration is present"
