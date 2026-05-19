#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_YML="$ROOT/project.yml"
APP_DIR="$ROOT/PaipaiReadAlong"
LEGAL_DIR="$APP_DIR/Resources/legal"

fail() {
  echo "[P0][FAIL] $*" >&2
  exit 1
}

warn() {
  echo "[P0][WARN] $*" >&2
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
bundle_id = value_for('PRODUCT_BUNDLE_IDENTIFIER') or ''
if not bundle_id.startswith('__FILL_FROM_DB_release_ios.bundle_identifier__'):
    fail('bundle id must be injected from release_ios config before archive')
print('[P0][OK] project.yml release scope and placeholders')
PY

if grep -RIn "appTransactionID\|CaptureView.OCRConfirmView\|platform: \[iOS, macOS\]\|MACOSX_DEPLOYMENT_TARGET" "$APP_DIR" "$PROJECT_YML"; then
  fail "known compile-risk tokens found"
fi

if grep -R "PowerSync\\|powersync\\|云同步\\|Cloud Sync" "$APP_DIR" >/dev/null 2>&1; then
  fail "Cloud sync implementation or UI text must not be present"
fi

ACCOUNT_MODELS="$APP_DIR/Core/Models/AccountModels.swift"
APP_STATE="$APP_DIR/App/PaipaiReadAlongApp.swift"
require_file "$ACCOUNT_MODELS"
grep -q 'serverVerified' "$ACCOUNT_MODELS" || fail "AccountEntitlement must decode backend serverVerified"
grep -q 'verificationSource == "backend_sys_billing"' "$ACCOUNT_MODELS" || fail "paid feature gates must require backend billing source"
grep -q 'backendVerifiedPremiumActive' "$APP_STATE" || fail "AppState must use backend-verified premium gate"
if grep -RIn "entitlement\.premiumActive" "$APP_DIR/Features" "$APP_STATE"; then
  fail "premium feature gates must not use raw premiumActive without backend verification"
fi

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
  grep -qi '<html' "$f" || warn "legal document may not be full HTML: $f"
done

echo "[P0][OK] static preflight passed"
