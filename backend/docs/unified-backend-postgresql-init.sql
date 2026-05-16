-- 统一后端 PostgreSQL 初始化 SQL
-- 用途：为 unified backend（含 reading / saving / reading 扩展业务域）做一次性初始化。
-- 特点：
-- 1) 适用于 PostgreSQL
-- 2) 包含必要表、约束、索引、字段备注
-- 3) 包含基础种子数据
-- 4) 采用幂等写法，便于重复执行

BEGIN;

SET TIME ZONE 'UTC';

-- =========================================================
-- 0. 核心系统表
-- =========================================================

CREATE TABLE IF NOT EXISTS sys_app (
    app_code VARCHAR(64) PRIMARY KEY,
    app_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_app IS '应用目录表：记录统一后端当前支持的 appCode 与状态。';
COMMENT ON COLUMN sys_app.app_code IS '应用唯一编码，例如 reading、saving。';
COMMENT ON COLUMN sys_app.app_name IS '应用展示名称。';
COMMENT ON COLUMN sys_app.status IS '应用状态，例如 active / inactive。';
COMMENT ON COLUMN sys_app.created_at IS '记录创建时间，建议按 UTC 写入。';
COMMENT ON COLUMN sys_app.updated_at IS '记录最后更新时间，建议按 UTC 写入。';

CREATE TABLE IF NOT EXISTS sys_remote_config (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    namespace_code VARCHAR(64) NOT NULL,
    config_key VARCHAR(128) NOT NULL,
    config_value_json JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_remote_config IS '远程配置表：按 app / namespace / key 存储运行时配置。';
COMMENT ON COLUMN sys_remote_config.id IS '远程配置主键。';
COMMENT ON COLUMN sys_remote_config.app_code IS '配置所属应用编码。';
COMMENT ON COLUMN sys_remote_config.namespace_code IS '配置命名空间，例如 bootstrap、features、cloud_provider。';
COMMENT ON COLUMN sys_remote_config.config_key IS '配置键名。';
COMMENT ON COLUMN sys_remote_config.config_value_json IS '配置值 JSON，对外通常读取 value 字段。';
COMMENT ON COLUMN sys_remote_config.status IS '配置状态，例如 active / archived。';
COMMENT ON COLUMN sys_remote_config.created_at IS '记录创建时间，建议按 UTC 写入。';
COMMENT ON COLUMN sys_remote_config.updated_at IS '记录最后更新时间，建议按 UTC 写入。';

CREATE INDEX IF NOT EXISTS idx_sys_remote_config_app_namespace_key
    ON sys_remote_config(app_code, namespace_code, config_key);

CREATE TABLE IF NOT EXISTS sys_audit_log (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    operator_type VARCHAR(32) NOT NULL,
    operator_id VARCHAR(128),
    action_code VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128),
    payload_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_audit_log IS '审计日志表：记录关键运维或业务动作。';
COMMENT ON COLUMN sys_audit_log.id IS '审计日志主键。';
COMMENT ON COLUMN sys_audit_log.app_code IS '操作所属应用编码。';
COMMENT ON COLUMN sys_audit_log.operator_type IS '操作者类型，例如 system / user / admin。';
COMMENT ON COLUMN sys_audit_log.operator_id IS '操作者标识，可为空。';
COMMENT ON COLUMN sys_audit_log.action_code IS '动作编码。';
COMMENT ON COLUMN sys_audit_log.trace_id IS '链路追踪 ID，便于排查请求。';
COMMENT ON COLUMN sys_audit_log.payload_json IS '审计附带的结构化负载。';
COMMENT ON COLUMN sys_audit_log.created_at IS '记录创建时间，建议按 UTC 写入。';

CREATE INDEX IF NOT EXISTS idx_sys_audit_log_app_action_created
    ON sys_audit_log(app_code, action_code, created_at DESC);

-- =========================================================
-- 1. 统一认证与会话
-- =========================================================

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    user_type VARCHAR(32) NOT NULL,
    display_name VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_user IS '统一用户表：保存各 app 内部用户主体。';
COMMENT ON COLUMN sys_user.id IS '统一用户主键。';
COMMENT ON COLUMN sys_user.app_code IS '所属应用编码。';
COMMENT ON COLUMN sys_user.user_type IS '用户类型，例如 guest / member。';
COMMENT ON COLUMN sys_user.display_name IS '展示名称。';
COMMENT ON COLUMN sys_user.status IS '用户状态，例如 active / deleted。';
COMMENT ON COLUMN sys_user.created_at IS '记录创建时间，建议按 UTC 写入。';
COMMENT ON COLUMN sys_user.updated_at IS '记录最后更新时间，建议按 UTC 写入。';

CREATE INDEX IF NOT EXISTS idx_sys_user_app_status_created
    ON sys_user(app_code, status, created_at DESC);

CREATE TABLE IF NOT EXISTS sys_auth_session (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    session_token_hash VARCHAR(128) NOT NULL UNIQUE,
    session_source VARCHAR(32) NOT NULL,
    device_id VARCHAR(128),
    client_platform VARCHAR(32),
    client_version VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_auth_session IS '认证会话表：保存 bearer session、来源、设备与有效期。';
COMMENT ON COLUMN sys_auth_session.id IS '会话主键。';
COMMENT ON COLUMN sys_auth_session.app_code IS '所属应用编码。';
COMMENT ON COLUMN sys_auth_session.user_id IS '关联统一用户主键。';
COMMENT ON COLUMN sys_auth_session.session_token_hash IS '会话 token 的哈希值，不保存明文 token。';
COMMENT ON COLUMN sys_auth_session.session_source IS '会话来源，例如 demo / apple。';
COMMENT ON COLUMN sys_auth_session.device_id IS '客户端设备标识。';
COMMENT ON COLUMN sys_auth_session.client_platform IS '客户端平台，例如 ios。';
COMMENT ON COLUMN sys_auth_session.client_version IS '客户端版本号。';
COMMENT ON COLUMN sys_auth_session.status IS '会话状态，例如 active / revoked。';
COMMENT ON COLUMN sys_auth_session.expires_at IS '会话失效时间。';
COMMENT ON COLUMN sys_auth_session.revoked_at IS '会话撤销时间。';
COMMENT ON COLUMN sys_auth_session.last_seen_at IS '最近一次被服务端确认使用的时间。';
COMMENT ON COLUMN sys_auth_session.created_at IS '记录创建时间，建议按 UTC 写入。';
COMMENT ON COLUMN sys_auth_session.updated_at IS '记录最后更新时间，建议按 UTC 写入。';

CREATE INDEX IF NOT EXISTS idx_sys_auth_session_user_status_created
    ON sys_auth_session(user_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sys_auth_session_app_status_created
    ON sys_auth_session(app_code, status, created_at DESC);

CREATE TABLE IF NOT EXISTS sys_user_identity (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    provider_code VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(256) NOT NULL,
    email VARCHAR(256),
    email_verified BOOLEAN,
    private_email BOOLEAN,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    payload_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_user_identity IS '第三方身份映射表：保存 Apple subject 等身份与 sys_user 的关联。';
COMMENT ON COLUMN sys_user_identity.id IS '身份映射主键。';
COMMENT ON COLUMN sys_user_identity.app_code IS '所属应用编码。';
COMMENT ON COLUMN sys_user_identity.user_id IS '关联统一用户主键。';
COMMENT ON COLUMN sys_user_identity.provider_code IS '身份提供方，例如 apple。';
COMMENT ON COLUMN sys_user_identity.provider_subject IS '提供方主体标识，例如 Apple sub。';
COMMENT ON COLUMN sys_user_identity.email IS '第三方返回的邮箱，可为空。';
COMMENT ON COLUMN sys_user_identity.email_verified IS '邮箱是否已验证。';
COMMENT ON COLUMN sys_user_identity.private_email IS '是否为 Apple 私密转发邮箱。';
COMMENT ON COLUMN sys_user_identity.status IS '身份映射状态，例如 active / revoked。';
COMMENT ON COLUMN sys_user_identity.payload_json IS '身份附带的原始或扩展结构化信息。';
COMMENT ON COLUMN sys_user_identity.created_at IS '记录创建时间，建议按 UTC 写入。';
COMMENT ON COLUMN sys_user_identity.updated_at IS '记录最后更新时间，建议按 UTC 写入。';

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_identity_app_provider_subject
    ON sys_user_identity(app_code, provider_code, provider_subject);
CREATE INDEX IF NOT EXISTS idx_sys_user_identity_user_provider
    ON sys_user_identity(user_id, provider_code, updated_at DESC);

CREATE TABLE IF NOT EXISTS sys_auth_provider_token (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    provider_code VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(256) NOT NULL,
    refresh_token TEXT,
    access_token TEXT,
    token_type VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    payload_json JSONB,
    refresh_token_key_id VARCHAR(64),
    refresh_token_encryption_algorithm VARCHAR(64),
    refresh_token_nonce_base64 TEXT,
    refresh_token_ciphertext_base64 TEXT,
    refresh_token_last_captured_at TIMESTAMPTZ,
    refresh_token_last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_auth_provider_token IS '第三方 provider token 表：保存 Apple refresh/access token 及其加密存储信息。';
COMMENT ON COLUMN sys_auth_provider_token.id IS 'provider token 主键。';
COMMENT ON COLUMN sys_auth_provider_token.app_code IS '所属应用编码。';
COMMENT ON COLUMN sys_auth_provider_token.user_id IS '关联统一用户主键。';
COMMENT ON COLUMN sys_auth_provider_token.provider_code IS 'provider 编码，例如 apple。';
COMMENT ON COLUMN sys_auth_provider_token.provider_subject IS 'provider 主体标识，例如 Apple subject。';
COMMENT ON COLUMN sys_auth_provider_token.refresh_token IS '明文 refresh token 兜底字段，生产应尽量为空。';
COMMENT ON COLUMN sys_auth_provider_token.access_token IS 'access token。';
COMMENT ON COLUMN sys_auth_provider_token.token_type IS 'token 类型。';
COMMENT ON COLUMN sys_auth_provider_token.status IS 'token 状态，例如 active / revoked。';
COMMENT ON COLUMN sys_auth_provider_token.payload_json IS '附带的原始或扩展结构化信息。';
COMMENT ON COLUMN sys_auth_provider_token.refresh_token_key_id IS '用于加密 refresh token 的密钥 ID。';
COMMENT ON COLUMN sys_auth_provider_token.refresh_token_encryption_algorithm IS 'refresh token 加密算法。';
COMMENT ON COLUMN sys_auth_provider_token.refresh_token_nonce_base64 IS 'refresh token 加密 nonce。';
COMMENT ON COLUMN sys_auth_provider_token.refresh_token_ciphertext_base64 IS '加密后的 refresh token。';
COMMENT ON COLUMN sys_auth_provider_token.refresh_token_last_captured_at IS '最近一次采集 refresh token 的时间。';
COMMENT ON COLUMN sys_auth_provider_token.refresh_token_last_used_at IS '最近一次使用 refresh token 的时间。';
COMMENT ON COLUMN sys_auth_provider_token.created_at IS '记录创建时间，建议按 UTC 写入。';
COMMENT ON COLUMN sys_auth_provider_token.updated_at IS '记录最后更新时间，建议按 UTC 写入。';

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_auth_provider_token_identity
    ON sys_auth_provider_token(app_code, provider_code, provider_subject);
CREATE INDEX IF NOT EXISTS idx_sys_auth_provider_token_user_provider
    ON sys_auth_provider_token(user_id, provider_code, updated_at DESC);

-- =========================================================
-- 2. 统一计费与 App Store 观测
-- =========================================================

CREATE TABLE IF NOT EXISTS sys_purchase_transaction (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    source_type VARCHAR(32) NOT NULL,
    product_id VARCHAR(128),
    transaction_id VARCHAR(128),
    original_transaction_id VARCHAR(128) NOT NULL,
    store_environment VARCHAR(32),
    storefront VARCHAR(32),
    app_account_token VARCHAR(128),
    signed_transaction_info_hash VARCHAR(128) NOT NULL,
    signed_renewal_info_hash VARCHAR(128),
    verification_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    processing_status VARCHAR(32) NOT NULL DEFAULT 'accepted',
    payload_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_purchase_transaction IS '购买交易表：保存 verify / restore 后的交易归档与校验状态。';
COMMENT ON COLUMN sys_purchase_transaction.id IS '交易记录主键。';
COMMENT ON COLUMN sys_purchase_transaction.app_code IS '所属应用编码。';
COMMENT ON COLUMN sys_purchase_transaction.user_id IS '关联统一用户主键。';
COMMENT ON COLUMN sys_purchase_transaction.source_type IS '来源类型，例如 purchase / restore / legacy_verify。';
COMMENT ON COLUMN sys_purchase_transaction.product_id IS '商品 ID。';
COMMENT ON COLUMN sys_purchase_transaction.transaction_id IS '交易 ID。';
COMMENT ON COLUMN sys_purchase_transaction.original_transaction_id IS '原始交易 ID。';
COMMENT ON COLUMN sys_purchase_transaction.store_environment IS '商店环境，例如 Sandbox / Production。';
COMMENT ON COLUMN sys_purchase_transaction.storefront IS '店面区域。';
COMMENT ON COLUMN sys_purchase_transaction.app_account_token IS '客户端传来的 appAccountToken。';
COMMENT ON COLUMN sys_purchase_transaction.signed_transaction_info_hash IS 'signedTransactionInfo 哈希。';
COMMENT ON COLUMN sys_purchase_transaction.signed_renewal_info_hash IS 'signedRenewalInfo 哈希。';
COMMENT ON COLUMN sys_purchase_transaction.verification_status IS '验签或校验状态。';
COMMENT ON COLUMN sys_purchase_transaction.processing_status IS '处理状态。';
COMMENT ON COLUMN sys_purchase_transaction.payload_json IS '交易原始或扩展结构化数据。';
COMMENT ON COLUMN sys_purchase_transaction.created_at IS '记录创建时间，建议按 UTC 写入。';
COMMENT ON COLUMN sys_purchase_transaction.updated_at IS '记录最后更新时间，建议按 UTC 写入。';

CREATE INDEX IF NOT EXISTS idx_sys_purchase_transaction_app_user_created
    ON sys_purchase_transaction(app_code, user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sys_purchase_transaction_original_tx
    ON sys_purchase_transaction(app_code, original_transaction_id, created_at DESC);

CREATE TABLE IF NOT EXISTS sys_entitlement_snapshot (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    entitlement_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    source_type VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ,
    payload_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_entitlement_snapshot IS '权益快照表：保存当前用户在各 app 下的生效权益投影。';
COMMENT ON COLUMN sys_entitlement_snapshot.id IS '权益快照主键。';
COMMENT ON COLUMN sys_entitlement_snapshot.app_code IS '所属应用编码。';
COMMENT ON COLUMN sys_entitlement_snapshot.user_id IS '关联统一用户主键。';
COMMENT ON COLUMN sys_entitlement_snapshot.entitlement_code IS '权益编码。';
COMMENT ON COLUMN sys_entitlement_snapshot.status IS '权益状态，例如 active / expired。';
COMMENT ON COLUMN sys_entitlement_snapshot.source_type IS '权益来源类型。';
COMMENT ON COLUMN sys_entitlement_snapshot.expires_at IS '权益失效时间，可为空表示长期有效。';
COMMENT ON COLUMN sys_entitlement_snapshot.payload_json IS '权益附带的扩展结构化数据。';
COMMENT ON COLUMN sys_entitlement_snapshot.created_at IS '记录创建时间，建议按 UTC 写入。';
COMMENT ON COLUMN sys_entitlement_snapshot.updated_at IS '记录最后更新时间，建议按 UTC 写入。';

CREATE INDEX IF NOT EXISTS idx_sys_entitlement_snapshot_user_status_updated
    ON sys_entitlement_snapshot(app_code, user_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS sys_app_store_notification (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    notification_uuid VARCHAR(128) NOT NULL,
    notification_type VARCHAR(128),
    subtype VARCHAR(128),
    signed_payload_hash VARCHAR(128) NOT NULL,
    verification_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    processing_status VARCHAR(32) NOT NULL DEFAULT 'accepted',
    raw_payload_json JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_app_store_notification IS 'App Store 通知表：归档 Apple server notification 原始数据与处理状态。';
COMMENT ON COLUMN sys_app_store_notification.id IS '通知记录主键。';
COMMENT ON COLUMN sys_app_store_notification.app_code IS '所属应用编码。';
COMMENT ON COLUMN sys_app_store_notification.notification_uuid IS 'Apple 通知唯一标识。';
COMMENT ON COLUMN sys_app_store_notification.notification_type IS '通知类型。';
COMMENT ON COLUMN sys_app_store_notification.subtype IS '通知子类型。';
COMMENT ON COLUMN sys_app_store_notification.signed_payload_hash IS '签名载荷哈希。';
COMMENT ON COLUMN sys_app_store_notification.verification_status IS '通知验签状态。';
COMMENT ON COLUMN sys_app_store_notification.processing_status IS '通知处理状态。';
COMMENT ON COLUMN sys_app_store_notification.raw_payload_json IS '原始通知内容。';
COMMENT ON COLUMN sys_app_store_notification.received_at IS '服务端接收时间。';
COMMENT ON COLUMN sys_app_store_notification.created_at IS '记录创建时间，建议按 UTC 写入。';
COMMENT ON COLUMN sys_app_store_notification.updated_at IS '记录最后更新时间，建议按 UTC 写入。';

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_app_store_notification_app_uuid
    ON sys_app_store_notification(app_code, notification_uuid);
CREATE INDEX IF NOT EXISTS idx_sys_app_store_notification_app_received
    ON sys_app_store_notification(app_code, received_at DESC);

-- =========================================================
-- 3. reading 业务域
-- =========================================================

CREATE TABLE IF NOT EXISTS reading_child_profile (
    id VARCHAR(64) PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'reading',
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    nickname VARCHAR(128) NOT NULL,
    age_band VARCHAR(32) NOT NULL,
    learning_track_code VARCHAR(64) NOT NULL,
    avatar_emoji VARCHAR(16) NOT NULL DEFAULT '🧸',
    profile_status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE reading_child_profile IS 'reading 孩子档案表：创建/更新前必须经过后端账号与权益校验。';
COMMENT ON COLUMN reading_child_profile.id IS '孩子档案 UUID。';
COMMENT ON COLUMN reading_child_profile.app_code IS '固定为 reading。';
COMMENT ON COLUMN reading_child_profile.user_id IS '关联统一用户主键。';
COMMENT ON COLUMN reading_child_profile.nickname IS '孩子昵称。';
COMMENT ON COLUMN reading_child_profile.age_band IS '年龄段编码。';
COMMENT ON COLUMN reading_child_profile.learning_track_code IS '学习方向编码。';
COMMENT ON COLUMN reading_child_profile.avatar_emoji IS '前端展示用头像 emoji。';
COMMENT ON COLUMN reading_child_profile.profile_status IS '档案状态，例如 active / deleted。';
COMMENT ON COLUMN reading_child_profile.created_at IS '记录创建时间，建议按 UTC 写入。';
COMMENT ON COLUMN reading_child_profile.updated_at IS '记录最后更新时间，建议按 UTC 写入。';

CREATE INDEX IF NOT EXISTS idx_reading_child_profile_user_status
    ON reading_child_profile(user_id, profile_status, updated_at DESC);

CREATE TABLE IF NOT EXISTS reading_review_card (
    id VARCHAR(64) PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'reading',
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    child_id VARCHAR(64) NOT NULL REFERENCES reading_child_profile(id),
    learning_track_code VARCHAR(64) NOT NULL,
    encrypted_text TEXT NOT NULL,
    text_preview VARCHAR(256),
    support_hint VARCHAR(512),
    proficiency INTEGER NOT NULL DEFAULT 0,
    next_review_at TIMESTAMPTZ,
    sync_enabled BOOLEAN NOT NULL DEFAULT false,
    storage_mode VARCHAR(32) NOT NULL DEFAULT 'server_authoritative',
    card_status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE reading_review_card IS 'reading 句卡表：保存家长确认后的短句卡，后续复习与周报都以后端记录为准。';
COMMENT ON COLUMN reading_review_card.id IS '句卡 UUID。';
COMMENT ON COLUMN reading_review_card.app_code IS '固定为 reading。';
COMMENT ON COLUMN reading_review_card.user_id IS '关联统一用户主键。';
COMMENT ON COLUMN reading_review_card.child_id IS '关联孩子档案 UUID。';
COMMENT ON COLUMN reading_review_card.learning_track_code IS '学习方向编码。';
COMMENT ON COLUMN reading_review_card.encrypted_text IS '句卡正文的密文或编码存储值。';
COMMENT ON COLUMN reading_review_card.text_preview IS '前端展示用的短预览文本。';
COMMENT ON COLUMN reading_review_card.support_hint IS '辅助提示文本。';
COMMENT ON COLUMN reading_review_card.proficiency IS '熟练度分值。';
COMMENT ON COLUMN reading_review_card.next_review_at IS '下次建议复习时间。';
COMMENT ON COLUMN reading_review_card.sync_enabled IS '是否允许同步到云端状态。';
COMMENT ON COLUMN reading_review_card.storage_mode IS '存储模式，例如 server_authoritative / server_synced。';
COMMENT ON COLUMN reading_review_card.card_status IS '句卡状态，例如 active / deleted。';
COMMENT ON COLUMN reading_review_card.created_at IS '记录创建时间，建议按 UTC 写入。';
COMMENT ON COLUMN reading_review_card.updated_at IS '记录最后更新时间，建议按 UTC 写入。';

CREATE INDEX IF NOT EXISTS idx_reading_review_card_user_status_created
    ON reading_review_card(user_id, card_status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reading_review_card_child_status_next_review
    ON reading_review_card(child_id, card_status, next_review_at ASC);

CREATE TABLE IF NOT EXISTS reading_review_event (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'reading',
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    child_id VARCHAR(64) NOT NULL REFERENCES reading_child_profile(id),
    card_id VARCHAR(64) NOT NULL REFERENCES reading_review_card(id),
    event_type VARCHAR(64) NOT NULL,
    result_level VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE reading_review_event IS 'reading 复习事件表：记录用户对句卡的完成情况，用于周报和成长统计。';
COMMENT ON COLUMN reading_review_event.id IS '复习事件主键。';
COMMENT ON COLUMN reading_review_event.app_code IS '固定为 reading。';
COMMENT ON COLUMN reading_review_event.user_id IS '关联统一用户主键。';
COMMENT ON COLUMN reading_review_event.child_id IS '关联孩子档案 UUID。';
COMMENT ON COLUMN reading_review_event.card_id IS '关联句卡 UUID。';
COMMENT ON COLUMN reading_review_event.event_type IS '事件类型，例如 completed。';
COMMENT ON COLUMN reading_review_event.result_level IS '结果等级，例如 remembered / easy / hard。';
COMMENT ON COLUMN reading_review_event.created_at IS '事件发生时间，建议按 UTC 写入。';

CREATE INDEX IF NOT EXISTS idx_reading_review_event_user_child_created
    ON reading_review_event(user_id, child_id, created_at DESC);

CREATE TABLE IF NOT EXISTS reading_daily_task_completion (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'reading',
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    child_id VARCHAR(64),
    task_id VARCHAR(128) NOT NULL,
    completion_type VARCHAR(64) NOT NULL,
    task_date DATE NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, task_id, task_date)
);

COMMENT ON TABLE reading_daily_task_completion IS 'reading 每日任务完成表：记录后端生成任务的完成回写。';
COMMENT ON COLUMN reading_daily_task_completion.id IS '每日任务完成主键。';
COMMENT ON COLUMN reading_daily_task_completion.app_code IS '固定为 reading。';
COMMENT ON COLUMN reading_daily_task_completion.user_id IS '关联统一用户主键。';
COMMENT ON COLUMN reading_daily_task_completion.child_id IS '关联孩子 UUID，可为空。';
COMMENT ON COLUMN reading_daily_task_completion.task_id IS '任务 ID。';
COMMENT ON COLUMN reading_daily_task_completion.completion_type IS '完成类型。';
COMMENT ON COLUMN reading_daily_task_completion.task_date IS '任务所属日期。';
COMMENT ON COLUMN reading_daily_task_completion.completed_at IS '完成时间，建议按 UTC 写入。';

CREATE INDEX IF NOT EXISTS idx_reading_daily_task_completion_user_date
    ON reading_daily_task_completion(user_id, task_date DESC);

CREATE TABLE IF NOT EXISTS reading_feedback_ticket (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'reading',
    user_id BIGINT REFERENCES sys_user(id),
    ticket_no VARCHAR(64) NOT NULL UNIQUE,
    category VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    contact_email VARCHAR(256),
    auth_mode VARCHAR(64),
    trace_id VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'open',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE reading_feedback_ticket IS 'reading 反馈工单表：保存低敏问题反馈。';
COMMENT ON COLUMN reading_feedback_ticket.id IS '反馈工单主键。';
COMMENT ON COLUMN reading_feedback_ticket.app_code IS '固定为 reading。';
COMMENT ON COLUMN reading_feedback_ticket.user_id IS '关联统一用户主键，可为空表示匿名反馈。';
COMMENT ON COLUMN reading_feedback_ticket.ticket_no IS '工单编号。';
COMMENT ON COLUMN reading_feedback_ticket.category IS '反馈分类。';
COMMENT ON COLUMN reading_feedback_ticket.content IS '反馈正文。';
COMMENT ON COLUMN reading_feedback_ticket.contact_email IS '联系邮箱，可为空。';
COMMENT ON COLUMN reading_feedback_ticket.auth_mode IS '提交时的账号模式。';
COMMENT ON COLUMN reading_feedback_ticket.trace_id IS '可选的诊断编号。';
COMMENT ON COLUMN reading_feedback_ticket.status IS '工单状态，例如 open / closed。';
COMMENT ON COLUMN reading_feedback_ticket.created_at IS '记录创建时间，建议按 UTC 写入。';
COMMENT ON COLUMN reading_feedback_ticket.updated_at IS '记录最后更新时间，建议按 UTC 写入。';

CREATE INDEX IF NOT EXISTS idx_reading_feedback_ticket_user_created
    ON reading_feedback_ticket(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS reading_ocr_audit (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'reading',
    user_id BIGINT REFERENCES sys_user(id),
    trace_id VARCHAR(128) NOT NULL UNIQUE,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    note VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE reading_ocr_audit IS 'reading OCR 审计表：不长期保存原始图片，只记录低敏诊断信息。';
COMMENT ON COLUMN reading_ocr_audit.id IS 'OCR 审计主键。';
COMMENT ON COLUMN reading_ocr_audit.app_code IS '固定为 reading。';
COMMENT ON COLUMN reading_ocr_audit.user_id IS '关联统一用户主键，可为空。';
COMMENT ON COLUMN reading_ocr_audit.trace_id IS '诊断编号。';
COMMENT ON COLUMN reading_ocr_audit.provider IS 'OCR provider 名称。';
COMMENT ON COLUMN reading_ocr_audit.model IS 'OCR 模型名称。';
COMMENT ON COLUMN reading_ocr_audit.status IS 'OCR 状态，例如 succeeded / empty / provider_failed。';
COMMENT ON COLUMN reading_ocr_audit.note IS '补充说明。';
COMMENT ON COLUMN reading_ocr_audit.created_at IS '记录创建时间，建议按 UTC 写入。';

CREATE INDEX IF NOT EXISTS idx_reading_ocr_audit_user_created
    ON reading_ocr_audit(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS reading_cloud_service_usage (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'reading',
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    service_type VARCHAR(64) NOT NULL,
    trial_limit INTEGER NOT NULL,
    trial_used INTEGER NOT NULL DEFAULT 0,
    purchased_credits INTEGER NOT NULL DEFAULT 0,
    purchased_used INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, service_type)
);

COMMENT ON TABLE reading_cloud_service_usage IS 'reading 云端服务次数表：控制云端 OCR / TTS 的试用与购买次数。';
COMMENT ON COLUMN reading_cloud_service_usage.id IS '云端服务次数主键。';
COMMENT ON COLUMN reading_cloud_service_usage.app_code IS '固定为 reading。';
COMMENT ON COLUMN reading_cloud_service_usage.user_id IS '关联统一用户主键。';
COMMENT ON COLUMN reading_cloud_service_usage.service_type IS '服务类型，例如 cloud_ocr / cloud_tts。';
COMMENT ON COLUMN reading_cloud_service_usage.trial_limit IS '试用次数上限。';
COMMENT ON COLUMN reading_cloud_service_usage.trial_used IS '已消耗的试用次数。';
COMMENT ON COLUMN reading_cloud_service_usage.purchased_credits IS '购买获得的额外次数总额。';
COMMENT ON COLUMN reading_cloud_service_usage.purchased_used IS '已消耗的购买次数。';
COMMENT ON COLUMN reading_cloud_service_usage.created_at IS '记录创建时间，建议按 UTC 写入。';
COMMENT ON COLUMN reading_cloud_service_usage.updated_at IS '记录最后更新时间，建议按 UTC 写入。';

CREATE UNIQUE INDEX IF NOT EXISTS uk_reading_cloud_service_usage_user_service
    ON reading_cloud_service_usage(user_id, service_type);

CREATE TABLE IF NOT EXISTS reading_cloud_service_credit_grant (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'paipai_readingcompanion',
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    service_type VARCHAR(64) NOT NULL,
    grant_type VARCHAR(32) NOT NULL,
    total_count INTEGER NOT NULL,
    used_count INTEGER NOT NULL DEFAULT 0,
    source_type VARCHAR(64) NOT NULL,
    source_ref VARCHAR(128),
    product_code VARCHAR(128),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_reading_credit_grant_count CHECK (total_count > 0 AND used_count >= 0 AND used_count <= total_count)
);

COMMENT ON TABLE reading_cloud_service_credit_grant IS 'reading 云端服务次数授权表：记录购买或赠送获得的 OCR / TTS 可用次数。';
COMMENT ON COLUMN reading_cloud_service_credit_grant.id IS '云端服务次数授权主键。';
COMMENT ON COLUMN reading_cloud_service_credit_grant.app_code IS 'App 标识，当前为 paipai_readingcompanion。';
COMMENT ON COLUMN reading_cloud_service_credit_grant.user_id IS '关联统一用户主键。';
COMMENT ON COLUMN reading_cloud_service_credit_grant.service_type IS '服务类型，例如 cloud_ocr / cloud_tts。';
COMMENT ON COLUMN reading_cloud_service_credit_grant.grant_type IS '授权类型，例如 paid / gift。';
COMMENT ON COLUMN reading_cloud_service_credit_grant.total_count IS '本次授权总次数。';
COMMENT ON COLUMN reading_cloud_service_credit_grant.used_count IS '本次授权已消耗次数。';
COMMENT ON COLUMN reading_cloud_service_credit_grant.source_type IS '授权来源类型，例如 internal_purchase。';
COMMENT ON COLUMN reading_cloud_service_credit_grant.source_ref IS '授权来源引用，用于幂等去重。';
COMMENT ON COLUMN reading_cloud_service_credit_grant.product_code IS '产品编码。';
COMMENT ON COLUMN reading_cloud_service_credit_grant.expires_at IS '授权过期时间。';
COMMENT ON COLUMN reading_cloud_service_credit_grant.created_at IS '记录创建时间，建议按 UTC 写入。';
COMMENT ON COLUMN reading_cloud_service_credit_grant.updated_at IS '记录最后更新时间，建议按 UTC 写入。';

CREATE UNIQUE INDEX IF NOT EXISTS ux_reading_credit_grant_source_ref
    ON reading_cloud_service_credit_grant(app_code, user_id, service_type, source_type, source_ref)
    WHERE source_ref IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_reading_credit_grant_user_service_active
    ON reading_cloud_service_credit_grant(app_code, user_id, service_type, expires_at, grant_type);

CREATE TABLE IF NOT EXISTS reading_announcement (
    id BIGSERIAL PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'reading',
    announcement_uuid VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(256) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'published',
    visible_start_at TIMESTAMPTZ NOT NULL,
    visible_end_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE reading_announcement IS 'reading 公告表：按时间窗向客户端下发公告通知。';
COMMENT ON COLUMN reading_announcement.id IS '公告主键。';
COMMENT ON COLUMN reading_announcement.app_code IS '固定为 reading。';
COMMENT ON COLUMN reading_announcement.announcement_uuid IS '公告唯一标识，前端用它做“不再展示”判断。';
COMMENT ON COLUMN reading_announcement.title IS '公告标题。';
COMMENT ON COLUMN reading_announcement.content IS '公告正文，可为长文本。';
COMMENT ON COLUMN reading_announcement.status IS '公告状态，例如 published / archived。';
COMMENT ON COLUMN reading_announcement.visible_start_at IS '公告开始展示时间。';
COMMENT ON COLUMN reading_announcement.visible_end_at IS '公告结束展示时间，可为空。';
COMMENT ON COLUMN reading_announcement.created_at IS '记录创建时间，建议按 UTC 写入。';
COMMENT ON COLUMN reading_announcement.updated_at IS '记录最后更新时间，建议按 UTC 写入。';

CREATE INDEX IF NOT EXISTS idx_reading_announcement_status_window
    ON reading_announcement(status, visible_start_at DESC, visible_end_at DESC);

-- =========================================================
-- 4. saving 基础种子配置（当前 unified backend 仍支持 saving）
-- =========================================================

INSERT INTO sys_app(app_code, app_name, status)
VALUES
    ('reading', '拍拍伴读', 'active'),
    ('saving', '省钱项目', 'active')
ON CONFLICT (app_code) DO NOTHING;

INSERT INTO sys_remote_config(app_code, namespace_code, config_key, config_value_json, status)
SELECT v.app_code, v.namespace_code, v.config_key, v.config_value_json::jsonb, v.status
FROM (
    VALUES
        ('reading', 'bootstrap', 'default_locale', '{"value":"zh-Hans"}', 'active'),
        ('reading', 'bootstrap', 'supported_locales', '{"value":["zh-Hans","en"]}', 'active'),
        ('reading', 'bootstrap', 'paywall_default_highlight', '{"value":"family_multi_child_lifetime"}', 'active'),
        ('reading', 'features', 'cloud_sync_enabled', '{"value":false}', 'active'),
        ('reading', 'billing_entitlements', 'productMappings.com.paipai.readalong.family.yearly', '{"value":"family_access"}', 'active'),
        ('reading', 'billing_entitlements', 'productMappings.com.paipai.readalong.family.monthly', '{"value":"family_access"}', 'active'),
        ('reading', 'billing_entitlements', 'productMappings.com.paipai.readalong.family.multi_child.lifetime', '{"value":"family_multi_child"}', 'active'),
        ('reading', 'billing_refresh_policy', 'candidateLimit', '{"value":20}', 'active'),
        ('reading', 'billing_refresh_policy', 'cooldownMinutes', '{"value":5}', 'active'),
        ('reading', 'cloud_provider', 'ocr.vendor', '{"value":"alibaba_bailian"}', 'active'),
        ('reading', 'cloud_provider', 'ocr.region', '{"value":"singapore"}', 'active'),
        ('reading', 'cloud_provider', 'ocr.endpoint', '{"value":"https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions"}', 'active'),
        ('reading', 'cloud_provider', 'ocr.apiKeyEnvName', '{"value":"DASHSCOPE_API_KEY"}', 'active'),
        ('reading', 'cloud_provider', 'ocr.headers', '{"value":{"Authorization":"Bearer ${API_KEY}","Content-Type":"application/json"}}', 'active'),
        ('reading', 'cloud_provider', 'ocr.model', '{"value":"qwen-vl-ocr-latest"}', 'active'),
        ('reading', 'cloud_provider', 'ocr.prompt', '{"value":"Please output only the text content from the image without any additional descriptions or formatting."}', 'active'),
        ('reading', 'cloud_provider', 'ocr.minPixels', '{"value":3072}', 'active'),
        ('reading', 'cloud_provider', 'ocr.maxPixels', '{"value":8388608}', 'active'),
        ('reading', 'cloud_provider', 'tts.vendor', '{"value":"alibaba_bailian"}', 'active'),
        ('reading', 'cloud_provider', 'tts.region', '{"value":"singapore"}', 'active'),
        ('reading', 'cloud_provider', 'tts.wsUrl', '{"value":"wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference"}', 'active'),
        ('reading', 'cloud_provider', 'tts.apiKeyEnvName', '{"value":"DASHSCOPE_API_KEY"}', 'active'),
        ('reading', 'cloud_provider', 'tts.headers', '{"value":{"Authorization":"Bearer ${API_KEY}"}}', 'active'),
        ('reading', 'cloud_provider', 'tts.model', '{"value":"cosyvoice-v3-flash"}', 'active'),
        ('reading', 'cloud_provider', 'tts.voice', '{"value":"longanyang"}', 'active'),
        ('reading', 'cloud_provider', 'tts.format', '{"value":"mp3"}', 'active'),
        ('reading', 'cloud_provider', 'tts.sampleRate', '{"value":22050}', 'active'),
        ('reading', 'cloud_provider', 'tts.volume', '{"value":50}', 'active'),
        ('reading', 'cloud_provider', 'tts.rate', '{"value":1.0}', 'active'),
        ('reading', 'cloud_provider', 'tts.pitch', '{"value":1.0}', 'active'),
        ('saving', 'bootstrap', 'default_locale', '{"value":"zh-Hans"}', 'active'),
        ('saving', 'bootstrap', 'supported_locales', '{"value":["zh-Hans","en","es"]}', 'active'),
        ('saving', 'bootstrap', 'recommended_plan_code', '{"value":"pro_yearly"}', 'active'),
        ('saving', 'features', 'advanced_report_enabled', '{"value":true}', 'active'),
        ('saving', 'billing_entitlements', 'productMappings.pro_yearly', '{"value":"pro_access"}', 'active'),
        ('saving', 'billing_refresh_policy', 'candidateLimit', '{"value":20}', 'active'),
        ('saving', 'billing_refresh_policy', 'cooldownMinutes', '{"value":5}', 'active')
) AS v(app_code, namespace_code, config_key, config_value_json, status)
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_remote_config existing
    WHERE existing.app_code = v.app_code
      AND existing.namespace_code = v.namespace_code
      AND existing.config_key = v.config_key
);

COMMIT;
