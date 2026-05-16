# Paipai V2 / Unified Backend 多 App 模板化落地改造清单（2026-04-22）

## 1. 背景与目标

用户已确认：

- Paipai V2 是第一个 App，不需要兼容旧内容。
- 统一后端后续还会接入其他 App。
- 不同 App 的 AppleID 登录、账户、权益、同步数据必须互相隔离。
- 之前审计中的“问题 5：内部仍保留 `reading` 业务命名”不能仅作为可延期整理项，而应升级为：

> **必须改造成完全模板化的多 App 脚手架，避免未来第二个 App 接入时继续靠人工复制和判断，埋下错接 appCode、错用 bundleId、错共享 entitlement 的坑。**

本文是后续工程改造的执行清单。

---

## 2. 总体改造目标

### 2.1 最终目标
把当前：

```text
统一 backend + reading 专属实现 + Paipai 首个 App 静态收口
```

升级为：

```text
统一 backend 基础设施 + 标准 AppModule 模板 + 每个 App 独立配置/路由/权益/同步适配器
```

### 2.2 多 App 模板化完成后的效果
新增一个 App 时，应能按固定流程完成：

1. 新增 app definition。
2. 新增 AppModule / AppAdapter。
3. 新增 entitlement mapping。
4. 新增 release gate profile。
5. 新增 iOS AppIdentity / local storage namespace。
6. 跑自动审计，确认没有接入到旧 App。
7. 跑 sandbox / TestFlight 实测。

而不是继续人工在代码里搜索：
- `reading`
- `paipai_readingcompanion`
- `com.paipai.readalong`
- `/api/v1/auth/...`

---

## 3. 改造总原则

### 3.1 必须隔离的 app identity
每个 App 必须独立拥有：

| 类型 | 字段 |
|---|---|
| 后端 app 身份 | `appCode` |
| iOS 身份 | `PRODUCT_BUNDLE_IDENTIFIER` |
| Apple 登录 | `auth.apple.clientId` |
| App Store 归属 | `billing.appstore.bundleId` / `appAppleId` |
| 权益配置 | productId → entitlementCode / planCode mapping |
| remote config | appCode + namespace |
| release gate | appCode-scoped gate profile |
| sync | appCode + userId + installationId |
| 本地缓存 | appCode/bundleId-scoped keys |

### 3.2 禁止项
后续新增 App 时禁止：

- 复用其他 App 的 `appCode`。
- 复用其他 App 的 `bundleId`。
- 复用其他 App 的 Apple `clientId`。
- 让 iOS 新 App 继续调用 Paipai 专属 compat 路由。
- 让 entitlement mapping 只靠 productId fallback 长期运行。
- 让本地 Keychain / UserDefaults key 不带 app 作用域。
- 让 release gate 不检查 iOS bundle 与 backend Apple/billing 配置一致性。

---

## 4. 目标架构建议

### 4.1 backend 分层

```text
com.apphub.backend
├── sys/                         # 统一系统能力，跨 App 共享
│   ├── auth/
│   ├── billing/
│   ├── appstore/
│   ├── powersync/
│   ├── remoteconfig/
│   └── app/
├── apps/
│   ├── common/                  # 多 App 模板与公共接口
│   │   ├── AppModule
│   │   ├── AppModuleRegistry
│   │   ├── AppCompatFacade
│   │   ├── AppReleaseProfile
│   │   ├── AppEntitlementProfile
│   │   └── AppPowerSyncAdapter
│   ├── reading/                 # Paipai/reading 业务实现，后续逐步模板化
│   ├── saving/                  # saving 业务实现
│   └── <future-app>/            # 未来 App 模块
```

### 4.2 iOS 分层

```text
Core/
├── AppIdentity.swift            # 每个 App 唯一身份，生成/配置注入
├── BackendRouting.swift         # appCode-aware route builder
├── AppScopedStorage.swift       # UserDefaults / Keychain / DB namespace
├── Services/
└── Sync/
```

### 4.3 推荐路由策略

#### 新 App 默认使用 system app-aware 路由
推荐：

```text
/api/v1/system/auth/apps/{appCode}/apple/exchange
/api/v1/system/auth/apps/{appCode}/apple/refresh
/api/v1/system/auth/apps/{appCode}/apple/revoke
/api/v1/powersync/{appCode}/bootstrap
/api/v1/powersync/{appCode}/token
/api/v1/powersync/{appCode}/upload
```

