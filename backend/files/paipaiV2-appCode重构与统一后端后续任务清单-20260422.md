# Paipai V2 appCode 重构与统一后端后续任务清单（2026-04-22）

## 0. 本轮已确认的核心决策

### 产品定位
- **Paipai V2 是全新首发 App**，不承接旧系统历史兼容责任。
- **统一后端继续保留**，后续允许多个不同方向的 App 接入。
- **各 App 账户不互通**，同一个 Apple 账号在不同 App 中也视为不同 app 域下的独立账户实体。

### app 身份与统一后端边界
- **统一的是 backend 能力**：auth / session / billing 核心 / remote config / sync / release gate。
- **不统一的是 app identity**：appCode / bundleId / clientId / App Store 配置 / entitlement 配置 / 数据域归属。

### 本轮定稿
- 正式 appCode：`paipai_readingcompanion`
- 本轮先 **切换运行时 appCode**，但 **暂不重写**：
  - Java package 名 `...apps.reading...`
  - resources 目录 `apps/reading/...`
  - 数据表前缀 `reading_`
  - 兼容层 API 前缀 `/api/v1/...`

这意味着：
- **对外产品身份** 改为 `paipai_readingcompanion`
- **内部业务域实现名** 暂时仍保留 `reading`

这是为了先把产品边界校正，同时避免把当前任务扩大成一次高风险的“目录/包名/表名”总迁移。

---

## 1. 本轮已落地的变更（已执行）

### 1.1 backend 运行时 appCode 切换
已将关键运行时绑定从 `reading` 改为 `paipai_readingcompanion`：

- `src/main/resources/application.yml`
  - `backend.apps.supported`
  - `backend.apps.definitions`
- `src/main/resources/apps/reading/app-definition.yml`
  - `app.code: paipai_readingcompanion`
- `src/main/java/com/apphub/backend/sys/app/controller/SystemController.java`
  - release gate 读取 `release_ios` namespace 的 appCode 已改为 `paipai_readingcompanion`
- 以下 reading 业务服务/控制器/适配层的 `APP_CODE` 常量已切换：
  - `ReadingAuthenticatedUserResolver`
  - `ReadingAnnouncementService`
  - `ReadingAppStoreWebhookCompatController`
  - `ReadingCloudProviderConfigService`
  - `ReadingAuthCompatController`
  - `ReadingBillingCompatController`
  - `ReadingPowerSyncAdapter`
  - `ReadingPowerSyncMapper`
  - `ReadingPreferenceService`
  - `ReadingCompatService`
  - `ReadingDeviceEventService`
  - `ReadingCloudUsageService`
  - `ReadingUsageService`

### 1.2 mapper 中 app_code 硬编码切换
已把以下查询/写入里的 `app_code='reading'` 切换为 `paipai_readingcompanion`：
- `ReadingUsageSessionMapper`
- `ReadingUsageSessionV2Mapper`
- `ReadingChildUsageDailyMapper`

### 1.3 数据迁移补丁
已新增迁移：
- `src/main/resources/db/migration/V7__rename_reading_app_code_to_paipai_readingcompanion.sql`
- `src/main/resources/db/migration/V8__set_paipai_readingcompanion_app_code_defaults.sql`

作用：
- `V7`：自动扫描当前 schema 中所有包含 `app_code` 列的表，将 `app_code='reading'` 统一改写为 `paipai_readingcompanion`
- `V8`：继续扫描当前 schema 中所有 `app_code` 列的默认值，把历史 `DEFAULT 'reading'` 统一改成 `DEFAULT 'paipai_readingcompanion'`

这保证：
- **已有开发库** 能升级到新的 appCode
- **fresh DB** 在执行完旧迁移后，也会被 `V7 + V8` 收口到新的产品 appCode 口径
- 后续若某些 insert 漏传 `app_code`，也不会因为历史 schema default 而悄悄回落到旧值 `reading`

### 1.4 iOS appCode / PowerSync 协同收口
已新增并接入：
- `paipaiV2/ios/PaipaiReadAlongV2/Core/Utilities/PaipaiAppIdentity.swift`
  - `appCode = "paipai_readingcompanion"`
  - `powerSyncPathSegment = appCode`
  - `powerSyncDatabaseFilename = "\(appCode)-powersync.sqlite"`

