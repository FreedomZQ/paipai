# paipaiV2 PowerSync 可直接开工的 SQL 与接口稿 v1

日期：2026-04-20  
适用范围：`reading/paipaiV2` 第一版接入 PowerSync，兼容 unified backend 多 App 模式

---

## 0. 文档定位

本文档不是方案说明，而是：
- **可以直接开始写 migration 的 SQL 草案**
- **可以直接开始写 controller/service 的接口草案**
- **可以直接用于联调约定的 JSON 协议草案**

配套文档：
1. `paipaiV2-PowerSync多APP同步方案-v1.md`
2. `paipaiV2-PowerSync数据库表设计-v1.md`
3. `paipaiV2-PowerSync文件级开发任务清单-v1.md`

---

## 1. 统一约定

## 1.1 认证方式
### App -> unified backend
沿用当前 `Authorization: Bearer <session_token>`。

- token 来源：当前已有正式登录会话（Apple / 邮箱验证码）
- backend 使用现有 `sys_auth_session` 体系完成鉴权

### PowerSync token
PowerSync token 不是 App 自己生成，而是：
- App 调 unified backend
- unified backend 校验 session 后签发短期 token / credentials
- App 再用这些 credentials 连接 PowerSync Service

---

## 1.2 `appCode`
第一版固定：
- `reading`

后续其它 App：
- 使用相同通用接口，替换 path 中的 `{appCode}`

---

## 1.3 客户端安装实例标识
### `installationId`
- 客户端首次安装时生成 UUID
- 长期保存在本地 Keychain / Secure store
- 每台设备 + 每个安装实例唯一

建议长度：
- `VARCHAR(64)` 足够

---

## 1.4 统一响应包格式
延续现有 backend 风格：

```json
{
  "requestId": "req_xxx",
  "data": {}
}
```

错误沿用当前全局异常/HTTP status 体系。

---

## 2. SQL：V4 通用层 migration

建议文件名：
- `src/main/resources/db/migration/V4__powersync_installation_and_audit.sql`

## 2.1 建表：`sys_sync_installation`
```sql
CREATE TABLE IF NOT EXISTS sys_sync_installation (
    installation_id              VARCHAR(64) PRIMARY KEY,
    app_code                     VARCHAR(64) NOT NULL,
    user_id                      BIGINT NOT NULL,
    device_id                    VARCHAR(128),
    client_platform              VARCHAR(32) NOT NULL,
    device_model                 VARCHAR(128),
    app_version                  VARCHAR(64),
    powersync_client_id          VARCHAR(128),
    cloud_sync_enabled           BOOLEAN NOT NULL DEFAULT FALSE,
    initial_sync_completed       BOOLEAN NOT NULL DEFAULT FALSE,
    last_sync_at                 TIMESTAMPTZ,
    last_pull_at                 TIMESTAMPTZ,
    last_push_at                 TIMESTAMPTZ,
    last_error_code              VARCHAR(64),
    last_error_message           TEXT,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sys_sync_installation_app_user_installation
    ON sys_sync_installation (app_code, user_id, installation_id);

CREATE INDEX IF NOT EXISTS idx_sys_sync_installation_user_app
    ON sys_sync_installation (user_id, app_code);

CREATE INDEX IF NOT EXISTS idx_sys_sync_installation_app_sync
    ON sys_sync_installation (app_code, cloud_sync_enabled, updated_at DESC);
```

## 2.2 建表：`sys_sync_audit_log`
```sql
CREATE TABLE IF NOT EXISTS sys_sync_audit_log (
    id                           BIGSERIAL PRIMARY KEY,
    app_code                     VARCHAR(64) NOT NULL,
    user_id                      BIGINT NOT NULL,
    installation_id              VARCHAR(64),
    action_type                  VARCHAR(64) NOT NULL,
    entity_type                  VARCHAR(64),
    entity_id                    VARCHAR(128),
    request_id                   VARCHAR(128),
    result_status                VARCHAR(32) NOT NULL,
    detail_json                  JSONB,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sys_sync_audit_log_user_app_created
    ON sys_sync_audit_log (user_id, app_code, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sys_sync_audit_log_installation_created
    ON sys_sync_audit_log (installation_id, created_at DESC);
```