#### compat 路由只作为当前 App 的兼容 facade
例如 Paipai 可暂时保留：

```text
/api/v1/system/auth/apps/{appCode}/apple/exchange
/api/v1/system/auth/apps/{appCode}/me
```

但必须明确：
- compat controller 是 app-specific facade。
- 不允许未来 App 直接复用 Paipai compat path。
- 新 App 如需要兼容层，必须生成自己的 compat facade。

---

## 5. P0：新增 App 前必须完成的多 App 安全改造

### P0-1. 引入 backend AppModule 抽象

#### 目标
把当前散落在 controller/service 里的 app-specific 常量收口到统一模块描述。

#### 建议新增接口

```java
public interface AppModule {
    String appCode();
    String appName();
    String tablePrefix();
    String apiPrefix();
    AppDefinition definition();
    AppEntitlementProfile entitlementProfile();
    AppReleaseProfile releaseProfile();
    PowerSyncAppAdapter powerSyncAdapter();
}
```

#### 需要处理的当前问题
- `ReadingAuthCompatController` 写死 `APP_CODE = "paipai_readingcompanion"`
- `ReadingAuthenticatedUserResolver` 写死 `APP_CODE`
- `ReadingBillingCompatController` 写死 appCode
- `ReadingPowerSyncAdapter.appCode()` 写死 appCode
- `ReadingCompatService` 内部仍有 app-specific 配置前缀

#### 验收标准
- 所有 app-specific 常量能追溯到一个 AppModule / AppDefinition。
- 新增 App 不需要复制修改多个 scattered `private static final String APP_CODE`。

---

### P0-2. 建立 AppModuleRegistry

#### 目标
统一管理所有 App 模块，避免 controller 各自手写 appCode 判断。

#### 建议形态

```java
@Component
public class AppModuleRegistry {
    AppModule require(String appCode);
    Optional<AppModule> get(String appCode);
    List<AppModule> activeModules();
}
```

#### 验收标准
- PowerSync、billing、release gate、compat facade 都能通过 registry 找到对应 AppModule。
- 未注册 appCode 必须返回明确错误，不能 fallback 到 Paipai。

---

### P0-3. 新 App 默认走 appCode-aware system 路由

#### 目标
防止新 App 误打 Paipai compat 路由。

#### iOS 改造
新增统一 route builder：

```swift
enum BackendRoute {
    static func appleExchange(appCode: String) -> String {
        "/api/v1/system/auth/apps/\(appCode)/apple/exchange"
    }
}
```

或通过 `AppIdentity` 注入：

```swift
BackendClient(appIdentity: AppIdentity.current)
```

#### backend 改造
- `SysAuthController` 作为新 App 标准入口继续强化。
- `ReadingAuthCompatController` 明确标注为 Paipai-only facade。

#### 验收标准
- 新 App 模板里不出现 `/api/v1/system/auth/apps/{appCode}/apple/exchange` 这种不带 appCode 的默认入口。
- 旧 compat 路由必须在文档和代码注释中标明 app-specific。

---

### P0-4. iOS 本地存储全面 app-scoped

#### 目标
所有本地持久化 key 都必须带 appCode 或 bundleId 作用域。

#### 当前已部分完成
- PowerSync database filename 已基于 `PaipaiAppIdentity.appCode`
- PowerSync credential/settings/installation/rejection store 已基于 appCode namespace

#### 待改 key
当前至少包括：
- `hasCompletedOnboarding`
- `hasAcceptedPrivacyPolicy`
- `privacyConsentDate`
- `hasAcceptedDeviceInfoCollection`
- `appUsageRecords`
- `paipai.interfaceLocale`
- `paipai.announcements.cache`
- `paipai.announcements.dismissed`
- `paipai.announcements.presentation-state`

#### 建议新增

```swift
struct AppScopedDefaults {
    let appCode: String

    func key(_ raw: String) -> String {
        "\(appCode).\(raw)"
    }
}
```

#### 验收标准
- grep `UserDefaults.standard.*forKey:` 不应再出现裸业务 key。
- 任何新增 key 必须通过 `AppScopedDefaults`。

---

### P0-5. iOS Keychain session store app-scoped

#### 目标
避免未来 Keychain Sharing / App Group / 公共 SDK 场景下 session key 冲突。

#### 当前状态
`SecureSessionStore.swift`：

```swift
service = "com.paipai.readalong.auth"
account = "current-bearer-session"
```

