#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.local-dev.yml"
PID_FILE="$ROOT_DIR/tmp/local-dev-backend.pid"
LOG_FILE="$ROOT_DIR/tmp/local-dev-backend.log"

export LOCAL_DB_NAME="${LOCAL_DB_NAME:-apphub_dev}"
export LOCAL_DB_USERNAME="${LOCAL_DB_USERNAME:-postgres}"
export LOCAL_DB_PASSWORD="${LOCAL_DB_PASSWORD:-postgres}"
export LOCAL_DB_PORT="${LOCAL_DB_PORT:-15432}"
export LOCAL_REDIS_PORT="${LOCAL_REDIS_PORT:-16379}"
export SERVER_PORT="${SERVER_PORT:-18082}"
export BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://127.0.0.1:${SERVER_PORT}}"
export BACKEND_OPS_TOKEN="${BACKEND_OPS_TOKEN:-dev-local-token}"

STRICT_GATE=false
if [[ "${1:-}" == "--strict-gate" ]]; then
  STRICT_GATE=true
elif [[ -n "${1:-}" ]]; then
  echo "usage: $0 [--strict-gate]" >&2
  exit 2
fi

compose() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    docker compose -f "$COMPOSE_FILE" "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose -f "$COMPOSE_FILE" "$@"
  else
    echo "docker compose is required" >&2
    exit 1
  fi
}

container_status() {
  local name="$1"
  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null || echo "missing"
}

pass() { printf 'PASS %-22s %s\n' "$1" "${2:-}"; }
warn() { printf 'WARN %-22s %s\n' "$1" "${2:-}"; }
fail() { printf 'FAIL %-22s %s\n' "$1" "${2:-}"; FAILED=true; }

FAILED=false

echo "local dev doctor"
echo "  backend_url: $BACKEND_BASE_URL"
echo "  db:          127.0.0.1:${LOCAL_DB_PORT}/${LOCAL_DB_NAME}"
echo "  redis:       127.0.0.1:${LOCAL_REDIS_PORT}"
echo ""

POSTGRES_STATUS="$(container_status apphub-local-postgres)"
REDIS_STATUS="$(container_status apphub-local-redis)"
[[ "$POSTGRES_STATUS" == "healthy" || "$POSTGRES_STATUS" == "running" ]] && pass postgres "container=$POSTGRES_STATUS" || fail postgres "container=$POSTGRES_STATUS"
[[ "$REDIS_STATUS" == "healthy" || "$REDIS_STATUS" == "running" ]] && pass redis "container=$REDIS_STATUS" || fail redis "container=$REDIS_STATUS"

if [[ -f "$PID_FILE" ]]; then
  PID="$(cat "$PID_FILE")"
  if kill -0 "$PID" 2>/dev/null; then
    pass backend-pid "pid=$PID"
  else
    fail backend-pid "stale pid=$PID"
  fi
else
  fail backend-pid "missing $PID_FILE"
fi

if docker exec apphub-local-postgres pg_isready -U "$LOCAL_DB_USERNAME" -d "$LOCAL_DB_NAME" >/dev/null 2>&1; then
  pass db-ready "pg_isready ok"
else
  fail db-ready "pg_isready failed"
fi

if docker exec apphub-local-postgres psql -U "$LOCAL_DB_USERNAME" -d "$LOCAL_DB_NAME" -Atc 'select count(*) from flyway_schema_history;' >/tmp/local-dev-doctor-flyway-count 2>/tmp/local-dev-doctor-db.err; then
  FLYWAY_COUNT="$(cat /tmp/local-dev-doctor-flyway-count)"
  pass db-flyway "migrations=$FLYWAY_COUNT"
else
  fail db-flyway "$(cat /tmp/local-dev-doctor-db.err 2>/dev/null | head -n1)"
fi

if docker exec apphub-local-redis redis-cli ping 2>/dev/null | grep -q '^PONG$'; then
  pass redis-ping "PONG"
else
  fail redis-ping "redis-cli ping failed"
fi

if HEALTH_BODY="$(curl -fsS --max-time 8 "$BACKEND_BASE_URL/api/v1/system/healthz" 2>/tmp/local-dev-doctor-health.err)"; then
  pass healthz "$(printf '%s' "$HEALTH_BODY" | sed -n 's/.*"status":"\([^"]*\)".*/status=\1/p')"
else
  fail healthz "$(cat /tmp/local-dev-doctor-health.err 2>/dev/null | head -n1)"
fi

if ROUTE_OUTPUT="$($ROOT_DIR/scripts/check-no-auth-compat-routes.sh 2>&1)"; then
  pass route-guard "$ROUTE_OUTPUT"
else
  fail route-guard "$ROUTE_OUTPUT"
fi

set +e
GATE_OUTPUT="$(BACKEND_BASE_URL="$BACKEND_BASE_URL" BACKEND_OPS_TOKEN="$BACKEND_OPS_TOKEN" "$ROOT_DIR/scripts/release-gate.sh" 2>&1)"
GATE_CODE=$?
set -e
GATE_STATUS="unknown"
if grep -q 'release-gate status=' <<<"$GATE_OUTPUT"; then
  GATE_STATUS="$(grep -o 'release-gate status=[^ ]*' <<<"$GATE_OUTPUT" | head -n1 | cut -d= -f2)"
fi
case "$GATE_CODE" in
  0)
    pass release-gate "status=$GATE_STATUS"
    ;;
  1|2)
    if [[ "$STRICT_GATE" == true ]]; then
      fail release-gate "status=$GATE_STATUS code=$GATE_CODE"
    else
      warn release-gate "status=$GATE_STATUS code=$GATE_CODE (use --strict-gate to fail on blocked/warning)"
    fi
    ;;
  *)
    fail release-gate "request failed code=$GATE_CODE: $(head -n1 <<<"$GATE_OUTPUT")"
    ;;
esac

if [[ -f "$LOG_FILE" ]]; then
  if grep -E 'Application run failed|Exception encountered during context initialization|Process terminated with exit code: 1' "$LOG_FILE" >/tmp/local-dev-doctor-log-errors 2>/dev/null; then
    warn backend-log "recent startup error markers found in $LOG_FILE; inspect with ./scripts/local-dev-tail.sh"
  else
    pass backend-log "no startup error markers"
  fi
else
  warn backend-log "missing $LOG_FILE"
fi

echo ""
echo "docker compose ps:"
compose ps

if [[ "$FAILED" == true ]]; then
  echo ""
  echo "doctor result: FAIL"
  exit 1
fi

echo ""
echo "doctor result: PASS"
exit 0