---

## 3. SQL：V5 reading 同步 schema migration

建议文件名：
- `src/main/resources/db/migration/V5__reading_sync_ready_schema.sql`

## 3.1 升级：`reading_child_profile`
```sql
ALTER TABLE reading_child_profile
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_modified_by_installation_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS record_version INTEGER NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_reading_child_profile_user_updated
    ON reading_child_profile (user_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_reading_child_profile_user_deleted
    ON reading_child_profile (user_id, deleted_at);
```

## 3.2 升级：`reading_review_card`
```sql
ALTER TABLE reading_review_card
    ADD COLUMN IF NOT EXISTS source_text TEXT,
    ADD COLUMN IF NOT EXISTS translated_text TEXT,
    ADD COLUMN IF NOT EXISTS source_language_code VARCHAR(32),
    ADD COLUMN IF NOT EXISTS target_language_code VARCHAR(32),
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS last_reviewed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_modified_by_installation_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS record_version INTEGER NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_reading_review_card_user_updated
    ON reading_review_card (user_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_reading_review_card_child_updated
    ON reading_review_card (child_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_reading_review_card_user_deleted
    ON reading_review_card (user_id, deleted_at);

CREATE INDEX IF NOT EXISTS idx_reading_review_card_user_next_review
    ON reading_review_card (user_id, next_review_at);
```

## 3.3 升级：`reading_user_preference`
```sql
ALTER TABLE reading_user_preference
    ADD COLUMN IF NOT EXISTS cloud_sync_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS last_modified_by_installation_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS record_version INTEGER NOT NULL DEFAULT 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_reading_user_preference_app_user
    ON reading_user_preference (app_code, user_id);
```

## 3.4 新建：`reading_review_event_v2`
```sql
CREATE TABLE IF NOT EXISTS reading_review_event_v2 (
    id                           VARCHAR(64) PRIMARY KEY,
    app_code                     VARCHAR(64) NOT NULL,
    user_id                      BIGINT NOT NULL,
    child_id                     VARCHAR(64) NOT NULL,
    card_id                      VARCHAR(64) NOT NULL,
    event_type                   VARCHAR(32) NOT NULL,
    result_level                 VARCHAR(32) NOT NULL,
    event_at                     TIMESTAMPTZ NOT NULL,
    last_modified_by_installation_id VARCHAR(64),
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reading_review_event_v2_user_event_at
    ON reading_review_event_v2 (user_id, event_at DESC);

CREATE INDEX IF NOT EXISTS idx_reading_review_event_v2_card_event_at
    ON reading_review_event_v2 (card_id, event_at DESC);

CREATE INDEX IF NOT EXISTS idx_reading_review_event_v2_child_event_at
    ON reading_review_event_v2 (child_id, event_at DESC);
```

## 3.5 新建：`reading_usage_session_v2`
```sql
CREATE TABLE IF NOT EXISTS reading_usage_session_v2 (
    id                           VARCHAR(64) PRIMARY KEY,
    app_code                     VARCHAR(64) NOT NULL,
    user_id                      BIGINT NOT NULL,
    child_id                     VARCHAR(64) NOT NULL,
    source_page                  VARCHAR(64) NOT NULL,
    started_at                   TIMESTAMPTZ NOT NULL,
    ended_at                     TIMESTAMPTZ,
    duration_seconds             INTEGER,
    client_platform              VARCHAR(32),
    device_model                 VARCHAR(128),
    last_modified_by_installation_id VARCHAR(64),
    deleted_at                   TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reading_usage_session_v2_user_started
    ON reading_usage_session_v2 (user_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_reading_usage_session_v2_child_started
    ON reading_usage_session_v2 (child_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_reading_usage_session_v2_user_deleted
    ON reading_usage_session_v2 (user_id, deleted_at);
```

---

## 4. PowerSync Sync Rules 草案（reading）

