#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_cmd mvn
require_cmd npm
require_cmd java

release_dir="$("$SCRIPT_DIR/package-release.sh" | tail -n 1)"

mkdir -p "$STAGING_DIR/config" "$STAGING_DIR/run" "$STAGING_DIR/logs"
ln -sfn "$release_dir" "$CURRENT_LINK"

if [[ ! -f "$STAGING_DIR/config/staging.env" ]]; then
    cp "$release_dir/config/staging.env" "$STAGING_DIR/config/staging.env"
fi

set -a
source "$STAGING_DIR/config/staging.env"
set +a
resolve_backend_port

active_listener_pid="$(listener_pid)"
managed_pid_file="$(pid_file_path)"
managed_pid=""
if [[ -f "$managed_pid_file" ]]; then
    managed_pid="$(cat "$managed_pid_file")"
fi
if [[ -n "$active_listener_pid" && "$active_listener_pid" != "$managed_pid" ]]; then
    echo "port ${BACKEND_PORT} is already in use by external pid ${active_listener_pid}" >&2
    exit 1
fi

if [[ "${SPRING_PROFILES_ACTIVE:-}" == *postgres* ]]; then
    "$SCRIPT_DIR/init-postgres.sh"
fi

"$SCRIPT_DIR/render-nginx-conf.sh" "$release_dir" >/dev/null
"$SCRIPT_DIR/stop-backend.sh" || true
"$SCRIPT_DIR/start-backend.sh" "$release_dir" >/dev/null
if ! "$SCRIPT_DIR/health-check.sh"; then
    echo "backend failed to become healthy, recent log:" >&2
    tail -n 80 "$STAGING_DIR/logs/backend.log" >&2 || true
    exit 1
fi

echo "$release_dir"
