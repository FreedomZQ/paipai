# 后端生成补偿 API 调用说明

## 1. 接口用途

该接口用于后台或运营系统提交补偿申请信息，由后端生成一条未使用的补偿码记录。

生成成功后，用户可在 App 家长区输入补偿码，并通过现有兑换接口 `/api/v1/account/compensation/redeem` 将权益补偿到当前账号。

接口实现稿文件：

- `backend/files/CompensationGenerateApiController.java`

> 注意：`backend/files` 是资料目录，不是当前 Spring Boot 编译源码目录。如需正式启用该接口，需要将实现文件移动到 `backend/src/main/java/com/apphub/backend/sys/compensation/controller/`，并将 package 改为 `com.apphub.backend.sys.compensation.controller`。

## 2. 请求信息

请求 URL：

```http
POST /api/v1/system/compensation-applications?appCode=paipai_readingcompanion
```

请求方法：

```http
POST
```

请求头：

```http
X-Admin-Config-Token: ${adminConfigToken}
Content-Type: application/json
Accept: application/json
```

权限要求：

- 必须携带有效 X-Admin-Config-Token。
- token 对应用户必须属于当前 `appCode`。
- 用户状态必须为 `active`。
- 游客账号 `guest` 不允许生成补偿记录。
- 生产环境建议继续接入更细粒度的后台角色，如 `admin`、`operator`、`support`。

## 3. Query 参数

| 参数 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| `appCode` | string | 是 | `paipai_readingcompanion` | 应用编码，避免多 App 串权。 |

## 4. 请求体字段

| 字段 | 类型 | 必填 | 规则 | 说明 |
| --- | --- | --- | --- | --- |
| `compensationCode` | string | 否 | `PP-ABCDE-FGHJK-MNPQR` 或空 | 可指定补偿码；不传则后端自动生成。 |
| `reason` | string | 是 | 1-120 字符 | 补偿事由，用于客服审计。 |
| `remark` | string | 否 | 最多 300 字符 | 备注，可填写工单号或补充说明。 |
| `benefitKey` | string | 是 | `capture` / `speech` / `cloud_ocr` / `cloud_tts` | 权益标识，沿用现有系统标识。 |
| `compensationCount` | integer | 是 | 1-1000 | 补偿次数。 |
| `validDays` | integer | 是 | 1-365 | 补偿权益有效期天数。 |

权益标识说明：

| 标识 | 含义 |
| --- | --- |
| `capture` | 本地拍读/识别次数 |
| `speech` | 本地朗读次数 |
| `cloud_ocr` | 云端 OCR 次数 |
| `cloud_tts` | 云端语音朗读次数 |

## 5. 请求示例

```bash
curl -X POST 'https://api.example.com/api/v1/system/compensation-applications?appCode=paipai_readingcompanion' \
  -H 'X-Admin-Config-Token: 7106e1405a154d068a166442dc773ad1ad10886a76144bc8ad6f4cbaa26354be' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{
    "reason": "云端朗读服务异常补偿",
    "remark": "工单 TICKET-20260516-0001",
    "benefitKey": "cloud_tts",
    "compensationCount": 10,
    "validDays": 30
  }'
```

指定补偿码示例：

```json
{
  "compensationCode": "PP-ABCDE-FGHJK-MNPQR",
  "reason": "云端 OCR 识别失败补偿",
  "remark": "客服手动补偿",
  "benefitKey": "cloud_ocr",
  "compensationCount": 5,
  "validDays": 14
}
```

## 6. 成功响应示例

```json
{
  "success": true,
  "requestId": "a8f1b1e0-9f2f-4e47-bb4e-90a4b2f6d111",
  "data": {
    "id": 123,
    "appCode": "paipai_readingcompanion",
    "compensationCode": "PP-ABCDE-FGHJK-MNPQR",
    "benefitType": "usage_credit",
    "planCode": null,
    "entitlementCode": null,
    "serviceType": "cloud_tts",
    "grantCount": 10,
    "grantValidDays": 30,
    "expiresAt": "2026-06-15T02:30:00Z",
    "maxUses": 1,
    "usedCount": 0,
    "status": "unused",
    "usedByUserId": null,
    "usedAt": null,
    "voidReason": null,
    "metadata": {
      "note": "补偿事由：云端朗读服务异常补偿；备注：工单 TICKET-20260516-0001",
      "benefitType": "usage_credit"
    },
    "createdByUserId": 10001,
    "createdAt": "2026-05-16T02:30:00Z",
    "updatedAt": "2026-05-16T02:30:00Z"
  },
  "message": null
}
```

## 7. 错误响应

实际错误响应由 Spring Boot 全局错误处理输出，常见字段包括 `status`、`error`、`message`、`path` 或项目统一错误结构。

### 7.1 参数无效

HTTP 状态码：

```http
400 Bad Request
```

示例：

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "权益标识仅支持 capture、speech、cloud_ocr、cloud_tts",
  "path": "/api/v1/system/compensation-applications"
}
```

触发场景：

- `appCode` 为空。
- `reason` 为空或超过 120 字符。
- `remark` 超过 300 字符。
- `benefitKey` 不是 `capture`、`speech`、`cloud_ocr`、`cloud_tts`。
- `compensationCount` 为空、小于 1 或大于 1000。
- `validDays` 为空、小于 1 或大于 365。
- `compensationCode` 格式不正确。

### 7.2 管理 token 缺失或无效

HTTP 状态码：

```http
401 Unauthorized
```

示例：

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "ADMIN_CONFIG_TOKEN_INVALID",
  "path": "/api/v1/system/compensation-applications"
}
```

### 7.3 无权限

HTTP 状态码：

```http
403 Forbidden
```

示例：

```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "无权为其他应用生成补偿记录",
  "path": "/api/v1/system/compensation-applications"
}
```

### 7.4 应用不存在

HTTP 状态码：

```http
404 Not Found
```

示例：

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "应用不存在",
  "path": "/api/v1/system/compensation-applications"
}
```

### 7.5 补偿码已存在

HTTP 状态码：

```http
409 Conflict
```

示例：

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "补偿码已存在",
  "path": "/api/v1/system/compensation-applications"
}
```

## 8. 后续兑换流程

用户拿到补偿码后，在 App 家长区输入补偿码。

兑换接口：

```http
POST /api/v1/account/compensation/redeem
```

请求体：

```json
{
  "compensationCode": "PP-ABCDE-FGHJK-MNPQR"
}
```

兑换成功后，系统会将对应的权益次数、有效期补偿到用户账号，并写入用户补偿领取记录。
