# Paipai Reading 阿里百炼云端 OCR / TTS DB 配置运维说明

## 1. 目的

本说明用于指导后续运维或你本人手动调整 reading 的阿里百炼云端 provider 配置。

当前实现已经支持：

- OCR：DashScope OpenAI-compatible HTTP
- TTS：CosyVoice WebSocket
- 通过数据库切换：
  - 新加坡
  - 美国（弗吉尼亚）
  - 北京
- 通过数据库修改：
  - endpoint / wsUrl
  - headers 模板
  - 模型名称
  - 音色
  - 采样率 / 语速 / 音调等

---

## 2. 配置所在位置

表：

```text
sys_remote_config
```

过滤条件：

```text
app_code = 'reading'
namespace_code = 'cloud_provider'
status = 'active'
```

---

## 3. OCR 配置 key

| key | 说明 |
| --- | --- |
| `ocr.vendor` | provider 名称，当前为 `alibaba_bailian` |
| `ocr.region` | 地域标识，例如 `singapore` / `us_virginia` / `beijing` |
| `ocr.endpoint` | OCR HTTP 接口地址 |
| `ocr.apiKeyEnvName` | API Key 对应的环境变量名 |
| `ocr.headers` | 请求头模板，支持 `${API_KEY}` 占位 |
| `ocr.model` | OCR 模型名称 |
| `ocr.prompt` | 默认识别 prompt |
| `ocr.minPixels` | 最小像素阈值 |
| `ocr.maxPixels` | 最大像素阈值 |

---

## 4. TTS 配置 key

| key | 说明 |
| --- | --- |
| `tts.vendor` | provider 名称，当前为 `alibaba_bailian` |
| `tts.region` | 地域标识 |
| `tts.wsUrl` | CosyVoice WebSocket URL |
| `tts.apiKeyEnvName` | API Key 对应的环境变量名 |
| `tts.headers` | 握手请求头模板，支持 `${API_KEY}` 占位 |
| `tts.model` | TTS 模型 |
| `tts.voice` | 音色 |
| `tts.format` | 音频格式，例如 `mp3` |
| `tts.sampleRate` | 采样率 |
| `tts.volume` | 音量 |
| `tts.rate` | 语速 |
| `tts.pitch` | 音调 |

---

## 5. 默认当前配置

### OCR

- region: `singapore`
- endpoint:

```text
https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions
```

- model:

```text
qwen-vl-ocr-latest
```

### TTS

- region: `singapore`
- wsUrl:

```text
wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference
```

- model:

```text
cosyvoice-v3-flash
```

- voice:

```text
longanyang
```

---

## 6. API Key 注入方式

注意：**API Key 不存数据库。**

数据库只存：

- `apiKeyEnvName`
- `headers` 模板

例如：

```json
{"value":"DASHSCOPE_API_KEY"}
```

以及：

```json
{"value":{"Authorization":"Bearer ${API_KEY}","Content-Type":"application/json"}}
```

运行时：

- 代码先读取 `apiKeyEnvName`
- 再从系统环境变量中拿值
- 用 `${API_KEY}` 替换到 headers 中

---

## 7. 切新加坡 / 美国 / 北京的推荐配置

## 7.1 新加坡

### OCR
```text
ocr.region = singapore
ocr.endpoint = https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions
ocr.apiKeyEnvName = DASHSCOPE_API_KEY
```

### TTS
```text
tts.region = singapore
tts.wsUrl = wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference
tts.apiKeyEnvName = DASHSCOPE_API_KEY
```

---

## 7.2 美国（弗吉尼亚）

### OCR
```text
ocr.region = us_virginia
ocr.endpoint = https://dashscope-us.aliyuncs.com/compatible-mode/v1/chat/completions
ocr.apiKeyEnvName = DASHSCOPE_API_KEY_US
```

### TTS
> 百炼文档里 OCR 兼容 OpenAI 接口明确给了美国 endpoint；TTS 现阶段代码结构已支持切 region，但是否使用美国 WebSocket URL，仍应以百炼对应地域文档和账号权限为准。

如果后续拿到美国 TTS 可用地址，再填：

```text
tts.region = us_virginia
tts.wsUrl = <美国 TTS WebSocket URL>
tts.apiKeyEnvName = DASHSCOPE_API_KEY_US
```

---

## 7.3 北京

### OCR
```text
ocr.region = beijing
ocr.endpoint = https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
ocr.apiKeyEnvName = DASHSCOPE_API_KEY_CN
```

### TTS
```text
tts.region = beijing
tts.wsUrl = wss://dashscope.aliyuncs.com/api-ws/v1/inference
tts.apiKeyEnvName = DASHSCOPE_API_KEY_CN
```

