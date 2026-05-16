#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
SRC_DIR="${1:-$BACKEND_DIR/src/main/resources/db/first_version_four_table}"
MIGRATION_DIR="$BACKEND_DIR/src/main/resources/db/migration"

python3 - "$SRC_DIR" "$MIGRATION_DIR" <<'PY'
from pathlib import Path
import re
import sys

src = Path(sys.argv[1])
migration_dir = Path(sys.argv[2])
files = {
    'sys_common_record.sql': ('common', ('sys_',)),
    'app_paipai_record.sql': ('paipai', ('reading_',)),
    'app_saving_record.sql': ('saving', ('saving_',)),
    'app_fitmystery_record.sql': ('fitmystery', ('fit_',)),
}

def create_tables(text):
    return [t.strip('"') for t in re.findall(r'CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+(?:public\.)?([A-Za-z0-9_\"]+)', text, flags=re.I)]

source_tables = []
for p in sorted(migration_dir.glob('V*.sql')):
    source_tables.extend(create_tables(p.read_text(encoding='utf-8', errors='ignore')))

split_tables = []
blockers = []
print('db four init files audit')
print(f'  split dir: {src}')
print(f'  migration dir: {migration_dir}')
for filename, (area, prefixes) in files.items():
    path = src / filename
    if not path.exists() or path.stat().st_size == 0:
        blockers.append(f'missing or empty file: {filename}')
        continue
    text = path.read_text(encoding='utf-8', errors='ignore')
    tables = create_tables(text)
    split_tables.extend(tables)
    print(f'  {filename}: {len(tables)} CREATE TABLE statements')
    bad = [t for t in tables if not t.startswith(prefixes)]
    if bad:
        blockers.append(f'{filename} contains tables outside {prefixes}: {bad}')

if sorted(source_tables) != sorted(split_tables):
    missing = sorted(set(source_tables) - set(split_tables))
    extra = sorted(set(split_tables) - set(source_tables))
    blockers.append(f'CREATE TABLE set mismatch: missing={missing}, extra={extra}, source_count={len(source_tables)}, split_count={len(split_tables)}')

if len(split_tables) != len(set(split_tables)):
    dupes = sorted(t for t in set(split_tables) if split_tables.count(t) > 1)
    blockers.append(f'duplicate CREATE TABLE names in split files: {dupes}')

unexpected = sorted(p.name for p in src.glob('*.sql') if p.name not in set(files) | {'V1__init.sql'})
if unexpected:
    blockers.append(f'unexpected sql files: {unexpected}')

if blockers:
    print('BLOCKERS:')
    for b in blockers:
        print(f'  - {b}')
    raise SystemExit(1)
print(f'  source CREATE TABLE count: {len(source_tables)}')
print(f'  split CREATE TABLE count: {len(split_tables)}')
print('BLOCKERS: none')
print('audit result: PASS')
PY
