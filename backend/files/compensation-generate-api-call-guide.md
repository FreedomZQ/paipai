# 拍拍伴读补偿申请生成 API 调用文档

## 1. 接口概述

`POST /api/v1/system/compensation-applications?appCode=paipai_readingcompanion` 用于运营后台、客服工具或受控运维脚本提交补偿申请，由后端生成一条未使用的补偿码记录。生成后的补偿码可交给用户在 App 家长中心「权益补偿」页面兑换，兑换成功后发放对应次数权益，并同步展示到权益信息页和首页权益统计。

适用场景：

- 云端 OCR、云端朗读、本地识别、本地朗读服务异常后的人工补偿。
- 客服按工单为指定用户或设备提供一次性补偿码。
- 运营批量生成受控补偿码，再通过安全渠道发放给用户。

使用限制：

- 当前接口仅生成 `usage_credit` 类型次数补偿，不生成套餐方案补偿。
- `appCode` 必须显式传入，拍拍伴读固定为 `paipai_readingcompanion`。
- 必须携带有效 `X-Admin-Config-Token`，该 token 来自后端配置 `backend.apps.paipai_readingcompanion.admin.configToken`。
- 默认 `claimScope=single_use`，补偿码只能兑换一次；需要多设备各兑换一次时传 `claimScope=multi_device_once` 且 `maxUses >= 2`。
- 补偿码格式固定为 `PP-ABCDE-FGHJK-MNPQR`，也接受不带横线的同等格式；不传时由后端自动生成。

相关前端体验：

- 兑换成功后，App 显示「补偿兑换成功」弹窗，并刷新账号权益、云端用量、权益记录缓存和首页统计。
- 兑换失败后，App 显示用户友好的失败原因，不展示 HTTP 状态码、traceId、requestId 或内部错误码。

## 2. Swagger / OpenAPI 规范定义

