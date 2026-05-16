# paipaiV2 P1 审计进度（2026-04-23）

更新时间：2026-04-23 09:36（Asia/Shanghai）

> 说明：本次结论基于 **iOS 展示层静态复扫 + 文档对照 + 代码修正后复核** 得出。当前 Linux 主机仍无 `swift` / `xcodebuild` / 真机环境，因此本文件能判断“代码是否继续收口”，但**不能替代 Mac/Xcode 最终编译与提审验收**。

## 一、本轮新增确认与修复

### 1. 已直接修复的展示层问题
- `Features/Paywall/PaywallView.swift`
  - 删除了重复且空白的 `localeText(...)` 函数块，消除一处明显的 Swift 编译风险。
  - Paywall 特性展示补上了 `advancedVoiceEnabled` 消费。
  - Plan 卡片摘要不再只按 `childLimit > 1` 推导，而是按：
    - `childLimit`
    - `historyEnabled`
    - `cloudSyncEnabled`
    - `advancedVoiceEnabled`
    动态组合展示，减少旧假设。
- `Features/Parent/ManageChildrenView.swift`
  - 不再写死“免费版当前支持 1 个孩子”。
  - 改为优先消费后端 entitlement 的：
    - `childCount`
    - `childLimit`
    - `remainingChildSlots`
    - `planName`
  - 当名额已满时，提示文案改成当前套餐上下文，不再默认按 free plan 口径展示。
- `Features/Onboarding/OnboardingView.swift`
  - 隐私同意页移除了“使用时长记录将在 30 天后自动删除”的旧口径。
  - 文案已改成与当前法务/后端口径一致：
    - 学习内容本地优先
    - 开启云同步或主动触发云端能力时才同步必要数据
    - usage 聚合用于展示今日 / 最近 7 天 / 累计统计
    - 云端 OCR / TTS 仅在用户主动触发时走网络并受权益/次数控制

### 2. 本轮复扫后确认“已消费到位”的字段
- entitlement / plan
  - `childLimit`
  - `childCount`
  - `remainingChildSlots`
  - `cloudSyncEnabled`
  - `historyEnabled`
  - `advancedVoiceEnabled`
  - `supportedLocales`
  - `supportedLearningTrackCodes`
- announcement
  - `announcementUuid`
  - `visibleStartAt`
  - `visibleEndAt`
  - `active`
  - `maxDisplayCount`
  - `minIntervalSeconds`
  - `triggerScene`
  - `actionUrl`
  - `actionText`
- usage / parent summary
  - `todayDurationSeconds`
  - `totalDurationSeconds`
  - `weeklyDurationSeconds`
  - `recentDailyUsage`
- privacy / legal copy
  - onboarding 隐私弹窗已与当前政策收口，不再保留 30 天旧假设

## 二、当前 P1 已完成项（静态代码视角）

### P1-1 家长区改为设备密码 / 生物识别优先
**结论：代码已完成，待 Xcode/真机验收。**
- `ParentAreaView.swift` 已优先调用系统级设备验证。
- `ParentGateService.swift` 已使用 `LocalAuthentication.deviceOwnerAuthentication`。
- 系统验证失败时保留数学题 fallback。
- `Info.plist` 已包含 `NSFaceIDUsageDescription`。

### P1-2 删除账号改为邮箱验证码确认
**结论：代码已完成，待真实邮箱/Apple relay/真机验收。**
- `DeleteAccountView.swift` + `VerificationCodeInputView.swift` 已接线。
- 后端链路已切到 request-code / confirm 口径。
- UI 已体现“邮箱仅用于临时验证码/反馈，不作为长期登录方式”。

### P1-3 价格 / 权益 / 语言种类后台动态化
**结论：主体已完成，本轮已继续清掉几处旧展示假设。**
- `PaywallView.swift` 已消费动态 plans。
- `LanguagePreferenceView.swift` 已消费：
  - `supportedLocales`
  - learning track / preferences
  - translation pack readiness
- `ManageChildrenView.swift` 已改为基于 entitlement 展示孩子名额。
- `OnboardingView.swift` 的隐私与能力口径已同步到当前产品边界。

### P1-4 usage 真正做到“按孩子展示日 / 总时长”
**结论：代码已完成，待真机生命周期回归。**
- `CaptureView.swift` / `LearningDetailView.swift` / `ReviewView.swift` 已补 usage session 生命周期。
- `ParentAreaView.swift` / `ManageChildrenView.swift` 已展示：
  - 今日
  - 累计
  - 最近 7 天
