# Paipai V2 / Unified Backend 多 App 隔离审计表（2026-04-22）

## 1. 文档目的

本文用于回答以下核心问题，并作为后续多 App 接入统一后端时的审计基线：

1. 不同 App 的 AppleID 登录是否会串号。
2. 不同 App 的账户、会话、权益、同步数据是否会互通。
3. 当前实现中哪些点已经满足多 App 隔离，哪些点仍有扩展性风险。
4. 后续是否必须把当前 `reading` 业务实现升级为**完全模板化的多 App 脚手架**。

## 2. 本轮审计最终结论

### 2.1 总结论
- **后端核心数据模型与鉴权模型已经基本满足“多 App 共后端、按 `app_code` 强隔离”的方向。**
- **当前未发现会导致不同 App Apple 登录直接共号、session 互用、权益互通的核心结构性缺陷。**
- **但当前工程形态仍偏“Paipai 首个 App + reading 专属兼容层”，还没有真正达到“完全模板化的多 App 脚手架”。**
- 因此，后续不能只停留在“内部保留 `reading` 名称也能跑”的状态，必须继续推进到：
  - app identity 模板化
  - 路由模板化
  - 配置模板化
  - release gate 模板化
  - PowerSync / billing / auth 接入模板化
  - iOS 本地存储命名模板化

### 2.2 现在可以确认的业务结论
- 现在这套 unified backend **可以支撑 Paipai 作为第一个 App 上线**。
- 如果未来要继续接第二个、第三个 App，**后端模型方向是对的，但工程脚手架仍不够标准化，存在“新 App 接入时人为接错/抄错”的埋坑风险**。
- 因此后续目标必须从“可运行”提升为“可模板化复制”。

---

## 3. 审计范围

### 3.1 backend
- 认证 / Apple 登录 / session
- billing / entitlement / App Store notification
- PowerSync bootstrap / token / upload / installation
- app definition / release gate / remote config
- reading compat controller 与 system controller 的边界

### 3.2 iOS
- `BackendClient` 路由绑定
- `SecureSessionStore` Keychain 作用域
- `UserDefaults` / `ScopedJSONStore` 命名空间
- PowerSync 本地 DB / credential / installation / rejection / settings store
- App identity 常量收口情况

---

## 4. 多 App 隔离审计表

