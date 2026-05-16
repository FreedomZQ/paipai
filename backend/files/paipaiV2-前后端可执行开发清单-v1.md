# Paipai V2 × Unified Backend 可执行开发清单（前端文件级 + 后端文件级 + 表结构级）

> ⚠️ 历史说明（2026-04-22）：本文件包含早期设计/开发阶段的旧方案记录。当前 reading 已收口为 **Apple 登录唯一正式入口**；删除账号仍保留**临时输入邮箱验证码确认**，但邮箱不再作为长期登录方式，demo session 也不再是现行登录方案。

> 目标：把 `/home/admin/code/app/paipaiV2` 从“UI/原型壳”推进到“可联调、可提审的首版”，并与 `/home/admin/code/app/backend` 的 unified backend reading 域对齐。
>
> 原则：
> 1. **保留统一后端目录结构不变**；
> 2. **前端优先复用旧版 `/home/admin/code/app/paipai/ios/PaipaiReadAlong` 的业务层**，避免在 V2 里从零重写；
> 3. **离线主功能可用**（本地 OCR / 分句 / 翻译 / 朗读 / 本地复习）；
> 4. **重要权益必须后端权威**（登录、支付、恢复购买、孩子数量、云端次数、删除账号、动态价格/公告/语言）；
> 5. **本版不做历史数据迁移**，只做首版上线所需的结构补齐。

---

# 一、当前判断（一句话）

当前最优路线不是继续在 `paipaiV2` 里零散补代码，而是：

**“V2 页面层 + 迁移旧版 paipai 业务服务层 + unified backend 补 5 类关键缺口（设备审计 / 邮箱验证码 / 偏好与语言 / 使用时长 / 公告规则）”。**

---

# 二、实施优先级总览

## P0（必须先做，否则无法形成真联调版本）

1. **把 V2 从 mock/UI 壳改成可编译、可联调前端**
2. **迁移旧版 paipai 已有业务服务层到 V2**
3. **接通 Apple 登录 / Session / Paywall / Purchase / Restore / Announcements / Review / Children / Deletion**
4. **后端补齐设备审计、邮箱验证码、用户偏好、孩子时长、公告规则表结构与接口**
5. **统一 release 配置口径：从“旧 MySQL remote_config_item”切到 unified backend `sys_remote_config`**

## P1（P0 完成后立即做，决定是否接近提审）

1. 家长区改成 **设备密码 / 生物识别优先**
2. 删除账号改成 **邮箱验证码二次确认**
3. 价格、权益、语言种类、公告展示规则改成 **后台动态化**
4. 把 V2 的隐私申报、Info.plist、Privacy Manifest 与真实能力对齐
5. 接入周报、家庭范围、多孩子 usage summary 真数据

## P2（可在首版联调完成后继续打磨）

1. 运营后台接口/脚本
2. 通知场景细化（按版本/国家/权益/语言定向）
3. usage 明细与聚合优化
4. 更细的风控与审计指标

---

# 三、P0 可执行开发清单

---

## P0-1：先让 `paipaiV2` 变成“可编译、可联调”的真前端

## 目标
解决 V2 当前直接引用缺失类、页面大量 mock/TODO 的问题，先形成一版能跑主链路的工程。

## 需要处理的前端文件

### 1. `/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/App/PaipaiReadAlongV2App.swift`

#### 当前问题
- 引用了不存在的：
  - `BackendClient`
  - `TranslationService`
  - `TTSService`
- `AnnouncementManager` 仍是 mock 公告。

#### 开发动作
- 新建或迁移真实服务后，把这些服务注入到 `AppState`。
- 增加统一的 `AppEnvironment` / `SessionStore` / `BootstrapState`。
- App 启动顺序改为：
  1. 读取本地 session
  2. 拉 `bootstrap/config`
  3. 若已有正式 session，则校验 `auth/me`
  4. 未登录时保持未认证态，等待用户通过 Apple 登录建立正式会话
  5. 登录后再拉 `account/me/state` / `announcements`

#### 验收
- 工程可编译
- 首次启动不再因缺失类失败
- 启动链路可跑到首页

---

### 2. 新增文件：`/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Core/Services/BackendClient.swift`

#### 来源
优先迁移并裁剪：
- `/home/admin/code/app/paipai/ios/PaipaiReadAlong/Core/Services/BackendClient.swift`

#### 要保留/接入的方法
- `fetchBootstrap()`
- `fetchPlans()`
- `fetchLegalDocs()`
- `fetchAnnouncements()`
- `fetchAuthMe()`
- `exchangeApplePreview()`
- `logout()`
- `fetchAccountState()`
- `fetchHomeSummary()`
- `fetchChildren()`
- `createChild()`
- `updateChild()`
- `fetchTodayReviewCards()`
- `createReviewCard()`
- `recordReviewEvent()`
- `fetchCurrentWeeklyReport()`
- `fetchWeeklyReportHistory()`
- `fetchSubscriptionStatus()`
- `refreshEntitlementSnapshot()`
- `submitTransactionIntake()`
- `requestAccountDeletion()`
- `submitFeedback()`
- `extractOcrText()`
- `synthesizeCloudSpeech()`

