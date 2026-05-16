# Paipai iOS 在 macOS / Xcode 下的真编译与联调执行单

## 1. 文档目的

本执行单用于在 macOS + Xcode 环境下完成以下工作：

1. 验证 `/home/admin/code/app/paipai/ios` 当前修改后的代码可以真实编译；
2. 验证 unified backend 中 `apps/reading` 的新接口与 iOS 解码模型真实匹配；
3. 验证 children / review / weekly report / feedback / OCR 等付费能力已回到后端权威校验，不再能靠客户端本地状态绕过；
4. 为后续 TestFlight / App Store 提审提供联调证据。

---

## 2. 执行前置条件

### 2.1 macOS 侧工具

必须具备：

- Xcode
- iOS Simulator
- `xcodegen`
- 有效 Apple 开发者证书 / team 配置（若要真机或正式构建）

建议先验证：

```bash
xcodebuild -version
xcrun simctl list devices
xcodegen --version
```

### 2.2 后端侧前置条件

需要先把 unified backend 启动起来，至少满足：

- reading 兼容接口可访问
- PostgreSQL 可用
- Redis 可用
- Flyway migration 可成功执行

建议先本地启动 unified backend：

```bash
cd /home/admin/code/app/backend
mvn spring-boot:run
```

或部署到测试环境后使用真实 `api.paipai.app` / staging 域名。

### 2.3 iOS 配置前置条件

在 `ios/project.yml` 或生成后的工程中，确认：

- `PAIPAI_API_BASE_URL` 指向当前 unified backend reading 接口地址
- 不是 `localhost` / `127.0.0.1` / placeholder（除非 Simulator 就跑本机）

若是本机联调，可用：

```text
http://127.0.0.1:8080
```

若是提审 / TestFlight 验收，应改成真实 HTTPS 域名。

---

## 3. 真编译步骤

### 3.1 生成 Xcode 工程

```bash
cd /home/admin/code/app/paipai/ios
xcodegen generate
```

预期：

- 生成 `PaipaiReadAlong.xcodeproj`
- 无 YAML 结构错误

### 3.2 打开工程

```bash
open PaipaiReadAlong.xcodeproj
```

### 3.3 选择目标设备

建议先：

- iPhone 15 / iOS 最新 Simulator

### 3.4 编译

Xcode 中执行：

- Product → Build

预期：

- 无 Swift 编译错误
- 无 Codable 解码字段错误
- 无 Info.plist / project 配置错误

---

## 4. 联调主链路执行顺序

建议按下面顺序执行，避免问题叠加难以定位。

### 4.1 启动配置拉取

目标接口：

- `GET /api/v1/bootstrap/config`
- `GET /api/v1/plans`
- `GET /api/v1/legal/docs`

预期：

- App 首次启动可以拿到 bootstrap
- Paywall 可以拿到后台返回的真实商品方案
- Support / Privacy / Terms / Child Data 链接能正常渲染

关注点：

- 不允许回退到本地硬编码价格作为正式价格
- 法务链接必须是 HTTPS 且非 placeholder

---

### 4.1.1 启动公告拉取（新增）

目标接口：

- `GET /api/v1/announcements?windowDays=30`

预期：

- App 启动并建立 reading 会话后，能请求到近 30 天公告
- 当前时间窗内的最新公告会自动弹出
- 长公告支持上下滚动
- 点击“不再展示这条公告”后，同一 `announcement_uuid` 不会再次自动弹出
- 设置页中可查看近 30 天本地历史公告

重点验证：

- 获取失败时不展示公告
- 同一 uuid 被本地标记“不再展示”后不会重复弹出
- 新公告 uuid 到来后会再次弹出

### 4.2 Apple 正式会话建立

目标接口：

- `POST /api/v1/system/auth/apps/{appCode}/apple/exchange`
- `GET /api/v1/system/auth/apps/{appCode}/me`

预期：

- 通过 Apple 登录成功拿到正式后端 session
- session 可持久化到 Keychain
- `authMode` 进入正式账号态，而不是继续停留 guest / demo 语义

关注点：

- Apple 登录未完成时，children / review / OCR 不能继续以正式能力方式绕过后端

---

### 4.3 孩子档案

目标接口：

- `GET /api/v1/children`
- `POST /api/v1/children`
- `PATCH /api/v1/children/{childId}`

预期：

- onboarding 创建孩子时，走后端创建
- 更新孩子信息时，走后端更新
- 免费版孩子数量限制由后端返回 `CHILD_LIMIT_REACHED` 等结果控制

重点验证：

- 在没有后端 session 时，不能继续本地“假创建多个孩子”
- 多孩子权限必须以后端 entitlement 为准

---

### 4.4 句卡 / 复习

目标接口：

- `GET /api/v1/review-cards/today`
- `POST /api/v1/review-cards`
- `POST /api/v1/review-events`

预期：

- 保存句卡成功后，后端能写入 reading 业务表
- 复习完成后，后端能记录 review event
- 首页 / 周报读取到的统计结果与后端记录一致

重点验证：

- 不能在没有后端 session 时继续把本地句卡当成正式权益内容
- `syncEnabled` / `storageMode` 行为与 entitlement 一致

---

### 4.5 每日任务

目标接口：

