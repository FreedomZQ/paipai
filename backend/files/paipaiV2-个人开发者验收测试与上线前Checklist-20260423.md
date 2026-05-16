# paipaiV2 个人开发者验收测试与上线前 Checklist（2026-04-23）

## 1. 文件用途

这份文件给**个人开发者**直接使用，目标不是做大团队流程管理，而是让一个人也能按顺序完成：
1. **开发执行 checklist**
2. **测试验收 checklist**
3. **上线前 gate checklist**

当前项目结论：
- **代码级匹配基本完成**
- **后端关键回归已通过**
- **release gate 代码侧已 ready**
- **仍未达到“可直接上线”状态**，因为还缺 Apple / App Store 正式发布配置和真机链路验收

---

## 2. 当前基线结论（执行前先确认）

### 已确认完成
- `appCode` 已统一为 `paipai_readingcompanion`
- iOS bundle / 后端 Apple clientId / billing bundleId 已统一到 `com.paipai.readalong.v2`
- 前端核心接口与后端 controller 已静态对齐
- PowerSync 本地 schema 与数据库迁移表结构已对齐
- 本次后端定向回归已通过：
  - `ReadingCompatServiceTest`
  - `ReadingAnnouncementServiceTest`
  - `ReadingAnnouncementCompatControllerWebMvcTest`
  - `ReadingBillingCompatControllerWebMvcTest`
  - `AppAppleReadinessServiceTest`
  - `ReadingCloudUsageServiceTest`
  - `SystemControllerWebMvcTest`
- 当前发布波次只统计 `paipai_readingcompanion`，`saving` 已排除出本次上线波次

### 当前明确未完成
- 9 项正式发布配置未补齐
- Mac / Xcode 真编译和运行证据未补齐
- Apple 登录 / App Store Server API / webhook 真链路未完成闭环

---

# 3. 开发执行 checklist

> 目标：把“代码已接近上线态”推进到“环境与配置也接近上线态”。

## 3.1 前端执行项（iOS）
- [ ] 确认 `AppIdentity.appCode == "paipai_readingcompanion"`
- [ ] 确认 `fallbackBundleIdentifier == "com.paipai.readalong.v2"`
- [ ] 确认 `PAIPAI_API_BASE_URL` 已在 Info.plist / Build Settings 正常注入
- [ ] 确认 Apple Sign In 使用的 client/bundle identity 与后端一致
- [ ] 再扫一轮用户可见文案，清理残留“reading 后端”等模板化表述
- [ ] 确认购买、恢复购买、首页、孩子管理、复习、周报、设置页没有调用旧接口或旧身份常量
- [ ] 在 Xcode 本地完成一次 clean build，保留截图或日志

## 3.2 后端执行项
- [ ] 确认 `app-definition.yml` 关键口径仍为：
  - [ ] `app.code = paipai_readingcompanion`
  - [ ] `apiPrefix = /api/v1`
  - [ ] `tablePrefix = reading_`
  - [ ] `auth.apple.clientId = com.paipai.readalong.v2`
  - [ ] `billing.appstore.bundleId = com.paipai.readalong.v2`
  - [ ] `auth.apple.remoteExchangeEnabled = true`
  - [ ] `billing.appstore.allowSandbox = false`
  - [ ] `app.release.requiredForCurrentWave = true`
- [ ] 确认 release gate 只统计 `paipai_readingcompanion`
- [ ] 确认 release gate 中 `codeStatus = ready` 不是被旧编译产物误导
- [ ] 若改过后端配置或 gate 逻辑，执行一次 `clean/package` 级别重建，避免 live 实例吃旧 classes
- [ ] 再扫一轮 OpenAPI/Tag/注释中的 `Reading ...` 暴露内部实现名问题

## 3.3 数据库执行项
- [ ] 确认 migration 已包含并保持顺序：V1 ~ V14
- [ ] 确认 `V7 ~ V14` 已覆盖本次多 App / release gate / Apple readiness 相关改动
- [ ] 确认 `release_ios` namespace 已回填到 `paipai_readingcompanion`
- [ ] 确认 `reading_` 前缀表保持为物理域命名，不误当成 appCode 漂移问题
- [ ] 确认 PowerSync 相关表存在并字段完整：
  - [ ] `reading_child_profile`
  - [ ] `reading_review_card`
  - [ ] `reading_review_event_v2`
  - [ ] `reading_usage_session_v2`
  - [ ] `reading_user_preference`

## 3.4 当前必须补齐的 9 项发布配置

### release_ios
- [ ] `release_ios.development_team`

### Apple Auth
- [ ] `auth.apple.teamId`
- [ ] `auth.apple.keyId`
- [ ] `auth.apple.privateKey`
- [ ] `auth.apple.redirectUri`
- [ ] `auth.apple.credentialEncryptionKey`

### App Store Server API
- [ ] `billing.appstore.issuerId`
- [ ] `billing.appstore.keyId`
- [ ] `billing.appstore.privateKey`

### 建议补齐但当前不一定阻塞
- [ ] `billing.appstore.appAppleId`

---

# 4. 测试验收 checklist

