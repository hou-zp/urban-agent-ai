#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$DEPLOY_DIR/.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"

STAGING_DIR="${STAGING_DIR:-$PROJECT_ROOT/.staging}"
SERVER_NAME="${SERVER_NAME:-_}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
RELEASES_DIR="$DEPLOY_DIR/releases"
CURRENT_LINK="$STAGING_DIR/current"

function require_cmd() {
    local cmd="$1"
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo "missing command: $cmd" >&2
        exit 1
    fi
}

function current_release_dir() {
    if [[ -L "$CURRENT_LINK" ]]; then
        readlink "$CURRENT_LINK"
        return 0
    fi
    if [[ -d "$CURRENT_LINK" ]]; then
        echo "$CURRENT_LINK"
        return 0
    fi
    echo "" 
}

function resolve_backend_port() {
    if [[ -n "${SERVER_PORT:-}" ]]; then
        BACKEND_PORT="$SERVER_PORT"
    fi
}

function listener_pid() {
    lsof -tiTCP:"$BACKEND_PORT" -sTCP:LISTEN 2>/dev/null | head -n 1 || true
}

function pid_file_path() {
    echo "$STAGING_DIR/run/backend.pid"
}

function is_pid_running() {
    local pid="${1:-}"
    [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1
}
