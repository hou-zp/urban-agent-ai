#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_cmd curl

env_file="$STAGING_DIR/config/staging.env"
if [[ -f "$env_file" ]]; then
    set -a
    source "$env_file"
    set +a
fi
resolve_backend_port

max_attempts="${MAX_HEALTH_ATTEMPTS:-30}"
sleep_seconds="${HEALTH_CHECK_INTERVAL_SECONDS:-1}"
pid_file="$(pid_file_path)"

for attempt in $(seq 1 "$max_attempts"); do
    if [[ ! -f "$pid_file" ]]; then
        echo "backend pid file not found during health check" >&2
        exit 1
    fi
    expected_pid="$(cat "$pid_file")"
    if ! is_pid_running "$expected_pid"; then
        echo "backend process exited before health check completed" >&2
        exit 1
    fi
    active_listener_pid="$(listener_pid)"
    if [[ -n "$active_listener_pid" && "$active_listener_pid" != "$expected_pid" ]]; then
        echo "port ${BACKEND_PORT} is occupied by pid ${active_listener_pid}, expected ${expected_pid}" >&2
        exit 1
    fi
    if curl --noproxy '*' -fsS "http://127.0.0.1:${BACKEND_PORT}/actuator/health"; then
        exit 0
    fi
    sleep "$sleep_seconds"
done

echo "health check failed after ${max_attempts} attempts" >&2
exit 1