> 目标：让个人开发者可以独立形成“我已经验证过”的证据链。

## 4.1 后端编译与自动化回归
- [ ] 执行：
```bash
mvn -q -DskipTests compile
```
- [ ] 结果为成功，无编译错误

- [ ] 执行：
```bash
mvn -q -Dtest=ReadingCompatServiceTest,ReadingAnnouncementServiceTest,ReadingAnnouncementCompatControllerWebMvcTest,ReadingBillingCompatControllerWebMvcTest,AppAppleReadinessServiceTest,ReadingCloudUsageServiceTest,SystemControllerWebMvcTest test
```
- [ ] 结果为成功，exit code = 0
- [ ] 保留测试输出或截图

## 4.2 后端启动验收
- [ ] 使用 Docker / 本地依赖启动后端
- [ ] 若 `8080` 被占用，切换到备用端口（例如 `18080`）
- [ ] 确认 `/actuator/health` 返回 `UP`
- [ ] 确认 Flyway 迁移版本已到最新（当前应看到 V14）

## 4.3 核心接口手工验收
- [ ] `/api/v1/plans` 可正常返回
- [ ] `/api/v1/bootstrap/config` 可正常返回
- [ ] `/api/v1/legal/docs` 可正常返回
- [ ] `/api/v1/announcements` 可正常返回
- [ ] `/api/v1/subscriptions/status` 返回结构正常
- [ ] `/api/v1/system/release-gate` 可正常返回完整 gate 结果
- [ ] `/api/v1/system/apps/paipai_readingcompanion/apple/readiness` 可返回 readiness 详情

## 4.4 iOS 本地验收
- [ ] Xcode clean build 通过
- [ ] 模拟器运行通过
- [ ] 如果有真机条件，真机运行通过
- [ ] Apple 登录按钮可展示且不崩溃
- [ ] 首页拉取 bootstrap/config 不报错
- [ ] 套餐页、恢复购买入口、设置页可正常进入
- [ ] 孩子管理、复习卡、周报、设置基础流程不崩溃

## 4.5 真链路验收（当前仍缺）
- [ ] 使用真实 Apple ID 完成一次登录闭环
- [ ] 使用正式 App Store Server API 凭证验证一次服务端链路
- [ ] 验证一次购买 intake 或恢复购买 intake
- [ ] 验证一次 webhook / notification 回源链路
- [ ] 验证 release gate 在正式配置下能从 `blocked` 收口到 `ready`

---

# 5. 上线前 gate checklist

> 目标：只有全部满足，才可认为“具备上线前签字条件”。

## 5.1 代码 gate
- [x] 前端 app identity 与后端 app definition 已统一
- [x] 核心 API 路由已静态匹配
- [x] PowerSync schema 与数据库迁移已静态匹配
- [x] 后端关键测试已通过
- [x] 当前 `codeStatus = ready`

## 5.2 配置 gate
- [ ] 9 项 Apple / 发布配置全部补齐
- [ ] `billing.appstore.appAppleId` 补齐
- [ ] release_ios 的 API base URL / bundle / development team 全部有效
- [ ] Apple 登录生产参数与 App Store Server API 生产参数均非 placeholder
- [ ] 运行中的实例已确认不是旧编译产物或旧配置污染

## 5.3 环境 gate
- [ ] 数据库 migration 全量执行完成
- [ ] 启动环境与生产口径一致，不再误开 sandbox
- [ ] 线上或预发环境中的 remote exchange 已开启
- [ ] 运行时机密（如 credentialEncryptionKey）已安全注入

## 5.4 验收 gate
- [ ] Xcode 真编译通过
- [ ] 模拟器 / 真机核心流程验收通过
- [ ] Apple 登录真链路通过
- [ ] App Store 购买 / 恢复购买 / entitlement 刷新真链路通过
- [ ] webhook 真链路通过
- [ ] TestFlight 或等价预发包验收通过

## 5.5 最终判定规则

### 可以判定“可上线”
必须同时满足：
- [ ] 代码 gate 全绿
- [ ] 配置 gate 全绿
- [ ] 环境 gate 全绿
- [ ] 验收 gate 全绿

### 当前实际状态
- **代码 gate：已基本通过**
- **配置 gate：未通过**
- **环境 gate：未完全通过**
- **验收 gate：未通过**

**当前结论：暂时还不能签字上线。**

---

# 6. 个人开发者建议执行顺序

按这个顺序最省时间：

1. **先补齐 9 项配置**
2. **重新打 release gate / apple readiness**
3. **跑后端 compile + 定向测试**
4. **在 Xcode 做 clean build + 模拟器运行**
5. **做 Apple 登录 / 购买 / webhook 真链路验证**
6. **最后再看 release gate 是否从 `blocked` 变成 `ready`**

---

# 7. 一句话结论

这次 paipaiV2 的问题，**已经不是“前后端数据库有没有对齐”**，而是**“最后一公里的正式 Apple / 发布配置和真实环境验收还没补完”**。对于个人开发者来说，下一步最值得做的不是继续大面积扫代码，而是**补配置、跑真机、打真链路、看 gate 收口**。