| 模块 | 审计点 | 当前状态 | 结论 | 风险级别 | 证据 / 文件 | 后续动作 |
|---|---|---|---|---|---|---|
| App 身份 | `app_code` 是否为一级隔离维度 | 已实现 | 正确 | 低 | `V1__baseline_current_schema.sql` | 保持 |
| 用户模型 | `sys_user` 是否带 `app_code` | 已实现 | 正确 | 低 | `sys_user` schema | 保持 |
| 第三方身份 | `sys_user_identity` 是否按 `app_code + provider_code + provider_subject` 唯一 | 已实现 | 不同 App 同 Apple subject 不会直接共号 | 低 | `uk_sys_user_identity_app_provider_subject` | 保持 |
| Provider token | `sys_auth_provider_token` 是否按 `app_code + provider_code + provider_subject` 唯一 | 已实现 | 不同 App 不共用 Apple token 归属 | 低 | `uk_sys_auth_provider_token_identity` | 保持 |
| 会话模型 | `sys_auth_session` 是否带 `app_code` | 已实现 | 不同 App session 有隔离基础 | 低 | `sys_auth_session` schema | 保持 |
| 会话解析 | 业务 resolver 是否校验 `session.appCode` / `user.appCode` | 已实现 | 不会把别的 App bearer token 误用到 reading 业务 | 低 | `ReadingAuthenticatedUserResolver.java` | 未来做成模板 resolver |
| Apple identity 校验 | 是否校验 issuer / audience / nonce | 已实现 | 不同 App 只要 clientId 不同，就不会互认身份票据 | 低 | `ReadyForIntegrationAppleIdentityTokenVerifier.java` | 保持 |
| Apple App 配置 | clientId / bundleId 是否由 app-definition 驱动 | 已实现 | 方向正确 | 低 | `apps/reading/app-definition.yml` | 做成 app 模板必填项 |
| Billing 交易归属 | `sys_purchase_transaction` 是否带 `app_code` | 已实现 | 权益归属基础正确 | 低 | `V1__baseline_current_schema.sql` | 保持 |
| Entitlement 快照 | `sys_entitlement_snapshot` 是否带 `app_code` | 已实现 | 不同 App 权益不互通 | 低 | `V1__baseline_current_schema.sql` | 保持 |
| 通知回补用户 | 是否按 `app_code + originalTransactionId/appAccountToken` 归因 | 已实现 | 不会跨 App 回补到错误用户 | 低 | `SysBillingService.java` / `SysPurchaseTransactionMapper.java` | 保持 |
| App Store authoritative 校验 | 是否校验回源 `bundleId` 与 appDefinition 配置一致 | 已实现 | 不同 App 不会共享同一 bundle 的权利 | 低 | `LiveAppStoreServerApiClient.java` | 保持 |
| PowerSync session 鉴权 | `/api/v1/powersync/{appCode}` 是否校验 URL appCode 与 session / user 一致 | 已实现 | 不同 App 同步链路有隔离 | 低 | `SysPowerSyncSessionService.java` | 保持 |
| PowerSync installation | installation 是否归属 `appCode + userId` | 已实现 | 不会跨 App / 跨用户复用 installation | 低 | `SysSyncInstallationService.java` | 保持 |
| PowerSync business apply | reading adapter 落库前是否按 userId 再验 ownership | 已实现 | 不会把他人/它 App 数据写入当前用户域 | 低 | `ReadingPowerSyncAdapter.java` / `ReadingPowerSyncValidator.java` | 未来做成通用 adapter 模板 |
| Backend compat 路由 | 客户端是否仍依赖 reading 专属 compat 路由 | 存在硬编码 | 当前 Paipai 可用，但新 App 极易误接到 `paipai_readingcompanion` compat 层 | 高 | `BackendClient.swift` / `ReadingAuthCompatController.java` | 必须改成模板化 app-aware 路由策略 |
| Backend system 路由 | 是否已有 `/api/v1/system/auth/apps/{appCode}/...` 这类显式路由 | 已存在 | 可作为多 App 标准路由基座 | 中 | `SysAuthController.java` | 新 App 默认走 system 路由 |
| Compat controller 命名 | 仍保留 `Reading*CompatController` | 存在 | 不影响当前隔离，但不利于多 App 脚手架复制 | 中 | `apps/reading/...` | 必须模板化 |
| 内部业务域命名 | 仍保留 `apps.reading` / `apps/reading` / `reading_` 表前缀 | 存在 | 当前不是串域 bug，但会提高第二个 App 接入的人为误配风险 | 中 | backend 目录结构 | 升级为模板脚手架，不再长期依赖 reading 专属骨架 |
| iOS App identity | 是否已有统一 `PaipaiAppIdentity.appCode` | 已实现 | Paipai 内部已开始收口 | 低 | `PaipaiAppIdentity.swift` | 升级为通用 `AppIdentity` 模板 |
| iOS PowerSync 本地 DB | DB 文件名是否 app-scoped | 已实现 | 降低多 App 本地缓存串域风险 | 低 | `PaipaiAppIdentity.powerSyncDatabaseFilename` / `PowerSyncManager.swift` | 保持并模板化 |
| iOS ScopedJSONStore | sync settings / credentials / installation / rejections 是否带 appCode namespace | 已实现 | 多 App 本地 sync 元数据基本隔离 | 低 | `PowerSync*Store.swift` / `SyncSettingsStore.swift` | 保持 |
| iOS Keychain session | `SecureSessionStore` 是否显式按 appCode/bundleId 分域 | 未完全实现 | 当前单 App 没明显问题，但不适合作为通用模板 | 中 | `SecureSessionStore.swift` | 必须改成 app-scoped keychain 策略 |
| iOS UserDefaults | 是否所有 key 都已 app-scoped | 未完全实现 | 当前单 App 可用，但未来多 App 模板化有坑 | 中 | `PaipaiReadAlongV2App.swift` / `DeviceInfoService.swift` / `OnboardingView.swift` | 必须统一命名规范 |
| Release gate | 是否已把 appCode 作为 release gate / config namespace 的维度 | 已部分实现 | 方向正确，但覆盖还不够完整 | 中 | `SystemController.java` / `release_ios` namespace | 扩展为模板化多 App gate |
| 工程可复制性 | 是否已经是“新 App 可按模板接入”的状态 | 否 | 目前仍偏首个 App 人工收口模式 | 高 | 全局 | 必须继续整改 |

---

## 5. 已确认正确、可继续保留的设计