#### 需要额外新增的方法
- `requestDeletionCode()`
- `confirmDeletionByCode()`
- `reportDeviceEvent()`
- `fetchUserPreferences()`
- `updateUserPreferences()`
- `startUsageSession()`
- `endUsageSession()`
- `fetchChildUsageSummary(childId:)`

#### 验收
- 所有主业务调用统一走此客户端
- 不再在各页面散落 URLSession 调用

---

### 3. 新增文件：`/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Core/Services/TranslationService.swift`

#### 当前问题
文件不存在，但 AppState 已引用。

#### 开发动作
- 从 V2 当前设计出发，落成统一翻译服务：
  - `device translation` 为默认
  - `cloud translation` 先不做成付费主线，保持本地优先
- 提供接口：
  - `translate(text:source:target:)`
  - `checkLanguagePackAvailability()`
  - `downloadLanguagePackIfNeeded()`
- 记录用户当前源语言/目标语言选择，用于 preferences 同步。

#### 验收
- `LearningDetailView` 不再引用缺失的 `UnifiedTranslationService`
- 本地翻译可用，失败有明确提示

---

### 4. 新增文件：`/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Core/Services/TTSService.swift`

#### 当前问题
文件不存在，但 AppState 已引用。

#### 开发动作
- 提供统一 TTS 策略：
  - 默认设备朗读（AVSpeechSynthesizer）
  - 用户主动切换时走 backend `/api/v1/tts/speak`
- 方法建议：
  - `speak(_:language:rate:mode:)`
  - `stop()`
  - `playCloudAudio(base64:mimeType:)`
- 接入语言偏好和 voice 选择。

#### 验收
- Review / Learning 页面朗读链路可用
- 云端 TTS 成功时可播放返回音频

---

### 5. 新增文件：`/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Core/Services/SecureSessionStore.swift`

#### 来源
优先迁移：
- `/home/admin/code/app/paipai/ios/PaipaiReadAlong/Core/Services/SecureSessionStore.swift`

#### 开发动作
- Keychain 保存 `StoredAuthSession`
- 支持 Apple 正式会话持久化与恢复
- 支持 logout / delete account 后清理

#### 验收
- 重启后 session 仍可恢复
- auth me 失败时可自动失效本地 session

---

### 6. 新增文件：`/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Core/Services/AppStorePurchaseService.swift`

#### 来源
优先迁移：
- `/home/admin/code/app/paipai/ios/PaipaiReadAlong/Core/Services/AppStorePurchaseService.swift`

#### 开发动作
- 接入 StoreKit 2
- 购买成功后：
  1. 提交 purchase intake
  2. 刷新 entitlement
  3. 刷新 account state / home summary
- 恢复购买成功后：
  1. 提交 restore intake
  2. 刷新 entitlement
  3. 刷新 account state / home summary

#### 验收
- Paywall 不再是假 loading
- 购买/恢复购买走真实闭环

---

### 7. 新增文件：`/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Core/Services/AppleSignInFlow.swift`

#### 来源
优先迁移：
- `/home/admin/code/app/paipai/ios/PaipaiReadAlong/Core/Services/AppleSignInFlow.swift`

#### 开发动作
- 保留 nonce/state 生成逻辑
- 接 `SignInWithAppleButton`
- 登录成功后调用 `/api/v1/system/auth/apps/{appCode}/apple/exchange`
- formal session 成功后落 Keychain

#### 验收
- Apple 登录可从家长区成功发起
- 登录后账号态切换为 formal account

---

### 8. `/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Features/Home/HomeView.swift`

#### 当前问题
- 全部是 mock data

#### 开发动作
- 接入真实：
  - `accountState`
  - `homeSummary`
- UI 字段映射：
  - membershipStatus ← entitlement.planName
  - OCR/TTS 已用/总量 ← quota / cloud usage / local usage 策略
  - reviewDueCount / todayCompleted / savedCount ← homeSummary
  - streak / weeklyActive / weeklyReview ← homeSummary.growth
  - recentCards ← homeSummary.recentCards
- 增加 loading / empty / error 态

#### 依赖接口
- `GET /api/v1/account/me/state`
- `GET /api/v1/account/me/home-summary`

#### 验收
- 首页不再有写死数字
- 免费/会员展示以后端返回为准

---

### 9. `/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Features/Capture/CaptureView.swift`

#### 当前问题
- “设备识别/云端识别”切换只做了 UI，实际 OCR 仍走本地

