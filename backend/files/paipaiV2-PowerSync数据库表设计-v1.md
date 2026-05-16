# paipaiV2 PowerSync 数据库表设计 v1

日期：2026-04-20  
适用范围：PowerSync 第一版接入（reading/paipaiV2）

---

## 1. 设计原则

1. **所有参与同步的业务记录必须有客户端可生成的稳定主键**
2. **统一使用软删除**（`deleted_at`）
3. **所有同步表必须有 `updated_at`**
4. **所有同步表必须能按 `app_code + user_id` 做隔离**
5. **PowerSync 只同步结构化数据，不同步原始图片/音频二进制**
6. **没有 guest，所有同步数据都绑定正式登录用户**

---

## 2. 现有表复用策略

本次第一版尽量复用已有表，避免同时做“大模型重构 + 同步接入”。

### 继续复用并升级的表
1. `reading_child_profile`
2. `reading_review_card`
3. `reading_review_event`
4. `reading_usage_session`
5. `reading_user_preference`

### 暂不纳入第一版同步
1. `reading_child_usage_daily`（派生聚合，客户端可本地计算）
2. `reading_cloud_service_usage`（服务端权威）
3. `reading_announcement`（服务端权威）
4. `reading_feedback_ticket`（继续走普通 API）
5. `reading_ocr_audit`（服务端审计）

---

## 3. 通用表（多个 App 复用）

## 3.1 `sys_sync_installation`
### 用途
记录每个 App 的设备安装实例、同步开关、最近同步状态。  
这是 **PowerSync 通用接入层** 必须有的通用表。

### 建议 DDL
```sql
CREATE TABLE sys_sync_installation (
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
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (app_code, user_id, installation_id)
);

CREATE INDEX idx_sys_sync_installation_user_app
    ON sys_sync_installation (user_id, app_code);
```

### 说明
- `installation_id`：客户端首次安装生成并长期保存
- `cloud_sync_enabled`：同步开关，建议按安装实例维度管理
- `initial_sync_completed`：用于判断是否已经有可离线使用的本地快照

---

## 3.2 `sys_sync_audit_log`
### 用途
记录关键同步动作：
- 重新绑定 installation
- 强制重建同步
- 大批量上传
- 失败与拒绝

### 建议 DDL
```sql
CREATE TABLE sys_sync_audit_log (
    id                          BIGSERIAL PRIMARY KEY,
    app_code                    VARCHAR(64) NOT NULL,
    user_id                     BIGINT NOT NULL,
    installation_id             VARCHAR(64),
    action_type                 VARCHAR(64) NOT NULL,
    entity_type                 VARCHAR(64),
    entity_id                   VARCHAR(128),
    request_id                  VARCHAR(128),
    result_status               VARCHAR(32) NOT NULL,
    detail_json                 JSONB,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sys_sync_audit_log_user_app_created
    ON sys_sync_audit_log (user_id, app_code, created_at DESC);
```

---

## 4. reading 业务表设计（云端 PostgreSQL）

## 4.1 `reading_child_profile`（升级）
### 当前用途
孩子档案。

### 需要新增字段
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

### 字段说明
- `deleted_at`：软删除，同步 tombstone 用
- `last_modified_by_installation_id`：审计与冲突排查用
- `record_version`：保留给后续冲突策略升级

---

## 4.2 `reading_review_card`（升级为第一版同步主表）
### 当前用途
句卡。

### 第一版定位
继续作为：
- 识别后的学习内容
- 翻译结果
- 复习状态