- 静态检查已确认 child context 切换、前后台切换、页面退出时都有 start/end 处理。

### P1-5 隐私与审核口径收口
**结论：主体已完成，但最终提审材料仍需 Mac/Xcode + App Store Connect 侧收尾。**
- `Info.plist` 当前仅见：
  - 相机
  - 相册
  - Face ID
- `PrivacyInfo.xcprivacy` 当前声明：
  - PhotosorVideos
  - EmailAddress
  - UserID
  - ProductInteraction
  - tracking = false
- `privacy-policy.html` / `terms-of-service.html` 已存在。
- 本轮已修正 onboarding 内与法务文档不一致的 30 天旧口径。

## 三、当前剩余上线缺口 / blocker

### blocker 1：当前环境无法完成 iOS 真编译验证
当前主机缺失：
- `swift`
- `xcodebuild`
- 真机 / Simulator

因此以下仍未完成：
- Debug 编译
- Release 编译
- Simulator smoke test
- 真机安装与回归

这仍是**上线前最后一道硬 blocker**。

### blocker 2：Apple / App Store 真实链路未在当前轮完成运行级回归
文档里仍要求最终实测：
- Apple 登录
- purchase
- restore purchase
- 删除账号验证码送达与确认
- App Store / backend 校验链路

当前只能确认“代码已接线”，**不能替代真实商店链路验收**。

### blocker 3：P1 文档里额外列出的后续缺口仍未在本轮关闭
来自 `paipaiV2-appCode重构与统一后端后续任务清单-20260422.md` 的剩余项：
- `verificationReadiness` 是否完全等于真实 readiness，仍需在真实配置环境验证
- entitlement 映射是否已彻底改成显式配置，需继续核对后端配置口径
- 云端 OCR / TTS 首版口径是否最终选择“设备端优先，不对外承诺云端”或“真实 provider 已验通”，仍需产品/提审口径定稿
- 公告 / 周报 / usage summary 真数据回归仍需设备侧实测

## 四、当前可判定的上线风险级别

### 已清掉的明显代码级风险
- Paywall 一处静态编译风险已移除
- 孩子名额提示不再默认按 free plan 假设展示
- onboarding 隐私说明不再保留“30 天自动删除”旧口径

### 仍不能仅凭当前环境宣告“可以直接上线”的原因
因为还缺：
1. Xcode 编译结果
2. 真机主链路录屏/截图
3. App Store / Apple 真链路验证
4. 提审问卷与 privacy manifest 的最终勾对

## 五、建议的最后执行顺序
1. **立即在 Mac/Xcode 环境跑 `paipaiV2-P0-P1-Xcode验收清单-20260420.md`**
2. 重点先验：
   - Paywall 编译与展示
   - 家长区设备验证
   - 删除账号验证码
   - usage 生命周期
   - legal docs 打开
3. 然后做真实链路：
   - Apple 登录
   - purchase / restore
   - announcement / weekly report / usage 真数据回归
4. 若以上通过，再判定“达到可以上线程度”

## 六、本轮继续补齐的后端 / 数据库收口（2026-04-23 00:02 UTC+0 验证）

### 1. entitlement / plan 映射已继续收口
已补后端：
- `ReadingCompatService.planCodeForProduct(...)`
  - 移除了原先按 `productId contains("family")` 的静默 fallback。
  - 改为仅接受两类**显式配置**：
    1. `reading_plan_catalog` 中声明的 `appStoreProductId` / `matchedProductIds` / `matchedEntitlementCodes`
    2. `billing_entitlements` 或 `app-definition.yml` 中声明的 `productMappings.*`
- `ReadingCompatService.resolveEntitlement(...)`
  - entitlement 命中逻辑改为支持 `matchedEntitlementCodes`，避免只有主 `entitlementCode` 命中时才能识别套餐。
- `SubscriptionStatusView.projections`
  - planCode 不再一律复用当前账号主 plan，而是按每条 entitlement 投影单独解析。
- `refreshEntitlement(...)`
  - `activeProjectionCount` 改为真实 active projection 数量，不再按多孩子开关硬猜。

### 2. verificationReadiness 已更接近真实 readiness
已补后端：
- `subscriptionVerificationReadiness()` 不再只看 Apple readiness。
- 现在会把 `sysBillingService.describeEntitlementObservability(...)` 的
  `effectiveMappingCount` 纳入判断。
- 当显式 productId → entitlement 映射缺失时：
  - `cryptographicVerificationLive = false`
  - `note` 会明确提示“映射缺失，已验证交易仍无法安全投影到套餐”