#### 开发动作
- `RecognitionMode.device`：继续走本地 `OCRService`
- `RecognitionMode.cloud`：改走 `BackendClient.extractOcrText()`
- 云端识别失败时要支持：
  - quota exhausted 提示
  - provider unavailable 提示
  - 回退手动编辑
- 保留“尽量只拍一句”的 UX 约束

#### 依赖接口
- `POST /api/v1/ocr/extract`

#### 验收
- 云端模式真实可用
- 无 session / quota 耗尽时提示正确

---

### 10. `/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Features/Learning/LearningDetailView.swift`

#### 当前问题
- 使用不存在的 `UnifiedTranslationService`
- `saveCard()` 只是本地占位

#### 开发动作
- 改用真实 `translationService`
- 保存句卡改为调用 `createReviewCard()`
- 保存成功后刷新：
  - home summary
  - review due count
- 若用户选择云端朗读，则调用 backend `/api/v1/tts/speak`

#### 依赖接口
- `POST /api/v1/review-cards`
- `POST /api/v1/tts/speak`

#### 验收
- 保存句卡后能出现在 review 列表/首页最近保存

---

### 11. `/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Features/Review/ReviewView.swift`

#### 当前问题
- 句卡列表是假数据
- `recordResult()` 仅切卡，不落后端

#### 开发动作
- 初始加载改成 `fetchTodayReviewCards()`
- 每次掌握度按钮点击时调用 `recordReviewEvent()`
- 完成后刷新首页/周报

#### 依赖接口
- `GET /api/v1/review-cards/today`
- `POST /api/v1/review-events`

#### 验收
- 复习结果能影响下一次排程和周报统计

---

### 12. `/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Features/Parent/ParentAreaView.swift`

#### 当前问题
- 家长门只是数学题
- children / usage 都是假数据

#### 开发动作
- gate 改成：
  - 第一优先：`LocalAuthentication.LAContext` 的 `deviceOwnerAuthentication`
  - 失败/不可用时：退回数学题作为 fallback，不再是主 gate
- children 改成 `fetchChildren()`
- usage 改成 `fetchChildUsageSummary()` / `family summary`
- 增加入口：
  - Apple 登录
  - Apple 登录
  - 删除账号
  - 语言偏好设置
  - 通知/公告中心

#### 依赖接口
- `GET /api/v1/children`
- `GET /api/v1/usage/children/{childId}/summary`
- `GET /api/v1/preferences/me`

#### 验收
- 家长区敏感操作不再靠纯数学题
- 使用时长可真实展示

---

### 13. `/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Features/Paywall/PaywallView.swift`

#### 当前问题
- 使用本地默认 plan
- purchase/restore 是假实现

#### 开发动作
- 拉真实 `plans`
- 价格显示以后端 catalog 为准
- 购买接 `AppStorePurchaseService.purchase()`
- 恢复购买接 `AppStorePurchaseService.restore()`
- 成功后刷新 subscription/account state
- 商品不可售时禁用按钮，不本地造价格

#### 依赖接口
- `GET /api/v1/plans`
- `GET /api/v1/subscriptions/status`
- `POST /api/v1/subscriptions/app-store/purchases/intake`
- `POST /api/v1/subscriptions/app-store/restores/intake`
- `POST /api/v1/subscriptions/entitlements/refresh`

#### 验收
- Paywall 真正成为后端权威入口

---

### 14. `/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Features/Components/AnnouncementView.swift`

#### 当前问题
- 当前显示 mock announcement
- 展示次数/间隔只靠本地默认规则

#### 开发动作
- `AnnouncementManager` 改成：
  - 拉后端 announcements
  - 基于后端规则 + 本地缓存做展示决策
- 增加本地存储：
  - 已展示次数
  - 最后展示时间
  - 已永久关闭的 announcementUuid

#### 依赖接口
- `GET /api/v1/announcements?windowDays=30`

#### 验收
- 公告不再是 mock
- 同一条公告能控制展示次数/间隔/关闭状态

---

### 15. `/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Features/Onboarding/OnboardingView.swift`

#### 当前问题
- 隐私同意文案有，但“查看完整隐私政策”没接真实链接
- 没把同意结果上报为设备事件

#### 开发动作
- 从 `legal/docs` 获取 privacy/terms/child-data 链接
- 首次同意后上报 `device_event`
- 记录 `locale/sourceLanguage/targetLanguage`

#### 依赖接口
- `GET /api/v1/legal/docs`
- `POST /api/v1/account/device-event`
- `PATCH /api/v1/preferences/me`

#### 验收
- 首启隐私链路完整
- 审核时能打开真实文档

---

## P0-2：迁移旧版 paipai 的业务逻辑文件（建议原样迁移后再裁剪）

## 建议优先迁移来源文件

从 `/home/admin/code/app/paipai/ios/PaipaiReadAlong` 迁移：

