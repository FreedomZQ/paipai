#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_YML="${1:-$ROOT/project.release.yml}"
APP_DIR="$ROOT/PaipaiReadAlong"
LEGAL_DIR="$APP_DIR/Resources/legal"

fail() {
  echo "[P0][FAIL] $*" >&2
  exit 1
}

require_file() {
  [[ -f "$1" ]] || fail "missing file: $1"
}

require_file "$PROJECT_YML"
require_file "$APP_DIR/Resources/Info.plist"
require_file "$APP_DIR/Resources/PrivacyInfo.xcprivacy"
require_file "$LEGAL_DIR/privacy-policy.html"
require_file "$LEGAL_DIR/terms-of-service.html"
require_file "$LEGAL_DIR/child-data.html"

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

if value_for('IPHONEOS_DEPLOYMENT_TARGET') != '18.0':
    fail('IPHONEOS_DEPLOYMENT_TARGET must be 18.0 for first release')
if value_for('MACOSX_DEPLOYMENT_TARGET') is not None:
    fail('macOS deployment target must stay out of iOS-first release config')
if value_for('platform') != 'iOS':
    fail('XcodeGen target platform must be iOS for first release')
checks = {
    'PRODUCT_BUNDLE_IDENTIFIER': (value_for('PRODUCT_BUNDLE_IDENTIFIER'), r'[A-Za-z0-9]+(\.[A-Za-z0-9][A-Za-z0-9_-]*)+'),
    'DEVELOPMENT_TEAM': (value_for('DEVELOPMENT_TEAM'), r'[A-Z0-9]{10}'),
    'MARKETING_VERSION': (value_for('MARKETING_VERSION'), r'\d+(\.\d+){1,2}'),
    'CURRENT_PROJECT_VERSION': (value_for('CURRENT_PROJECT_VERSION'), r'[1-9]\d*'),
}
for key, (value, pattern) in checks.items():
    if not value or '__FILL_FROM_DB_' in str(value):
        fail(f'{key} is missing or still a placeholder')
    if not re.fullmatch(pattern, str(value)):
        fail(f'{key} has invalid release value: {value}')
api = value_for('PAIPAI_API_BASE_URL') or ''
if not api.startswith('https://') or '127.0.0.1' in api or 'localhost' in api:
    fail(f'PAIPAI_API_BASE_URL must be a non-local HTTPS URL, got: {api}')
print('[P0][OK] release project.yml values')
PY

if grep -RIn "__FILL_FROM_DB_\|http://127.0.0.1\|localhost" "$PROJECT_YML"; then
  fail "release project contains placeholders or local URLs"
fi

if grep -RIn "appTransactionID\|CaptureView.OCRConfirmView\|platform: \[iOS, macOS\]\|MACOSX_DEPLOYMENT_TARGET" "$APP_DIR" "$PROJECT_YML"; then
  fail "known compile-risk tokens found"
fi

BOOTSTRAP_API="$APP_DIR/Core/Sync/PowerSyncBootstrapAPI.swift"
grep -q 'deviceId: nil' "$BOOTSTRAP_API" || fail "PowerSync bootstrap must not upload deviceId"
grep -q 'clientPlatform: hasConsent ?' "$BOOTSTRAP_API" || fail "PowerSync bootstrap clientPlatform must be consent-gated"
grep -q 'deviceModel: hasConsent ? device.model : nil' "$BOOTSTRAP_API" || fail "PowerSync bootstrap deviceModel must be consent-gated"
grep -q 'appVersion: hasConsent ? device.appVersion : nil' "$BOOTSTRAP_API" || fail "PowerSync bootstrap appVersion must be consent-gated"

grep -RIn "<<<<<<<\|=======\|>>>>>>>" "$APP_DIR" "$PROJECT_YML" && fail "merge conflict marker found"

python3 - <<'PY' "$APP_DIR/Resources/PrivacyInfo.xcprivacy" "$APP_DIR/Resources/Info.plist"
from pathlib import Path
import plistlib
import sys

privacy_path = Path(sys.argv[1])
info_path = Path(sys.argv[2])
with privacy_path.open('rb') as fh:
    privacy = plistlib.load(fh)
with info_path.open('rb') as fh:
    info = plistlib.load(fh)

def fail(message):
    raise SystemExit(f"[P0][FAIL] {message}")

required_info_strings = [
    'NSCameraUsageDescription',
    'NSPhotoLibraryUsageDescription',
    'NSFaceIDUsageDescription',
    'PAIPAI_API_BASE_URL',
]
for key in required_info_strings:
    value = info.get(key)
    if not isinstance(value, str) or not value.strip():
        fail(f'Info.plist missing non-empty {key}')
ats = info.get('NSAppTransportSecurity')
if not isinstance(ats, dict) or ats.get('NSAllowsArbitraryLoads') is not False:
    fail('Info.plist must keep NSAllowsArbitraryLoads=false for release safety')
if privacy.get('NSPrivacyTracking') is not False:
    fail('PrivacyInfo.xcprivacy must explicitly set NSPrivacyTracking=false')
if not isinstance(privacy.get('NSPrivacyCollectedDataTypes'), list):
    fail('PrivacyInfo.xcprivacy missing NSPrivacyCollectedDataTypes array')
if not isinstance(privacy.get('NSPrivacyAccessedAPITypes'), list):
    fail('PrivacyInfo.xcprivacy missing NSPrivacyAccessedAPITypes array')
print('[P0][OK] Info.plist and PrivacyInfo.xcprivacy release keys')
PY

for f in "$LEGAL_DIR"/*.html; do
  [[ -s "$f" ]] || fail "empty legal document: $f"
  grep -qi '<html' "$f" || fail "legal document is not full HTML: $f"
done

echo "[P0][OK] archive preflight passed"
