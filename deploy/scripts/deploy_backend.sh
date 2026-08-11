#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$script_dir/common.sh"

validate_image_tag "$@"
image_tag=$1
"$script_dir/verify_runtime.sh"
select_image "$image_tag"

echo "Pulling FastAPI image only"
docker pull "ghcr.io/vesapehkonen/jatrail:$image_tag"

echo "Replacing only the FastAPI web container"
compose up -d --no-deps --no-build --pull never --wait web
public_health_check
record_deployment "$image_tag"
compose ps web
echo "Backend deployment complete: ghcr.io/vesapehkonen/jatrail:$image_tag"