### 核心服务
- `Core/Services/BackendClient.swift`
- `Core/Services/AppStorePurchaseService.swift`
- `Core/Services/AppleSignInFlow.swift`
- `Core/Services/SecureSessionStore.swift`
- `Core/Services/AnnouncementStore.swift`
- `Core/Services/ParentGatePolicy.swift`
- `Core/Services/ServerTimeFormatter.swift`

### 关键页面逻辑参考
- `Features/Parent/ParentAreaView.swift`
- `Features/Parent/PaywallView.swift`
- `Features/Parent/SupportAndPrivacyView.swift`
- `Features/Parent/AnnouncementHistoryView.swift`
- `Features/Parent/LegalDocsView.swift`
- `Features/Parent/ManageChildrenView.swift`
- `Features/Parent/WeeklyReportView.swift`
- `Features/Review/ReviewTodayView.swift`

### 关键模型参考
- `Core/Models/AppBootstrap.swift`
- `Core/Models/AuthSession.swift`
- `Core/Models/AccountState.swift`
- `Core/Models/HomeSummary.swift`
- `Core/Models/Plan.swift`
- `Core/Models/AppAnnouncement.swift`
- `Core/Models/SubscriptionStatus.swift`
- `Core/Models/WeeklyParentReport.swift`
- `Core/Models/AccountDeletionStatus.swift`

---

## P0-3：unified backend 补关键能力（表结构 + 文件级）

---

## 目标
补齐 V2 首版真正缺的 5 类后端能力：

1. 设备审计
2. Apple 登录 / 删除二次验证
3. 用户偏好与语种选择
4. 分孩子使用时长
5. 公告规则增强

---

## 表结构级清单（新增）

### 1. 新增表：`sys_user_device_event`

## 用途
记录创建账号、登录、跳过登录、首次同意隐私时的设备信息与应用版本信息。

## 建议字段
- `id BIGSERIAL PK`
- `app_code VARCHAR(64)`
- `user_id BIGINT NULL`
- `session_id BIGINT NULL`
- `event_type VARCHAR(64)`  
  - `app_launch`
  - `privacy_accepted`
  - `demo_session_created`
  - `apple_login_succeeded`
  - `email_login_succeeded`
  - `skip_login`
- `bundle_id VARCHAR(128)`
- `client_platform VARCHAR(32)`  
  - `iphone` / `ipad` / `mac`
- `device_model VARCHAR(128)`
- `system_name VARCHAR(64)`
- `system_version VARCHAR(64)`
- `app_version VARCHAR(64)`
- `build_number VARCHAR(64)`
- `locale VARCHAR(32)`
- `ip_country VARCHAR(32) NULL`
- `payload_json JSONB NULL`
- `created_at TIMESTAMPTZ`

## 索引
- `(app_code, user_id, created_at desc)`
- `(app_code, event_type, created_at desc)`

---

### 2. 新增表：`sys_email_verification_ticket`

## 用途
同时支撑：
- Apple 登录
- 删除账号邮箱验证码确认

## 建议字段
- `id BIGSERIAL PK`
- `app_code VARCHAR(64)`
- `email VARCHAR(256)`
- `scene_code VARCHAR(64)`  
  - `login`
  - `delete_account`
- `code_hash VARCHAR(128)`
- `status VARCHAR(32)`  
  - `pending` / `verified` / `expired` / `consumed` / `cancelled`
- `attempt_count INT`
- `max_attempt_count INT`
- `expires_at TIMESTAMPTZ`
- `verified_at TIMESTAMPTZ NULL`
- `consumed_at TIMESTAMPTZ NULL`
- `request_ip VARCHAR(64) NULL`
- `payload_json JSONB NULL`
- `created_at TIMESTAMPTZ`
- `updated_at TIMESTAMPTZ`

## 索引
- `(app_code, email, scene_code, created_at desc)`
- `(app_code, status, expires_at)`

---

### 3. 新增表：`reading_user_preference`

## 用途
记录用户当前选择的语言/语种/语音等偏好，满足“整体页面动态支持最开始选择的语种展示，数据库也有记录”。

## 建议字段
- `user_id BIGINT PK`
- `app_code VARCHAR(64) default 'reading'`
- `ui_locale VARCHAR(32)`
- `source_language_code VARCHAR(32)`
- `target_language_code VARCHAR(32)`
- `reading_track_code VARCHAR(64)`
- `tts_voice_code VARCHAR(64) NULL`
- `translation_mode VARCHAR(32)`
- `updated_at TIMESTAMPTZ`
- `created_at TIMESTAMPTZ`

---

### 4. 新增表：`reading_usage_session`

## 用途
记录孩子维度的使用 session，供每日/总时长聚合。