建议文件：
- `backend/powersync/apps/reading/sync-rules.yaml`

## 4.1 claims 假设
PowerSync token 中带：
- `appCode`
- `userId`
- `installationId`

## 4.2 草案（示意）
> 具体语法以接入的 PowerSync 版本文档为准，下面用于开发对齐，不保证可直接运行。

```yaml
bucket_definitions:
  reading_user_data:
    parameters: SELECT request.user_id() as user_id, request.jwt() ->> 'appCode' as app_code
    data:
      - SELECT * FROM reading_child_profile
        WHERE user_id = bucket.user_id
          AND app_code = bucket.app_code

      - SELECT * FROM reading_review_card
        WHERE user_id = bucket.user_id
          AND app_code = bucket.app_code

      - SELECT * FROM reading_review_event_v2
        WHERE user_id = bucket.user_id
          AND app_code = bucket.app_code

      - SELECT * FROM reading_usage_session_v2
        WHERE user_id = bucket.user_id
          AND app_code = bucket.app_code

      - SELECT * FROM reading_user_preference
        WHERE user_id = bucket.user_id
          AND app_code = bucket.app_code
```

### 注意
如果 PowerSync 规则需要额外的 bucket/key 结构，保持原则不变：
- **按 `app_code + user_id` 隔离**
- 第一版不把权威 entitlement / 公告 / 云服务计数放进同步规则

---

## 5. 统一 backend 接口草案

## 5.1 `POST /api/v1/powersync/{appCode}/bootstrap`
### 作用
- 注册/刷新 installation
- 返回 PowerSync endpoint
- 返回当前 installation 的同步状态
- 返回是否建议做首次全量同步或 rebuild

### 鉴权
- 必须正式 session
- Bearer token 必填

### 请求体
```json
{
  "installationId": "f2ce4a97-24ca-4c46-88d3-f096e7a1c3b1",
  "deviceId": "ios-device-001",
  "clientPlatform": "ios",
  "deviceModel": "iPhone16,2",
  "appVersion": "1.0.0",
  "cloudSyncEnabled": true,
  "powersyncClientId": "reading-ios-f2ce4a97"
}
```

### 返回体
```json
{
  "requestId": "req_bootstrap_001",
  "data": {
    "appCode": "reading",
    "installationId": "f2ce4a97-24ca-4c46-88d3-f096e7a1c3b1",
    "cloudSyncEnabled": true,
    "initialSyncCompleted": false,
    "powerSyncEndpoint": "https://sync.example.com",
    "tokenExpiresAt": "2026-04-21T00:00:00Z",
    "shouldRebuild": false,
    "serverTime": "2026-04-20T23:00:00Z"
  }
}
```

### backend 行为
- upsert `sys_sync_installation`
- 若同用户同 installation 已存在，更新设备信息
- 记录审计日志：`bootstrap`

---

## 5.2 `POST /api/v1/powersync/{appCode}/token`
### 作用
为客户端生成连接 PowerSync Service 的短期 token/credentials。

### 请求体
```json
{
  "installationId": "f2ce4a97-24ca-4c46-88d3-f096e7a1c3b1"
}
```

### 返回体
```json
{
  "requestId": "req_token_001",
  "data": {
    "endpoint": "https://sync.example.com",
    "token": "eyJhbGciOi...",
    "expiresAt": "2026-04-21T00:00:00Z",
    "claims": {
      "appCode": "reading",
      "userId": 101,
      "installationId": "f2ce4a97-24ca-4c46-88d3-f096e7a1c3b1"
    }
  }
}
```

### backend 行为
- 校验 installation 属于当前登录用户 + 当前 appCode
- 只有 `cloud_sync_enabled = true` 时才允许签发
- 审计日志：`issue_token`

---

## 5.3 `POST /api/v1/powersync/{appCode}/upload`
### 作用
接收 PowerSync Connector 上传的本地改动批次。