```yaml
openapi: 3.0.3
info:
  title: 拍拍伴读系统补偿申请 API
  version: 1.0.0
  description: 后台生成补偿码，供用户在家长中心兑换次数权益。
servers:
  - url: https://api.example.com
paths:
  /api/v1/system/compensation-applications:
    post:
      tags:
        - 系统补偿申请
      summary: 提交补偿申请并生成补偿记录
      description: |
        后台或运营系统提交补偿申请信息，后端完成参数校验、管理 token 校验，
        并生成一个未使用的 usage_credit 补偿码记录。用户随后可在 App 家长区兑换。
      parameters:
        - name: appCode
          in: query
          required: true
          schema:
            type: string
            example: paipai_readingcompanion
          description: 应用编码。拍拍伴读固定为 paipai_readingcompanion。
        - name: X-Admin-Config-Token
          in: header
          required: true
          schema:
            type: string
          description: 后台管理 token。生产环境必须通过安全配置注入，不得写入前端或公开仓库。
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CompensationApplicationRequest'
            examples:
              singleUse:
                summary: 单次兑换补偿
                value:
                  reason: 云端朗读服务异常补偿
                  remark: 工单 TICKET-20260516-0001
                  benefitKey: cloud_tts
                  compensationCount: 10
                  validDays: 30
              multiDevice:
                summary: 多设备各兑换一次
                value:
                  compensationCode: PP-ABCDE-FGHJK-MNPQR
                  reason: 区域服务波动补偿
                  remark: 批次 OPS-20260519-A
                  benefitKey: cloud_ocr
                  compensationCount: 5
                  validDays: 14
                  claimScope: multi_device_once
                  maxUses: 100
      responses:
        '200':
          description: 生成成功。
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiResponseCompensationCodeView'
        '400':
          description: 请求参数无效。
        '403':
          description: 管理 token 未配置、缺失或无效。
        '404':
          description: appCode 对应应用不存在。
        '409':
          description: 指定补偿码已存在。
components:
  schemas:
    CompensationApplicationRequest:
      type: object
      required:
        - reason
        - benefitKey
        - compensationCount
        - validDays
      properties:
        compensationCode:
          type: string
          nullable: true
          pattern: '^(|PP(?:-?[A-Z2-9]{5}){3})$'
          example: PP-ABCDE-FGHJK-MNPQR
          description: 可选指定补偿码；为空时后端自动生成。
        reason:
          type: string
          maxLength: 120
          example: 云端朗读服务异常补偿
          description: 补偿事由，用于客服和运营审计。
        remark:
          type: string
          maxLength: 300
          nullable: true
          example: 工单 TICKET-20260516-0001
          description: 备注，可填写工单号、批次号或补充说明。
        benefitKey:
          type: string
          enum: [local_ocr, local_tts, cloud_ocr, cloud_tts]
          example: cloud_tts
          description: 权益标识。
        compensationCount:
          type: integer
          minimum: 1
          maximum: 1000
          example: 10
          description: 补偿次数。
        validDays:
          type: integer
          minimum: 1
          maximum: 365
          example: 30
          description: 兑换后权益有效天数。
        expiresAt:
          type: string
          format: date-time
          nullable: true
          example: '2026-12-31T23:59:59Z'
          description: 补偿码自身过期时间；为空时按创建时间加 validDays 计算。
        claimScope:
          type: string
          enum: [single_use, multi_device_once]
          nullable: true
          default: single_use
          description: 领取范围。single_use 只能成功兑换一次；multi_device_once 允许多个设备各兑换一次。
        maxUses:
          type: integer
          minimum: 1
          maximum: 100000
          nullable: true
          default: 1
          description: 最大领取次数。single_use 固定为 1；multi_device_once 必须至少为 2。
    ApiResponseCompensationCodeView:
      type: object
      properties:
        success:
          type: boolean
          example: true
        requestId:
          type: string
          example: a8f1b1e0-9f2f-4e47-bb4e-90a4b2f6d111
        data:
          $ref: '#/components/schemas/CompensationCodeView'
        message:
          type: string
          nullable: true
    CompensationCodeView:
      type: object
      properties:
        id: { type: integer, format: int64, example: 123 }
        appCode: { type: string, example: paipai_readingcompanion }
        compensationCode: { type: string, example: PP-ABCDE-FGHJK-MNPQR }
        benefitType: { type: string, example: usage_credit }
        planCode: { type: string, nullable: true }
        entitlementCode: { type: string, nullable: true }
        serviceType: { type: string, example: cloud_tts }
        grantCount: { type: integer, example: 10 }
        grantValidDays: { type: integer, example: 30 }
        grantValidUntilAt: { type: string, format: date-time, nullable: true }
        expiresAt: { type: string, format: date-time, nullable: true }
        claimScope: { type: string, example: single_use }
        maxUses: { type: integer, example: 1 }
        usedCount: { type: integer, example: 0 }
        status: { type: string, example: unused }
        usedAt: { type: string, format: date-time, nullable: true }
        voidReason: { type: string, nullable: true }
        metadata: { type: object, additionalProperties: true }
        createdAt: { type: string, format: date-time }
        updatedAt: { type: string, format: date-time }
```

## 3. 请求参数说明

### 3.1 Query 参数

| 参数 | 类型 | 必填 | 默认值 | 取值范围 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `appCode` | string | 是 | 无 | `paipai_readingcompanion` | 应用编码，用于多 App 数据隔离。 |

### 3.2 Header 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `X-Admin-Config-Token` | string | 是 | 无 | 后台管理 token，后端按配置值精确匹配。 |
| `Content-Type` | string | 是 | 无 | 固定为 `application/json`。 |
| `Accept` | string | 否 | `application/json` | 建议显式传 `application/json`。 |

### 3.3 Body 参数

