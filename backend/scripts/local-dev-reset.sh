#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.local-dev.yml"
PID_FILE="$ROOT_DIR/tmp/local-dev-backend.pid"
LOG_FILE="$ROOT_DIR/tmp/local-dev-backend.log"

MODE="background"
if [[ "${1:-}" == "--deps-only" ]]; then
  MODE="deps-only"
elif [[ "${1:-}" == "--foreground" ]]; then
  MODE="foreground"
elif [[ "${1:-}" == "--background" || -z "${1:-}" ]]; then
  MODE="background"
else
  echo "usage: $0 [--deps-only|--background|--foreground]" >&2
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

if [[ -f "$PID_FILE" ]]; then
  PID="$(cat "$PID_FILE")"
  if kill -0 "$PID" 2>/dev/null; then
    kill "$PID" || true
    echo "Stopped backend pid $PID"
    sleep 2
  fi
  rm -f "$PID_FILE"
fi

rm -f "$LOG_FILE"

echo "Removing local dev containers and volumes..."
compose down -v --remove-orphans

echo "Rebuilding local dev environment from empty PostgreSQL/Redis volumes..."
case "$MODE" in
  deps-only)
    exec "$ROOT_DIR/scripts/local-dev-up.sh" --deps-only
    ;;
  foreground)
    exec "$ROOT_DIR/scripts/local-dev-up.sh"
    ;;
  background)
    exec "$ROOT_DIR/scripts/local-dev-up.sh" --backend-background
    ;;
esac