## 建议字段
- `id BIGSERIAL PK`
- `app_code VARCHAR(64) default 'reading'`
- `user_id BIGINT`
- `child_id VARCHAR(64)`
- `session_uuid VARCHAR(64) UNIQUE`
- `started_at TIMESTAMPTZ`
- `ended_at TIMESTAMPTZ NULL`
- `duration_seconds INT DEFAULT 0`
- `client_platform VARCHAR(32)`
- `device_model VARCHAR(128) NULL`
- `source_page VARCHAR(64) NULL`
- `created_at TIMESTAMPTZ`
- `updated_at TIMESTAMPTZ`

## 索引
- `(user_id, child_id, started_at desc)`
- `(child_id, started_at desc)`

---

### 5. 新增表：`reading_child_usage_daily`

## 用途
按孩子做日汇总，用于家长区展示“当日时长 / 累计总时长”。

## 建议字段
- `id BIGSERIAL PK`
- `app_code VARCHAR(64) default 'reading'`
- `user_id BIGINT`
- `child_id VARCHAR(64)`
- `usage_date DATE`
- `duration_seconds INT`
- `session_count INT`
- `created_at TIMESTAMPTZ`
- `updated_at TIMESTAMPTZ`
- `UNIQUE(user_id, child_id, usage_date)`

---

### 6. 扩展表：`reading_announcement`

## 当前已有字段
- `announcement_uuid`
- `title`
- `content`
- `visible_start_at`
- `visible_end_at`

## 建议新增字段
- `announcement_type VARCHAR(32)`
- `priority INT DEFAULT 0`
- `action_url VARCHAR(512) NULL`
- `action_text VARCHAR(128) NULL`
- `dismissible BOOLEAN DEFAULT TRUE`
- `max_display_count INT DEFAULT 1`
- `min_interval_seconds INT DEFAULT 86400`
- `trigger_scene VARCHAR(64) DEFAULT 'app_launch'`
- `target_locale VARCHAR(32) NULL`
- `target_plan_code VARCHAR(64) NULL`
- `target_min_app_version VARCHAR(64) NULL`
- `target_max_app_version VARCHAR(64) NULL`

---

## 后端文件级清单

### 1. 迁移文件：`/home/admin/code/app/backend/src/main/resources/db/migration/V2__reading_device_email_usage_preferences.sql`

## 内容
- 创建：
  - `sys_user_device_event`
  - `sys_email_verification_ticket`
  - `reading_user_preference`
  - `reading_usage_session`
  - `reading_child_usage_daily`
- 扩展 `reading_announcement`

## 验收
- `mvn -q clean verify` 通过
- Flyway 可正常执行

---

### 2. 新增实体

放到：`/home/admin/code/app/backend/src/main/java/com/apphub/backend/apps/reading/domain/entity/` 或 `sys/auth/entity/`

#### 新增
- `SysUserDeviceEventEntity.java`
- `SysEmailVerificationTicketEntity.java`
- `ReadingUserPreferenceEntity.java`
- `ReadingUsageSessionEntity.java`
- `ReadingChildUsageDailyEntity.java`

#### 如使用 reading 域
- `ReadingAnnouncementEntity.java` 增加新字段

---

### 3. 新增 mapper

#### `sys/auth/mapper/`
- `SysUserDeviceEventMapper.java`
- `SysEmailVerificationTicketMapper.java`

#### `apps/reading/domain/mapper/`
- `ReadingUserPreferenceMapper.java`
- `ReadingUsageSessionMapper.java`
- `ReadingChildUsageDailyMapper.java`
- `ReadingAnnouncementMapper.java`（扩展查询条件和字段）

---

### 4. 新增/扩展 service

#### 扩展：`/home/admin/code/app/backend/src/main/java/com/apphub/backend/apps/reading/compat/service/ReadingCompatService.java`

## 要增加的方法
- `preferences(user)`
- `updatePreferences(user, request)`
- `reportDeviceEvent(userOrNull, request)`
- `startUsageSession(user, request)`
- `endUsageSession(user, request)`
- `childUsageSummary(user, childId)`
- `requestDeletionCode(user, request)`
- `confirmDeletionByCode(user, request)`

#### 新增：`/home/admin/code/app/backend/src/main/java/com/apphub/backend/sys/auth/service/SysEmailVerificationService.java`

## 要负责
- 生成验证码
- 哈希存储
- 校验有效期/次数
- 消费 ticket
- 抽象邮件发送口（先预留 provider，后续接 SES/Resend/企业邮箱 SMTP）

#### 新增：`/home/admin/code/app/backend/src/main/java/com/apphub/backend/apps/reading/announcement/service/ReadingAnnouncementRuleService.java`

## 要负责
- 按 locale / plan / appVersion / scene 过滤公告
- 返回前端所需 display rule 字段

---

### 5. 新增 controller

#### A. `apps/reading/auth/controller/ReadingEmailAuthCompatController.java`

