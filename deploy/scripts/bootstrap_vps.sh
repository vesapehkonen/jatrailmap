#!/bin/bash

set -Eeuo pipefail

if [[ ${EUID:-$(id -u)} -ne 0 ]]; then
    echo "bootstrap_vps.sh must run as root" >&2
    exit 2
fi

if [[ $# -ne 1 ]] || ! id "$1" >/dev/null 2>&1; then
    echo "Usage: bootstrap_vps.sh EXISTING_DEPLOYMENT_USER" >&2
    exit 2
fi

deploy_user=$1
deploy_group=$(id -gn "$deploy_user")

if [[ ! -r /etc/os-release ]]; then
    echo "Cannot identify the operating system" >&2
    exit 2
fi
. /etc/os-release
if [[ ${ID:-} != ubuntu ]]; then
    echo "This bootstrap supports Ubuntu only" >&2
    exit 2
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl openssl

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc

    architecture=$(dpkg --print-architecture)
    ubuntu_codename=${UBUNTU_CODENAME:-$VERSION_CODENAME}
    printf '%s\n' \
        'Types: deb' \
        'URIs: https://download.docker.com/linux/ubuntu' \
        "Suites: $ubuntu_codename" \
        'Components: stable' \
        "Architectures: $architecture" \
        'Signed-By: /etc/apt/keyrings/docker.asc' \
        > /etc/apt/sources.list.d/docker.sources

    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker
usermod -aG docker "$deploy_user"

install -d -m 0750 -o "$deploy_user" -g "$deploy_group" /srv/jatrail
install -d -m 0750 -o "$deploy_user" -g "$deploy_group" /srv/jatrail/deploy
install -d -m 0700 -o "$deploy_user" -g "$deploy_group" /srv/jatrail/deploy/secrets
install -d -m 0750 -o "$deploy_user" -g "$deploy_group" /srv/jatrail/deploy/scripts
install -d -m 0750 -o "$deploy_user" -g "$deploy_group" /srv/jatrail/deploy/mongo-init
install -d -m 0700 -o "$deploy_user" -g "$deploy_group" /srv/jatrail/backups

create_secret_if_missing() {
    local target=$1
    local value=$2
    if [[ -e "$target" || -L "$target" ]]; then
        echo "Preserving existing credential file: $target"
        return
    fi

    umask 022
    printf '%s' "$value" > "$target"
    chown "$deploy_user:$deploy_group" "$target"
    chmod 0644 "$target"
    echo "Created credential file: $target"
}

create_secret_if_missing \
    /srv/jatrail/deploy/secrets/mongo_root_username \
    jatrail-admin
create_secret_if_missing \
    /srv/jatrail/deploy/secrets/mongo_root_password \
    "$(openssl rand -base64 48)"
create_secret_if_missing \
    /srv/jatrail/deploy/secrets/mongo_app_username \
    jatrail-app
create_secret_if_missing \
    /srv/jatrail/deploy/secrets/mongo_app_password \
    "$(openssl rand -base64 48)"

docker --version
docker compose version
echo "Bootstrap complete. No application containers were deployed."
echo "MongoDB credential files exist locally on the VPS and were not sent to GitHub."
echo "Open a new SSH session before running Docker as $deploy_user."
