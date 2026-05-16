#!/usr/bin/env bash
# 作用：调用统一后端的 release-gate 接口，判断当前环境是否允许发版。
# 约定：
#   - 默认请求本地 127.0.0.1:8080
#   - 可通过 BACKEND_BASE_URL / BACKEND_OPS_TOKEN 覆盖目标地址与鉴权 token
#   - ready => 退出码 0
#   - warning => 默认退出码 2；若 ALLOW_WARNINGS=true 则退出码 0
#   - blocked => 退出码 1
#   - 请求失败 / 返回异常 => 退出码 3 或 4
set -euo pipefail

# 后端基础地址，例如：https://backend.example.com
BASE_URL="${BACKEND_BASE_URL:-http://127.0.0.1:8080}"
# system 接口的运维鉴权 token；为空时仅适用于未启用 token 的环境
OPS_TOKEN="${BACKEND_OPS_TOKEN:-}"
# 是否允许 warning 状态直接通过发布门禁
ALLOW_WARNINGS="${ALLOW_WARNINGS:-false}"
# curl 连接超时，避免流水线因网络异常长期卡住
CURL_CONNECT_TIMEOUT="${CURL_CONNECT_TIMEOUT:-5}"
# curl 总超时，避免发布流程无限等待
CURL_MAX_TIME="${CURL_MAX_TIME:-30}"
# 统一的 release-gate 接口地址
ENDPOINT="$BASE_URL/api/v1/system/release-gate"

# 使用临时文件承接响应体，便于后续同时读取 HTTP 状态码与 JSON 内容
TMP_JSON="$(mktemp)"
trap 'rm -f "$TMP_JSON"' EXIT

# 兼容老版本 curl：通过 --output + --write-out 获取响应体与 HTTP 状态码
CURL_ARGS=(
  --silent
  --show-error
  --connect-timeout "$CURL_CONNECT_TIMEOUT"
  --max-time "$CURL_MAX_TIME"
  --output "$TMP_JSON"
  --write-out "%{http_code}"
  "$ENDPOINT"
)
if [[ -n "$OPS_TOKEN" ]]; then
  CURL_ARGS=(-H "X-Ops-Token: $OPS_TOKEN" "${CURL_ARGS[@]}")
fi

# 先处理网络层失败，例如 DNS、超时、连接拒绝等
if ! HTTP_STATUS="$(curl "${CURL_ARGS[@]}")"; then
  echo "release-gate request failed: unable to fetch $ENDPOINT" >&2
  exit 3
fi
# 非 2xx 统一视为请求失败，并尽量给出可操作的诊断信息
if [[ ! "$HTTP_STATUS" =~ ^2[0-9][0-9]$ ]]; then
  echo "release-gate request failed: http_status=$HTTP_STATUS endpoint=$ENDPOINT" >&2
  if [[ -s "$TMP_JSON" ]]; then
    if grep -qiE '<!DOCTYPE html|<html|SearXNG|Page not found' "$TMP_JSON"; then
      echo "hint: endpoint returned HTML instead of backend JSON. The default BACKEND_BASE_URL may be pointing at the wrong local service. Set BACKEND_BASE_URL explicitly, for example: BACKEND_BASE_URL=http://127.0.0.1:18082 ./scripts/release-gate.sh" >&2
    else
      cat "$TMP_JSON" >&2
      echo >&2
    fi
  else
    echo "hint: empty response body. Verify BACKEND_BASE_URL and backend availability." >&2
  fi
  exit 3
fi

# 解析 release-gate JSON 响应，并根据状态映射为稳定的退出码
python3 - "$TMP_JSON" "$ALLOW_WARNINGS" <<'PY'
import json
import sys
from pathlib import Path

json_path = Path(sys.argv[1])
allow_warnings = sys.argv[2].strip().lower() == "true"
try:
    body = json.loads(json_path.read_text())
except json.JSONDecodeError as exc:
    print(f"release-gate response was not valid JSON: {exc}", file=sys.stderr)
    sys.exit(4)
if not body.get("success"):
    print(f"release-gate request failed: {body.get('message')}", file=sys.stderr)
    sys.exit(3)

data = body.get("data") or {}
status = (data.get("status") or "unknown").lower()
environment = data.get("environment") or "unknown"
app_count = data.get("appCount", 0)
blocked = data.get("blockedAppCount", 0)
warning = data.get("warningAppCount", 0)
print(f"release-gate status={status} environment={environment} apps={app_count} blockedApps={blocked} warningApps={warning}")

for check in data.get("checks") or []:
    print(f"  system-check {check.get('key')} status={check.get('status')} current={check.get('currentValue')} expected={check.get('expectedValue')} note={check.get('note')}")
for app in data.get("apps") or []:
    print(f"  app {app.get('appCode')} status={app.get('status')} blockers={app.get('blockerCount')} warnings={app.get('warningCount')}")
for blocker in data.get("blockers") or []:
    print(f"  blocker: {blocker}")
for warn in data.get("warnings") or []:
    print(f"  warning: {warn}")

if status == "ready":
    sys.exit(0)
if status == "warning" and allow_warnings:
    sys.exit(0)
if status == "warning":
    sys.exit(2)
if status == "blocked":
    sys.exit(1)
sys.exit(4)
PY