## 路由
- `POST /api/v1/auth/email/request-code`
- `POST /api/v1/auth/email/verify-code`

## 说明
- 仅支持家长通过 Apple 登录建立正式账号
- 成功后签发正式 session

---

#### B. `apps/reading/compat/controller/ReadingPreferenceCompatController.java`

## 路由
- `GET /api/v1/preferences/me`
- `PATCH /api/v1/preferences/me`

## 用途
- 保存 UI 语种 / 源语 / 目标语 / learning track / voice

---

#### C. `apps/reading/compat/controller/ReadingUsageCompatController.java`

## 路由
- `POST /api/v1/usage/session/start`
- `POST /api/v1/usage/session/end`
- `GET /api/v1/usage/children/{childId}/summary`
- `GET /api/v1/usage/family/summary`

## 用途
- 家长区展示孩子当日使用时长、总时长

---

#### D. `apps/reading/compat/controller/ReadingDeviceCompatController.java`

## 路由
- `POST /api/v1/account/device-event`

## 用途
- 上报 app 启动 / 登录 / 隐私同意 / skip login 的设备环境信息

---

#### E. 扩展 `ReadingAccountCompatController.java`

## 新增路由
- `POST /api/v1/account/deletion/request-code`
- `POST /api/v1/account/deletion/confirm`

## 说明
- 旧的 `/deletion-requests` 可保留兼容
- 新主链路改为：
  1. request-code
  2. confirm(code)
  3. 执行 deletion

---

### 6. 扩展 `ReadingAnnouncementCompatController.java`

## 当前
- `GET /api/v1/announcements?windowDays=30`

## 扩展建议
支持 query：
- `scene=app_launch`
- `locale=zh-Hans`
- `appVersion=1.0.0`
- `planCode=free`

## 返回字段增加
- `type`
- `priority`
- `actionUrl`
- `actionText`
- `dismissible`
- `maxDisplayCount`
- `minIntervalSeconds`
- `triggerScene`

---

## P0-4：统一 release 配置口径

### 当前问题
`/home/admin/code/app/paipaiV2/ios/project.yml` 注释写的是：
- MySQL `remote_config_item`
- `release_ios.*`

但 unified backend 现在真实配置中心是：
- `sys_remote_config`

### 需要修改的文件

#### 1. `/home/admin/code/app/paipaiV2/ios/project.yml`

## 修改动作
- 把注释中的 `backend MySQL remote_config_item` 改成 `sys_remote_config`
- release 配置统一由：
  - namespace：`release_ios`
  - key：
    - `development_team`
    - `marketing_version`
    - `current_project_version`
    - `paipai_api_base_url`

#### 2. backend migration seed / ops docs

需要新增 `release_ios` namespace 的初始 seed 和说明文档。

### 建议新增 backend seed
在 migration 或独立 SQL 中加入：
- `reading / release_ios / development_team`
- `reading / release_ios / marketing_version`
- `reading / release_ios / current_project_version`
- `reading / release_ios / paipai_api_base_url`

---

# 四、P1 可执行开发清单

---

## P1-1：家长区改为设备密码/生物识别优先

## 前端文件
- `/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Features/Parent/ParentAreaView.swift`
- 新增：`Core/Services/ParentGateService.swift`

## 开发动作
- 使用 `LocalAuthentication.LAContext.evaluatePolicy(.deviceOwnerAuthentication, ...)`
- 支持：
  - Face ID / Touch ID
  - 设备密码 fallback
- 失败时才进入备用数学题 gate

## 验收
- 进入家长区优先走系统级设备验证
- 无生物识别时可走设备密码

---

## P1-2：删除账号改为邮箱验证码确认

## 后端接口
- `POST /api/v1/account/deletion/request-code`
- `POST /api/v1/account/deletion/confirm`

## 前端文件
- 新增：`Features/Parent/DeleteAccountView.swift`
- `Features/Parent/ParentAreaView.swift`
- `Features/Components/VerificationCodeInputView.swift`

## 开发动作
- 删除账号前需临时输入邮箱并发送验证码；邮箱不作为长期登录方式保存
- Apple private relay 邮箱同样可发送验证码
- 如果用户没有 email（极少数）才允许走高风险 fallback 流程

## 验收
- 删除账号不是一键误触
- 审核时链路可演示

---

## P1-3：价格/权益/语言种类后台动态化

## 后端
- 利用 `sys_remote_config` + `ReadingCompatService.plans()` 动态读取目录
- 新 namespace 建议：
  - `reading_plan_catalog`
  - `reading_language_catalog`
  - `reading_feature_limits`

## 前端文件
- `Features/Paywall/PaywallView.swift`
- `Core/Models/SubscriptionModels.swift`
- `Features/Onboarding/OnboardingView.swift`
- `Features/Parent/LanguagePreferenceView.swift`（新增）

