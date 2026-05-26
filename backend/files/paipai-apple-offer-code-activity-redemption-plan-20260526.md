# 拍拍伴读活动码兑换功能模块产品技术说明

版本：2026-05-26  
模块名称：活动码兑换  
适用目录：`backend/files`  
适用平台：iOS / iPadOS  
苹果机制：App Store Connect In-App Purchase Offer Codes + StoreKit 2 + App Store Server API

## 1. 官方规则

### 1.1 支持范围

| 项 | 规则 |
| --- | --- |
| 可兑换对象 | App Store Connect 中已配置的 In-App Purchase offer。 |
| 支持 IAP 类型 | Consumable、Non-Consumable、Non-Renewing Subscription、Auto-Renewable Subscription。 |
| 兑换结果 | Apple 生成 IAP transaction；App 和后端按交易发放权益。 |
| 免费兑换 | 支持。Offer 创建时选择 `Free`，不是把 IAP 商品价格设为免费。 |
| 折扣兑换 | 支持。Offer 创建时选择 `Paid Offer` 并设置折扣价格。 |
| App 内输入兑换码 | 支持，但只能通过 Apple 系统兑换页输入；禁止自定义输入框提交 code 给后端或 Apple。 |
| App 外兑换 | 支持 redemption URL 或 App Store 兑换入口。 |
| 生产 code 前提 | App 状态为 `Ready for Distribution`，关联 IAP 状态为 `Approved`。 |
| Active offers 限制 | 每个 App 最多 10 个 active offers。 |
| 总兑换额度 | 每个 App 每季度最多 1,000,000 个 code 额度，所有 IAP 共享。 |
| 用户兑换限制 | 同一 Apple customer 每个 offer 最多兑换 1 个 code。 |

### 1.2 Code 类型

| 类型 | 是否单码单次 | 是否单码多人 | 创建者 | code 形态 | 数量限制 | 过期规则 | 兑换入口 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| One-time-use codes | 是。每个 code 只能兑换 1 次。 | 否。 | Apple 随机生成。 | 唯一随机码。 | 每批 500 到 25,000；App 每季度总额 1,000,000。 | 最长 6 个月；到期日 PT 00:00 失效。 | redemption URL、App Store 手动输入、App 内 Apple 系统兑换页。 |
| Custom codes | 否。 | 是。可被多个用户兑换，直到达到设置上限。 | 开发者在 App Store Connect 创建。 | 最多 64 字符，不含特殊字符。 | 单次设置最多 25,000 人；需要更多时对同一 offer 继续创建同名 custom code；App 每季度总额 1,000,000。 | 可设置到期日，最长 6 个月；也可选择 no end date。补偿活动必须设置到期日。 | redemption URL、App Store 手动输入、App 内 Apple 系统兑换页。 |
| Sandbox codes | 是。测试码。 | Sandbox Apple Account 不强制生产兑换限制。 | Apple 随机生成。 | 唯一随机码。 | 每批 10 到 10,000；App 每季度总额 10,000。 | 可选，到期最长 6 个月。 | Sandbox Account settings、App 内 Apple 系统兑换页。 |

### 1.3 免费兑换规则

| 项 | 规则 |
| --- | --- |
| 商品价格 | IAP 商品保留正常价格档位。 |
| Offer 价格 | Offer 创建时选择 `Free`。 |
| 交易性质 | 仍是 App Store IAP transaction。 |
| 发放依据 | 后端以 Apple signed transaction 的 `productId`、`type`、`offerType`、`offerIdentifier`、`transactionId` 为准。 |
| 禁止规则 | 禁止后端按用户输入 code 直接增加积分、次数或权益。 |
| 财务字段 | `price` / `currency` 只作交易记录，不作为财务对账来源。 |

### 1.4 技术限制

