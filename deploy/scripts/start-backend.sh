#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

release_dir="${1:-$(current_release_dir)}"
if [[ -z "$release_dir" || ! -d "$release_dir" ]]; then
    echo "release directory not found" >&2
    exit 1
fi

mkdir -p "$STAGING_DIR/run" "$STAGING_DIR/logs"

env_file="$STAGING_DIR/config/staging.env"
if [[ ! -f "$env_file" ]]; then
    cp "$release_dir/config/staging.env" "$env_file"
fi

set -a
source "$env_file"
set +a
resolve_backend_port

local_no_proxy="127.0.0.1,localhost"
export NO_PROXY="${NO_PROXY:-$local_no_proxy}"
export no_proxy="${no_proxy:-${NO_PROXY}}"
case ",$NO_PROXY," in
    *",127.0.0.1,"*) ;;
    *) export NO_PROXY="${NO_PROXY},${local_no_proxy}" ;;
esac
case ",$no_proxy," in
    *",127.0.0.1,"*) ;;
    *) export no_proxy="${no_proxy},${local_no_proxy}" ;;
esac

pid_file="$(pid_file_path)"
log_file="$STAGING_DIR/logs/backend.log"

if [[ -f "$pid_file" ]]; then
    existing_pid="$(cat "$pid_file")"
    if is_pid_running "$existing_pid"; then
        echo "backend already running with pid $existing_pid"
        exit 0
    fi
    rm -f "$pid_file"
fi

nohup java -jar "$release_dir/backend/urban-agent-backend.jar" >> "$log_file" 2>&1 &
echo $! > "$pid_file"
echo "$(cat "$pid_file")"
