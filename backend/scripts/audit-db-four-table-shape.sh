#!/usr/bin/env bash
# Compatibility wrapper after clarification: audit the four area-owned initialization SQL files.
# The files together should preserve all CREATE TABLE statements from active migrations.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/audit-db-four-init-files.sh" "$@"