- `GET /api/v1/learning/daily-task`
- `POST /api/v1/learning/daily-task/{taskId}/complete`

预期：

- 每日任务由后端生成
- 完成动作由后端记录
- `todayCompletedCount`、`weeklyReviewCount` 等汇总不再依赖客户端本地猜测

---

### 4.6 周报

目标接口：

- `GET /api/v1/reports/weekly/current`
- `GET /api/v1/reports/weekly/history`

预期：

- child scope 正常返回当前孩子周报
- family scope 在无家庭多孩子 entitlement 时被后端阻止
- history 在无 history entitlement 时被后端阻止

重点验证：

- 周报范围控制必须由后端 entitlement 判断
- 前端不能靠本地 flags 打开 family/history 入口越权

---

### 4.7 订阅状态 / 恢复购买 / 刷新权益

目标接口：

- `GET /api/v1/subscriptions/status`
- `POST /api/v1/subscriptions/entitlements/refresh`
- `POST /api/v1/subscriptions/app-store/purchases/intake`
- `POST /api/v1/subscriptions/app-store/restores/intake`

预期：

- 前端读取到的订阅状态与 unified backend 当前 projection 一致
- 刷新权益走 reading 后端兼容层
- 恢复购买后，权益刷新由后端 projection 决定，而不是客户端本地假设

---

### 4.8 OCR

### 4.8.1 阿里百炼 OCR 真环境点

当前后端已经接入阿里百炼 OCR provider，且配置来自 DB：

- `ocr.endpoint`
- `ocr.headers`
- `ocr.model`
- `ocr.apiKeyEnvName`

真环境验证时需重点确认：

- 新加坡 / 美国 / 北京配置是否和对应 API Key 匹配
- data URL 图片输入是否在真实账号下可用
- OCR 成功时，云端剩余次数是否正确扣减
- OCR 失败时，前端是否弹出正确的升级或失败提示


目标接口：

- `POST /api/v1/ocr/extract`

当前预期：

- 必须先建立后端 session
- 后端会做 quota 校验
- 后端会记录 OCR audit
- 当前仍可能返回 `manual_fallback / not_configured`

重点验证：

- 没有后端 session 时，不允许继续把 OCR 当正式能力使用
- 不上传或长期持久化原始图片到业务表

---

### 4.8.2 阿里百炼云端朗读真环境点

当前后端已经接入 CosyVoice WebSocket provider，且配置来自 DB：

- `tts.wsUrl`
- `tts.headers`
- `tts.model`
- `tts.voice`
- `tts.sampleRate`
- `tts.rate`
- `tts.pitch`
- `tts.apiKeyEnvName`

真环境验证时需重点确认：

- WebSocket 能否成功返回 `task-started` / `task-finished`
- 是否能收到非空音频分片
- iOS 端是否能播放返回的 `audioBase64`
- 成功时云端朗读剩余次数是否正确扣减
- 失败时是否正确弹出升级或失败提示

### 4.9 反馈 / 删除账号

目标接口：

- `POST /api/v1/feedback`
- `POST /api/v1/account/deletion-requests`

预期：

- 反馈可匿名，也可带账号态
- 删除账号会撤销 session / identity / provider token，并处理孩子档案与句卡数据
- Apple formal 账号时，尽量尝试 Apple revoke

重点验证：

- 删除账号按钮不是假动作
- 删除后再进 App，不应继续保留旧账号正式态

---

## 5. 重点观察项

### 5.1 UI 观察

- onboarding 后孩子档案是否正确创建
- Paywall 商品价格是否来自后台
- 周报 family / history 是否按 entitlement 正确锁定
- OCR 失败时是否显示“需要先连接体验账号 / 正式登录”而不是偷偷走本地

### 5.2 网络观察

可在 Xcode / Charles / Proxyman 里重点看：

- 请求是否都打到 unified backend reading 兼容接口
- 是否还残留旧 paipai backend 地址
- 是否有接口返回结构与 Swift Codable 不匹配

### 5.3 日志观察

后端重点看：

- `requestId`
- 4xx / 5xx
- quota / entitlement / child limit 拦截
- delete account 执行结果
- OCR audit 记录

---

## 6. 执行结果判定标准

### 通过标准

同时满足以下条件可判定本轮真联调通过：

1. iOS 工程可真编译
2. Apple 正式会话可成功建立
3. children / review / weekly report / feedback / OCR 相关请求都能打到 unified backend reading 接口
4. 付费边界全部由后端 entitlement 控制
5. 不存在明显客户端本地绕过路径
6. 删除账号动作真实有效

### 不通过标准

出现以下任一项即判定未通过：

- Swift 编译失败
- Apple 正式会话建不起来，且前端继续把付费能力当正式能力本地运行
- 周报 / 多孩子 / OCR 能在无后端授权下继续本地越权
- 删除账号仍是假动作
- 商品价格或权益判断仍依赖客户端本地硬编码

---

## 7. 建议产出物

执行完本联调后，建议产出：

1. Xcode build 成功截图
2. children / review / weekly report / OCR / deletion 流程录屏
3. 后端 requestId / 响应样例
4. `PASS / FAIL / BLOCKED` 表格
5. 若失败，记录对应接口、页面、设备、系统版本、requestId
