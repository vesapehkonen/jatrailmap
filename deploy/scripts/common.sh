#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
deploy_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
jatrail_root=$(CDPATH= cd -- "$deploy_dir/.." && pwd)
compose_file="$deploy_dir/compose.yaml"
image_env="$deploy_dir/image.env"
deployed_record="$deploy_dir/.deployed-image"

validate_image_tag() {
    if [ "$#" -ne 1 ] || ! printf '%s\n' "$1" | grep -Eq '^(latest|[0-9a-f]{40})$'; then
        echo "Expected latest or a full 40-character lowercase Git commit SHA" >&2
        exit 2
    fi

    if [ "$1" = "latest" ]; then
        echo "Warning: latest is a moving tag; use a full commit SHA for production" >&2
    fi
}

compose() {
    docker compose --env-file "$image_env" -f "$compose_file" "$@"
}

select_image() {
    selected_tag=$1
    next_env="$image_env.next"
    umask 077
    printf 'JATRAIL_IMAGE_VERSION=%s\n' "$selected_tag" > "$next_env"
    mv -- "$next_env" "$image_env"
    chmod 600 "$image_env"
}

public_health_check() {
    site_address=$(sed -n 's/^JATRAIL_SITE_ADDRESS=//p' "$deploy_dir/caddy.env" | tail -n 1)
    case "$site_address" in
        http://*|https://*) health_base=$site_address ;;
        '')
            echo "JATRAIL_SITE_ADDRESS is missing from $deploy_dir/caddy.env" >&2
            exit 2
            ;;
        *) health_base="https://$site_address" ;;
    esac

    echo "Checking $health_base/health"
    health_attempt=1
    health_attempts=12
    health_retry_seconds=5
    while :; do
        if curl --fail --silent --show-error --max-time 5 "$health_base/health"; then
            printf '\n'
            return 0
        fi

        if [ "$health_attempt" -ge "$health_attempts" ]; then
            echo "Public health check failed after $health_attempt attempts" >&2
            echo "Inspect Caddy with: docker logs --tail=200 jatrail-caddy-1" >&2
            return 1
        fi

        echo "Public endpoint is not ready; retrying in ${health_retry_seconds}s ($health_attempt/$health_attempts)"
        sleep "$health_retry_seconds"
        health_attempt=$((health_attempt + 1))
    done
}

record_deployment() {
    umask 077
    printf '%s\n' "$1" > "$deployed_record"
    chmod 600 "$deployed_record"
}