#### 建议改成

```swift
service = "\(bundleId).\(appCode).auth"
account = "\(appCode).current-bearer-session"
```

或：

```swift
SecureSessionStore(appIdentity: AppIdentity)
```

#### 验收标准
- session key 不能只靠固定字符串。
- 新 App 模板必须自动生成独立 Keychain key。

---

### P0-6. Release gate 必须多 App 模板化

#### 目标
release gate 不再只检查 Paipai，而是能按 AppModule 自动检查每个 App。

#### 每个 App 必须检查
- appCode 是否在 supported app 列表。
- appDefinition 是否存在。
- iOS bundleId 是否与 `auth.apple.clientId` 一致。
- iOS bundleId 是否与 `billing.appstore.bundleId` 一致。
- release_ios required keys 是否完整。
- production 下 `allowSandbox=false`。
- Apple Sign in 配置是否完整。
- App Store Server API 配置是否完整。
- entitlement product mappings 是否显式配置。

#### 验收标准
- release gate 可以输出 per-app readiness。
- 不允许 “Paipai ready” 被误认为 “所有 App ready”。
- 不允许 backend appDefinition ready 但 iOS bundle 仍不一致。

---

### P0-7. Entitlement mapping 显式化

#### 当前问题
`SysBillingService.resolveEntitlementCode(...)` 仍存在 productId fallback。

#### 目标
每个 App 必须显式配置：

```yaml
app:
  billing:
    entitlements:
      productMappings:
        com.xxx.monthly: family_monthly
        com.xxx.yearly: family_yearly
```

#### 验收标准
- production release gate 中，缺 product mapping 应至少 warning，最好 blocker。
- 权益不能长期靠 productId 字符串 fallback 作为 entitlementCode。

---

### P0-8. PowerSync adapter 模板化

#### 目标
保留 `PowerSyncAppAdapter` 思路，但让新 App 接入有标准模板。

#### 当前状态
- `ReadingPowerSyncAdapter` 已能按 `paipai_readingcompanion` 工作。
- 但结构仍是 reading 专属实现。

#### 建议
新增模板说明：

```java
public interface AppPowerSyncAdapter extends PowerSyncAppAdapter {
    AppModule appModule();
    List<SyncEntitySpec> entities();
}
```

每个 App 必须声明：
- entity type
- ownership field
- create/update/delete policy
- entitlement gate
- conflict/version policy

#### 验收标准
- 新 App 不需要反复复制 reading adapter 大段逻辑。
- 每类同步实体必须显式声明 ownership 校验。

---

## 6. P1：把 reading 专属实现升级为模板脚手架

### P1-1. 抽出 app common 包

#### 新增目录建议

```text
src/main/java/com/apphub/backend/apps/common/
├── AppModule.java
├── AppModuleRegistry.java
├── AppScopedAuthenticatedUserResolver.java
├── AppCompatFacadeSupport.java
├── AppReleaseGateSupport.java
├── AppEntitlementProfile.java
└── AppStorageNamingPolicy.java
```

#### 目标
把 currently reading-only 的做法抽为可复用模板。

---

### P1-2. Reading module 显式实现 AppModule

#### 建议新增

```java
@Component
public class ReadingAppModule implements AppModule {
    public String appCode() { return "paipai_readingcompanion"; }
    public String internalDomain() { return "reading"; }
    public String tablePrefix() { return "reading_"; }
}
```

#### 注意
这里可以保留 `internalDomain = reading`，但必须和 `appCode = paipai_readingcompanion` 分开。

#### 验收标准
- 代码中出现 `reading` 时能明确知道是 internal domain，不是 appCode。
- `paipai_readingcompanion` 只从 AppModule / AppDefinition 派生。

---

### P1-3. Compat controller 改成 facade 模板

#### 当前问题
`ReadingAuthCompatController` / `ReadingBillingCompatController` 等都强绑定 Paipai。

#### 目标
引入通用基类或组合工具：

```java
public abstract class AppCompatControllerSupport {
    protected abstract AppModule appModule();
}
```

#### 验收标准
- 新 App 如需 compat controller，只需声明自己的 AppModule。
- 不再手写 scattered appCode。

---

### P1-4. 业务表前缀策略模板化

#### 判断
不要求立刻把 `reading_` 表重命名为 `paipai_`，因为这会引发高风险 schema 迁移。