这比之前更接近文档里要求的“真实 readiness”，至少不会在 Apple 凭证就位但 entitlement 映射缺失时误报 ready。

### 3. 数据库迁移已补齐显式 alias 与公告样例修正
新增：
- `src/main/resources/db/migration/V12__paipai_plan_aliases_and_support_announcement_fix.sql`

内容：
- 给 `family_multi_child_lifetime` 增加：
  - `matchedEntitlementCodes = ["family_access", "family_multi_child"]`
  - `matchedProductIds = [
      "com.paipai.readalong.family.yearly",
      "com.paipai.readalong.family.monthly",
      "com.paipai.readalong.family.multi_child.lifetime"
    ]`
- 把支持中心联调公告 `support-center-sample-20260422` 的 `target_plan_code` 从
  `premium_lite_monthly` 修正为当前默认付费 plan `family_multi_child_lifetime`

### 4. Docker 编译 / 测试 / 启动验证结果
已完成：
- Docker 依赖启动：
  - `postgres`
  - `redis`
- Maven 容器编译通过：
  - `mvn -DskipTests compile`
- 目标测试通过：
  - 第一轮定向回归：
    - `ReadingCompatServiceTest`
    - `ReadingAnnouncementServiceTest`
    - `ReadingAnnouncementCompatControllerWebMvcTest`
    - `ReadingBillingCompatControllerWebMvcTest`
    - `AppAppleReadinessServiceTest`
    - `ReadingCloudUsageServiceTest`
  - 第二轮 reading 域扩大回归（Docker Maven）：
    - `ReadingAccountCompatControllerWebMvcTest`
    - `ReadingAnnouncementCompatControllerWebMvcTest`
    - `ReadingAppStoreWebhookCompatControllerWebMvcTest`
    - `ReadingAppStoreWebhookCompatOpsTokenWebMvcTest`
    - `ReadingEmailAuthCompatControllerWebMvcTest`
    - `ReadingBillingCompatControllerWebMvcTest`
    - `ReadingCloudUsageServiceTest`
    - `ReadingCompatServiceTest`
    - `ReadingDeletionVerificationCompatControllerWebMvcTest`
    - `ReadingPreferenceServiceTest`
    - `ReadingPowerSyncAdapterTest`
    - `ReadingPublicCompatControllerWebMvcTest`
    - `ReadingTtsCompatControllerWebMvcTest`
    - `ReadingAnnouncementServiceTest`
    - `ReadingCloudProviderConfigServiceTest`
  - 第三轮系统门禁 / 计费 / ops / appstore 回归：
    - `SystemControllerWebMvcTest`
    - `SystemProductionConfigurationGuardTest`
    - `SysBillingServiceTest`
    - `SysBillingControllerWebMvcTest`
    - `ProductionProfileConfigurationTest`
    - `SystemOpsTokenFailClosedWebMvcTest`
    - `SystemOpsTokenFilterWebMvcTest`
    - `SysAppStoreControllerWebMvcTest`
    - `SysAppStoreOpsTokenFilterWebMvcTest`
    - `SysAppStoreNotificationServiceTest`
- Spring Boot 在 Docker + 本地 compose 依赖下启动成功：
  - 由于宿主 `8080` 已被占用，改用临时端口 `18080`
  - `GET /actuator/health` 返回 `UP`
  - `GET /api/v1/plans` 返回正常，family/free plan 可读

## 七、P3 多 App 模板化本轮新增进展（2026-04-23 09:36 Asia/Shanghai）

### 1. 后端模板化收口继续前进
已新增或接入：
- `AppCodes.java`
- `AppCompatControllerSupport.java`
- `ReadingAppModule.java`
- `SysAuthController.java` 已收口到通用 app-scoped 门禁辅助
- `SysBillingController.java` 已收口到通用 app-scoped 门禁辅助
- `ReadingAppStoreWebhookCompatController.java` 已改为经统一辅助校验 app definition

其中 `AppCompatControllerSupport` 本轮进一步改成：
- `SessionTokenResolver`
- `SysAuthSessionService`
  采用 **延迟获取（ObjectProvider）**

这样像 App Store webhook 这类只需要 `requireAppDefinition(...)` 的控制器，不会再被无关的会话依赖强耦合，WebMvc slice test 也不会因为缺少认证 bean 而假失败。

