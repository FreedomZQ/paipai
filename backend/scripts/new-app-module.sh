#!/usr/bin/env bash
# 新 App 接入脚手架生成器。
#
# 用法：
#   scripts/new-app-module.sh <appCode> <internalDomain> <bundleId> [outputDir]
#
# 默认只生成到 generated/new-app-module/<appCode>/，不直接覆盖源码。
# 这样个人开发者可以先 review，再按需拷贝/改名，避免误改 Paipai 首发代码。
set -euo pipefail

if [[ $# -lt 3 || $# -gt 4 ]]; then
  cat >&2 <<'USAGE'
usage: scripts/new-app-module.sh <appCode> <internalDomain> <bundleId> [outputDir]

example:
  scripts/new-app-module.sh story_reader storyreader com.example.storyreader
USAGE
  exit 2
fi

APP_CODE="$1"
INTERNAL_DOMAIN="$2"
BUNDLE_ID="$3"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
APP_DIR="$(cd -- "$BACKEND_DIR/.." && pwd)"
OUT_DIR="${4:-$BACKEND_DIR/generated/new-app-module/$APP_CODE}"

if [[ ! "$APP_CODE" =~ ^[a-z][a-z0-9_]{2,63}$ ]]; then
  echo "invalid appCode: $APP_CODE (expected lowercase snake-like code)" >&2
  exit 2
fi
if [[ ! "$INTERNAL_DOMAIN" =~ ^[a-z][a-z0-9_]{2,63}$ ]]; then
  echo "invalid internalDomain: $INTERNAL_DOMAIN" >&2
  exit 2
fi
if [[ ! "$BUNDLE_ID" =~ ^[A-Za-z0-9][A-Za-z0-9.-]+$ ]]; then
  echo "invalid bundleId: $BUNDLE_ID" >&2
  exit 2
fi

CLASS_PREFIX="$(python3 - "$INTERNAL_DOMAIN" <<'PY'
import sys
print(''.join(part[:1].upper() + part[1:] for part in sys.argv[1].replace('-', '_').split('_') if part))
PY
)"
TABLE_PREFIX="${INTERNAL_DOMAIN}_"
PACKAGE_DOMAIN="${INTERNAL_DOMAIN//_/.}"

mkdir -p \
  "$OUT_DIR/backend/src/main/resources/apps/$INTERNAL_DOMAIN" \
  "$OUT_DIR/backend/src/main/java/com/apphub/backend/apps/$PACKAGE_DOMAIN" \
  "$OUT_DIR/ios/Core" \
  "$OUT_DIR/docs"

cat > "$OUT_DIR/backend/src/main/resources/apps/$INTERNAL_DOMAIN/app-definition.yml" <<YAML
# $APP_CODE 应用定义模板。
# 中文说明：新增 App 必须拥有独立 appCode / bundleId / Apple clientId / billing bundleId / entitlement mapping。
# 不要复制 Paipai 的正式凭证；生产值建议通过环境变量或部署配置覆盖。
app:
  code: $APP_CODE
  name: TODO-应用展示名
  apiPrefix: /api/v1
  tablePrefix: $TABLE_PREFIX
  support:
    legalRequired: "true"
    appleSignInRequired: "true"
    billingRequired: "true"
  release:
    requiredForCurrentWave: "false"
    minimumIosVersion: "18.0"
    minimumIpadosVersion: "18.0"
  auth:
    demoSessionEnabled: "false"
    apple:
      clientId: $BUNDLE_ID
      teamId: ""
      keyId: ""
      privateKey: ""
      audience: https://appleid.apple.com
      redirectUri: ""
      environment: production
      tokenEndpoint: https://appleid.apple.com/auth/token
      jwksUrl: https://appleid.apple.com/auth/keys
      revokeEndpoint: https://appleid.apple.com/auth/revoke
      remoteExchangeEnabled: "true"
  billing:
    appstore:
      bundleId: $BUNDLE_ID
      environment: production
      allowSandbox: "false"
      appAppleId: ""
      issuerId: ""
      keyId: ""
      privateKey: ""
    entitlements:
      # 必须显式配置，避免 productId fallback 造成跨 App 权益误判。
      productMappings: {}
      refreshPolicy:
        candidateLimit: "20"
        cooldownMinutes: "5"
YAML

cat > "$OUT_DIR/backend/src/main/java/com/apphub/backend/apps/$PACKAGE_DOMAIN/${CLASS_PREFIX}AppModule.java" <<JAVA
package com.apphub.backend.apps.${PACKAGE_DOMAIN};

import com.apphub.backend.apps.common.AppModule;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import org.springframework.stereotype.Component;

/**
 * ${APP_CODE} App 模块声明。
 *
 * 中文说明：
 * - appCode 是账号、权益、remote config、同步数据的隔离边界。
 * - internalDomain/tablePrefix 只是当前 App 的内部业务域和物理表前缀。
 * - 新 App 不允许复用 Paipai 的 appCode、bundleId、Apple clientId 或 entitlement mapping。
 */
@Component
public class ${CLASS_PREFIX}AppModule implements AppModule {
    public static final String APP_CODE = "${APP_CODE}";
    public static final String INTERNAL_DOMAIN = "${INTERNAL_DOMAIN}";
    public static final String TABLE_PREFIX = "${TABLE_PREFIX}";

    private final AppDefinitionService appDefinitionService;

    public ${CLASS_PREFIX}AppModule(AppDefinitionService appDefinitionService) {
        this.appDefinitionService = appDefinitionService;
    }

    @Override
    public String appCode() {
        return APP_CODE;
    }

    @Override
    public String appName() {
        return definition().name();
    }

    @Override
    public String internalDomain() {
        return INTERNAL_DOMAIN;
    }

    @Override
    public String tablePrefix() {
        return TABLE_PREFIX;
    }

    @Override
    public String apiPrefix() {
        return definition().apiPrefix();
    }

    @Override
    public AppDefinition definition() {
        return appDefinitionService.get(APP_CODE)
            .orElseThrow(() -> new IllegalStateException("Missing app definition for " + APP_CODE));
    }
}
JAVA

cat > "$OUT_DIR/ios/Core/AppIdentity.swift" <<SWIFT
import Foundation

/// ${APP_CODE} App identity 模板。
/// 保持 appCode / bundleId / local storage namespace 独立，避免未来多 App 共用后端时串账号、串权益、串缓存。
enum AppIdentity {
    static let appCode = "${APP_CODE}"
    static let bundleIdentifier = "${BUNDLE_ID}"
    static let apiBaseURL = URL(string: "https://api.example.com")!
    static let localStorageNamespace = "${APP_CODE}"
}

struct AppScopedDefaults {
    private let namespace: String
    private let defaults: UserDefaults

    init(namespace: String = AppIdentity.localStorageNamespace, defaults: UserDefaults = .standard) {
        self.namespace = namespace
        self.defaults = defaults
    }

    func key(_ raw: String) -> String { "\(namespace).\(raw)" }
    func string(forKey raw: String) -> String? { defaults.string(forKey: key(raw)) }
    func bool(forKey raw: String) -> Bool { defaults.bool(forKey: key(raw)) }
    func set(_ value: Any?, forKey raw: String) { defaults.set(value, forKey: key(raw)) }
    func removeObject(forKey raw: String) { defaults.removeObject(forKey: key(raw)) }
}
SWIFT

cat > "$OUT_DIR/docs/checklist.md" <<MD
# $APP_CODE 接入检查清单

- [ ] \`application.yml\` 的 \`backend.apps.supported\` 增加 \`$APP_CODE\`。
- [ ] \`backend.apps.definitions.$APP_CODE\` 指向新 app-definition。
- [ ] Apple clientId / teamId / keyId / privateKey / redirectUri 通过生产配置补齐。
- [ ] App Store bundleId / appAppleId / issuerId / keyId / privateKey 通过生产配置补齐。
- [ ] entitlement productMappings 显式配置，不依赖 productId fallback。
- [ ] release_ios namespace 为 \`$APP_CODE\` 单独配置。
- [ ] iOS Keychain、UserDefaults、PowerSync DB 均使用 \`$APP_CODE\` namespace。
- [ ] 运行 \`scripts/audit-multi-app-isolation.sh\`。
- [ ] 运行 App A / App B 隔离测试，确认 Apple subject、session、entitlement、PowerSync installation 不串线。
MD

cat > "$OUT_DIR/README.md" <<MD
# $APP_CODE generated module skeleton

生成时间：$(date -u +%Y-%m-%dT%H:%M:%SZ)

这个目录是模板输出，不会自动覆盖源码。请 review 后再按需合并。

核心边界：
- appCode: \`$APP_CODE\`
- internalDomain: \`$INTERNAL_DOMAIN\`
- tablePrefix: \`$TABLE_PREFIX\`
- bundleId/clientId: \`$BUNDLE_ID\`

低风险上线原则：先让 release gate 和静态审计通过，再接真实 Apple 登录、IAP、PowerSync 和 App Store metadata。
MD

echo "generated new app module skeleton: $OUT_DIR"
find "$OUT_DIR" -type f | sort