### 建议新增字段
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
```

### 字段映射建议
- `source_text`：完整原文
- `translated_text`：完整译文
- `source_type`：`camera` / `photo_library` / `manual`
- 保留当前字段兼容旧代码：
  - `text_preview`
  - `support_hint`
  - `proficiency`
  - `next_review_at`

### 兼容策略
第一版后台写入时：
- `translated_text` 和 `support_hint` 同时维护
- `source_text` 和 `text_preview` 同时维护（preview 可截断）

这样可以让旧代码与新同步版本并存过渡。

---

## 4.3 `reading_review_event`（重构为 UUID 主键）
### 当前问题
当前是 `BIGINT AUTO` 主键，不适合客户端离线先创建再同步。

### 第一版改造建议
改为客户端生成 UUID/String 主键。

### 建议目标结构
```sql
CREATE TABLE reading_review_event_v2 (
    id                          VARCHAR(64) PRIMARY KEY,
    app_code                    VARCHAR(64) NOT NULL,
    user_id                     BIGINT NOT NULL,
    child_id                    VARCHAR(64) NOT NULL,
    card_id                     VARCHAR(64) NOT NULL,
    event_type                  VARCHAR(32) NOT NULL,
    result_level                VARCHAR(32) NOT NULL,
    event_at                    TIMESTAMPTZ NOT NULL,
    last_modified_by_installation_id VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reading_review_event_v2_user_event_at
    ON reading_review_event_v2 (user_id, event_at DESC);

CREATE INDEX idx_reading_review_event_v2_card_event_at
    ON reading_review_event_v2 (card_id, event_at DESC);
```

### 为什么建议 `v2` 表
因为当前 `reading_review_event` 仍在跑，第一版同步最好不要一边接 PowerSync 一边直接破坏旧表主键结构。

### 迁移建议
- 第一版新代码直接写 `reading_review_event_v2`
- 旧 `reading_review_event` 可保留只读兼容或后续迁移下线

---

## 4.4 `reading_usage_session`（重构为客户端主键）
### 当前问题
当前是自增 `id` + `session_uuid`，不够 sync-friendly。

### 建议目标结构
```sql
CREATE TABLE reading_usage_session_v2 (
    id                          VARCHAR(64) PRIMARY KEY,
    app_code                    VARCHAR(64) NOT NULL,
    user_id                     BIGINT NOT NULL,
    child_id                    VARCHAR(64) NOT NULL,
    source_page                 VARCHAR(64) NOT NULL,
    started_at                  TIMESTAMPTZ NOT NULL,
    ended_at                    TIMESTAMPTZ,
    duration_seconds            INTEGER,
    client_platform             VARCHAR(32),
    device_model                VARCHAR(128),
    last_modified_by_installation_id VARCHAR(64),
    deleted_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reading_usage_session_v2_user_started
    ON reading_usage_session_v2 (user_id, started_at DESC);

CREATE INDEX idx_reading_usage_session_v2_child_started
    ON reading_usage_session_v2 (child_id, started_at DESC);
```

### 说明
- `id` 直接使用客户端生成的 UUID
- 不再依赖单独的 `session_uuid`
- 当前前端已有 `usageSessionId = UUID().uuidString`，可以直接复用

---

## 4.5 `reading_user_preference`（升级）
### 当前用途
用户偏好。

### 建议新增字段
```sql
ALTER TABLE reading_user_preference
    ADD COLUMN IF NOT EXISTS cloud_sync_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS last_modified_by_installation_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS record_version INTEGER NOT NULL DEFAULT 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_reading_user_preference_app_user
    ON reading_user_preference (app_code, user_id);
```

### 说明
- `cloud_sync_enabled`：也可以只放在 `sys_sync_installation`，但第一版建议同时在业务偏好表里保留账户级偏好，方便前端设置页直接读取
- `last_modified_by_installation_id`：冲突排查用

---

## 4.6 `reading_sync_local_asset_ref`（新增，本地附件索引在云端只做引用，可选）
### 第一版建议
**不做云同步。**

如果后续要支持多设备恢复图片，再新增对象存储 + 引用表。
第一版先不落。

---

## 5. 本地 SQLite 表设计（iOS / PowerSync）

## 5.1 同步表（本地镜像）
由 PowerSync 自动映射/管理，结构与云端对应：
1. `reading_child_profile`
2. `reading_review_card`
3. `reading_review_event_v2`
4. `reading_usage_session_v2`
5. `reading_user_preference`

这些表是 App 主数据源。

---

## 5.2 本地 only 表
### `local_asset_file_ref`
保存不进云的文件路径。

```sql
CREATE TABLE local_asset_file_ref (
    id                          TEXT PRIMARY KEY,
    owner_table                 TEXT NOT NULL,
    owner_id                    TEXT NOT NULL,
    asset_type                  TEXT NOT NULL,
    local_path                  TEXT NOT NULL,
    file_size_bytes             INTEGER,
    mime_type                   TEXT,
    created_at                  TEXT NOT NULL,
    updated_at                  TEXT NOT NULL
);
```

### `local_runtime_kv`
运行时状态缓存。

```sql
CREATE TABLE local_runtime_kv (
    key                         TEXT PRIMARY KEY,
    value_json                  TEXT NOT NULL,
    updated_at                  TEXT NOT NULL
);
```

### 用途
- 图片本地路径
- 最近公告缓存
- 当前同步状态
- 最近一次全量重建时间

---

## 6. PowerSync Sync Rules 关注对象

第一版建议同步规则只覆盖：
1. `reading_child_profile`
2. `reading_review_card`
3. `reading_review_event_v2`
4. `reading_usage_session_v2`
5. `reading_user_preference`

### 过滤条件统一要求
- `app_code = token.appCode`
- `user_id = token.userId`
- `deleted_at IS NULL` 或同步 tombstone 规则明确配置

### 不建议第一版直接进规则的表
- `reading_cloud_service_usage`
- `reading_announcement`
- `reading_feedback_ticket`
- `reading_ocr_audit`
- `reading_child_usage_daily`

---

## 7. 索引建议

### 通用原则
所有同步表至少要有：
1. `user_id + updated_at`
2. `deleted_at`
3. 常用查询维度（如 `child_id + updated_at`）

### 推荐附加索引
```sql
CREATE INDEX IF NOT EXISTS idx_reading_review_card_user_next_review
    ON reading_review_card (user_id, next_review_at);

CREATE INDEX IF NOT EXISTS idx_reading_review_card_user_track_updated
    ON reading_review_card (user_id, learning_track_code, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_reading_usage_session_v2_user_source_started
    ON reading_usage_session_v2 (user_id, source_page, started_at DESC);
```

---

## 8. 建议的 migration 文件拆分

### 通用层
1. `V4__powersync_installation_and_audit.sql`
   - `sys_sync_installation`
   - `sys_sync_audit_log`

### reading app 专属
2. `V5__reading_sync_ready_schema.sql`
   - `reading_child_profile` 升级
   - `reading_review_card` 升级
   - `reading_user_preference` 升级
   - 新建 `reading_review_event_v2`
   - 新建 `reading_usage_session_v2`

> 因为你当前不涉及历史迁移包袱，这样拆最稳，回滚和审计都更清晰。

---

## 9. 与现有代码的兼容结论

### 兼容成本较低
- `reading_child_profile`
- `reading_user_preference`

### 兼容成本中等
- `reading_review_card`（增加字段但保留旧字段）

### 建议新旧分表并行
- `reading_review_event_v2`
- `reading_usage_session_v2`

原因：
- 这两张表当前自增主键不适合同步
- 分表切换比强改原表更稳

---

## 10. 开发实施时的数据库底线要求

1. 所有同步主键必须由客户端生成
2. 所有同步写入必须幂等 upsert
3. 所有同步删除必须软删除
4. 所有同步表必须保留 `updated_at`
5. 所有同步表必须能按 `user_id` 和 `app_code` 隔离

---

## 11. 本文档配套
同目录继续查看：
- `paipaiV2-PowerSync多APP同步方案-v1.md`
- `paipaiV2-PowerSync文件级开发任务清单-v1.md`