另外，`AppAppleReadinessView` / `AppAppleReadinessService` / `SystemController.releaseGate` 本轮新增了更贴近上线判断的信号：
- `formalSessionReady`
  - 明确表示 Apple formal session 所需的 clientId / key / redirect / token endpoint / encryption key / bundle 身份是否已经同时就绪
- `bundleIdentityAligned`
  - 明确校验 `auth.apple.clientId` 与 `billing.appstore.bundleId` 是否一致
- `productionSandboxSafe`
  - 明确标记当前 App Store 配置是否已经满足“生产口径下不接受 sandbox”
- release gate 新增 fail-closed 检查：
  - `apple.remoteExchangeEnabled`
  - `billing.allowSandbox`
- release gate 的 API base URL 预检已同时接受：
  - `api_base_url`
  - `paipai_api_base_url`
  - `reading_api_base_url`

这使得 release gate / readiness 不再只是“字段有没有”，而是更接近“这套 Apple / App Store 配置能不能支撑正式上线”，同时避免因 P3 阶段内部 `reading` 与对外 `paipai` 命名并存而产生误报。

### 2. 后端回归结果
本轮重新验证通过：
- `ReadingAppStoreWebhookCompatControllerWebMvcTest`
- `SysBillingControllerWebMvcTest`
- `SysAuthControllerWebMvcTest`
- `SystemControllerWebMvcTest`
- `AppModuleRegistryTest`
- `SystemProductionConfigurationGuardTest`
- `AppAppleReadinessServiceTest`
- `ReadingCompatServiceTest`

后续在统一口径后再次扩大回归，也继续通过：
- `AppAppleReadinessServiceTest`
- `SystemControllerWebMvcTest`
- `SystemProductionConfigurationGuardTest`
- `AppModuleRegistryTest`
- `SysAuthControllerWebMvcTest`
- `SysBillingControllerWebMvcTest`
- `ReadingAppStoreWebhookCompatControllerWebMvcTest`
- `ReadingCompatServiceTest`

说明当前 P3 门禁抽象改造、release gate 收口和 readiness 增强都没有把关键 controller / service 回归打炸。

### 3. 数据库与运行级验证
在本地 docker 依赖下已再次确认：
- PostgreSQL 连接成功
- Redis 连接成功
- Flyway 成功校验并识别 `13` 号 migration
- `Schema \"public\" is up to date`
- `http://127.0.0.1:18080/actuator/health` 返回 `UP`
- 新代码实例已在临时端口 `28082` 与 `28083` 启动成功
  - `http://127.0.0.1:28082/actuator/health` 返回 `UP`
  - `http://127.0.0.1:28083/actuator/health` 返回 `UP`
  - `http://127.0.0.1:28083/api/v1/system/release-gate` 已能返回新的 blocker/warning 结构
  - `http://127.0.0.1:28083/api/v1/system/apps/paipai_readingcompanion/apple/readiness` 已能返回：
    - `formalSessionReady=false`
    - `bundleIdentityAligned=true`
    - `productionSandboxSafe=false`
  - 最新运行级 blocker 已清晰暴露为：
    - `apple.remoteExchangeEnabled must be true`
    - `billing.allowSandbox must be false in production-like release config`
    - 缺失 `teamId / keyId / privateKey / redirectUri / issuerId / app store key` 等 Apple / App Store 正式凭证

注意：启动阶段多次失败都属于**环境端口占用**，不是 P3 代码或 migration 失败：
- `8080` 被占用
- `18080` 已有旧实例占用
- `28081` 也被已有进程占用

### 4. iOS 本地存储与路由模板化复扫
本轮继续静态确认：
- `AppIdentity.swift` 已作为通用 app identity 入口
- `PaipaiAppIdentity.swift` 已降级为兼容薄别名
- `AppScopedDefaults.swift` 已统一承接：
  - onboarding
  - privacy consent
  - interface locale
  - announcements cache / dismissed / presentation state
  - usage records
- `BackendRoute.swift` 新增 `systemAuthPrefix`
  - auth 路由生成与 `BackendClient` 边界校验改为共享同一前缀来源
  - 避免未来多 App 时一边升级为 app-scoped 路由，另一边仍残留手写旧前缀
- 追加扫描未发现新的 `UserDefaults.standard` 直写漏网点
- 追加扫描未发现新的旧式 auth / powersync 非 app-scoped 路由拼接点
- 本轮继续把一批对外暴露文案里的内部 `reading` 实现名收口：
  - iOS `CaptureView.swift` 已把“经过 reading 后端校验”改为“经过当前 App 后端校验”
  - backend `OpenApiConfig.java` 的 API 描述改为强调 multi-app / app-scoped auth,billing,sync,release gate 基线
  - 多个 compat controller 的 `@Tag` / 顶部注释已统一改成“拍拍伴读...”表述，避免 Swagger / 注释继续暴露内部实现名