### 5.1 后端统一模型方向是正确的
以下原则应视为已经定稿：
- `app_code` 是统一后端内最关键的一层业务隔离域。
- 同一个 Apple 用户在不同 App 中，应被视为不同 app 域下的不同用户主体。
- entitlement / purchase / session / sync / remote config 都必须落在 app 域下。

### 5.2 `system` 层路由是未来模板化基座
以下 system 级设计是应该继续强化的：
- `/api/v1/system/auth/apps/{appCode}/...`
- `/api/v1/powersync/{appCode}/...`
- `AppDefinitionService.get(appCode)`
- `PowerSyncAppAdapterRegistry.require(appCode)`

这些比“reading compat controller”更适合成为多 App 标准脚手架基础层。

---

## 6. 已确认存在、必须治理的风险

### 6.1 风险 A：客户端仍可能误连到 Paipai 专属 compat 层
当前 `BackendClient.swift` 仍走固定路径：
- `/api/v1/system/auth/apps/{appCode}/apple/exchange`
- `/api/v1/system/auth/apps/{appCode}/me`
- `/api/v1/subscriptions/...`
- `/api/v1/account/...`

而 `ReadingAuthCompatController` 内部直接写死：
- `APP_CODE = "paipai_readingcompanion"`

这意味着：
- 现在 Paipai 可用；
- 但新 App 若复用这套客户端代码，会直接打进 Paipai 的 compat controller；
- 这属于**工程接入风险**，不是数据模型问题，但必须在第二个 App 前清掉。

### 6.2 风险 B：iOS 本地存储命名还没有完全模板化
目前已有一部分已收口（PowerSync store），但仍有未统一前缀的本地 key，例如：
- `hasCompletedOnboarding`
- `hasAcceptedPrivacyPolicy`
- `hasAcceptedDeviceInfoCollection`
- `appUsageRecords`

当前单 App 没大问题，但对“未来多 App 统一基座”来说仍是不合格状态。

### 6.3 风险 C：内部 `reading` 业务域保留太久，会让多 App 接入继续靠人工判断
此前这些内容暂时保留是为了先收口首发：
- `apps.reading` Java package
- `apps/reading` 资源目录
- `reading_` 表前缀
- `Reading*` 类名

现在你的要求已经明确变更为：

> **问题 5 不再允许停留在“先保留 reading 命名也行”的阶段，而要升级为完全模板化的多 App 脚手架。**

因此这部分现在应从“可延期项”升级为“后续必须完成的结构改造项”。

---

## 7. 审计建议（定稿）

### 7.1 结构建议
后续统一后端必须分成三层：

#### A. system 基础设施层（统一）
- auth/session 核心
- Apple token verify/exchange/revoke/refresh 基础设施
- App Store Server API 封装
- PowerSync 基础控制器 / session require / installation / audit
- remote config / release gate 骨架

#### B. app module 模板层（可复制）
每个 App 必须有标准化模板入口，例如：
- app definition
- app-specific compat facade（如仍需要）
- billing mapping
- entitlement mapping
- powersync adapter
- app release checks

#### C. app implementation 层（业务差异）
- reading、saving、未来新 App 的业务模型、统计逻辑、UI 文案差异

### 7.2 规则建议
新增 App 时，必须一口气补齐以下最小集：
- `appCode`
- `bundleId`
- `auth.apple.clientId`
- `billing.appstore.bundleId`
- `billing.appstore.appAppleId`
- entitlement product mapping
- release gate required checks
- iOS local storage namespace
- AppStore notification routing
- PowerSync adapter / validator

### 7.3 交付标准建议
以后不允许再用“人工 grep 看看有没有旧 appCode”作为主要接入方式；应补成：
- checklist
- 模板文件
- 代码生成/脚手架
- release gate 自动检查
- CI lint / static audit

---

## 8. 结论（供决策）

### 8.1 当前是否存在不同 App AppleID 互相访问的明显问题
**没有发现。**

### 8.2 当前 unified backend 是否已经足够支撑 Paipai 首发
**可以。**

### 8.3 当前 unified backend 是否已经足够支撑“以后随时低风险接更多 App”
**还不够。**

### 8.4 后续策略是否应该把“reading 专属实现”升级为完全模板化多 App 脚手架
**应该，而且现在已经不是建议项，而是必须项。**

---

## 9. 与落地清单的关系

本文负责回答：
- 现状是否隔离
- 哪些地方已经对
- 哪些地方仍然会埋坑

配套执行文档见：
- `paipaiV2-多App模板化落地改造清单-20260422.md`