### 请求体
```json
{
  "installationId": "f2ce4a97-24ca-4c46-88d3-f096e7a1c3b1",
  "changes": [
    {
      "entityType": "review_card",
      "operation": "upsert",
      "entityId": "card_7db3c609-c3df-4e4c-9306-8b6c6c11c112",
      "clientUpdatedAt": "2026-04-20T23:01:00Z",
      "payload": {
        "id": "card_7db3c609-c3df-4e4c-9306-8b6c6c11c112",
        "childId": "child_001",
        "learningTrackCode": "zh_to_en",
        "sourceText": "The cat is sleeping.",
        "translatedText": "猫正在睡觉。",
        "sourceLanguageCode": "en",
        "targetLanguageCode": "zh-Hans",
        "sourceType": "camera",
        "textPreview": "The cat is sleeping.",
        "supportHint": "猫正在睡觉。",
        "proficiency": 0,
        "nextReviewAt": "2026-04-21T09:00:00Z",
        "cardStatus": "active",
        "updatedAt": "2026-04-20T23:01:00Z"
      }
    }
  ]
}
```

### 返回体
```json
{
  "requestId": "req_upload_001",
  "data": {
    "accepted": [
      {
        "entityType": "review_card",
        "entityId": "card_7db3c609-c3df-4e4c-9306-8b6c6c11c112",
        "serverUpdatedAt": "2026-04-20T23:01:02Z"
      }
    ],
    "rejected": []
  }
}
```

### 被拒绝返回示例
```json
{
  "requestId": "req_upload_002",
  "data": {
    "accepted": [],
    "rejected": [
      {
        "entityType": "child_profile",
        "entityId": "child_005",
        "reasonCode": "CHILD_LIMIT_EXCEEDED",
        "reasonMessage": "Current entitlement only allows 3 children."
      }
    ]
  }
}
```

### backend 行为
- 校验登录用户、installation、appCode
- 按 entityType 路由到 `ReadingPowerSyncAdapter`
- 所有写入都必须幂等
- 审计日志：`upload_batch`

---

## 5.4 `POST /api/v1/powersync/{appCode}/rebuild`
### 作用
- 触发客户端重新做全量同步
- 或重置 installation 的同步状态

### 请求体
```json
{
  "installationId": "f2ce4a97-24ca-4c46-88d3-f096e7a1c3b1",
  "reason": "manual_reset"
}
```

### 返回体
```json
{
  "requestId": "req_rebuild_001",
  "data": {
    "installationId": "f2ce4a97-24ca-4c46-88d3-f096e7a1c3b1",
    "shouldRebuild": true,
    "message": "Rebuild has been scheduled."
  }
}
```

### backend 行为
- 将 `initial_sync_completed` 重置或标记 `shouldRebuild`
- 记录审计日志：`rebuild_requested`

---

## 6. entityType 与 payload 草案

## 6.1 `child_profile`
### operation
- `upsert`
- `delete`（服务端转软删除）

### payload
```json
{
  "id": "child_001",
  "nickname": "小月",
  "ageBand": "5_7",
  "learningTrackCode": "zh_to_en",
  "avatarEmoji": "🦊",
  "profileStatus": "active",
  "updatedAt": "2026-04-20T23:01:00Z"
}
```

---

## 6.2 `review_card`
```json
{
  "id": "card_001",
  "childId": "child_001",
  "learningTrackCode": "zh_to_en",
  "sourceText": "Good morning.",
  "translatedText": "早上好。",
  "sourceLanguageCode": "en",
  "targetLanguageCode": "zh-Hans",
  "sourceType": "camera",
  "textPreview": "Good morning.",
  "supportHint": "早上好。",
  "proficiency": 1,
  "nextReviewAt": "2026-04-21T10:00:00Z",
  "cardStatus": "active",
  "updatedAt": "2026-04-20T23:01:00Z"
}
```

---

## 6.3 `review_event`
```json
{
  "id": "evt_001",
  "childId": "child_001",
  "cardId": "card_001",
  "eventType": "review",
  "resultLevel": "good",
  "eventAt": "2026-04-20T23:05:00Z"
}
```

### 特性
- append-only
- 幂等去重按 `id`

---