## 开发动作
- `plans()` 不再只返回单一硬编码 lifetime 包
- 支持后台配置：
  - plan 名称
  - 展示价/原价
  - childLimit
  - historyEnabled
  - cloudSyncEnabled
  - 支持语言列表

---

## P1-4：usage 真正做到“按孩子展示日/总时长”

## 前端
- `ParentAreaView.swift`
- `ManageChildrenView.swift`（新增/迁移）

## 后端
- `ReadingUsageCompatController.java`
- `ReadingCompatService.java`
- `ReadingUsageSessionMapper.java`
- `ReadingChildUsageDailyMapper.java`

## 开发动作
- 学习页/复习页/拍读页在 child context 下启动 usage session
- 退出/切 child / 退后台时结束 session
- 家长区展示：
  - 今日时长
  - 累计总时长
  - 最近 7 天

---

## P1-5：隐私与审核口径收口

## 需要处理文件
- `/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Resources/Info.plist`
- `/home/admin/code/app/paipaiV2/ios/PaipaiReadAlongV2/Resources/PrivacyInfo.xcprivacy`
- `files/privacy-policy.html`
- `files/terms-of-service.html`

## 当前问题
- V2 里有 `NSUserTrackingUsageDescription`，但当前并没有明确真实 tracking 实现
- Privacy manifest 声明了 `UserID / DeviceID / ProductInteraction`，需要和真实采集保持一致

## 开发动作
- 若本版不做 tracking，建议移除 `NSUserTrackingUsageDescription`
- 重新核对隐私清单与真实采集字段
- 隐私政策文案改成：
  - 哪些是本地处理
  - 哪些会上报后端
  - 设备信息为何采集
  - 孩子使用时长保存多久

---

# 五、P2 可执行开发清单

---

## P2-1：公告后台运营能力

## 后端
- 新增 system ops/内部脚本或管理接口
- 支持创建/更新/下线公告
- 支持 target 条件

## 文件建议
- `sys/configcenter` 侧增加 namespace 管理入口
- 或新增内部脚本 `scripts/paipai-announcement-upsert.sh`

---

## P2-2：邮件发送 provider 抽象

## 后端新增
- `MailSender` 接口
- `SmtpMailSender` / `ResendMailSender` / `SesMailSender`

## 目标
- 验证码发送解耦
- 低成本运维可切换供应商

---

## P2-3：更细的 observability

## 建议增加 system 指标接口或 dashboard 数据源
- email verification success rate
- deletion confirm success rate
- usage session lost rate
- purchase/restore success rate
- announcement display rate / dismiss rate

---

# 六、接口清单（给前后端联调直接用）

---

## 已有接口（优先接入）
- `GET /api/v1/bootstrap/config`
- `GET /api/v1/plans`
- `GET /api/v1/legal/docs`
- `POST /api/v1/auth/demo/session`
- `POST /api/v1/system/auth/apps/{appCode}/apple/exchange`
- `POST /api/v1/system/auth/apps/{appCode}/apple/refresh`
- `POST /api/v1/system/auth/apps/{appCode}/logout`
- `GET /api/v1/system/auth/apps/{appCode}/me`
- `GET /api/v1/account/me/state`
- `GET /api/v1/account/me/home-summary`
- `POST /api/v1/account/deletion-requests`
- `GET /api/v1/children`
- `POST /api/v1/children`
- `PATCH /api/v1/children/{childId}`
- `GET /api/v1/review-cards/today`
- `POST /api/v1/review-cards`
- `POST /api/v1/review-events`
- `GET /api/v1/reports/weekly/current`
- `GET /api/v1/reports/weekly/history`
- `GET /api/v1/announcements`
- `POST /api/v1/feedback`
- `POST /api/v1/ocr/extract`
- `POST /api/v1/tts/speak`
- `GET /api/v1/subscriptions/status`
- `POST /api/v1/subscriptions/entitlements/refresh`
- `POST /api/v1/subscriptions/app-store/purchases/intake`
- `POST /api/v1/subscriptions/app-store/restores/intake`

## 本轮建议新增接口
- `POST /api/v1/auth/email/request-code`
- `POST /api/v1/auth/email/verify-code`
- `POST /api/v1/account/deletion/request-code`
- `POST /api/v1/account/deletion/confirm`
- `POST /api/v1/account/device-event`
- `GET /api/v1/preferences/me`
- `PATCH /api/v1/preferences/me`
- `POST /api/v1/usage/session/start`
- `POST /api/v1/usage/session/end`
- `GET /api/v1/usage/children/{childId}/summary`
- `GET /api/v1/usage/family/summary`

---

# 七、推荐开发顺序（按周/阶段推进）

