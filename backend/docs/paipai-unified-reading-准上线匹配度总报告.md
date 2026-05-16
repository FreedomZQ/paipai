# Paipai 前端 × Unified Backend Reading 准上线匹配度总报告

## 1. 报告目的

本报告用于回答以下核心问题：

1. 当前 unified backend 与 Paipai iOS 前端是否已经整体匹配；
2. 提供真实配置账户、域名和阿里百炼 API Key 后，是否已达到“准上线”状态；
3. 是否已经尽量防止用户通过客户端修改获得不属于他的权益；
4. 是否满足个人开发者低成本运维、低法律责任、提高留存和付费转化的目标；
5. 是否接近符合 Apple Store 个人开发者上架审核要求。

---

## 2. 当前总评结论

### 2.1 结论一句话

**当前状态已经达到“代码层准上线”级别，但还没有达到“真环境可直接提审”级别。**

并且公告通知能力已经补齐：后端支持按时间窗下发公告，前端支持启动弹窗、滚动查看、不再展示和 30 天本地历史缓存。

### 2.2 原因拆解
补充验证：云端 OCR / 云端 TTS 的后端次数控制已增加 `ReadingCloudUsageServiceTest`，覆盖 OCR 20 次试用、TTS 30 次试用、次数用尽后的升级提示以及成功消费后的剩余次数递减。


### 2.3 runtime/runtime-like 占位值审计补充

已做过一轮运行时占位值审计：

- backend 运行时代码层未发现新的 `example.com` / `localhost` 型线上阻塞值；
- iOS 运行时代码层 `PAIPAI_API_BASE_URL` 继续保持 fail-fast，不再 silently fallback 到 localhost；
- 当前残留的 placeholder 主要位于 docs、dev/test 配置和 release owner 待填写项，而不是 reading 线上主逻辑。

#### 已达到的部分
- 公告通知能力已接入 unified backend reading：启动拉取、时间窗弹出、滚动查看、不再展示、本地 30 天历史缓存。
- unified backend reading 目录内的关键业务接口已经补齐；
- 前端主要调用面已与 reading 兼容层对齐；
- 付费边界基本回到后端控制；
- 云端 OCR / 云端 TTS 已接入阿里百炼真实 provider 代码；
- provider 的 endpoint / headers / model / voice / region 已改成 DB 配置驱动；
- 后端 `mvn -q clean verify` 已通过。

#### 尚未完成的部分
- iOS 侧尚未在 macOS + Xcode 下完成真编译、真机或 Simulator 联调；
- 阿里百炼 OCR / TTS 尚未在真实 API Key 与真实 region 下做端到端联调；
- Support / Privacy / Terms / Delete Account 等公网资产仍需真实可访问；
- Apple Sign In / App Store 商品 / 审核材料仍需真环境闭环。

因此：

- **从后端和接口设计角度：已经接近上线**；
- **从苹果提审和真环境角度：还差最后一层外部条件验证**。

---

## 3. 匹配度审查结果

## 3.1 接口匹配度

### 3.1.1 当前前端主调用面

Paipai iOS 当前主调用面包括：

- bootstrap / plans / legal docs
- auth me / apple exchange / logout
- account state / home summary / deletion
- children
- review cards / review events
- daily learning task
- weekly report / history
- feedback
- OCR extract
- announcements
- subscriptions status / entitlement refresh / purchase intake / restore intake
- TTS speak

### 3.1.2 当前 reading 后端已覆盖情况

当前 unified backend 的 `apps/reading` 已覆盖上述主调用面，包括新增公告接口 `GET /api/v1/announcements?windowDays=30`。

**结论：接口覆盖度高，主链路已打通。**

> 说明：中间做过一次脚本级路由核查，`children` / `feedback` 的未命中属于脚本解析无参注解问题，不是实际接口缺失。控制器已实装。

---

## 3.2 数据结构匹配度

### 3.2.1 已基本对齐的模型

以下返回结构已经基本与 Swift Codable 模型对齐：