## 6.4 `usage_session`
```json
{
  "id": "usage_001",
  "childId": "child_001",
  "sourcePage": "review",
  "startedAt": "2026-04-20T23:10:00Z",
  "endedAt": "2026-04-20T23:15:00Z",
  "durationSeconds": 300,
  "clientPlatform": "ios",
  "deviceModel": "iPhone16,2",
  "updatedAt": "2026-04-20T23:15:01Z"
}
```

---

## 6.5 `user_preference`
```json
{
  "userId": 101,
  "uiLocale": "zh-Hans",
  "sourceLanguageCode": "en",
  "targetLanguageCode": "zh-Hans",
  "readingTrackCode": "zh_to_en",
  "ttsVoiceCode": "en-US",
  "translationMode": "system",
  "cloudSyncEnabled": true,
  "updatedAt": "2026-04-20T23:20:00Z"
}
```

---

## 7. adapter 处理规则草案

## 7.1 `child_profile`
### upsert
- 校验当前用户 entitlement childLimit
- 若 child 数超过限制，reject
- 否则 upsert 并写 `last_modified_by_installation_id`

### delete
- 不物理删除
- 写 `deleted_at = now()`
- `profile_status = 'deleted'`（如需兼容旧逻辑）

---

## 7.2 `review_card`
### upsert
- upsert by `id + user_id + app_code`
- 兼容字段同步：
  - `source_text -> text_preview`
  - `translated_text -> support_hint`
- `deleted_at IS NULL` 时视为 active

### delete
- 写 `deleted_at = now()`
- `card_status = 'deleted'`

---

## 7.3 `review_event`
### upsert
- 实际是 append-only insert
- 同 `id` 重复上传时直接忽略

### delete
- 第一版不支持 delete review event
- 如收到 delete，直接 reject：`EVENT_DELETE_NOT_SUPPORTED`

---

## 7.4 `usage_session`
### upsert
- 以 `id` 幂等 upsert
- 用于离线恢复和聚合

### delete
- 第一版可以直接软删除，或 reject
- 推荐：仅支持软删除

---

## 7.5 `user_preference`
### upsert
- 以 `app_code + user_id` 唯一 upsert
- 注意不要让本地 preference 改掉服务端权威 entitlement

---

## 8. backend 错误码建议

### 通用
- `POWERSYNC_INSTALLATION_NOT_FOUND`
- `POWERSYNC_INSTALLATION_USER_MISMATCH`
- `POWERSYNC_DISABLED`
- `POWERSYNC_TOKEN_DENIED`
- `POWERSYNC_UPLOAD_EMPTY`
- `POWERSYNC_ENTITY_TYPE_UNSUPPORTED`
- `POWERSYNC_PAYLOAD_INVALID`

### reading 专属
- `CHILD_LIMIT_EXCEEDED`
- `CHILD_NOT_FOUND`
- `CARD_NOT_FOUND`
- `ACCOUNT_DELETED`
- `EVENT_DELETE_NOT_SUPPORTED`

---

## 9. controller/service 开发顺序建议

### 第一步
- `V4__powersync_installation_and_audit.sql`
- `SysSyncInstallationEntity/Mapper`
- `SysSyncAuditLogEntity/Mapper`

### 第二步
- `SysPowerSyncController`
- `SysPowerSyncService`
- `bootstrap/token/rebuild`

### 第三步
- `V5__reading_sync_ready_schema.sql`
- `ReadingReviewEventV2Entity/Mapper`
- `ReadingUsageSessionV2Entity/Mapper`

### 第四步
- `SysPowerSyncUploadController`
- `SysPowerSyncUploadService`
- `PowerSyncAppAdapterRegistry`
- `ReadingPowerSyncAdapter`

### 第五步
- `ReadingUsageService` / `ReadingPreferenceService` / `ReadingCompatService` 适配新表和新字段

---

## 10. 本文档用途总结

开发时可直接拿本文档做：
1. migration 起草
2. controller/service DTO 起草
3. PowerSync Connector 联调协议对齐
4. Postman/Apifox 接口 mock 数据准备