## 第 1 阶段：先把 V2 编译和主链路打通
1. 迁移 `BackendClient / SecureSessionStore / AppleSignInFlow / AppStorePurchaseService`
2. 改 `PaipaiReadAlongV2App.swift`
3. 改 `HomeView / CaptureView / LearningDetailView / ReviewView / PaywallView`
4. 接上已有 backend 接口

## 第 2 阶段：backend 补 5 类缺口
1. migration V2
2. entity / mapper / service / controller
3. preference / device / usage / email verification / deletion confirm
4. announcements 增强

## 第 3 阶段：家长区和审核收口
1. Parent gate → LocalAuthentication
2. Support/Privacy/Delete Account 真链路
3. Info.plist / Privacy manifest / policy 文案对齐
4. release_ios 配置接入 `sys_remote_config`

## 第 4 阶段：真机联调清零
1. Apple 登录
2. Purchase / restore / entitlement refresh
3. Delete account with email code
4. announcements rules
5. child usage summary
6. legal/support URLs

---

# 八、完成定义（DoD）

满足以下条件，才可认为“达到准上线联调版”：

1. `paipaiV2` 无缺失类、可编译
2. 首页/拍读/学习/复习/家长区/付费页不再依赖 mock 数据
3. Apple 登录可用
4. purchase / restore / entitlement refresh 可跑通
5. 多孩子上限以后端为准
6. 删除账号具备验证码二次确认
7. 家长区具备设备级验证
8. 公告支持后台动态规则
9. 孩子 usage 支持当日/累计展示
10. 语种偏好写入数据库并回显
11. 隐私、支持、条款、删除账号说明页都可访问
12. release 配置不再依赖旧口径 MySQL 注释

---

# 九、我建议你下一步直接开工的最小任务包

如果要最快推进，我建议先按下面的最小包执行：

## 前端最小包
- 迁移：
  - `BackendClient.swift`
  - `SecureSessionStore.swift`
  - `AppStorePurchaseService.swift`
  - `AppleSignInFlow.swift`
- 修改：
  - `PaipaiReadAlongV2App.swift`
  - `HomeView.swift`
  - `CaptureView.swift`
  - `LearningDetailView.swift`
  - `ReviewView.swift`
  - `ParentAreaView.swift`
  - `PaywallView.swift`
  - `AnnouncementView.swift`

## 后端最小包
- 新 migration：
  - `V2__reading_device_email_usage_preferences.sql`
- 新增：
  - `SysEmailVerificationService.java`
  - `ReadingPreferenceCompatController.java`
  - `ReadingUsageCompatController.java`
  - `ReadingDeviceCompatController.java`
- 扩展：
  - `ReadingCompatService.java`
  - `ReadingAccountCompatController.java`
  - `ReadingAnnouncementCompatController.java`
  - `ReadingAnnouncementService.java`

这个最小包做完，项目就能从“设计稿壳子”变成“真联调版首版”。


---

# 十、2026-04-20 已落地进度（持续更新）

## 已落地（代码已改）
- backend：新增设备事件、邮箱验证码、用户偏好、usage session/daily 汇总、公告增强字段的 migration 和代码骨架。
- backend：新增 preferences / device-event / usage / email auth / deletion verify 接口。
- backend：`ReadingCompatService` 已补 supportHint 存储和 recentCards 文本返回。
- backend：新增 `SysEmailVerificationServiceTest`、`ReadingPreferenceServiceTest`、`ReadingEmailAuthCompatControllerWebMvcTest`，并通过 `mvn -q test` / `mvn -q verify`。
- iOS V2：迁入 `BackendClient`、`SecureSessionStore`、`AnnouncementStore`、`AppStorePurchaseService`、`AppleSignInFlow`。
- iOS V2：补齐 `TranslationService`、`TTSService`、偏好/usage/验证码/公告等模型。
- iOS V2：`AppState` 已接启动、account/home/plans/legalDocs/children/review/preferences/usage 刷新链路。
- iOS V2：首页、拍读、学习详情、复习、家长区、付费页、公告、首启隐私同意均已改为接真实 backend 或真实服务。
- iOS V2：已补 Apple 登录入口、删除账号验证码确认入口。
- iOS V2：已补家长区设备级验证（LocalAuthentication），数学题仅保留为备用兜底。
- iOS V2：已补支付正式账号门禁、Onboarding 真隐私链接、CaptureView 跨 iOS/macOS 的 `PlatformImage` 兼容。

## 当前仍需在真机 / Xcode 环境完成的最终验证
- iOS / iPadOS / macOS 真编译通过。
- Apple 登录实机授权回调验证。
- StoreKit 真商品购买 / 恢复购买验证。
- 删除账号验证码真实邮件发送验证（当前 dev 环境为 debugCode/日志联调模式）。
- 云端 OCR / TTS 配额与升级提示真链路验证。
- App Store 提审前隐私政策、支持页、法务文档内容最终校对。