但必须做到：
- `reading_` 被明确声明为 Paipai 当前 App 的 internal domain tablePrefix。
- 未来 App 不能误用 `reading_`。
- 新 App tablePrefix 必须在 AppModule 中声明。

#### 验收标准
- migration / mapper / sync schema 都能追溯到 tablePrefix 所属 AppModule。
- release gate 或 static audit 能发现新 App 引用了错误 tablePrefix。

---

### P1-5. AppDefinition 模板化

#### 目标
为每个 App 提供标准 app-definition 模板。

#### 模板字段

```yaml
app:
  code: <app_code>
  name: <display_name>
  apiPrefix: /api/v1
  tablePrefix: <domain_>
  support:
    appleSignInRequired: "true"
    billingRequired: "true"
  auth:
    apple:
      clientId: <bundle_id_or_service_id>
      teamId: <env_override_required>
      keyId: <env_override_required>
      privateKey: <env_override_required>
      redirectUri: <env_override_required>
      remoteExchangeEnabled: "true"
  billing:
    appstore:
      bundleId: <bundle_id>
      appAppleId: <app_apple_id>
      environment: production
      allowSandbox: "false"
    entitlements:
      productMappings: {}
```

#### 验收标准
- 新 App 配置缺任何关键项，release gate 必须 blocked。

---

## 7. P2：自动化审计与防回归

### P2-1. 新增多 App 静态审计脚本

#### 建议脚本

```text
scripts/audit-multi-app-isolation.sh
```

#### 检查内容
- 搜索裸 appCode：`reading` / `paipai_readingcompanion` / `saving`
- 检查 controller 是否写死 `APP_CODE`
- 检查 iOS 是否使用裸 UserDefaults key
- 检查 Keychain service/account 是否 app-scoped
- 检查 PowerSync route 是否带 appCode
- 检查 appDefinition bundle/client/billing 一致性

#### 验收标准
- CI 能跑。
- release gate 或开发脚本能输出问题列表。

---

### P2-2. 新增 App 接入 checklist 生成器

#### 目标
以后新增 App 时自动生成：
- app-definition.yml
- AppModule.java
- release gate profile
- iOS AppIdentity.swift
- local storage namespace template
- entitlement mapping template

#### 可选脚本

```text
scripts/new-app-module.sh <appCode> <internalDomain> <bundleId>
```

#### 验收标准
- 新 App 接入不再靠复制 Paipai 文件手改。

---

### P2-3. 测试覆盖

#### backend 单测建议
- 同一个 Apple subject + 不同 appCode → 生成不同 sys_user。
- App A session token 调 App B endpoint → 401/403。
- App A transaction notification 不会回补到 App B user。
- App A entitlement 不出现在 App B overview。
- App A PowerSync installation 不能被 App B session 使用。

#### iOS 测试建议
- AppIdentity 变化时 Keychain/UserDefaults/PowerSync DB key 都变化。
- BackendRoute 生成 path 必须包含 appCode。

---

## 8. P3：内部命名清理与长期演进

### P3-1. 是否重命名 Java package

#### 当前建议
不要马上全量重命名，但要先完成模板抽象。

后续可选：
- `apps.reading` → `apps.paipai` 或 `apps.readingcompanion`
- `Reading*` 类 → `PaipaiReading*`

#### 前置条件
- AppModule 抽象完成。
- release gate 与静态审计可防回归。
- 后端测试可跑。

### P3-2. 是否重命名数据库表前缀

#### 当前建议
不作为近期目标。

原因：
- 表名前缀是内部 physical schema。
- 当前真正隔离靠 `app_code`。
- 重命名表风险高、收益低。

但必须补文档说明：
- `reading_` 是 Paipai 当前业务域的 physical table prefix。
- 不代表 appCode。
- 未来 App 不得复用该 tablePrefix。

---

## 9. 建议执行顺序

### 第一阶段：先防止未来 App 接错
1. 新增 AppModule / AppModuleRegistry。
2. 新 App 默认走 appCode-aware system route。
3. BackendClient 引入 AppIdentity + BackendRoute。
4. Release gate 增加 bundle/client/billing 一致性检查。
5. iOS Keychain/UserDefaults 全部 app-scoped。

### 第二阶段：模板化 reading 当前实现
1. ReadingAppModule 显式声明 appCode / internalDomain / tablePrefix。
2. Reading compat controller 改为 AppModule 注入。
3. Reading PowerSync adapter 绑定 AppModule。
4. Entitlement mapping 强制显式化。

