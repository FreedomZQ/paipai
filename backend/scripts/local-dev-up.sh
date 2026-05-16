#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.local-dev.yml"
TMP_DIR="$ROOT_DIR/tmp"
PID_FILE="$TMP_DIR/local-dev-backend.pid"
LOG_FILE="$TMP_DIR/local-dev-backend.log"

mkdir -p "$TMP_DIR"

MODE="foreground"
if [[ "${1:-}" == "--deps-only" ]]; then
  MODE="deps-only"
elif [[ "${1:-}" == "--backend-background" ]]; then
  MODE="background"
elif [[ -n "${1:-}" ]]; then
  echo "usage: $0 [--deps-only|--backend-background]" >&2
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

find_mvn() {
  if command -v mvn >/dev/null 2>&1; then
    command -v mvn
    return
  fi
  if [[ -x /tmp/openclaw-tools/apache-maven-3.9.9/bin/mvn ]]; then
    echo /tmp/openclaw-tools/apache-maven-3.9.9/bin/mvn
    return
  fi
  echo "mvn not found. Install Maven or provide it in PATH." >&2
  exit 1
}

find_java_home() {
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    echo "$JAVA_HOME"
    return
  fi
  if [[ -x /tmp/openclaw-tools/jdk-17/bin/java ]]; then
    echo /tmp/openclaw-tools/jdk-17
    return
  fi
  if command -v java >/dev/null 2>&1; then
    local java_bin
    java_bin="$(command -v java)"
    echo "$(cd -- "$(dirname -- "$java_bin")/.." && pwd)"
    return
  fi
  echo "java not found. Install JDK 17+ or set JAVA_HOME." >&2
  exit 1
}

wait_for_health() {
  local container="$1"
  local retries=40
  while (( retries > 0 )); do
    local status
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      return 0
    fi
    sleep 2
    retries=$((retries - 1))
  done
  echo "Timed out waiting for container $container" >&2
  docker ps --filter "name=$container" >&2 || true
  exit 1
}

MVN_BIN="$(find_mvn)"
export JAVA_HOME="$(find_java_home)"
export PATH="$(dirname "$MVN_BIN"):$JAVA_HOME/bin:$PATH"
BACKEND_RUN_JVM_ARGS="-Dspring.flyway.placeholders.API_KEY=\${API_KEY}"

export LOCAL_DB_NAME="${LOCAL_DB_NAME:-apphub_dev}"
export LOCAL_DB_USERNAME="${LOCAL_DB_USERNAME:-postgres}"
export LOCAL_DB_PASSWORD="${LOCAL_DB_PASSWORD:-postgres}"
export LOCAL_DB_PORT="${LOCAL_DB_PORT:-15432}"
export LOCAL_REDIS_PORT="${LOCAL_REDIS_PORT:-16379}"

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
export SERVER_ADDRESS="${SERVER_ADDRESS:-0.0.0.0}"
export SERVER_PORT="${SERVER_PORT:-18082}"
export DB_URL="${DB_URL:-jdbc:postgresql://127.0.0.1:${LOCAL_DB_PORT}/${LOCAL_DB_NAME}}"
export DB_USERNAME="${DB_USERNAME:-$LOCAL_DB_USERNAME}"
export DB_PASSWORD="${DB_PASSWORD:-$LOCAL_DB_PASSWORD}"
export REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
export REDIS_PORT="${REDIS_PORT:-$LOCAL_REDIS_PORT}"
export BACKEND_ENV="${BACKEND_ENV:-dev}"
export BACKEND_OPS_TOKEN="${BACKEND_OPS_TOKEN:-dev-local-token}"
export BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://$(ipconfig getifaddr en0 2>/dev/null || echo 127.0.0.1):${SERVER_PORT}}"
# Keep Flyway from treating cloud-provider header templates like ${API_KEY} as missing startup placeholders.
# We pass this as an exact JVM system property because Flyway placeholder keys are case-sensitive.

compose up -d postgres redis
wait_for_health apphub-local-postgres
wait_for_health apphub-local-redis

echo "Dependencies are ready."
echo "  PostgreSQL: 127.0.0.1:${LOCAL_DB_PORT}/${LOCAL_DB_NAME}"
echo "  Redis:      127.0.0.1:${LOCAL_REDIS_PORT}"
echo "  Backend:    ${BACKEND_BASE_URL}"
echo "  Ops token:  ${BACKEND_OPS_TOKEN}"

if [[ "$MODE" == "deps-only" ]]; then
  exit 0
fi

cd "$ROOT_DIR"

if [[ "$MODE" == "background" ]]; then
  if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "Backend already running with pid $(cat "$PID_FILE")" >&2
    exit 1
  fi
  nohup "$MVN_BIN" -q -DskipTests "-Dspring-boot.run.jvmArguments=$BACKEND_RUN_JVM_ARGS" spring-boot:run >"$LOG_FILE" 2>&1 &
  echo $! >"$PID_FILE"
  echo "Backend started in background."
  echo "  pid:  $(cat "$PID_FILE")"
  echo "  log:  $LOG_FILE"
  echo "  tail: tail -f $LOG_FILE"
  exit 0
fi

echo "Starting backend in foreground..."
echo "Press Ctrl-C to stop backend. Dependencies stay up until you run scripts/local-dev-down.sh"
exec "$MVN_BIN" -q -DskipTests "-Dspring-boot.run.jvmArguments=$BACKEND_RUN_JVM_ARGS" spring-boot:run
