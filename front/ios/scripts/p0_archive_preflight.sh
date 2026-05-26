#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_YML="${1:-$ROOT/project.release.yml}"
APP_DIR="$ROOT/PaipaiReadAlong"
STATIC_PREFLIGHT="$ROOT/scripts/p0_static_preflight.sh"

fail() {
  echo "[P0][FAIL] $*" >&2
  exit 1
}

[[ -f "$PROJECT_YML" ]] || fail "missing release project file: $PROJECT_YML"

python3 - <<'PY' "$PROJECT_YML"
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
text = path.read_text()

def value_for(key):
    match = re.search(rf"^\s*{re.escape(key)}:\s*['\"]?([^'\"#\n]+)['\"]?", text, re.MULTILINE)
    return match.group(1).strip() if match else None

def fail(message):
    raise SystemExit(f"[P0][FAIL] {message}")

checks = {
    "PRODUCT_BUNDLE_IDENTIFIER": (value_for("PRODUCT_BUNDLE_IDENTIFIER"), r"[A-Za-z0-9]+(\.[A-Za-z0-9][A-Za-z0-9_-]*)+"),
    "DEVELOPMENT_TEAM": (value_for("DEVELOPMENT_TEAM"), r"[A-Z0-9]{10}"),
    "MARKETING_VERSION": (value_for("MARKETING_VERSION"), r"\d+(\.\d+){1,2}"),
    "CURRENT_PROJECT_VERSION": (value_for("CURRENT_PROJECT_VERSION"), r"[1-9]\d*"),
}
for key, (value, pattern) in checks.items():
    if not value or "__FILL_FROM_DB_" in str(value):
        fail(f"{key} is missing or still a placeholder")
    if not re.fullmatch(pattern, str(value)):
        fail(f"{key} has invalid release value: {value}")
if value_for("PAIPAI_API_BASE_URL") is not None:
    fail("no-backend release project must not define PAIPAI_API_BASE_URL")
if value_for("IPHONEOS_DEPLOYMENT_TARGET") != "18.0":
    fail("IPHONEOS_DEPLOYMENT_TARGET must be 18.0")
if value_for("platform") != "iOS":
    fail("target platform must be iOS")
print("[P0][OK] release project values")
PY

if grep -RIn "__FILL_FROM_DB_\|http://127.0.0.1\|localhost\|PAIPAI_API_BASE_URL" "$PROJECT_YML"; then
  fail "release project contains placeholders, local URLs, or backend URL keys"
fi

"$STATIC_PREFLIGHT"

echo "[P0][OK] no-backend archive preflight passed"
