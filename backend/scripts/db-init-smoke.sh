#!/usr/bin/env bash
# 单文件数据库初始化 smoke test。
#
# 作用：在干净 PostgreSQL 容器中用 Flyway 执行全部迁移，验证：
# - Flyway 能成功解析并执行初始化文件与后续增量迁移；
# - `${API_KEY}` 这类需要保留给运行时替换的字面值没有被 Flyway placeholder 误吞；
# - Paipai / saving / 多 App 首发所需的核心表、remote config、公告与 usage policy 种子数据存在。
#
# 该脚本不依赖本机 Postgres，适合个人开发者本地/CI 低运维运行。需要 Docker。
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
MIGRATION_DIR="$BACKEND_DIR/src/main/resources/db/migration"
CONTAINER_NAME="${DB_INIT_SMOKE_CONTAINER:-apphub-db-init-smoke}"
POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgres:17-alpine}"
FLYWAY_IMAGE="${FLYWAY_IMAGE:-flyway/flyway:11-alpine}"
HOST_PORT="${DB_INIT_SMOKE_PORT:-55433}"
DB_NAME="${DB_INIT_SMOKE_DB:-apphub_smoke}"
DB_USER="postgres"
DB_PASSWORD="postgres"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required for db init smoke" >&2
  exit 2
fi
if [[ ! -f "$MIGRATION_DIR/V1__init.sql" ]]; then
  echo "missing migration baseline: $MIGRATION_DIR/V1__init.sql" >&2
  exit 1
fi

cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup

echo "starting PostgreSQL smoke container: $CONTAINER_NAME"
docker run --name "$CONTAINER_NAME" \
  -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -e POSTGRES_DB="$DB_NAME" \
  -p "$HOST_PORT:5432" \
  -d "$POSTGRES_IMAGE" >/dev/null

for _ in $(seq 1 60); do
  if docker exec "$CONTAINER_NAME" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
if ! docker exec "$CONTAINER_NAME" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
  echo "PostgreSQL did not become ready" >&2
  exit 1
fi

echo "running Flyway migration from $MIGRATION_DIR"
docker run --rm --network host \
  -v "$MIGRATION_DIR:/flyway/sql:ro" \
  "$FLYWAY_IMAGE" \
  -url="jdbc:postgresql://127.0.0.1:${HOST_PORT}/${DB_NAME}" \
  -user="$DB_USER" \
  -password="$DB_PASSWORD" \
  -connectRetries=3 \
  migrate >/tmp/apphub-db-init-smoke-flyway.log

SQL="
select 'flyway_versions=' || count(*) from flyway_schema_history where success = true and type = 'SQL';
select 'tables=' || count(*) from information_schema.tables where table_schema = 'public';
select 'remote_config=' || count(*) from sys_remote_config;
select 'announcements=' || count(*) from reading_announcement;
select 'usage_policy=' || count(*) from sys_remote_config where app_code='paipai_readingcompanion' and namespace_code='reading_usage_policy';
select 'api_key_literal=' || (config_value_json #>> '{value,Authorization}') from sys_remote_config where app_code='paipai_readingcompanion' and namespace_code='cloud_provider' and config_key='ocr.headers';
"
RESULTS="$(docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -Atc "$SQL")"
echo "$RESULTS"

expect_line() {
  local expected="$1"
  if ! grep -Fxq "$expected" <<<"$RESULTS"; then
    echo "db init smoke failed: expected line '$expected'" >&2
    echo "Flyway log:" >&2
    cat /tmp/apphub-db-init-smoke-flyway.log >&2 || true
    exit 1
  fi
}

expect_numeric_at_least() {
  local key="$1"
  local minimum="$2"
  local line
  line="$(grep -E "^${key}=[0-9]+$" <<<"$RESULTS" || true)"
  if [[ -z "$line" ]]; then
    echo "db init smoke failed: missing numeric line '${key}=...'" >&2
    echo "Flyway log:" >&2
    cat /tmp/apphub-db-init-smoke-flyway.log >&2 || true
    exit 1
  fi
  local actual="${line#*=}"
  if (( actual < minimum )); then
    echo "db init smoke failed: expected ${key} >= ${minimum}, got ${actual}" >&2
    echo "Flyway log:" >&2
    cat /tmp/apphub-db-init-smoke-flyway.log >&2 || true
    exit 1
  fi
}

EXPECTED_FLYWAY_VERSIONS="$(find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*__*.sql' | wc -l | tr -d '[:space:]')"
expect_line "flyway_versions=${EXPECTED_FLYWAY_VERSIONS}"
expect_numeric_at_least "remote_config" 58
expect_line "announcements=3"
expect_line "usage_policy=4"
expect_line 'api_key_literal=Bearer ${API_KEY}'

echo "db init smoke: PASS"