已调整：
- PowerSync bootstrap/token/rebuild/upload 请求路径改为 `/api/v1/powersync/paipai_readingcompanion/...`
- PowerSync `appMetadata.appCode` 改为读取 `PaipaiAppIdentity.appCode`
- 本地 PowerSync 数据库文件名改为按 appCode 命名，避免后续多 App 本地缓存串域
- 本地 sync credential/settings/installation/rejection store namespace 改为按 `PaipaiAppIdentity.appCode` 派生
- 本地同步表写入的 `app_code` 由统一身份常量派生，不再在各 repository 分散写死旧 appCode

静态扫描结果：
- iOS 源码中已未发现 `/powersync/reading`、`reading.sync.*`、`paipai-reading-powersync.sqlite` 等旧运行时路径/本地 namespace 残留
- 当前目录未发现 `*.xcodeproj`、`*.xcworkspace` 或 `Package.swift`，因此还不能确认新增 Swift 文件已被 Xcode target 编译引用

---

## 2. 当前明确不改的内容（故意保留）

这些不是遗漏，而是当前阶段的**有意分层处理**：

### 2.1 保留 `reading_` 表名前缀
当前不改：
- `reading_child_profile`
- `reading_review_card`
- `reading_usage_session_v2`
- 其他 `reading_*` 业务表

原因：
- 改表名前缀会触发全量 SQL / mapper / FK / migration / PowerSync 规则联动
- 这是典型的 **P2/P3 级整理任务**，不应和当前 release 收口、Apple 收口绑在一起
- 对外隔离的关键是 `app_code`，不是表名前缀字符串是否叫 reading

### 2.2 保留 `apps/reading/` 与 Java package `...apps.reading...`
原因同上：
- 它们是 **内部实现名**，不影响产品外部身份
- 提前大规模重命名只会提高风险，收益很低

### 2.3 保留 `/api/v1/...` 兼容层路径
原因：
- iOS 已经按当前路径接线
- 当前最重要的是让后端权威口径、Apple/支付/删除账号/公告/权益动态配置先完整可跑
- 改路径不是当前 blocker

---

## 3. 统一后端的推荐分层模型（后续总原则）

## 3.1 app 级必须隔离的字段 / 能力
每个接入统一后端的 App，必须单独定义：
- `appCode`
- `app.name`
- `bundleId`
- `auth.apple.clientId`
- `auth.apple.redirectUri`
- `billing.appstore.bundleId`
- `billing.appstore.appAppleId`
- `billing.appstore.issuerId`
- `billing.appstore.keyId`
- `billing.appstore.privateKey`
- entitlement / productId 映射
- remote config namespace 下的业务开关
- 数据统计口径 / 业务表 app_code 归属
- session / token / purchase / sync 数据归属

## 3.2 backend 可共享的基础设施
这些能力可以继续做成统一底层：
- session 验签 / formal session 安全校验
- Apple token 校验与 exchange 基础设施
- App Store Server API 封装
- App Store Notification V2 处理骨架
- release gate / ops gate / readiness 检查框架
- remote config 读取与 namespace 分发
- PowerSync 接入框架
- 删除账号验证票据与幂等处理机制
- 公告规则分发框架
- usage summary / weekly report 统计骨架

## 3.3 统一后端的隔离原则
所有以下实体都必须显式带 `app_code` 或由 appCode 推导：
- 用户身份
- session
- provider identity
- purchase / restore / transaction
- entitlement / membership projection
- remote config
- deletion ticket
- usage event / sync 数据

**禁止假设**：
- “一个 Apple 用户天然等于同一个系统用户”
- “同一 bundle 的 entitlement 可以跨 App 复用”
- “后续多个 App 可共用一份 app-level 配置”

---

## 4. P0：当前必须继续完成的任务（提审前必做）

以下仍然是当前最重要的首批任务，且优先级高于目录改名、包名美化、schema 美化。

### P0-1. 补齐并注入 `release_ios` 正式发布配置
必须补齐：
- `paipai_readingcompanion.release_ios.development_team`
- `paipai_readingcompanion.release_ios.marketing_version`
- `paipai_readingcompanion.release_ios.current_project_version`
- `paipai_readingcompanion.release_ios.paipai_api_base_url`

> 注意：namespace 仍叫 `release_ios`，但 appCode 已经切换为 `paipai_readingcompanion`，旧的 `reading.release_ios.*` 应视为待清理历史口径。

要求：
- 产物中不能残留：
  - `__FILL_FROM_DB_release_ios.*`
  - `http://127.0.0.1:8080`
- `PAIPAI_API_BASE_URL` 必须为 HTTPS 正式地址

