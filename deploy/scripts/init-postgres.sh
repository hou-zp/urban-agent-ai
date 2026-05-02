#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

env_file="$STAGING_DIR/config/staging.env"
if [[ -f "$env_file" ]]; then
    set -a
    source "$env_file"
    set +a
fi

sql_file="$PROJECT_ROOT/backend/scripts/init-postgres.sql"
postgres_container="${POSTGRES_CONTAINER:-postgres-15}"
postgres_superuser="${POSTGRES_SUPERUSER:-postgres}"
postgres_database="${POSTGRES_SUPERUSER_DATABASE:-postgres}"
db_name="${DB_NAME:-urban_agent}"
db_username="${DB_USERNAME:-urban_agent}"
db_password="${DB_PASSWORD:-}"

if [[ ! -f "$sql_file" ]]; then
    echo "postgres init sql not found: $sql_file" >&2
    exit 1
fi

if [[ -z "$db_password" ]]; then
    echo "DB_PASSWORD is required to initialize PostgreSQL" >&2
    exit 1
fi

psql_vars=(
    -v ON_ERROR_STOP=1
    -v app_db="$db_name"
    -v app_user="$db_username"
    -v app_password="$db_password"
)

if command -v psql >/dev/null 2>&1; then
    psql "${psql_vars[@]}" -U "$postgres_superuser" -d "$postgres_database" -f "$sql_file"
    exit 0
fi

if command -v docker >/dev/null 2>&1; then
    if ! docker ps --format '{{.Names}}' | grep -Fx "$postgres_container" >/dev/null 2>&1; then
        echo "postgres container not found: $postgres_container" >&2
        exit 1
    fi
    docker exec -i "$postgres_container" psql "${psql_vars[@]}" -U "$postgres_superuser" -d "$postgres_database" < "$sql_file"
    exit 0
fi

echo "psql or docker is required to initialize PostgreSQL" >&2
exit 1
