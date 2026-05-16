# Reading OCR Provider 接入位点设计

## 1. 文档目的

本设计文档用于明确：

1. unified backend 中 reading 域 OCR 当前处于什么状态；
2. 若后续接入真实 OCR provider，应该把实现放在哪些 reading 自己目录下；
3. 如何在满足个人开发者、低运维、低法律风险的前提下，把 OCR 从“后端鉴权+审计接管态”演进为“真实识别能力”；
4. 如何保证 OCR 不成为儿童图片数据风险入口。

---

## 2. 当前状态

当前 unified backend 中 OCR 已经完成了以下工作：

- 接口已落地在 reading 域：
  - `POST /api/v1/ocr/extract`
- 调用必须经过 reading 后端 session
- 已做 quota 校验
- 已做 OCR audit 落库：`reading_ocr_audit`
- 不长期保存原始图片
- 当前返回值仍为：
  - `provider = manual_fallback`
  - `model = not_configured`
  - `text = ""`

### 2.1 这意味着什么

这意味着 OCR 已经不再是“客户端本地随便调用”的能力，而是已经被后端接管：

- 权限边界回到了服务端
- 审计也回到了服务端
- 但真实识别 provider 还没接上

所以当前状态适合：

- 先堵住本地绕过
- 先降低儿童图片处理风险
- 再决定是否引入真实 OCR 供应商

---

## 3. 设计原则

### 3.1 目录隔离原则

所有 OCR 真识别相关代码，都应放在：

```text
/home/admin/code/app/backend/src/main/java/com/apphub/backend/apps/reading/...
```

不要混进非拍拍项目或 system 公共域。

推荐后续目录结构：

```text
apps/reading/ocr/
  controller/
  service/
  provider/
  model/
  policy/
```

### 3.2 数据最小化原则

OCR 是儿童图片处理入口，必须遵守：

- 不长期保存原始图片
- 不把原始图片写入数据库
- 不把原始图片写入日志
- 审计只保存：
  - traceId
  - provider
  - model
  - status
  - note
  - 时间戳
- 若必须缓存临时文件，应：
  - 用临时目录
  - 短时有效
  - 处理完成后立即删除

### 3.3 权益与额度原则

OCR 应继续受 reading 后端控制：

- 必须有合法 session
- 必须有剩余额度或合法 entitlement
- 必须能记录审计 traceId
- 前端不能因为 provider 挂了就自动本地伪造“识别成功”

### 3.4 合规与风控原则

- OCR 结果仅作为辅助文案，不得被描述为高准确率承诺
- 不能把 OCR 输出当成教育评价结论
- 不做孩子能力分级、诊断、排名
- 不做对用户有重大影响的自动化决策

---

## 4. 推荐的实现分层

## 4.1 Controller 层

保持入口不变：

- `POST /api/v1/ocr/extract`

职责：

- 解析请求
- 验证 session
- 调用 reading OCR service
- 返回 `OcrExtractReceipt`

不负责：

- 直接调用外部 OCR SDK
- 直接做 quota 判定
- 直接写原始图片

---

## 4.2 Service 层

推荐新增：

### `ReadingOcrService`

职责：

- quota 校验
- provider 路由
- 调用 provider
- 生成统一返回值
- 写 audit

### `ReadingOcrPolicyService`

职责：

- 判定当前账号是否允许 OCR
- 判定是否需要 premium entitlement
- 判定是否超额

### `ReadingOcrAuditService`

职责：

- 只记录低敏审计信息
- 不写原始图片
- 不写原始 OCR 文本全文（如需保存，也只保存必要摘要）

---

## 4.3 Provider 层

推荐抽象接口：

```text
ReadingOcrProvider
```

返回统一结构，例如：

- status
- text
- provider
- model
- note
- minPixels / maxPixels

可做的实现：

- `ReadingNoopOcrProvider`（当前 fallback）
- `ReadingHttpOcrProvider`（调用外部 HTTP OCR）
- `ReadingLocalVisionBridgeProvider`（如果未来有本地桥接能力）

### 推荐优先实现

优先建议：

**HTTP OCR provider + 严格审计 + 不落图片**

原因：

- 对个人开发者最容易运维
- 易于替换供应商
- 出问题时可直接关闭
- 不需要在 backend 内集成复杂原生 OCR 依赖

---

## 5. 推荐请求流程

```text
iOS -> /api/v1/ocr/extract
    -> reading auth/session check
    -> quota / entitlement check
    -> ReadingOcrService
        -> ReadingOcrProvider.extract(...)
        -> 记录 reading_ocr_audit
    -> 返回 OcrExtractReceipt
```

返回给前端的数据应保持与当前模型兼容：

- `traceId`
- `provider`
- `model`
- `text`
- `prompt`
- `minPixels`
- `maxPixels`

这样可以避免前端再大改 Codable 模型。

---

## 6. 供应商选择建议

从你的目标出发：

- 个人开发者好维护
- 法律风险最低
- 收益优先
- Apple Store 更稳

建议供应商选择顺序：

### 方案 A：可审计、可关闭的 HTTP OCR 服务

优点：

- 实现快
- 容易替换
- 易加熔断和限流
- 运维负担低

缺点：

- 需要处理外部图像传输风险
- 需要额外隐私披露

### 方案 B：完全本地 OCR

优点：

- 隐私风险最低
- 不上传图片

缺点：

- 当前 unified backend 侧不适合直接做
- 更适合 iOS 端原生能力，不适合作为当前后端接管方案

### 当前推荐

**短期：先不上真 OCR provider，或者只在明确合规披露后接入可关闭的 HTTP OCR。**

原因：

- 你的核心收益不一定来自 OCR 本身，而是来自“拍读 -> 句卡 -> 复习 -> 周报 -> 多孩子权益”这一闭环；
- OCR 是风险入口，应该在明确合规和运营承受能力后再放量；
- 当前先把鉴权、配额、审计接管好，已经是正确顺序。

---

## 7. 风险控制建议

### 7.1 必须做

- 请求超时
- provider 熔断
- 重试次数限制
- OCR provider 开关（环境变量 / remote config）
- traceId 全链路记录
- 不打印图片内容
- 不打印 OCR 原始全文

### 7.2 建议做

- provider 限流
- 单用户每日 OCR 次数限制
- 单图像大小限制
- MIME type 白名单
- 非法图片快速拒绝

### 7.3 不建议做

- 永久保存儿童原图
- 保存整页 OCR 全文做训练语料
- 将 OCR 结果用于能力画像、学习分层、成绩结论

---

## 8. 推荐后续实施顺序

1. 保持当前 reading OCR 接口和 audit 结构不变
2. 新增 `ReadingOcrProvider` 接口
3. 默认实现仍为 `manual_fallback`
4. 在 feature flag 下接入真实 provider
5. 在 staging 环境压测和审计验证
6. 更新隐私政策 / child-data 页面 / 审核说明
7. 再决定是否在正式版默认开启

---

## 9. 当前建议结论

如果你的目标是：

- 快速稳定上架
- 法律风险最低
- 运维压力最低

那么当前最合理的策略是：

### 短期
- 保持 OCR 为 **后端鉴权 + quota + 审计接管态**
- 不把 OCR 作为必须成功的核心付费卖点宣传

### 中期
- 在 reading 自己目录内接入可关闭的 OCR provider
- 保持不落原图、不落高敏数据

### 长期
- 只把 OCR 当成“输入辅助能力”，不要把它升级成“高风险教育判断系统”