### P0-2. 统一 Apple 身份三件套
必须统一以下三项：
- iOS `PRODUCT_BUNDLE_IDENTIFIER`
- `app.auth.apple.clientId`
- `app.billing.appstore.bundleId`

推荐统一为：
- `com.paipai.readalong`

当前原则：
- 一个正式 App，一套正式 bundle/client identity
- 不要长期保留 `.v2` 作为外部 App 身份

### P0-3. 补齐 Apple Sign in 生产配置
必须补齐：
- `teamId`
- `keyId`
- `privateKey`
- `redirectUri`
- `remoteExchangeEnabled=true`

否则：
- iOS 虽有 Apple 登录入口
- backend 仍不能稳定完成正式 authorization-code exchange 并签发 formal session

### P0-4. 完成 Apple refresh token 存储安全收口
必须完成：
- 配置 `APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY`
- 清理 plaintext fallback
- 明确老 token 的迁移/失效策略

### P0-5. 补齐 App Store Server API 正式配置
必须补齐：
- `appAppleId`
- `issuerId`
- `keyId`
- `privateKey`
- 生产环境 `allowSandbox=false`

### P0-6. 跑真实 sandbox / TestFlight 验证
必须实测：
- Apple 登录
- purchase
- restore
- server notification
- delete account

当前静态代码虽已接线，但仍**不能把“代码已接线”当成“已可提审”**。

### P0-7. 补 release gate 覆盖缺口
至少要新增自动检查：
- iOS `PRODUCT_BUNDLE_IDENTIFIER` vs backend `clientId` 一致
- iOS `PRODUCT_BUNDLE_IDENTIFIER` vs backend `bundleId` 一致
- 产物中不含 `__FILL_FROM_DB_release_ios.*`
- 产物中不含本地/明文占位 base URL
- `remoteExchangeEnabled=true`
- production 下 `allowSandbox=false`

---

## 5. P1：首版上线后尽快补齐的任务

### P1-1. `SubscriptionStatusView.verificationReadiness` 改成真实 readiness
当前风险：
- 这是前端/运维易误判点
- 会把“代码结构就位”误看成“Apple 生产验证已 ready”

### P1-2. entitlement 映射改成显式配置
避免长期依赖：
- `product_id_fallback`

要求：
- productId -> entitlementCode / planCode 必须显式配置
- 失败时要有清晰告警，而不是静默 fallback

### P1-3. 云端 OCR / TTS 首版口径收口
二选一：
- **方案 A**：首版不承诺云端 OCR/TTS，只保留设备端优先口径
- **方案 B**：接入真实 provider，并完成配额/错误/降级验证

### P1-4. 公告 / 周报 / usage summary 真数据回归验证
重点看：
- 公告时间窗
- scene / locale / version / plan 过滤
- weekly report 是否按真实 usage 统计

---

## 6. P2：多 App 平台化完善任务

### P2-1. 抽出统一 app 接入 checklist
每新增一个 App 时，最少要复制/新建：
- app-definition
- app-specific auth/billing config
- remote config seed
- entitlement/product mapping
- release gate registration
- readiness gate registration
- app-specific compat/service layer

### P2-2. 为 appCode 做统一常量/枚举管理
避免未来再出现：
- 多处手写字符串
- mapper / service / gate / seed 不一致

建议新增：
- `AppCodes` 常量类
- reading domain 内引用统一常量，而不是重复写字符串

### P2-3. PowerSync 多 app 隔离校验
需要确认：
- bucket / scope / sync rule 是否显式带 appCode 维度
- 不允许 reading companion 数据串到未来其他 app

### P2-4. 加 app-level preflight
新增统一检查：
- appCode 是否注册
- definition 是否存在
- remote config seeds 是否完整
- auth / billing 必要字段是否齐全
- release config namespace 是否齐全

---

## 7. P3：内部实现名整理（非当前 release blocker）

这些可以做，但不建议和当前 P0 混在同一波：

### P3-1. 目录/资源名重构
可选改造：
- `apps/reading/` -> `apps/paipai_readingcompanion/`
- `V3__reading_*` / `V6__reading_*` 这类文件名整理

### P3-2. Java package 重构
可选改造：
- `com.apphub.backend.apps.reading...`
- 改为更贴近产品命名的 package

### P3-3. 表名前缀改造（慎做）
只有在以下条件全部满足时才建议推进：
- 已经稳定上线一段时间
- 有足够 migration / rollback / data verification 经验
- 对 PowerSync / BI / mapper / FK / SQL 影响已经全面梳理

否则建议长期保留 `reading_` 作为内部业务域前缀即可。

