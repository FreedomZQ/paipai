#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_FILE="${LOCAL_DEV_BACKEND_LOG:-$ROOT_DIR/tmp/local-dev-backend.log}"
LINES="${LOCAL_DEV_TAIL_LINES:-120}"
FOLLOW=true

while [[ $# -gt 0 ]]; do
  case "$1" in
    -n|--lines)
      LINES="${2:?missing line count}"
      shift 2
      ;;
    --no-follow)
      FOLLOW=false
      shift
      ;;
    -h|--help)
      cat <<'USAGE'
usage: scripts/local-dev-tail.sh [--no-follow] [-n|--lines N]

Tail the local backend log written by scripts/local-dev-up.sh --backend-background.
USAGE
      exit 0
      ;;
    *)
      echo "unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ ! -f "$LOG_FILE" ]]; then
  echo "backend log not found: $LOG_FILE" >&2
  echo "Start backend first: ./scripts/local-dev-up.sh --backend-background" >&2
  exit 1
fi

if [[ "$FOLLOW" == true ]]; then
  exec tail -n "$LINES" -f "$LOG_FILE"
fi
exec tail -n "$LINES" "$LOG_FILE"
