#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
compose_file="$script_dir/compose.yaml"
image_env="$script_dir/image.env"
deployed_record="$script_dir/.deployed-image"

if [ "$#" -ne 1 ] || ! printf '%s\n' "$1" | grep -Eq '^(latest|[0-9a-f]{40})$'; then
    echo "Usage: $0 <latest|full-40-character-lowercase-git-commit-sha>" >&2
    exit 2
fi

commit_sha=$1
image="ghcr.io/vesapehkonen/jatrail:$commit_sha"

if [ "$commit_sha" = "latest" ]; then
    echo "Warning: latest is a moving tag; use a full commit SHA for production" >&2
fi

required_files="
$script_dir/jatrail.env
$script_dir/caddy.env
$script_dir/secrets/mongo_root_username
$script_dir/secrets/mongo_root_password
$script_dir/secrets/mongo_app_username
$script_dir/secrets/mongo_app_password
"

for required_file in $required_files; do
    if [ ! -s "$required_file" ]; then
        echo "Required deployment file is missing or empty: $required_file" >&2
        exit 2
    fi
done

next_env="$image_env.next"
cleanup() {
    rm -f -- "$next_env"
}
trap cleanup EXIT HUP INT TERM

umask 077
printf 'JATRAIL_IMAGE_VERSION=%s\n' "$commit_sha" > "$next_env"

echo "Pulling MongoDB and Caddy images required by the Compose stack"
docker compose --env-file "$next_env" -f "$compose_file" pull mongo caddy

echo "Pulling FastAPI image: $image"
docker compose --env-file "$next_env" -f "$compose_file" pull web

# Record the selected image atomically before changing containers. If startup
# fails, rerunning this same SHA continues the interrupted deployment; there is
# deliberately no automatic rollback.
mv -- "$next_env" "$image_env"
trap - EXIT HUP INT TERM

echo "Starting JaTrail without building images on this VPS"
docker compose --env-file "$image_env" -f "$compose_file" up \
    -d --no-build --pull never --wait

site_address=$(sed -n 's/^JATRAIL_SITE_ADDRESS=//p' "$script_dir/caddy.env" | tail -n 1)
case "$site_address" in
    http://*|https://*) health_base=$site_address ;;
    '')
        echo "JATRAIL_SITE_ADDRESS is missing from $script_dir/caddy.env" >&2
        exit 2
        ;;
    *) health_base="https://$site_address" ;;
esac

echo "Checking $health_base/health"
curl --fail --silent --show-error --max-time 20 "$health_base/health"
printf '\n'

printf '%s\n' "$commit_sha" > "$deployed_record"
chmod 600 "$image_env" "$deployed_record"

docker compose --env-file "$image_env" -f "$compose_file" ps
echo "Deployment complete: $image"
