#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_cmd mvn
require_cmd npm
require_cmd java

timestamp="$(date '+%Y%m%d-%H%M%S')"
release_dir="$RELEASES_DIR/$timestamp"

mkdir -p "$release_dir/backend" "$release_dir/frontend" "$release_dir/config" "$release_dir/nginx" "$release_dir/systemd" "$release_dir/db"

(
    cd "$BACKEND_DIR"
    mvn -DskipTests package
)

(
    cd "$FRONTEND_DIR"
    npm run build
)

backend_jar="$(find "$BACKEND_DIR/target" -maxdepth 1 -name '*.jar' ! -name '*original*.jar' | head -n 1)"
if [[ -z "$backend_jar" ]]; then
    echo "backend jar not found" >&2
    exit 1
fi

cp "$backend_jar" "$release_dir/backend/urban-agent-backend.jar"
cp -R "$FRONTEND_DIR/dist/." "$release_dir/frontend/"
cp "$DEPLOY_DIR/env/staging.env.example" "$release_dir/config/staging.env"
cp "$DEPLOY_DIR/nginx/urban-agent.conf.template" "$release_dir/nginx/"
cp "$DEPLOY_DIR/systemd/urban-agent-backend.service.template" "$release_dir/systemd/"
cp "$BACKEND_DIR/scripts/init-postgres.sql" "$release_dir/db/"

echo "$release_dir"
