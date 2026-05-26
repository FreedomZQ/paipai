#!/usr/bin/env bash
# Verifies that the removed cloud-sync feature does not reappear in active code,
# product docs, templates, or tests.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd)"

PATTERN='PowerSync|powersync|Cloud Sync|cloud sync|cloud-sync|云同步|sync_enabled|cloud_sync|storage_mode|last_modified_by_installation_id|server_synced|server_authoritative'

scan_paths=(
  "$ROOT_DIR/front/ios/PaipaiReadAlong"
  "$ROOT_DIR/front/ios/project.yml"
  "$ROOT_DIR/front/files"
  "$ROOT_DIR/backend/src/main/java"
  "$ROOT_DIR/backend/src/test/java"
  "$ROOT_DIR/backend/src/main/resources"
  "$ROOT_DIR/backend/docs"
  "$ROOT_DIR/backend/files"
  "$ROOT_DIR/backend/scripts"
)

matches="$(
  rg -n -i "$PATTERN" "${scan_paths[@]}" \
    -g '!**/target/**' \
    -g '!**/*.docx' \
    -g '!backend/scripts/audit-cloud-sync-removal.sh' \
    -g '!backend/scripts/audit-db-four-table-init-files.sh' \
    -g '!front/ios/scripts/p0_static_preflight.sh' \
    -g '!front/ios/scripts/p0_archive_preflight.sh' \
    || true
)"

if [[ -n "$matches" ]]; then
  echo "cloud sync removal audit failed; unexpected residue found:" >&2
  echo "$matches" >&2
  exit 1
fi

echo "cloud sync removal audit: PASS"