| 限制 | 开发规则 |
| --- | --- |
| StoreKit 不提供 `redeem(code: String)` 业务 API | 前端不得实现自定义 code 输入并提交后端兑换。 |
| App 内兑换页由 Apple 控制 | 使用 `offerCodeRedemption(isPresented:onCompletion:)` 或 `AppStore.presentOfferCodeRedeemSheet(in:)`。 |
| 消耗型等 IAP App 内兑换系统要求 | iOS / iPadOS 16.3+。低版本只展示 App Store / redemption URL 兑换指引。 |
| 用户可在 App 外兑换 | App 必须在启动时监听 StoreKit transaction updates。 |
| App 外兑换可能无 `appAccountToken` | 后端允许首次客户端提交时绑定当前用户；必要时调用 App Store Server API `Set App Account Token`。 |
| 同一用户需要多次补偿同一面额 | 不能使用同一个 offer；需要创建不同 offer 或不同 IAP product。 |
| Custom code 不能精确绑定单个用户 | 只能用兑换总量、有效期、Apple eligibility 和运营发放记录控量。 |
| Deactivate offer codes | 未兑换 code 立即失效；已兑换交易不受影响；额度不返还到季度限额。 |

## 2. 业务规则

### 2.1 模块命名

| 原名称 | 新名称 |
| --- | --- |
| 权益补偿 | 活动码兑换 |
| 补偿码 | 活动码 |
| 补偿记录 | 活动码兑换记录 |
| 后端补偿码生成 | Apple Offer Codes 发放 |

### 2.2 前端业务规则

| 编号 | 规则 |
| --- | --- |
| F-01 | 家长中心入口名称为 `活动码兑换`。 |
| F-02 | 页面主操作为 `打开 Apple 活动码兑换`。 |
| F-03 | 用户输入 code 的界面只能是 Apple 系统兑换页。 |
| F-04 | 禁止展示自定义 TextField。 |
| F-05 | 禁止校验 `PP-ABCDE-FGHJK-MNPQR` 等开发者自定义格式。 |
| F-06 | iOS / iPadOS 16.3+ 调起 Apple 系统兑换页。 |
| F-07 | iOS / iPadOS 16.3 以下展示 redemption URL / App Store 兑换指引，不调起系统页。 |
| F-08 | App 启动时立即注册 StoreKit transaction listener。 |
| F-09 | 只处理 `VerificationResult.verified` transaction。 |
| F-10 | 交易提交后端成功前不得 `finish()`。 |
| F-11 | 后端返回 `verified_granted` 或 `duplicate_granted` 后调用 `transaction.finish()`。 |
| F-12 | 后端返回 `pending_apple_lookup` 时不 `finish()`，进入本地重试队列。 |
| F-13 | 权益记录来源显示为 `活动码兑换`。 |

### 2.3 后端业务规则

| 编号 | 规则 |
| --- | --- |
| B-01 | 不提供 `POST /redeem-code` 或 `POST /compensation/redeem` 这种 code 字符串兑换接口。 |
| B-02 | 只接收 Apple transaction。 |
| B-03 | 必须验证客户端提交的 `signedTransactionInfo`。 |
| B-04 | 必须调用 App Store Server API `Get Transaction Info` 复核 transaction。 |
| B-05 | 以后端解码 Apple JWS 后的字段作为发放依据。 |
| B-06 | `offerType` 必须为 Apple offer code 类型。StoreKit 中对应 `Transaction.OfferType.code`，raw value 为 `3`。 |
| B-07 | `productId` 必须命中活动码允许兑换商品表。 |
| B-08 | `bundleId`、`environment`、`appAppleId` 或 app 配置必须与当前 App 匹配。 |
| B-09 | `transactionId` 全局幂等，已处理交易不得重复发放。 |
| B-10 | `revocationDate`、`revocationReason` 或退款通知存在时不得新增发放。 |
| B-11 | 权益数量只能由后端商品映射表计算，禁止信任客户端传入数量。 |
| B-12 | 无 `appAccountToken` 的交易允许首次绑定到当前登录用户或匿名设备。 |
| B-13 | 已绑定到其他用户的 transaction 再次提交时返回 `ownership_conflict`。 |
| B-14 | App 外兑换先到达通知但无用户归属时，记录为 `unassigned_verified`，等待客户端提交绑定。 |
| B-15 | 绑定完成后，如交易缺少 `appAccountToken`，后端可调用 Apple `Set App Account Token` 写入当前用户 UUID。 |
| B-16 | App Store Server Notifications V2 收到 `ONE_TIME_CHARGE` 时，按 consumable / non-consumable / non-renewing 活动码交易处理。 |
| B-17 | App Store Server Notifications V2 收到 `SUBSCRIBED` 或 `OFFER_REDEEMED` 时，按 auto-renewable subscription 活动码交易处理。 |

