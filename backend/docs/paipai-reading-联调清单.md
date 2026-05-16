# Paipai iOS ↔ Unified Backend Reading 联调清单

## 1. 文档目的

本清单用于说明：

1. `/home/admin/code/app/backend` 当前 unified backend 中，`apps/reading` 目录下已经落地了哪些 Paipai 前端所需接口；
2. `/home/admin/code/app/paipai/ios` 当前前端的调用面，和 unified backend 是否已经对齐；
3. 哪些内容已经进入 **reading 后端权威校验**，避免客户端本地状态绕过付费/权益限制；
4. 还剩哪些事项需要在 macOS + Xcode / 真环境中继续联调确认。

---

## 2. 当前联调结论

### 2.1 总结结论

当前 unified backend 已经形成一套 **reading 独立实现**，并且已经覆盖 Paipai iOS 依赖的关键付费/权益接口：

- auth / Apple exchange
- account state / home summary / account deletion
- children
- review cards / review events
- daily learning task
- weekly report / history
- feedback
- OCR extract
- subscription status / entitlement refresh / purchase intake / restore intake

这些新增接口均放在：

```text
/home/admin/code/app/backend/src/main/java/com/apphub/backend/apps/reading/...
```

没有混入 saving 项目或 system 公共业务域。

### 2.2 对齐状态判断

**代码层面：基本对齐，可进入真机/Simulator 联调阶段。**

**运行层面：仍需 macOS + Xcode 真编译与真环境配置验证。**

原因：

- 当前 Linux 环境已完成 unified backend 的 `mvn -q clean verify`；
- 但无法在当前环境执行 Swift / XcodeGen / Xcode / iOS Simulator；
- 因此前端改动目前只完成了 **静态对齐**，尚未完成 **真编译与真交互验证**。

---

## 3. reading 目录内当前已落地接口

以下接口已经在 unified backend 的 `apps/reading` 目录内落地。

### 3.1 公开配置面

| Method | Path | 用途 |
| --- | --- | --- |
| GET | `/api/v1/bootstrap/config` | App 启动配置 |
| GET | `/api/v1/plans` | Paywall 商品与价格 |
| GET | `/api/v1/legal/docs` | 隐私政策 / 条款 / child data 文档链接 |

### 3.2 认证与会话

| Method | Path | 用途 |
| --- | --- | --- |
| POST | `/api/v1/system/auth/apps/{appCode}/apple/exchange` | Apple 登录 exchange |
| POST | `/api/v1/system/auth/apps/{appCode}/apple/refresh` | Apple session refresh |
| POST | `/api/v1/system/auth/apps/{appCode}/apple/revoke` | Apple revoke |
| GET | `/api/v1/system/auth/apps/{appCode}/me` | 当前会话与账号上下文 |
| POST | `/api/v1/system/auth/apps/{appCode}/logout` | 登出 |

### 3.3 账号与权益

| Method | Path | 用途 |
| --- | --- | --- |
| GET | `/api/v1/account/me/state` | 账号权益与 quota 权威状态 |
| GET | `/api/v1/account/me/home-summary` | 首页汇总 |
| POST | `/api/v1/account/deletion-requests` | 删除账号 |

### 3.4 孩子档案

| Method | Path | 用途 |
| --- | --- | --- |
| GET | `/api/v1/children` | 读取孩子档案 |
| POST | `/api/v1/children` | 创建孩子档案 |
| PATCH | `/api/v1/children/{childId}` | 更新孩子档案 |

### 3.5 句卡 / 复习 / 每日任务 / 周报

| Method | Path | 用途 |
| --- | --- | --- |
| GET | `/api/v1/review-cards/today` | 今日应复习句卡 |
| POST | `/api/v1/review-cards` | 保存句卡 |
| POST | `/api/v1/review-events` | 记录复习结果 |
| GET | `/api/v1/learning/daily-task` | 每日任务 |
| POST | `/api/v1/learning/daily-task/{taskId}/complete` | 完成每日任务 |
| GET | `/api/v1/reports/weekly/current` | 当前周报 |
| GET | `/api/v1/reports/weekly/history` | 历史周报 |

### 3.6 公告 / 支持 / OCR / 订阅

| Method | Path | 用途 |
| --- | --- | --- |
| GET | `/api/v1/announcements?windowDays=30` | 拉取近 30 天公告，前端据此做启动弹窗与历史缓存 |
| POST | `/api/v1/feedback` | 提交反馈 |
| POST | `/api/v1/ocr/extract` | OCR 抽取（当前为后端鉴权+审计接管态） |
| GET | `/api/v1/subscriptions/status` | 订阅状态 |
| POST | `/api/v1/subscriptions/entitlements/refresh` | 刷新权益快照 |
| POST | `/api/v1/subscriptions/app-store/purchases/intake` | 提交购买线索 |
| POST | `/api/v1/subscriptions/app-store/restores/intake` | 提交恢复购买线索 |
| POST | `/api/v1/subscriptions/transactions/verify` | 旧 verify 兼容路由 |

---

## 4. 当前 reading 域的数据落点

为避免付费内容继续留在客户端本地绕过后端校验，这次新增了 reading 独立业务表：