| 字段 | 类型 | 必填 | 默认值 | 取值范围 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `compensationCode` | string | 否 | 后端生成 | `PP(?:-?[A-Z2-9]{5}){3}` | 指定补偿码；建议只在客服需要复用外部编号时传入。 |
| `reason` | string | 是 | 无 | 1-120 字符 | 补偿事由，会进入审计备注。 |
| `remark` | string | 否 | 空 | 最多 300 字符 | 工单号、批次号、客服记录等补充信息。 |
| `benefitKey` | string | 是 | 无 | `local_ocr`、`local_tts`、`cloud_ocr`、`cloud_tts` | 待补偿的权益类型。 |
| `compensationCount` | integer | 是 | 无 | 1-1000 | 补偿次数。 |
| `validDays` | integer | 是 | 无 | 1-365 | 兑换后权益有效天数。 |
| `expiresAt` | string(date-time) | 否 | 创建时间 + `validDays` | ISO-8601 | 补偿码可兑换截止时间。 |
| `claimScope` | string | 否 | `single_use` | `single_use`、`multi_device_once` | 领取范围。 |
| `maxUses` | integer | 否 | `1` | 1-100000 | 最大领取次数；多设备码必须至少为 2。 |

权益标识说明：

| `benefitKey` | 中文含义 | 发放口径 |
| --- | --- | --- |
| `local_ocr` | 本地拍读/识别次数 | 兑换后进入本地 OCR 次数权益。 |
| `local_tts` | 本地朗读次数 | 兑换后进入本地 TTS 次数权益。 |
| `cloud_ocr` | 云端 OCR 次数 | 兑换后进入云端 OCR 次数权益。 |
| `cloud_tts` | 云端语音朗读次数 | 兑换后进入云端 TTS 次数权益。 |

## 4. 响应数据结构说明

