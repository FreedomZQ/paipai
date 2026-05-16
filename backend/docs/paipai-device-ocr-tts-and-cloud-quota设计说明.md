# Paipai 设备自带 OCR / TTS 与云端次数控制设计说明

## 1. 目标

本方案用于满足以下目标：

1. **后端停止后，用户仍可继续使用非付费内容**；
2. 在识图页与朗读页分别提供：
   - 设备自带
   - 云端服务
3. 默认优先使用设备自带能力，降低开发者成本和合规风险；
4. 云端 OCR / 云端朗读只在用户主动选择时才调用 backend；
5. 云端次数必须由 reading 后端记录和控制，防止超额试用造成开发者损失。

---

## 2. 当前实现原则

### 2.1 默认策略

- OCR：默认 `设备自带`
- 朗读：默认 `设备自带`
- 只有当用户明确切到 `云端服务` 时，前端才会调用 unified backend reading 的对应接口。

### 2.2 非付费内容的可用性

即使 backend 不可用，用户仍可继续使用：

- 设备自带 OCR（iOS / iPadOS 15+）
- 设备自带 TTS（iOS / iPadOS / macOS）

因此 App 不会因为后端暂时不可用而完全失去基础体验。

### 2.3 云端能力的成本控制

云端 OCR / 云端朗读会先经过 reading 后端：

- session 校验
- 云端剩余试用次数校验
- 次数用尽后的提升信息返回

这样可以避免用户无限调用云端服务造成额外成本。

---

## 3. Apple 平台能力边界

### 3.1 设备自带朗读

使用：

- `AVSpeechSynthesizer`

可用范围：

- iPhone / iPad：可用
- macOS 10.14+：可用

### 3.2 设备自带 OCR

使用：

- `Vision` 框架
- `VNRecognizeTextRequest`

可用范围：

- iPhone / iPad：iOS / iPadOS 15+ 可用
- macOS：**本地 OCR 需 10.15+**

### 3.3 macOS 10.14 的处理策略

由于 macOS 10.14 不具备 `VNRecognizeTextRequest` 本地 OCR 条件，因此：

- 本地朗读仍可使用
- 本地 OCR 不可用
- 可提示用户改用云端服务或手动输入

---

## 4. 当前后端新增内容

本轮 unified backend reading 新增了云端次数控制相关内容：

- migration：
  - `V1__baseline_current_schema.sql`（当前基线已包含 reading 云端次数控制表）
- entity / mapper：
  - `ReadingCloudServiceUsageEntity`
  - `ReadingCloudServiceUsageMapper`
- service：
  - `ReadingCloudUsageService`
- controller：
  - `ReadingOcrCompatController`（云端 OCR 次数 gate 已接入）
  - `ReadingTtsCompatController`（云端朗读次数 gate 已接入）

### 4.1 当前试用次数规则

- 云端 OCR：20 次
- 云端朗读：30 次

当前由 backend 中的 reading 云端次数表记录：

- `trial_limit`
- `trial_used`
- `purchased_credits`
- `purchased_used`

### 4.2 当前 provider 状态

当前 reading backend 已接入：

- 云端次数控制
- 结构化返回
- 提升信息返回
- 阿里百炼 OCR provider（DashScope OpenAI-compatible HTTP）
- 阿里百炼 TTS provider（CosyVoice WebSocket 后端代理）

provider 的 region / endpoint / headers / model / voice 等均来自 `sys_remote_config` 的 `cloud_provider` namespace，不再写死在 Java 代码里。

当前默认配置为新加坡地域：

- OCR：`https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions`
- TTS：`wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference`

若未来切美国或北京，只需要更新 DB 配置中的 endpoint / wsUrl / API Key 环境变量名 / model / voice 等 key。

---

## 5. 当前前端新增内容

本轮前端主要新增：

### 5.1 模式选择

在两个页面增加模式按钮：

- `CaptureEditorView`
- `ReadAloudView`

选择项：

- `设备自带`
- `云端服务`

### 5.2 本地 OCR

`LocalOCRService` 已从原先 demo stub 改为真正调用：

- `Vision`
- `VNRecognizeTextRequest`

### 5.3 本地朗读

`ReadAloudService` 继续使用：

- `AVSpeechSynthesizer`

并预留了云端音频播放位点：

- `playCloudAudio(...)`

### 5.4 云端接口前端接入

`BackendClient` 新增：

- `createDemoSession(...)`
- `extractOcrText(...)`（云端 OCR）
- `synthesizeCloudSpeech(...)`（云端朗读）

### 5.5 本地每日使用次数

前端新增：

- `DailyAssistUsageStore`

用于在 backend 停止时，仍能在本地维持免费次数逻辑，不让 App 完全失去非付费功能。

---

## 6. 次数口径说明

### 6.1 当前口径

当前实现采用：

- **设备自带与云端方式，共用同一种能力的每日免费次数口径**
- 云端另外叠加 **累计试用次数** 由后端控制

也就是：

- OCR：本地 / 云端共用同一日计数口径
- TTS：本地 / 云端共用同一日计数口径
- 云端是否还能继续使用，还要再看后端剩余 trial / 购买次数

### 6.2 为什么这样设计

原因是：

1. 本地默认可用，保证基础体验；
2. 云端要控制成本，所以必须有后端累计计数；
3. 不让用户因为切换“设备自带 / 云端服务”而拿到两套免费次数。

---

## 7. 提示与升级策略

当用户切到云端服务时，reading backend 可能返回：

- 次数仍可用
- 云端未启用
- 云端试用已用完

前端会基于返回内容弹窗提示。

### 当前升级文案方向

当试用次数耗尽时，返回的提升方向包括：

- 开通会员解锁更高次数
- 购买独立云端次数包继续使用

后续真正接商品与权益时，应把这部分文案和商品口径再统一一次。

---

## 8. 当前尚未完成的事项

### 8.1 macOS 真编译

当前项目 `ios/project.yml` 仍是 iOS target，尚未完成真正 macOS target / 真机编译验证。

### 8.2 云端 OCR / TTS provider

当前已经有真实 provider 接入代码：

- OCR：`ReadingBailianOcrProvider`
- TTS：`ReadingBailianTtsProvider`

仍需真环境验证：

- `DASHSCOPE_API_KEY` 或 DB 配置中指定的 API Key 环境变量是否已注入；
- 新加坡 / 美国 / 北京地域的 API Key 是否与 endpoint 匹配；
- Qwen OCR 是否接受当前 data URL 输入格式；
- CosyVoice WebSocket 在真实网络下是否完整返回音频分片；
- iOS 端是否能播放后端返回的 `audioBase64`。

### 8.3 真实商品与次数包

虽然当前后端已经返回“购买次数包”方向的提示，但实际的商品、购买流程和权益发放还未接上。

---

## 9. 当前结论

当前方案已经满足：

- 非付费内容在 backend 停止后仍然可继续使用（设备自带 OCR / 设备自带朗读）
- 云端能力不会默认偷跑
- 云端次数不会无限制消耗
- 后续继续接 provider 与次数包时，不需要推翻当前结构

也就是说，这是一套：

- **低运维**
- **低成本**
- **低法律风险**
- **便于后续商业化扩展**

的当前最优结构。