- `reading_child_profile`
- `reading_review_card`
- `reading_review_event`
- `reading_daily_task_completion`
- `reading_feedback_ticket`
- `reading_ocr_audit`

对应迁移：

```text
src/main/resources/db/migration/V1__baseline_current_schema.sql
```

### 4.1 设计原则

- **付费能力的事实源在后端，不在客户端。**
- **孩子数量限制必须由后端 entitlement 判定。**
- **句卡、复习、周报、OCR 都必须建立在后端 session + 后端 quota / entitlement 上。**
- **反馈和 OCR 尽量只保存低敏元信息，不长期保存儿童原始图片。**

---

## 5. 前端已做的配套收口

本轮对 Paipai iOS 只做了两处必要改动：

- `/home/admin/code/app/paipai/ios/PaipaiReadAlong/Core/Services/BackendClient.swift`
- `/home/admin/code/app/paipai/ios/PaipaiReadAlong/App/AppEnvironment.swift`

### 5.1 已收口的点

#### A. 公告能力已接入

- 启动成功建立 reading 会话后拉取公告
- 当前时间窗内的最新公告自动弹窗
- 支持长内容滚动
- 支持“不再展示这条公告”
- 设置页支持查看近 30 天历史公告


#### A. 使用 Apple 登录建立正式后端会话

当前 reading 已收口为 Apple 登录唯一入口：

- `POST /api/v1/system/auth/apps/{appCode}/apple/exchange`
- `GET /api/v1/system/auth/apps/{appCode}/me`

后续 children / review / OCR 等能力都应建立在正式后端 session 之上。

#### B. OCR 现在要求后端 session

`BackendClient.extractOcrText(...)` 已改为 `requiresAuth: true`。

也就是说：

- OCR 不再是无鉴权公共接口；
- OCR 必须经过后端 session 与 quota 校验；
- 当前仍不保存原始图片到后端表中。

#### C. children / review / OCR / review-event 的本地绕过路径已收紧

当前前端必须先建立后端正式 session；如果拿不到后端 session，则：

- 创建孩子档案：阻止继续本地正式生效
- 更新孩子档案：阻止继续本地正式生效
- 保存句卡：阻止继续本地正式生效
- 记录复习：阻止继续本地正式生效
- OCR：阻止继续调用并提示先完成 Apple 登录

### 5.2 仍需 macOS 真编译验证的点

虽然代码已静态收口，但仍需在 Xcode 中确认：

- Apple 登录成功后，`AuthSessionEnvelope` 能正确落 Keychain；
- 页面状态切换不会因为 async 顺序导致 UI 闪烁或错误提示；
- onboarding / children / review / OCR 页面在真实交互中不再回退到旧本地口径。

---

## 6. 当前已知仍待联调确认的事项

### 6.1 代码已对齐，但需要真编译验证

| 项目 | 当前状态 | 说明 |
| --- | --- | --- |
| iOS Swift 编译 | 未验证 | 当前环境没有 Swift / Xcode |
| XcodeGen generate | 未验证 | 当前环境没有 xcodegen |
| iOS Simulator | 未验证 | 当前环境没有 Xcode |
| 真机联调 | 未验证 | 当前环境没有 iOS 真机和签名上下文 |

### 6.2 OCR 仍是“后端接管权限，但未接真 provider”

当前 `/api/v1/ocr/extract` 的后端能力是：

- 已接管 session 鉴权
- 已接管 quota 校验
- 已做 OCR 审计落库
- 不长期保存原始图片
- 当前返回 `manual_fallback / not_configured`

所以它已经不是“客户端本地绕过 OCR”，但也还不是“真实 OCR provider 已接通”。

### 6.3 外部公开面还需要真实上线

需要真实存在且可访问：

- `https://www.paipai.app/support`
- `https://www.paipai.app/privacy`
- `https://www.paipai.app/terms`
- `https://www.paipai.app/child-data`
- `https://www.paipai.app/delete-account`
- `support@paipai.app`
- `api.paipai.app`

---

## 7. 联调建议顺序

建议按照下面顺序继续：

1. **先跑 macOS + Xcode 真编译**
2. **再跑 Apple 登录 -> children -> review -> weekly report -> subscription 真联调**
3. **确认 OCR 是否需要在提审前接入真实 provider**
4. **最后做 App Store 提审 blocker 清零**

---

## 8. 当前判断

### 已完成到的程度

- unified backend reading 目录内的最小付费内容接口已经补齐；
- children / review / weekly report / feedback / OCR 已经回到后端权威实现；
- 前端的关键本地绕过路径已经开始收口；
- unified backend 当前 `mvn -q clean verify` 已通过。

### 还没完成到的程度

- 还没在 macOS + Xcode 上完成真编译 / 真交互验证；
- 还没接入真实 OCR provider；
- 还没确认真实 support/legal/delete-account 公网资产已上线。

---

## 9. 结论

从“个人开发者、低运维、低法律风险、Apple Store 提审更稳”的角度，当前路径是正确的：

- **应该把付费能力继续收口到 reading 后端**；
- **不应该允许客户端本地数据继续充当正式权益事实源**；
- **应继续在 `apps/reading` 目录内独立扩展，而不是混入其它项目域。**