成功响应示例：

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
    "grantValidUntilAt": "2026-06-18T10:00:00Z",
    "expiresAt": "2026-06-18T10:00:00Z",
    "claimScope": "single_use",
    "maxUses": 1,
    "usedCount": 0,
    "status": "unused",
    "usedAt": null,
    "voidReason": null,
    "metadata": {
      "note": "补偿事由：云端朗读服务异常补偿；备注：工单 TICKET-20260516-0001；操作来源：admin:10.0.0.12",
      "benefitType": "usage_credit"
    },
    "createdAt": "2026-05-19T10:00:00Z",
    "updatedAt": "2026-05-19T10:00:00Z"
  },
  "message": null
}
```

| 字段 | 类型 | 含义 | 可能取值 |
| --- | --- | --- | --- |
| `success` | boolean | 请求是否按业务成功返回。 | `true` |
| `requestId` | string | 后端链路追踪 ID。 | 字符串 |
| `data.id` | integer | 补偿码主记录 ID。 | 正整数 |
| `data.appCode` | string | 应用编码。 | `paipai_readingcompanion` |
| `data.compensationCode` | string | 生成或指定的补偿码。 | `PP-...` |
| `data.benefitType` | string | 补偿类型。 | 当前固定 `usage_credit` |
| `data.serviceType` | string | 次数权益类型。 | `local_ocr`、`local_tts`、`cloud_ocr`、`cloud_tts` |
| `data.grantCount` | integer | 兑换后发放次数。 | 1-1000 |
| `data.grantValidDays` | integer | 兑换后有效天数。 | 1-365 |
| `data.grantValidUntilAt` | string/null | 权益最终有效期。 | ISO-8601 |
| `data.expiresAt` | string/null | 补偿码兑换截止时间。 | ISO-8601 |
| `data.claimScope` | string | 领取范围。 | `single_use`、`multi_device_once` |
| `data.maxUses` | integer | 最大领取次数。 | 1-100000 |
| `data.usedCount` | integer | 已领取次数。 | 0 到 `maxUses` |
| `data.status` | string | 补偿码状态。 | `unused`、`used`、`voided` |
| `data.usedAt` | string/null | 首次或最终使用时间。 | ISO-8601 |
| `data.voidReason` | string/null | 作废原因。 | 字符串 |
| `data.metadata` | object | 审计信息。 | 包含 `note` 等 |
| `data.createdAt` | string | 创建时间。 | ISO-8601 |
| `data.updatedAt` | string | 更新时间。 | ISO-8601 |

## 5. 错误码说明

错误响应由 Spring Boot 全局错误处理返回，常见结构可能包含 `status`、`error`、`message`、`path`，或项目统一错误结构。调用方应以 HTTP 状态码和 `message` 作为主要判断依据。

| HTTP 状态 | 错误信息示例 | 触发原因 | 解决方案 |
| --- | --- | --- | --- |
| 400 | `APP_CODE_REQUIRED` | 未传 `appCode` 或传空字符串。 | 补齐 `?appCode=paipai_readingcompanion`。 |
| 400 | `权益标识仅支持 local_ocr、local_tts、cloud_ocr、cloud_tts` | `benefitKey` 不在允许范围。 | 改为受支持的权益标识。 |
| 400 | `补偿次数不能为空` / `补偿次数至少为1` | `compensationCount` 缺失或越界。 | 传 1-1000 的整数。 |
| 400 | `有效期天数不能为空` / `有效期天数至少为1` | `validDays` 缺失或越界。 | 传 1-365 的整数。 |
| 400 | `补偿码格式不正确` | 指定 `compensationCode` 格式不合法。 | 使用 `PP-ABCDE-FGHJK-MNPQR` 格式或不传。 |
| 400 | `多设备补偿码 maxUses 至少为2` | `claimScope=multi_device_once` 但 `maxUses` 小于 2。 | 将 `maxUses` 设置为 2 或更大。 |
| 403 | `ADMIN_CONFIG_TOKEN_NOT_CONFIGURED` | 后端未配置管理 token。 | 在服务端配置 `backend.apps.paipai_readingcompanion.admin.configToken`。 |
| 403 | `ADMIN_CONFIG_TOKEN_INVALID` | 请求头 token 缺失或不匹配。 | 使用正确的后台 token，检查环境变量和请求头。 |
| 404 | `应用不存在` | `appCode` 未在系统应用表或配置中注册。 | 检查应用初始化数据和传参。 |
| 409 | `补偿码已存在` | 指定的 `compensationCode` 已存在。 | 更换补偿码或不指定，让后端自动生成。 |
| 500 | `补偿码生成失败` | 自动生成多次仍发生碰撞或服务异常。 | 重试；若持续出现，检查数据库唯一索引和服务日志。 |

## 6. 接口调用示例

### 6.1 curl

```bash
curl -X POST 'https://api.example.com/api/v1/system/compensation-applications?appCode=paipai_readingcompanion' \
  -H 'X-Admin-Config-Token: ${ADMIN_CONFIG_TOKEN}' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{
    "reason": "云端朗读服务异常补偿",
    "remark": "工单 TICKET-20260516-0001",
    "benefitKey": "cloud_tts",
    "compensationCount": 10,
    "validDays": 30,
    "claimScope": "single_use",
    "maxUses": 1
  }'
```

### 6.2 Java 11+ HttpClient

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class CompensationGenerateExample {
    public static void main(String[] args) throws Exception {
        String token = System.getenv("ADMIN_CONFIG_TOKEN");
        String body = "{"
            + "\"reason\":\"云端朗读服务异常补偿\","
            + "\"remark\":\"工单 TICKET-20260516-0001\","
            + "\"benefitKey\":\"cloud_tts\","
            + "\"compensationCount\":10,"
            + "\"validDays\":30"
            + "}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/api/v1/system/compensation-applications?appCode=paipai_readingcompanion"))
            .header("X-Admin-Config-Token", token)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Generate failed: " + response.body());
        }
        System.out.println(response.body());
    }
}
```

### 6.3 Java OkHttp