- `AppBootstrap`
- `Plan`
- `LegalDocument`
- `AuthSessionEnvelope`
- `AccountState`
- `HomeSummary`
- `ChildProfile`
- `ReviewCard`
- `DailyLearningTaskFeed`
- `WeeklyParentReport`
- `FeedbackSubmissionReceipt`
- `SubscriptionStatus`
- `EntitlementRefreshReceipt`
- `TransactionIntakeReceipt`
- `OcrExtractReceipt`
- `CloudSpeechReceipt`

### 3.2.2 目前的注意点

虽然字段层面已基本对齐，但还需要 Xcode 真编译确认：

- 新增可选字段是否全部可被前端稳定解码；
- `audioBase64` / `mimeType` 等云端 TTS 字段在真机播放链路上是否完整可用；
- `OcrExtractReceipt.allowed / remainingTrialCount / upgradeTitle / upgradeMessage` 在真请求下是否符合预期。

**结论：静态结构匹配度高，但仍需真机编译验证。**

---

## 4. 使用逻辑与代码逻辑审查

## 4.1 认证逻辑

### 当前状态
- reading 已收口为 Apple 登录唯一正式入口；
- `/auth/me`、`/logout`、`/deletion-requests` 已打通；
- 删除账号仍保留邮箱验证码确认，但不再作为长期登录方式。

### 结论
认证链路已转向单一正式账号态，核心目标是：

- Apple 正式登录
- 后端权威会话
- 删除账号的临时邮箱验证码确认

---

## 4.2 付费边界是否仍能被前端本地绕过

### 已收口到后端的能力
- 孩子档案数量
- 周报 family / history 范围
- review card / review event 记录
- 云端 OCR
- 云端 TTS
- account deletion
- subscription status / entitlement refresh

### 当前判断
**核心付费权益已经基本回到后端权威控制。**

前端现在默认：
- 设备自带 OCR / TTS 可本地使用；
- 云端 OCR / TTS 必须走 reading backend session + 次数校验。

### 仍需注意的点
本地 OCR / 本地 TTS 的每日免费次数，当前已经从 `UserDefaults` 提升到 **Keychain 持久化**，抗轻度重装/清理更稳，但它仍然不是服务端权威数据。

这意味着：

- **对开发者成本敏感的云端权益已受后端控制；**
- **对纯本地能力的“每日免费限制”是增强型本地约束，不是绝对不可绕过。**

### 结论
- **不会因为修改客户端轻易薅走云端资源或付费能力；**
- **本地免费能力仍可在 backend 挂掉时继续使用，这是产品设计取舍，而不是严重安全漏洞。**

---

## 4.3 OCR 逻辑合理性

### 当前策略
- 默认推荐：设备自带 OCR
- 用户主动切换后：走云端 OCR
- 云端 OCR：
  - 先校验 session
  - 再校验云端剩余次数
  - 再调阿里百炼 OCR

### 优点
- backend 停止后，用户仍能使用设备自带 OCR
- 云端成本被后端控制
- 不长期保存原始图片
- 风险较低

### 风险点
- 阿里百炼 OCR 的真实 data URL 输入兼容性还需真环境验证
- OCR 结果质量尚未真机实测

### 结论
逻辑上合理，满足低风险和低成本目标，但仍需真实 provider 验证。

---

## 4.4 TTS 逻辑合理性

### 当前策略
- 默认推荐：设备自带 TTS（AVSpeechSynthesizer）
- 用户主动切换后：走云端 TTS
- 云端 TTS：
  - 后端代理 WebSocket
  - 前端不直接拿 API Key
  - 成功后返回 `audioBase64`

### 优点
- 设备自带朗读不依赖后端
- 云端成本可控
- 适合个人开发者

### 风险点
- 百炼 CosyVoice WebSocket 虽然已按文档时序落地，但仍需真环境跑通
- iOS 端音频解码播放还需 Xcode 真联调

### 结论
设计合理，且符合“前端不暴露云端密钥”的安全要求。

---

## 5. 低成本运维与低法律责任审查

## 5.1 低成本运维

当前实现是偏低成本的：

- 设备自带 OCR / TTS 默认承担基础能力
- 云端能力只在用户主动选择时启用
- 云端次数由后端记录
- region / model / headers / endpoint 全部可 DB 切换
- API Key 不落 DB，只通过环境变量名引用