### 2.4 数据库业务规则

| 编号 | 规则 |
| --- | --- |
| D-01 | 不再新增 `sys_compensation_code`。 |
| D-02 | 不再新增 `sys_user_compensation_record`。 |
| D-03 | 活动码配置只保存 Apple offer、product、campaign、发放批次和交易结果。 |
| D-04 | One-time-use 原始 code 如需存储，必须加密；查询和去重使用 HMAC hash。 |
| D-05 | Custom code 原始值可按运营需要加密存储；不得作为后端兑换凭据。 |
| D-06 | 交易表保存 Apple JWS 摘要字段和加密原文。 |
| D-07 | 权益发放表必须以 `transaction_id` 做幂等唯一键。 |

## 3. 前端接口与数据结构

### 3.1 页面状态接口

```http
GET /api/v1/activity-code/config
```

响应：

```json
{
  "enabled": true,
  "minInAppRedemptionOS": "16.3",
  "title": "活动码兑换",
  "primaryActionTitle": "打开 Apple 活动码兑换",
  "fallbackText": "请使用 Apple 兑换链接或前往 App Store 兑换。"
}
```

### 3.2 StoreKit 调用

SwiftUI：

```swift
.offerCodeRedemption(isPresented: $isRedeeming) { result in
    // result 只代表系统页流程结束；权益发放以后续 transaction 为准。
}
```

UIKit：

```swift
try await AppStore.presentOfferCodeRedeemSheet(in: windowScene)
```

禁止：

```swift
// 禁止：无 Apple 官方兑换能力
POST /api/v1/activity-code/redeem { "code": "SUMMER25" }
```

### 3.3 客户端交易提交

```http
POST /api/v1/billing/app-store/transactions/intake
Content-Type: application/json
Authorization: Bearer <session>
```

请求：

```json
{
  "source": "offer_code_redemption",
  "productId": "com.paipai.readalong.local.ocr.50",
  "transactionId": "2000000000000000",
  "originalTransactionId": "2000000000000000",
  "environment": "Production",
  "appAccountToken": "550e8400-e29b-41d4-a716-446655440000",
  "appTransactionId": "711000000000000000",
  "offerType": "code",
  "offerIdentifier": "INCIDENT_20260526_FREE_50",
  "signedTransactionInfo": "eyJhbGciOiJFUzI1NiIsIng1YyI6Wy..."
}
```

字段规则：

| 字段 | 必填 | 来源 | 规则 |
| --- | --- | --- | --- |
| `source` | 是 | App | 固定 `offer_code_redemption`。 |
| `productId` | 是 | StoreKit transaction | 与 Apple JWS 复核一致。 |
| `transactionId` | 是 | StoreKit transaction | 与 Apple JWS 复核一致。 |
| `originalTransactionId` | 是 | StoreKit transaction | 与 Apple JWS 复核一致。 |
| `environment` | 是 | StoreKit transaction | `Sandbox` / `Production`。 |
| `appAccountToken` | 否 | StoreKit transaction / App 当前用户 | Apple JWS 缺失时后端可按当前用户绑定。 |
| `appTransactionId` | 否 | StoreKit AppTransaction / Transaction | 用于 App 外兑换关联。 |
| `offerType` | 否 | StoreKit transaction | 客户端辅助字段；后端以 Apple JWS 为准。 |
| `offerIdentifier` | 否 | StoreKit transaction | 客户端辅助字段；后端以 Apple JWS 为准。 |
| `signedTransactionInfo` | 是 | StoreKit `Transaction.jwsRepresentation` | 后端验签。 |

响应：

```json
{
  "status": "verified_granted",
  "transactionId": "2000000000000000",
  "productId": "com.paipai.readalong.local.ocr.50",
  "grantType": "apple_offer_code",
  "serviceType": "local_ocr",
  "quantity": 50,
  "finishAllowed": true,
  "accountStateRefreshRequired": true
}
```

`status` 枚举：

