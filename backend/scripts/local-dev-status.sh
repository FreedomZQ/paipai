#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.local-dev.yml"
PID_FILE="$ROOT_DIR/tmp/local-dev-backend.pid"
LOG_FILE="$ROOT_DIR/tmp/local-dev-backend.log"

export LOCAL_DB_NAME="${LOCAL_DB_NAME:-apphub_dev}"
export LOCAL_DB_PORT="${LOCAL_DB_PORT:-15432}"
export LOCAL_REDIS_PORT="${LOCAL_REDIS_PORT:-16379}"
export SERVER_PORT="${SERVER_PORT:-18082}"
export BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://127.0.0.1:${SERVER_PORT}}"
export BACKEND_OPS_TOKEN="${BACKEND_OPS_TOKEN:-dev-local-token}"

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

ok=true

POSTGRES_STATUS="$(container_status apphub-local-postgres)"
REDIS_STATUS="$(container_status apphub-local-redis)"

BACKEND_PID="-"
BACKEND_PID_STATUS="missing"
if [[ -f "$PID_FILE" ]]; then
  BACKEND_PID="$(cat "$PID_FILE")"
  if kill -0 "$BACKEND_PID" 2>/dev/null; then
    BACKEND_PID_STATUS="running"
  else
    BACKEND_PID_STATUS="stale"
    ok=false
  fi
else
  ok=false
fi

HEALTH_STATUS="down"
HEALTH_BODY=""
if HEALTH_BODY="$(curl -fsS --max-time 5 "$BACKEND_BASE_URL/api/v1/system/healthz" 2>/dev/null)"; then
  HEALTH_STATUS="up"
else
  ok=false
fi

RELEASE_GATE_STATUS="unknown"
if RELEASE_GATE_OUTPUT="$(BACKEND_BASE_URL="$BACKEND_BASE_URL" BACKEND_OPS_TOKEN="$BACKEND_OPS_TOKEN" "$ROOT_DIR/scripts/release-gate.sh" 2>&1 || true)"; then
  :
fi
if grep -q 'release-gate status=' <<<"$RELEASE_GATE_OUTPUT"; then
  RELEASE_GATE_STATUS="$(grep -o 'release-gate status=[^ ]*' <<<"$RELEASE_GATE_OUTPUT" | head -n1 | cut -d= -f2)"
fi

echo "local dev status"
echo "  backend_url:      $BACKEND_BASE_URL"
echo "  postgres:         $POSTGRES_STATUS (127.0.0.1:${LOCAL_DB_PORT}/${LOCAL_DB_NAME})"
echo "  redis:            $REDIS_STATUS (127.0.0.1:${LOCAL_REDIS_PORT})"
echo "  backend_pid:      $BACKEND_PID ($BACKEND_PID_STATUS)"
echo "  backend_healthz:  $HEALTH_STATUS"
echo "  release_gate:     $RELEASE_GATE_STATUS"
if [[ -f "$LOG_FILE" ]]; then
  echo "  backend_log:      $LOG_FILE"
fi

if [[ -n "$HEALTH_BODY" ]]; then
  echo ""
  echo "healthz body:"
  echo "$HEALTH_BODY"
fi

echo ""
echo "docker compose ps:"
compose ps

if [[ "$ok" == true && "$POSTGRES_STATUS" != "missing" && "$REDIS_STATUS" != "missing" && "$HEALTH_STATUS" == "up" ]]; then
  exit 0
fi
exit 1