### 第三阶段：自动化防回归
1. 新增 multi-app audit 脚本。
2. 新增 new-app-module 脚手架。
3. 加 backend 单测。
4. 加 iOS route/storage 单测。

### 第四阶段：可选内部命名清理
1. 再评估是否重命名 Java package。
2. 再评估是否重命名 resources 目录。
3. 暂不优先重命名数据库表。

---

## 10. Definition of Done

只有满足以下条件，才能认为“完全模板化的多 App 脚手架”完成：

- [ ] 新 App 可以通过模板生成基本 backend/iOS 接入文件。
- [ ] 新 App 不需要复制 Paipai compat route。
- [ ] 所有 app-specific 常量都能追溯到 AppModule / AppDefinition / AppIdentity。
- [ ] Apple 登录 identity token 按 App 独立 clientId 校验。
- [ ] session、provider token、purchase、entitlement、PowerSync 均按 `app_code` 隔离。
- [ ] iOS Keychain、UserDefaults、PowerSync DB、sync metadata 均按 appCode/bundleId 隔离。
- [ ] release gate 能按 appCode 输出 per-app readiness。
- [ ] release gate 能检查 iOS bundleId、Apple clientId、billing bundleId 一致性。
- [ ] entitlement product mapping 不再长期依赖 fallback。
- [ ] CI/static audit 能发现裸 appCode、裸 UserDefaults key、错误 route、错误 bundle 配置。
- [ ] 至少有一组测试证明：同一 Apple subject 在两个 appCode 下会生成两个独立用户，不共享权益。

---

## 11. 当前立即可执行的文件级清单

### backend
- `src/main/java/com/apphub/backend/apps/common/AppModule.java`
- `src/main/java/com/apphub/backend/apps/common/AppModuleRegistry.java`
- `src/main/java/com/apphub/backend/apps/common/AppCompatControllerSupport.java`
- `src/main/java/com/apphub/backend/apps/common/AppReleaseGateSupport.java`
- `src/main/java/com/apphub/backend/apps/reading/ReadingAppModule.java`
- `src/main/java/com/apphub/backend/apps/reading/auth/controller/ReadingAuthCompatController.java`
- `src/main/java/com/apphub/backend/apps/reading/common/ReadingAuthenticatedUserResolver.java`
- `src/main/java/com/apphub/backend/apps/reading/billing/controller/ReadingBillingCompatController.java`
- `src/main/java/com/apphub/backend/apps/reading/powersync/ReadingPowerSyncAdapter.java`
- `src/main/java/com/apphub/backend/sys/app/controller/SystemController.java`
- `src/main/java/com/apphub/backend/sys/auth/controller/SysAuthController.java`
- `src/main/java/com/apphub/backend/sys/billing/service/SysBillingService.java`
- `src/main/resources/apps/reading/app-definition.yml`

### iOS
- `Core/Utilities/PaipaiAppIdentity.swift` → 后续升级为可模板化 `AppIdentity.swift`
- `Core/Services/BackendClient.swift`
- `Core/Services/SecureSessionStore.swift`
- `Core/Sync/SyncSupport.swift`
- `Core/Sync/PowerSyncManager.swift`
- `Core/Sync/PowerSyncInstallationStore.swift`
- `Core/Sync/PowerSyncCredentialStore.swift`
- `Core/Sync/PowerSyncRejectionStore.swift`
- `Core/Sync/SyncSettingsStore.swift`
- `App/PaipaiReadAlongV2App.swift`
- `Core/Services/DeviceInfoService.swift`
- `Core/Services/AnnouncementStore.swift`

### scripts / docs
- `scripts/audit-multi-app-isolation.sh`
- `scripts/new-app-module.sh`
- `backend/files/paipaiV2-多App隔离审计表-20260422.md`
- `backend/files/paipaiV2-多App模板化落地改造清单-20260422.md`

---

## 12. 最终提醒

这次的关键不是“把 reading 字符串全部替换掉”。

真正的目标是：

> **让 appCode / bundleId / clientId / entitlement / sync / local storage 的隔离边界变成模板和自动检查，而不是继续靠人脑记住哪些地方不能抄错。**

因此后续执行时应优先做：
1. AppModule 抽象。
2. appCode-aware 路由。
3. iOS app-scoped storage。
4. release gate 多 App 检查。
5. 自动审计脚本。

等这些做好后，再考虑 Java package / 目录名 / 类名是否要进一步美化。
