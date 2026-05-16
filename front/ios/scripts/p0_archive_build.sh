#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEME="${PAIPAI_XCODE_SCHEME:-PaipaiReadAlong}"
RELEASE_PROJECT="${PAIPAI_RELEASE_PROJECT_YML:-$ROOT/project.release.yml}"
ARCHIVE_PATH="${PAIPAI_ARCHIVE_PATH:-$ROOT/build/PaipaiReadAlong.xcarchive}"

fail() {
  echo "[P0][FAIL] $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing command: $1"
}

cd "$ROOT"

require_cmd python3
require_cmd xcodegen
require_cmd xcodebuild

./scripts/p0_static_preflight.sh
./scripts/render_release_project_yml.py --source project.yml --output "$RELEASE_PROJECT"
./scripts/p0_archive_preflight.sh "$RELEASE_PROJECT"

xcodegen generate --spec "$RELEASE_PROJECT"

mkdir -p "$(dirname "$ARCHIVE_PATH")"
xcodebuild \
  -scheme "$SCHEME" \
  -destination 'generic/platform=iOS' \
  -archivePath "$ARCHIVE_PATH" \
  archive

echo "[P0][OK] archive produced: $ARCHIVE_PATH"
