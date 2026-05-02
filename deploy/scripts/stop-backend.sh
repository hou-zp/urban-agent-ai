#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

pid_file="$(pid_file_path)"
if [[ ! -f "$pid_file" ]]; then
    echo "backend pid file not found"
    exit 0
fi

pid="$(cat "$pid_file")"
if is_pid_running "$pid"; then
    kill "$pid"
fi

rm -f "$pid_file"