- 追加轻量回归继续通过：
  - `ReadingCompatServiceTest`
  - `SysBillingControllerWebMvcTest`
  - `ReadingAppStoreWebhookCompatControllerWebMvcTest`
  - `SystemControllerWebMvcTest`

当前仍保留但可接受的 `Paipai` 痕迹主要是：
- 真实商品 ID
- 当前 target 的 fallback bundle identifier
- 兼容别名类型名

它们属于首个 App 的真实产品身份，不等于模板化遗漏。
- P3 第一轮模板化收口（当前新增）：
  - backend 新增 `AppCodes.java`，开始集中承载对外 `appCode` 常量，减少散落硬编码。
  - backend 新增 `AppCompatControllerSupport.java`，把 app 是否存在、会话是否属于指定 app 的门禁逻辑抽到统一辅助层。
  - `SysAuthController.java` 已接入 `AppCompatControllerSupport`，减少未来多 App 控制器复制粘贴漂移。
  - iOS 新增 `Core/Utilities/AppIdentity.swift`，并把一批底层 route / storage / PowerSync 默认参数改为优先消费 `AppIdentity`。
  - `PaipaiAppIdentity.swift` 保留为兼容薄别名，避免一次性模板化重命名带来漏改风险。
  - DB 新增 `V13__document_reading_table_prefix_as_physical_domain.sql`，通过表注释明确 `reading_` 只是物理业务域前缀，不等于对外 appCode。
- DB 实查确认：
  - `family_multi_child_lifetime` 已有 `matchedEntitlementCodes` 与 `matchedProductIds`
  - `support-center-sample-20260422.target_plan_code = family_multi_child_lifetime`
  - P3 新增 `V13__document_reading_table_prefix_as_physical_domain.sql` 已被 Flyway 成功执行，当前 schema 版本已到 `13`
- P3 本轮验证结果：
  - Docker Maven `mvn -DskipTests compile` 通过
  - `SysAuthControllerWebMvcTest`
  - `AppModuleRegistryTest`
  - `SystemControllerWebMvcTest`
  - 带本地 postgres/redis 的 `spring-boot:run` 在修正 dev 端口覆盖后启动成功
  - `GET /actuator/health` 返回 `UP`
  - `GET /api/v1/system/apps/paipai_readingcompanion` 返回正常，`tablePrefix=reading_` 与 `appCode=paipai_readingcompanion` 的分层口径可见

## 七、当前剩余上线 blocker（按优先级）
### P0：正式发布前必须补齐
- Apple formal code exchange 仍未闭环：
  - `app.auth.apple.remoteExchangeEnabled` 必须切到 `true`
- 生产口径下 App Store sandbox 仍未关：
  - `billing.appstore.allowSandbox` 必须为 `false`
- Apple / App Store 正式凭证仍缺失：
  - `auth.apple.teamId`
  - `auth.apple.keyId`
  - `auth.apple.privateKey`
  - `auth.apple.redirectUri`
  - `billing.appstore.issuerId`
  - `billing.appstore.keyId`
  - `billing.appstore.privateKey`
- 仍缺真实 Apple / App Store 链路回归：
  - Sign in with Apple formal exchange
  - App Store 购买 / 恢复购买 / server notification
  - entitlement 投影与 production 口径校验

### P1：发布前强烈建议完成
- 在 Mac / Xcode 环境完成 iOS 编译与基础 UI 回归
- 在真机或至少模拟器完成登录、购买、OCR/TTS、PowerSync、账号删除链路回归
- 继续补齐 production/release 配置源，确保 release gate 从 `blocked` 收敛到 `ready`

### P2：可继续做但不阻止当前代码收口
- 持续清理代码注释/文档中不必要暴露的内部 `reading` 实现名
- 继续为未来第二个 App 保留更明确的 app-scoped 模板说明

## 八、本轮结论
**当前状态已经比上一轮更接近上线，但仍应定义为：**

> **“代码收口继续前进，前端旧展示假设与后端 entitlement / readiness / 配置 alias 缺口已继续补齐，并已完成 Docker 编译、目标测试和本地启动验证；但在没有 Xcode 编译和真实 Apple / 真机回归之前，还不能单凭当前 Linux 环境直接签字上线。”**