---

## 8. 推荐 SQL 更新模板

> 下面示例适合你手动在数据库里改。

### 8.1 切 OCR 到美国

```sql
UPDATE sys_remote_config
SET config_value_json = '{"value":"us_virginia"}', updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'reading' AND namespace_code = 'cloud_provider' AND config_key = 'ocr.region' AND status = 'active';

UPDATE sys_remote_config
SET config_value_json = '{"value":"https://dashscope-us.aliyuncs.com/compatible-mode/v1/chat/completions"}', updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'reading' AND namespace_code = 'cloud_provider' AND config_key = 'ocr.endpoint' AND status = 'active';

UPDATE sys_remote_config
SET config_value_json = '{"value":"DASHSCOPE_API_KEY_US"}', updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'reading' AND namespace_code = 'cloud_provider' AND config_key = 'ocr.apiKeyEnvName' AND status = 'active';
```

### 8.2 切 OCR 到北京

```sql
UPDATE sys_remote_config
SET config_value_json = '{"value":"beijing"}', updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'reading' AND namespace_code = 'cloud_provider' AND config_key = 'ocr.region' AND status = 'active';

UPDATE sys_remote_config
SET config_value_json = '{"value":"https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"}', updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'reading' AND namespace_code = 'cloud_provider' AND config_key = 'ocr.endpoint' AND status = 'active';

UPDATE sys_remote_config
SET config_value_json = '{"value":"DASHSCOPE_API_KEY_CN"}', updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'reading' AND namespace_code = 'cloud_provider' AND config_key = 'ocr.apiKeyEnvName' AND status = 'active';
```

### 8.3 切 TTS 到北京

```sql
UPDATE sys_remote_config
SET config_value_json = '{"value":"beijing"}', updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'reading' AND namespace_code = 'cloud_provider' AND config_key = 'tts.region' AND status = 'active';

UPDATE sys_remote_config
SET config_value_json = '{"value":"wss://dashscope.aliyuncs.com/api-ws/v1/inference"}', updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'reading' AND namespace_code = 'cloud_provider' AND config_key = 'tts.wsUrl' AND status = 'active';

UPDATE sys_remote_config
SET config_value_json = '{"value":"DASHSCOPE_API_KEY_CN"}', updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'reading' AND namespace_code = 'cloud_provider' AND config_key = 'tts.apiKeyEnvName' AND status = 'active';
```

---

## 9. headers 的改法

例如 OCR headers 当前为：

```json
{"value":{"Authorization":"Bearer ${API_KEY}","Content-Type":"application/json"}}
```

如果未来还要加额外 header，可以直接改数据库：

```sql
UPDATE sys_remote_config
SET config_value_json = '{"value":{"Authorization":"Bearer ${API_KEY}","Content-Type":"application/json","X-Custom-Header":"demo"}}', updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'reading' AND namespace_code = 'cloud_provider' AND config_key = 'ocr.headers' AND status = 'active';
```

---

## 10. 模型与音色改法

### OCR 模型

```sql
UPDATE sys_remote_config
SET config_value_json = '{"value":"qwen-vl-ocr-latest"}', updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'reading' AND namespace_code = 'cloud_provider' AND config_key = 'ocr.model' AND status = 'active';
```

### TTS 模型和音色

```sql
UPDATE sys_remote_config
SET config_value_json = '{"value":"cosyvoice-v3-plus"}', updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'reading' AND namespace_code = 'cloud_provider' AND config_key = 'tts.model' AND status = 'active';

UPDATE sys_remote_config
SET config_value_json = '{"value":"longxiaochun"}', updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'reading' AND namespace_code = 'cloud_provider' AND config_key = 'tts.voice' AND status = 'active';
```

---

## 11. 当前仍需真环境确认的点

1. `DASHSCOPE_API_KEY` / `DASHSCOPE_API_KEY_US` / `DASHSCOPE_API_KEY_CN` 是否已实际注入
2. 对应 region 的 API Key 是否和 endpoint 匹配
3. OCR data URL 输入是否在真实百炼账号下可正常使用
4. TTS WebSocket 在真实环境是否能完整回收音频分片
5. iOS 端是否能正常播放返回的 `audioBase64`

---

## 12. 结论

当前这套实现已经满足：

- provider 配置不写死在代码里
- 可以通过数据库切 region / endpoint / headers / model / voice
- API Key 仍不落库，避免泄露风险
- 后续要从新加坡切美国 / 北京时，不需要再改 Java 主逻辑
