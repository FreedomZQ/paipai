#!/usr/bin/env bash
# Audit area-owned initialization SQL files.
#
# 中文说明：历史脚本名里保留了 “four-table”，实际检查的是当前 first_version
# 初始化目录中按领域维护的 SQL 文件，避免新增合规表只写迁移、不进入初始化验收链路。
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
DB_DIR="$BACKEND_DIR/src/main/resources/db/first_version"

required_files=(
  "$DB_DIR/V45__reading_privacy_account_deletion_and_purchase_retention.sql"
  "$DB_DIR/V46__reading_entitlement_token_reservation_snapshot.sql"
  "$DB_DIR/app_paipai_record.sql"
  "$DB_DIR/sys_common_record.sql"
)

missing=0
for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "BLOCKER: missing init SQL file: $file"
    missing=1
  fi
done
[[ "$missing" -eq 0 ]] || exit 1

python3 - "$DB_DIR" <<'PY'
from pathlib import Path
import re
import sys

db_dir = Path(sys.argv[1])
v45 = (db_dir / "V45__reading_privacy_account_deletion_and_purchase_retention.sql").read_text(encoding="utf-8")
v46 = (db_dir / "V46__reading_entitlement_token_reservation_snapshot.sql").read_text(encoding="utf-8")
aggregate = (db_dir / "app_paipai_record.sql").read_text(encoding="utf-8") + "\n" + (db_dir / "sys_common_record.sql").read_text(encoding="utf-8")

required_tables = [
    "reading_privacy_request",
    "reading_privacy_purchase_retention",
    "reading_account_deletion_job",
    "reading_deleted_user_tombstone",
    "reading_entitlement_token",
    "reading_entitlement_wallet",
    "reading_entitlement_reservation",
    "reading_entitlement_ledger",
    "reading_entitlement_snapshot",
]

blockers = []
combined = v45 + "\n" + v46
for table in required_tables:
    if not re.search(rf"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+public\.{table}\b", combined, re.I):
        blockers.append(f"missing CREATE TABLE IF NOT EXISTS for {table}")
    table_match = re.search(rf"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+public\.{table}\s*\((.*?)\);", combined, re.I | re.S)
    if table_match and "PRIMARY KEY" not in table_match.group(1).upper():
        blockers.append(f"{table} has no inline primary key")

for table in ["reading_daily_quota_config", "reading_weekly_report_snapshot", "sys_compensation_code", "sys_user_compensation_record"]:
    if table not in aggregate:
        blockers.append(f"aggregate init SQL is missing existing core table {table}")

if "uk_reading_entitlement_reservation_idempotency" not in v46:
    blockers.append("reservation idempotency unique index is missing")
if "idx_reading_purchase_retention_purge" not in v45:
    blockers.append("purchase retention purge index is missing")

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