| 值 | finishAllowed | 含义 |
| --- | --- | --- |
| `verified_granted` | true | 已验证并发放。 |
| `duplicate_granted` | true | 交易已处理，本次为幂等返回。 |
| `pending_apple_lookup` | false | Apple Server API 暂不可用，等待重试。 |
| `unassigned_verified` | false | 交易已验证但未绑定用户。 |
| `rejected` | false | 交易不符合业务规则。 |

## 4. 后端接口

### 4.1 交易 intake

```http
POST /api/v1/billing/app-store/transactions/intake
```

处理顺序：

1. 校验登录态或匿名设备态。
2. 校验请求字段非空。
3. 验证客户端 `signedTransactionInfo` JWS。
4. 调用 Apple `GET /inApps/v1/transactions/{transactionId}`。
5. 验证 Apple 返回的 `signedTransactionInfo` JWS。
6. 比对 `transactionId`、`originalTransactionId`、`productId`、`bundleId`、`environment`。
7. 校验 `offerType == 3` 或等价 `code`。
8. 校验 `productId` 在 `activity_code_offer_product` allowlist。
9. 校验 `offerIdentifier` 命中 active offer 配置；无法稳定命中时只按 `productId + offerType` 控制。
10. 校验交易未 revoked / refunded。
11. 幂等写入 `app_store_transaction`。
12. 绑定 `user_id` / `anonymous_device_id`。
13. 生成 `entitlement_grant`。
14. 返回发放结果。

### 4.2 活动配置管理

```http
POST /api/v1/admin/activity-code/offers
```

请求：

```json
{
  "campaignCode": "INCIDENT_20260526",
  "offerReferenceName": "INCIDENT_20260526_FREE_50",
  "offerKind": "one_time_use",
  "productId": "com.paipai.readalong.local.ocr.50",
  "serviceType": "local_ocr",
  "quantity": 50,
  "environment": "Production",
  "startsAt": "2026-05-26T00:00:00Z",
  "endsAt": "2026-06-25T23:59:59Z",
  "status": "active"
}
```

响应：

```json
{
  "id": 10001,
  "status": "active"
}
```

### 4.3 One-time-use code 批次导入

```http
POST /api/v1/admin/activity-code/code-batches/import
Content-Type: multipart/form-data
```

表单字段：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `offerId` | 是 | `activity_code_offer.id`。 |
| `csvFile` | 是 | App Store Connect 下载的 code CSV。 |
| `encryptPlainCode` | 否 | 默认 `true`。 |

导入规则：

1. 原始 code 加密存储到 `code_ciphertext`。
2. `code_hash = HMAC_SHA256(app_secret, normalized_code)`。
3. 不提供按 code 兑换接口。
4. 仅用于运营发放、查重和客服追踪。

### 4.4 App Account Token 绑定

```http
PUT https://api.storekit.apple.com/inApps/v1/transactions/{originalTransactionId}/appAccountToken
```

调用条件：

| 条件 | 规则 |
| --- | --- |
| Apple JWS 缺少 `appAccountToken` | 可调用。 |
| 交易已绑定当前系统用户 | 可调用。 |
| `originalTransactionId` 不是原始交易 ID | 不调用。 |
| Family Sharing transaction | 不调用。 |
| 已绑定其他系统用户 | 不调用。 |

请求体：

