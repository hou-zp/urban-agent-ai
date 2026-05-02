#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

release_dir="${1:-$(current_release_dir)}"
if [[ -z "$release_dir" || ! -d "$release_dir" ]]; then
    echo "release directory not found" >&2
    exit 1
fi

template="$release_dir/nginx/urban-agent.conf.template"
output_dir="$STAGING_DIR/nginx"
output_file="$output_dir/urban-agent.conf"

mkdir -p "$output_dir"

frontend_dist_dir="$STAGING_DIR/current/frontend"

sed \
    -e "s|\${SERVER_NAME}|$SERVER_NAME|g" \
    -e "s|\${FRONTEND_DIST_DIR}|$frontend_dist_dir|g" \
    -e "s|\${BACKEND_PORT}|$BACKEND_PORT|g" \
    "$template" > "$output_file"

echo "$output_file"