---

## 8. 推荐执行顺序（非常重要）

### 第一阶段：身份收口（已开始）
1. appCode 切到 `paipai_readingcompanion`
2. 迁移 DB 中 app_code 数据
3. 修正 release gate / mapper / APP_CODE 常量

### 第二阶段：Apple / App Store 正式环境收口（当前最关键）
4. 统一 bundleId / clientId / appstore.bundleId
5. 补齐 Apple Sign in 配置并开启正式 exchange
6. 补齐 App Store Server API 凭证
7. 关闭 production sandbox 容忍
8. 补 token encryption key 与 plaintext fallback 清理

### 第三阶段：发布门禁自动化
9. 扩 release gate
10. 增加 app-level readiness 自动校验
11. 增加产物占位符扫描与 bundle/client 一致性校验

### 第四阶段：真实验证
12. sandbox 购买/恢复/通知
13. TestFlight 真机验证
14. 删除账号闭环验证
15. 登录态 / formal session / entitlement 实测

### 第五阶段：平台化增强
16. appCode 常量中心化
17. app preflight 标准化
18. PowerSync 多 app 隔离补强
19. 新 app onboarding checklist

---

## 9. 建议新增的具体落地项（文件级）

### 9.1 release gate 扩展
建议改造点：
- `src/main/java/com/apphub/backend/sys/app/controller/SystemController.java`
- `addReadingIosReleaseConfigChecks(...)`

建议新增检查：
- bundleId / clientId / appstore.bundleId 一致性
- release_ios 配置完整性
- placeholder 残留扫描
- HTTPS base URL 校验
- Apple 生产关键开关校验

### 9.2 app-level readiness 扩展
建议改造点：
- `src/main/java/com/apphub/backend/sys/app/service/AppAppleReadinessService.java`
- `src/main/java/com/apphub/backend/sys/app/model/AppAppleReadinessView.java`

建议补充：
- Sign in with Apple formal session readiness
- App Store Server API readiness
- credential encryption readiness
- production sandbox risk readiness

### 9.3 appCode 常量中心化
建议新增：
- `src/main/java/com/apphub/backend/sys/app/model/AppCodes.java` 或等价常量类

至少包含：
- `PAIPAI_READINGCOMPANION = "paipai_readingcompanion"`
- `SAVING = "saving"`

后续 reading 业务层改为引用常量，而非散落硬编码字符串。

---

## 10. 风险提醒

### 10.1 当前已改 appCode，但内部“reading”实现名仍大量存在
这不是 bug，但容易让后续维护者误会：
- reading 有时代表“旧 appCode”
- 有时只是“业务域名字”

因此必须统一认知：
- **appCode 已定为 `paipai_readingcompanion`**
- `reading` 在当前阶段只应被理解为内部 domain 名

### 10.2 没有 mvn / Xcode 的环境不能声称验收通过
当前环境复查结果：
- `mvn` 不存在
- `backend/mvnw` 不存在
- `swiftc` 不存在
- `xcodebuild` 不存在
- `paipaiV2/ios` 下未发现 `*.xcodeproj`、`*.xcworkspace` 或 `Package.swift`

因此当前仍不能声称：
- backend tests passed
- iOS compile passed
- archive passed
- TestFlight passed

### 10.3 不能把 release gate ready 当成 Apple 提审 ready
必须继续坚持两层判断：
- system release gate
- app-level Apple readiness

---

## 11. 下一步建议（可直接执行）

如果下一轮继续推进，建议按这个顺序开工：

1. **先补 release gate 覆盖缺口**
   - 让 gate 自动检查 bundle/client/billing 一致性
   - 让 gate 自动检查 placeholder 与 production sandbox 风险

2. **再统一 Apple 三件套**
   - `PRODUCT_BUNDLE_IDENTIFIER`
   - `clientId`
   - `appstore.bundleId`

3. **再补 Apple/Server API 正式凭证**
   - sign in
   - app store server api
   - encryption key

4. **最后做真实 sandbox / TestFlight 验证**
   - 登录
   - 购买
   - 恢复
   - 通知
   - 删除账号

---

## 12. 当前文档用途

这份文档用于：
- 作为 `paipai_readingcompanion` 的正式 appCode 决策记录
- 作为当前 backend 收口与后续平台化改造的执行路线图
- 明确区分：
  - 已完成的 appCode 运行时切换
  - 当前提审 blocker
  - 后续平台化增强任务
  - 非 blocker 的内部实现名整理任务
