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

require_file() {
  [[ -f "$1" ]] || fail "missing file: $1"
}

require_file "$PROJECT_YML"
require_file "$APP_DIR/Resources/Info.plist"
require_file "$APP_DIR/Resources/PrivacyInfo.xcprivacy"
require_file "$APP_DIR/Core/Services/LocalCreditWalletService.swift"
require_file "$APP_DIR/Core/Services/AppStorePurchaseService.swift"
require_file "$APP_DIR/Core/Utilities/AppIdentity.swift"
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

if value_for("IPHONEOS_DEPLOYMENT_TARGET") != "18.0":
    fail("IPHONEOS_DEPLOYMENT_TARGET must be 18.0 for first release")
if value_for("MACOSX_DEPLOYMENT_TARGET") is not None:
    fail("macOS deployment target must stay out of iOS-first release config")
if value_for("platform") != "iOS":
    fail("XcodeGen target platform must be iOS for first release")
if value_for("PAIPAI_API_BASE_URL") is not None:
    fail("no-backend launch build must not define PAIPAI_API_BASE_URL")
print("[P0][OK] project.yml no-backend release scope")
PY

python3 - <<'PY' "$APP_DIR/Resources/PrivacyInfo.xcprivacy" "$APP_DIR/Resources/Info.plist"
from pathlib import Path
import plistlib
import sys

privacy_path = Path(sys.argv[1])
info_path = Path(sys.argv[2])
with privacy_path.open("rb") as fh:
    privacy = plistlib.load(fh)
with info_path.open("rb") as fh:
    info = plistlib.load(fh)

def fail(message):
    raise SystemExit(f"[P0][FAIL] {message}")

for key in [
    "NSCameraUsageDescription",
    "NSPhotoLibraryUsageDescription",
    "NSFaceIDUsageDescription",
]:
    value = info.get(key)
    if not isinstance(value, str) or not value.strip():
        fail(f"Info.plist missing non-empty {key}")
if "PAIPAI_API_BASE_URL" in info:
    fail("Info.plist must not contain PAIPAI_API_BASE_URL in no-backend launch mode")
ats = info.get("NSAppTransportSecurity")
if not isinstance(ats, dict) or ats.get("NSAllowsArbitraryLoads") is not False:
    fail("Info.plist must keep NSAllowsArbitraryLoads=false")
if privacy.get("NSPrivacyTracking") is not False:
    fail("PrivacyInfo.xcprivacy must explicitly set NSPrivacyTracking=false")
if privacy.get("NSPrivacyCollectedDataTypes") != []:
    fail("no-backend launch PrivacyInfo.xcprivacy must not declare collected data types")
api_types = privacy.get("NSPrivacyAccessedAPITypes")
if not isinstance(api_types, list) or not api_types:
    fail("PrivacyInfo.xcprivacy missing required reason API entries")
api_reason_map = {
    entry.get("NSPrivacyAccessedAPIType"): set(entry.get("NSPrivacyAccessedAPITypeReasons") or [])
    for entry in api_types
    if isinstance(entry, dict)
}
if "CA92.1" not in api_reason_map.get("NSPrivacyAccessedAPICategoryUserDefaults", set()):
    fail("PrivacyInfo.xcprivacy must declare UserDefaults reason CA92.1")
if "35F9.1" not in api_reason_map.get("NSPrivacyAccessedAPICategorySystemBootTime", set()):
    fail("PrivacyInfo.xcprivacy must declare SystemBootTime reason 35F9.1 for ProcessInfo.systemUptime")
print("[P0][OK] Info.plist and PrivacyInfo.xcprivacy")
PY

grep -q "static let developerBackendEnabled = false" "$APP_DIR/Core/Utilities/AppIdentity.swift" \
  || fail "AppIdentity.developerBackendEnabled must be false for no-backend launch"
grep -q "kidsCategoryEnabled: true" "$APP_DIR/Core/Models/AppBootstrap.swift" \
  || fail "local AppBootstrap placeholder must keep Kids Category mode enabled"
grep -q "actor LocalCreditWalletService" "$APP_DIR/Core/Services/LocalCreditWalletService.swift" \
  || fail "LocalCreditWalletService must be an actor"
grep -q "kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly" "$APP_DIR/Core/Services/LocalCreditWalletService.swift" \
  || fail "local wallet Keychain accessibility must be ThisDeviceOnly"
grep -q "AES.GCM" "$APP_DIR/Core/Services/LocalCreditWalletService.swift" \
  || fail "local wallet must use authenticated encryption"
grep -q "Transaction.unfinished" "$APP_DIR/Core/Services/AppStorePurchaseService.swift" \
  || fail "StoreKit unfinished transactions must be processed"
grep -q "AppStore.sync" "$APP_DIR/Core/Services/AppStorePurchaseService.swift" \
  || fail "restore/refresh must use AppStore.sync"
grep -q "StoreKit.Transaction.updates" "$APP_DIR/App/PaipaiReadAlongApp.swift" \
  || fail "StoreKit transaction updates listener is missing"

if grep -RIn "SKIncludeConsumableInAppPurchaseHistory\|restoreCompletedTransactions\|Transaction\\.all" "$APP_DIR" "$PROJECT_YML"; then
  fail "forbidden consumable restore mechanism found"
fi

if grep -RIn "Firebase\|AppsFlyer\|Adjust\|UMeng\|友盟\|ATTrackingManager\|NSUserTrackingUsageDescription\|GADMobileAds\|AdSupport" "$APP_DIR" "$PROJECT_YML"; then
  fail "third-party analytics/ads/tracking token found"
fi

if grep -RIn "ALIYUN_ACCESS_KEY\|DASHSCOPE_API_KEY\|OPENAI_API_KEY\|sk-[A-Za-z0-9]" "$APP_DIR" "$PROJECT_YML"; then
  fail "cloud API key token found in iOS app"
fi

if grep -RIn "family_multi_child_lifetime\|com\\.paipai\\.readalong\\.family\\.multi_child\\.lifetime\|云端 API 积分\|换设备也能找回\|恢复所有历史积分\|高级版可解锁\|查看高级版权益\|Premium unlocks\|View premium benefits" "$APP_DIR"; then
  fail "old premium/cloud/cross-device restore wording found"
fi

grep -RIn "<<<<<<<\|=======\|>>>>>>>" "$APP_DIR" "$PROJECT_YML" && fail "merge conflict marker found"

for f in "$LEGAL_DIR"/*.html; do
  [[ -s "$f" ]] || fail "empty legal document: $f"
  grep -qi "<html" "$f" || fail "legal document is not full HTML: $f"
done

echo "[P0][OK] no-backend static preflight passed"
