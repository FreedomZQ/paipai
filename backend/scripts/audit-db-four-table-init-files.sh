#!/usr/bin/env bash
# Compatibility wrapper after clarification: "four-table init files" means four area-owned SQL files,
# not exactly four physical database tables.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/audit-db-four-init-files.sh" "$@"