```java
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CompensationOkHttpExample {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static void main(String[] args) throws Exception {
        OkHttpClient client = new OkHttpClient();
        String json = "{"
            + "\"reason\":\"云端 OCR 识别失败补偿\","
            + "\"remark\":\"客服手动补偿\","
            + "\"benefitKey\":\"cloud_ocr\","
            + "\"compensationCount\":5,"
            + "\"validDays\":14"
            + "}";

        Request request = new Request.Builder()
            .url("https://api.example.com/api/v1/system/compensation-applications?appCode=paipai_readingcompanion")
            .addHeader("X-Admin-Config-Token", System.getenv("ADMIN_CONFIG_TOKEN"))
            .addHeader("Accept", "application/json")
            .post(RequestBody.create(json, JSON))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Generate failed: " + response.code() + " " + response.body().string());
            }
            System.out.println(response.body().string());
        }
    }
}
```

### 6.4 Python requests

```python
import os
import requests

url = "https://api.example.com/api/v1/system/compensation-applications"
params = {"appCode": "paipai_readingcompanion"}
headers = {
    "X-Admin-Config-Token": os.environ["ADMIN_CONFIG_TOKEN"],
    "Content-Type": "application/json",
    "Accept": "application/json",
}
payload = {
    "reason": "云端朗读服务异常补偿",
    "remark": "工单 TICKET-20260516-0001",
    "benefitKey": "cloud_tts",
    "compensationCount": 10,
    "validDays": 30,
}

response = requests.post(url, params=params, headers=headers, json=payload, timeout=10)
response.raise_for_status()
print(response.json()["data"]["compensationCode"])
```

### 6.5 Node.js fetch

```javascript
const response = await fetch(
  "https://api.example.com/api/v1/system/compensation-applications?appCode=paipai_readingcompanion",
  {
    method: "POST",
    headers: {
      "X-Admin-Config-Token": process.env.ADMIN_CONFIG_TOKEN,
      "Content-Type": "application/json",
      "Accept": "application/json"
    },
    body: JSON.stringify({
      reason: "区域服务波动补偿",
      remark: "批次 OPS-20260519-A",
      benefitKey: "cloud_ocr",
      compensationCount: 5,
      validDays: 14,
      claimScope: "multi_device_once",
      maxUses: 100
    })
  }
);

if (!response.ok) {
  throw new Error(`Generate failed: ${response.status} ${await response.text()}`);
}
console.log(await response.json());
```

## 7. 安全说明

- 认证方式：接口使用 `X-Admin-Config-Token` 进行后台调用认证。token 必须只保存在服务端配置、CI/CD 密钥或运维密钥管理系统中。
- 权限要求：仅后台运营、客服主管或受控自动化任务可调用；普通 App 客户端不得调用该接口。
- 传输安全：生产环境必须使用 HTTPS；禁止在 URL、日志、截图或客服聊天中暴露 token。
- 数据隔离：调用时必须传入正确 `appCode`，避免多 App 共用后端时串权。
- 审计要求：`reason` 和 `remark` 应填写可追踪信息，如工单号、事件批次、补偿依据。后端会把来源 IP 写入 metadata note。
- 发放控制：优先使用自动生成补偿码；指定补偿码时需确保不可预测。多设备码必须合理设置 `maxUses`，避免无限扩散。
- 前端展示：兑换失败只展示业务原因，例如“补偿码已过期”“补偿码已使用”，不得向用户展示内部错误码、traceId、requestId 或栈信息。

## 8. 家长中心兑换与权益同步要求

已实现的前端优化点：

- 成功兑换后显示「补偿兑换成功」弹窗，并在页面保留本次兑换摘要，包含权益类型、补偿内容、有效期和当前权益。
- 失败兑换后显示「补偿兑换失败」弹窗，失败内容经过清洗，只展示用户可理解的原因。
- 成功后触发权益同步：刷新账号状态、云端用量、权益记录缓存、活跃权益摘要和首页数据。
- 权益信息页进入时仍会强制从后端同步记录；若用户刚完成兑换，缓存已经由兑换流程更新，页面可立即展示新记录。
- 首页顶部权益统计读取统一的 `entitlementDisplaySummary`，兑换后通过同步后的账号状态和权益记录摘要实时更新。
