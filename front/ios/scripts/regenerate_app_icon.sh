#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ICONSET="$ROOT/PaipaiReadAlong/Resources/Assets.xcassets/AppIcon.appiconset"
SOURCE="${1:-$ICONSET/AppIcon-1024.png}"

fail() {
  echo "[APPICON][FAIL] $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing command: $1"
}

[[ -f "$SOURCE" ]] || fail "missing source image: $SOURCE"
require_cmd sips

mkdir -p "$ICONSET"

generate() {
  local src="$1"
  local out="$2"
  local width="$3"
  local height="$4"
  sips -z "$height" "$width" "$src" --out "$out" >/dev/null
}

generate "$SOURCE" "$ICONSET/AppIcon-1024.png" 1024 1024
generate "$SOURCE" "$ICONSET/AppIcon-20@2x.png" 40 40
generate "$SOURCE" "$ICONSET/AppIcon-20@3x.png" 60 60
generate "$SOURCE" "$ICONSET/AppIcon-29@2x.png" 58 58
generate "$SOURCE" "$ICONSET/AppIcon-29@3x.png" 87 87
generate "$SOURCE" "$ICONSET/AppIcon-40@2x.png" 80 80
generate "$SOURCE" "$ICONSET/AppIcon-40@3x.png" 120 120
generate "$SOURCE" "$ICONSET/AppIcon-60@2x.png" 120 120
generate "$SOURCE" "$ICONSET/AppIcon-60@3x.png" 180 180
generate "$SOURCE" "$ICONSET/AppIcon-iPad-20@1x.png" 20 20
generate "$SOURCE" "$ICONSET/AppIcon-iPad-20@2x.png" 40 40
generate "$SOURCE" "$ICONSET/AppIcon-iPad-29@1x.png" 29 29
generate "$SOURCE" "$ICONSET/AppIcon-iPad-29@2x.png" 58 58
generate "$SOURCE" "$ICONSET/AppIcon-iPad-40@1x.png" 40 40
generate "$SOURCE" "$ICONSET/AppIcon-iPad-40@2x.png" 80 80
generate "$SOURCE" "$ICONSET/AppIcon-iPad-76@1x.png" 76 76
generate "$SOURCE" "$ICONSET/AppIcon-iPad-76@2x.png" 152 152
generate "$SOURCE" "$ICONSET/AppIcon-iPad-83.5@2x.png" 167 167

echo "[APPICON][OK] regenerated app icon set from: $SOURCE"
