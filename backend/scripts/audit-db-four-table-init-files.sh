#!/usr/bin/env bash
# Audit the unified first-version initialization SQL file.
#
# 中文说明：历史脚本名里保留了 “four-table”，当前首发库已经收敛为单一
# V1__init.sql。本脚本防止重新引入增量迁移或分领域聚合 SQL。
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
DB_DIR="$BACKEND_DIR/src/main/resources/db/first_version"
BASELINE="$DB_DIR/V1__init.sql"

if [[ ! -f "$BASELINE" ]]; then
  echo "BLOCKER: missing unified init SQL file: $BASELINE"
  exit 1
fi

python3 - "$DB_DIR" <<'PY'
from pathlib import Path
import re
import sys

db_dir = Path(sys.argv[1])
sql_files = sorted(path.name for path in db_dir.glob("*.sql"))
baseline = (db_dir / "V1__init.sql").read_text(encoding="utf-8")

required_tables = [
    "sys_app",
    "sys_remote_config",
    "sys_compensation_code",
    "sys_user_compensation_record",
    "reading_daily_quota_config",
    "reading_weekly_report_snapshot",
    "reading_resource_pack_catalog",
    "reading_privacy_request",
    "reading_privacy_purchase_retention",
    "reading_account_deletion_job",
    "reading_deleted_user_tombstone",
    "reading_entitlement_token",
    "reading_entitlement_wallet",
    "reading_entitlement_reservation",
    "reading_entitlement_ledger",
    "reading_entitlement_snapshot",
    "reading_parent_consent",
    "reading_jurisdiction_policy",
    "reading_privacy_event",
    "reading_vendor_registry",
    "reading_security_incident",
]

blockers = []
if sql_files != ["V1__init.sql"]:
    blockers.append(f"first_version must contain only V1__init.sql, found: {sql_files}")

for table in required_tables:
    if not re.search(rf"CREATE\s+TABLE\s+public\.{table}\b", baseline, re.I):
        blockers.append(f"missing CREATE TABLE for {table}")
    table_match = re.search(rf"CREATE\s+TABLE\s+public\.{table}\s*\((.*?)\);", baseline, re.I | re.S)
    if table_match and "PRIMARY KEY" not in table_match.group(1).upper():
        table_body = table_match.group(1).upper()
        constraint_pattern = rf"ALTER\s+TABLE\s+ONLY\s+public\.{table}\s+ADD\s+CONSTRAINT\s+.+PRIMARY\s+KEY"
        if not re.search(constraint_pattern, baseline, re.I | re.S):
            blockers.append(f"{table} has no primary key")

if "uk_reading_entitlement_reservation_idempotency" not in baseline:
    blockers.append("reservation idempotency unique index is missing")
if "idx_reading_purchase_retention_purge" not in baseline:
    blockers.append("purchase retention purge index is missing")
if re.search(r"\bDROP\s+(TABLE|SEQUENCE)\b", baseline, re.I) or re.search(r"\bALTER\s+TABLE\b[^;]*\bDROP\s+COLUMN\b", baseline, re.I | re.S):
    blockers.append("unified baseline must not contain migration-style cleanup operations")
if re.search(r"sys_sync_|cloud_sync|sync_enabled|last_modified_by_installation_id|storage_mode|PowerSync|云同步", baseline, re.I):
    blockers.append("cloud sync residue found in unified baseline")

print("db first_version init audit")
print(f"  checked directory: {db_dir}")
if blockers:
    print("BLOCKERS:")
    for item in blockers:
        print(f"  - {item}")
    sys.exit(1)
print("BLOCKERS: none")
print("audit result: PASS")
PY