```json
{
  "appAccountToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 4.5 App Store Server Notifications V2

| notificationType | productType | 处理 |
| --- | --- | --- |
| `ONE_TIME_CHARGE` | Consumable / Non-Consumable / Non-Renewing Subscription | 验签 signedPayload，保存通知，拉取 transaction，按 `offerType == 3` 记录活动码交易。 |
| `SUBSCRIBED` | Auto-Renewable Subscription 初购或重新订阅 | 验签 signedPayload，保存通知，拉取 transaction，按订阅权益规则处理。 |
| `OFFER_REDEEMED` | Active Auto-Renewable Subscription | 验签 signedPayload，保存通知，刷新订阅状态。 |
| `REFUND` / `REVOKE` | 任意 | 撤销或调整未消耗权益。 |

处理规则：

1. 通知必须按 `notificationUUID` 幂等。
2. 通知先于客户端 intake 到达时，transaction 记录为 `unassigned_verified`。
3. 客户端 intake 后补齐 `user_id` / `anonymous_device_id` 并发放权益。
4. 后端不得仅凭通知发放到未知用户。

## 5. 数据库结构

### 5.1 活动 offer 表

```sql
CREATE TABLE activity_code_offer (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    campaign_code VARCHAR(64) NOT NULL,
    offer_reference_name VARCHAR(128) NOT NULL,
    offer_kind VARCHAR(32) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    environment VARCHAR(32) NOT NULL,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_activity_code_offer_ref UNIQUE (app_code, environment, offer_reference_name),
    CONSTRAINT ck_activity_code_offer_kind CHECK (offer_kind IN ('one_time_use', 'custom', 'sandbox')),
    CONSTRAINT ck_activity_code_offer_status CHECK (status IN ('draft', 'active', 'expired', 'deactivated')),
    CONSTRAINT ck_activity_code_offer_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_activity_code_offer_product
    ON activity_code_offer (app_code, environment, product_id, status);
```

### 5.2 code 发放表

```sql
CREATE TABLE activity_code_distribution (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    offer_id BIGINT NOT NULL REFERENCES activity_code_offer(id),
    campaign_code VARCHAR(64) NOT NULL,
    code_hash VARCHAR(128),
    code_ciphertext TEXT,
    recipient_user_id BIGINT,
    recipient_device_id VARCHAR(128),
    recipient_ticket_no VARCHAR(128),
    delivery_channel VARCHAR(32),
    delivery_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    delivered_at TIMESTAMPTZ,
    redeemed_transaction_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_activity_code_distribution_hash UNIQUE (app_code, code_hash),
    CONSTRAINT ck_activity_code_delivery_status CHECK (delivery_status IN ('pending', 'sent', 'failed', 'redeemed', 'voided'))
);

CREATE INDEX idx_activity_code_distribution_recipient
    ON activity_code_distribution (app_code, recipient_user_id, recipient_device_id);
```

### 5.3 Apple 交易表

```sql
CREATE TABLE app_store_transaction (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    user_id BIGINT,
    anonymous_device_id VARCHAR(128),
    source VARCHAR(64) NOT NULL,
    environment VARCHAR(32) NOT NULL,
    bundle_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    product_type VARCHAR(64),
    transaction_id VARCHAR(128) NOT NULL,
    original_transaction_id VARCHAR(128) NOT NULL,
    app_transaction_id VARCHAR(128),
    app_account_token UUID,
    offer_type INTEGER,
    offer_identifier VARCHAR(255),
    offer_discount_type VARCHAR(64),
    price_milliunits BIGINT,
    currency_code VARCHAR(8),
    storefront VARCHAR(8),
    purchase_date TIMESTAMPTZ,
    revocation_date TIMESTAMPTZ,
    verification_status VARCHAR(32) NOT NULL,
    assignment_status VARCHAR(32) NOT NULL,
    raw_signed_transaction_info_ciphertext TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_store_transaction UNIQUE (app_code, transaction_id),
    CONSTRAINT ck_app_store_transaction_source CHECK (source IN ('normal_purchase', 'offer_code_redemption')),
    CONSTRAINT ck_app_store_transaction_verification CHECK (verification_status IN ('pending', 'verified', 'rejected', 'revoked')),
    CONSTRAINT ck_app_store_transaction_assignment CHECK (assignment_status IN ('unassigned', 'assigned', 'conflict'))
);

CREATE INDEX idx_app_store_transaction_original
    ON app_store_transaction (app_code, original_transaction_id);

CREATE INDEX idx_app_store_transaction_user
    ON app_store_transaction (app_code, user_id, created_at DESC);
```

### 5.4 权益发放表

```sql
CREATE TABLE entitlement_grant (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    user_id BIGINT,
    anonymous_device_id VARCHAR(128),
    grant_type VARCHAR(64) NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(128) NOT NULL,
    original_transaction_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uk_entitlement_grant_transaction UNIQUE (app_code, grant_type, transaction_id, service_type),
    CONSTRAINT ck_entitlement_grant_type CHECK (grant_type IN ('apple_offer_code', 'app_store_purchase')),
    CONSTRAINT ck_entitlement_grant_quantity CHECK (quantity > 0),
    CONSTRAINT ck_entitlement_grant_status CHECK (status IN ('active', 'revoked'))
);

CREATE INDEX idx_entitlement_grant_user
    ON entitlement_grant (app_code, user_id, granted_at DESC);
```

### 5.5 Apple 通知表

```sql
CREATE TABLE app_store_notification (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    environment VARCHAR(32) NOT NULL,
    notification_uuid VARCHAR(128) NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    subtype VARCHAR(64),
    transaction_id VARCHAR(128),
    original_transaction_id VARCHAR(128),
    signed_payload_ciphertext TEXT NOT NULL,
    processing_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    CONSTRAINT uk_app_store_notification UNIQUE (app_code, notification_uuid),
    CONSTRAINT ck_app_store_notification_status CHECK (processing_status IN ('pending', 'processed', 'ignored', 'failed'))
);

CREATE INDEX idx_app_store_notification_transaction
    ON app_store_notification (app_code, transaction_id, original_transaction_id);
```

## 6. Apple JWS 字段映射

| Apple 字段 | 表字段 | 用途 |
| --- | --- | --- |
| `transactionId` | `transaction_id` | 幂等主键。 |
| `originalTransactionId` | `original_transaction_id` | App Account Token 绑定、订阅续期关联。 |
| `productId` | `product_id` | 商品映射。 |
| `type` | `product_type` | IAP 类型校验。 |
| `offerType` | `offer_type` | 必须为 code。 |
| `offerIdentifier` | `offer_identifier` | Apple offer 标识校验和记录。 |
| `offerDiscountType` | `offer_discount_type` | 免费 / 折扣记录。 |
| `price` | `price_milliunits` | 交易记录。 |
| `currency` | `currency_code` | 交易记录。 |
| `appAccountToken` | `app_account_token` | 用户关联。 |
| `appTransactionId` | `app_transaction_id` | App 外兑换关联。 |
| `bundleId` | `bundle_id` | App 校验。 |
| `environment` | `environment` | 沙盒 / 生产隔离。 |
| `storefront` | `storefront` | 地区记录。 |
| `purchaseDate` | `purchase_date` | 交易时间。 |
| `revocationDate` | `revocation_date` | 退款 / 撤销。 |

## 7. 状态机

### 7.1 交易状态

```text
pending -> verified -> assigned -> granted
pending -> rejected
verified -> unassigned_verified -> assigned -> granted
verified/granted -> revoked
```

### 7.2 发放规则

| 当前状态 | 事件 | 下一状态 |
| --- | --- | --- |
| `pending` | Apple Server API 验证通过且有用户归属 | `verified_granted` |
| `pending` | Apple Server API 验证通过但无用户归属 | `unassigned_verified` |
| `pending` | Apple Server API 失败可重试 | `pending_apple_lookup` |
| `pending` | JWS 无效或业务校验失败 | `rejected` |
| `verified_granted` | 同 transaction 再次提交 | `duplicate_granted` |
| `verified_granted` | Apple 退款 / 撤销 | `revoked` |

## 8. 错误码

| HTTP | code | 处理 |
| --- | --- | --- |
| 400 | `invalid_request` | 请求字段缺失或格式错误。 |
| 400 | `not_offer_code_transaction` | Apple 交易不是 offer code。 |
| 400 | `product_not_allowed` | 商品不在活动码 allowlist。 |
| 401 | `unauthorized` | 用户态缺失。 |
| 409 | `duplicate_transaction` | 幂等命中，返回既有发放结果。 |
| 409 | `ownership_conflict` | 交易已绑定其他用户。 |
| 410 | `legacy_compensation_disabled` | 旧补偿码接口已关闭。 |
| 422 | `transaction_revoked` | 交易已退款或撤销。 |
| 503 | `apple_lookup_pending` | Apple Server API 暂不可用。 |

## 9. 下线旧机制

### 9.1 前端删除

| 路径 | 动作 |
| --- | --- |
| `front/ios/PaipaiReadAlong/Features/Parent/CompensationCodeView.swift` | 替换为 `ActivityCodeRedemptionView`。 |
| `front/ios/PaipaiReadAlong/App/PaipaiReadAlongApp.swift` | 删除 `redeemCompensationCode`、补偿码本地去重、补偿码格式化。 |
| `front/ios/PaipaiReadAlong/Core/Services/BackendClient.swift` | 删除 `/api/v1/account/compensation/redeem` 调用。 |
| `front/ios/PaipaiReadAlong/Core/Utilities/AppScopedDefaults.swift` | 删除 `redeemedCompensationCodes`。 |

### 9.2 后端删除

| 路径 | 动作 |
| --- | --- |
| `backend/src/main/java/com/apphub/backend/sys/compensation/**` | 删除。 |
| `backend/src/main/java/com/apphub/backend/apps/reading/compensation/controller/ReadingCompensationCompatController.java` | 删除或保留 410 过渡。 |
| `backend/src/main/java/com/apphub/backend/common/config/MybatisPlusConfig.java` | 移除 `sys.compensation.mapper` 扫描。 |
| `backend/files/compensation-generate-api-call-guide.md` | 标记废弃。 |

### 9.3 数据库删除

| 对象 | 动作 |
| --- | --- |
| `sys_compensation_code` | 旧客户端清零后 drop。 |
| `sys_user_compensation_record` | 归档后 drop。 |
| `idx_sys_compensation_code_*` | 随表删除。 |
| `idx_sys_user_compensation_record_*` | 随表删除。 |
| `uk_sys_compensation_code_app_code` | 随表删除。 |
| `uk_sys_user_compensation_record_app_code_claim` | 随表删除。 |

## 10. 验收用例

| 编号 | 用例 | 预期 |
| --- | --- | --- |
| T-01 | iOS 16.3+ 点击活动码兑换 | 打开 Apple 系统兑换页。 |
| T-02 | iOS 16.2 点击活动码兑换 | 不打开系统页，展示 App Store / URL 兑换指引。 |
| T-03 | Sandbox one-time-use code 首次兑换 | 后端验证交易并发放权益。 |
| T-04 | 同一 transaction 重复提交 | 返回 `duplicate_granted`，不重复发放。 |
| T-05 | 非 offer code 普通购买提交为活动码来源 | 返回 `not_offer_code_transaction`。 |
| T-06 | 不在 allowlist 的 productId | 返回 `product_not_allowed`。 |
| T-07 | Apple Server API 5xx | 返回 `pending_apple_lookup`，客户端不 finish。 |
| T-08 | App 外兑换后启动 App | StoreKit listener 捕获 transaction 并提交后端。 |
| T-09 | 无 `appAccountToken` 的 App 外兑换 | 首次提交绑定当前用户，必要时调用 `Set App Account Token`。 |
| T-10 | 已绑定其他用户的 transaction 再提交 | 返回 `ownership_conflict`。 |
| T-11 | refund / revocation 通知到达 | 权益状态改为 `revoked`。 |
| T-12 | 旧 `/api/v1/account/compensation/redeem` | 返回 410。 |

## 11. 官方依据

| 来源 | URL |
| --- | --- |
| App Store Connect - Create offer codes for In-App Purchases | `https://developer.apple.com/help/app-store-connect/manage-in-app-purchases/create-offer-codes-for-in-app-purchases` |
| StoreKit - Supporting offer codes in your app | `https://developer.apple.com/documentation/storekit/supporting-offer-codes-in-your-app` |
| StoreKit - presentOfferCodeRedeemSheet | `https://developer.apple.com/documentation/storekit/appstore/presentoffercoderedeemsheet(in:)` |
| StoreKit - Transaction.OfferType.code | `https://developer.apple.com/documentation/storekit/transaction/offertype-swift.struct/code` |
| App Store Server API - JWSTransactionDecodedPayload | `https://developer.apple.com/documentation/appstoreserverapi/jwstransactiondecodedpayload` |
| App Store Server API - Get Transaction Info | `https://developer.apple.com/documentation/appstoreserverapi/transactioninforesponse` |
| App Store Server API - Set App Account Token | `https://developer.apple.com/documentation/appstoreserverapi/set-app-account-token` |
| App Review Guidelines | `https://developer.apple.com/app-store/review/guidelines/` |
