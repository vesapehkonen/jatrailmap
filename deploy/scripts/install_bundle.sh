#!/bin/bash

set -Eeuo pipefail

if [[ ${EUID:-$(id -u)} -ne 0 || $# -ne 2 ]]; then
    echo "Usage: install_bundle.sh EXTRACTED_DEPLOY_DIRECTORY DEPLOYMENT_USER" >&2
    exit 2
fi

source_dir=$1
deploy_user=$2
deploy_group=$(id -gn "$deploy_user")
target_dir=/srv/jatrail/deploy

for required_file in compose.yaml Caddyfile mongo-init/01-create-app-user.sh; do
    if [[ ! -s "$source_dir/$required_file" ]]; then
        echo "Deployment bundle is missing $required_file" >&2
        exit 2
    fi
done

install -d -m 0750 -o "$deploy_user" -g "$deploy_group" "$target_dir/scripts"
install -d -m 0750 -o "$deploy_user" -g "$deploy_group" "$target_dir/mongo-init"
install -m 0644 -o "$deploy_user" -g "$deploy_group" "$source_dir/compose.yaml" "$target_dir/compose.yaml"
install -m 0644 -o "$deploy_user" -g "$deploy_group" "$source_dir/Caddyfile" "$target_dir/Caddyfile"
install -m 0644 -o "$deploy_user" -g "$deploy_group" \
    "$source_dir/mongo-init/01-create-app-user.sh" \
    "$target_dir/mongo-init/01-create-app-user.sh"

for script_name in common.sh verify_runtime.sh deploy_stack.sh deploy_backend.sh backup_mongodb.sh; do
    source_script="$source_dir/scripts/$script_name"
    if [[ ! -s "$source_script" ]]; then
        echo "Deployment bundle is missing scripts/$script_name" >&2
        exit 2
    fi
    install -m 0755 -o "$deploy_user" -g "$deploy_group" \
        "$source_script" "$target_dir/scripts/$script_name"
done

# Remove only files belonging to the retired repository-based deployment.
rm -f "$target_dir/deploy_image.sh" "$target_dir/backup_mongodb.sh"

echo "Deployment bundle installed without changing VPS-local configuration or secrets"