### 结论
对个人开发者来说，这已经是一套比较省心的运维结构。

---

## 5.2 法律责任和隐私风险

### 已做对的部分
- 不长期保存原始图片
- feedback 不接高敏原始媒体
- OCR audit 只存低敏元信息
- 删除账号后会处理 session / identity / review 数据
- Apple revoke 已尽量尝试

### 仍需真环境补足的部分
- 隐私政策 / child data 页需要真实上线
- support 邮箱需要真实可收信
- OCR / TTS 外部服务的实际隐私披露需要与法务文案保持一致

### 结论
当前实现方向正确，法律风险已比“全部走云端”或“全部本地伪装”低很多，但提审前必须补完公开法务资产。

---

## 6. 留存率与付费转化审查

## 6.1 留存方面

当前结构对留存是正向的：

- backend 停止后，用户仍可继续使用设备自带 OCR / TTS
- 基础体验不会因为服务端波动完全中断
- 这对新用户留存很重要

### 结论
**有利于留存。**

---

## 6.2 付费转化方面

当前结构对转化也更合理：

- 云端 OCR / TTS 有明确“设备自带 vs 云端服务”的认知区分
- 云端次数用完后，有结构化升级提示：
  - 开通会员
  - 购买额外次数包

### 结论
**有利于转化，而且不会因为默认全云端而直接提高开发者成本。**

---

## 7. Apple Store 个人开发者审核适配度

## 7.1 已接近满足的部分
- 删除账号链路已补齐
- 付费边界以后端为准
- 支持/法务配置已可通过 backend 输出
- 非付费体验在后端挂掉时仍可用
- 不把 API Key 暴露给前端

## 7.2 仍是提审 blocker 的部分
- Xcode 真编译 / 真机联调未完成
- support/privacy/terms/child-data/delete-account 公网页面未确认真实上线
- support 邮箱未确认真实可收信
- Apple Sign In 真配置未最终验收
- 阿里百炼真环境 API Key / region 联调未完成
- IAP 商品和真实 entitlement 真环境闭环未完成

### 结论
**离“可提审”只差真环境和审核资产，不差主代码结构。**

---

## 8. 最终评分式结论

> 这是当前代码层面的主观评分，不是 Apple 审核最终结果。

### 8.1 接口匹配度
**9 / 10**

### 8.2 权益边界安全性
**8.5 / 10**

说明：
- 云端能力已收口到后端
- 本地免费能力仍存在离线本地约束，不是绝对不可绕过，但成本风险很低

### 8.3 低成本运维程度
**9 / 10**

### 8.4 法律风险控制程度
**8 / 10**

说明：
- 代码方向正确
- 还差法务页面、support 邮箱、真环境配置闭环

### 8.5 准上线程度
**8 / 10**

### 8.6 可提审程度
**6.5 / 10**

原因：
- 代码层基本到位
- 真环境 / Xcode / 公网审核资产还没补齐

---

## 9. 最终结论

### 当前是否“完全吻合、提供真环境信息后就可以使用”的准上线程度？

**接近是。**

更准确地说：

- **一旦补齐真实 API Key、域名、support/legal 页面、Apple 配置，并在 Xcode 下完成真编译联调，当前结构已经可以进入上线准备阶段。**

### 当前是否已经完全满足 Apple Store 个人开发者全部审核要求？

**还不能直接说完全满足。**

因为还差：
- 真机 / 真编译验证
- 真实 support/legal/delete-account 公开资产
- 真实 Apple Sign In / IAP / OCR/TTS provider 账号联调

### 但是否已经具备正确的上线架构方向？

**是，已经具备。**

而且这条路径兼顾了：

- 权益安全边界
- 低成本运维
- 较低法律风险
- 用户体验连续性
- 留存与转化空间

---

## 10. 建议下一步

按优先级建议：

1. **macOS + Xcode 真编译与前后端真联调**
2. **注入真实百炼 API Key，验证 OCR / TTS 真调用**
3. **上线 support / privacy / terms / child-data / delete-account 页面**
4. **确认 support@paipai.app 可收信**
5. **完成 Apple Sign In / IAP 真环境闭环**
6. **再做一轮提审 blocker 清零复查**
