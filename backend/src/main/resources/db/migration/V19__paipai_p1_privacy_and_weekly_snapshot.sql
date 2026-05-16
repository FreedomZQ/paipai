-- paipai P1 隐私与周报历史升级。
-- 目标：降低儿童原文内容在云端明文留存风险；把历史周报升级为首次生成后复用的低运维快照。
-- 多 App 说明：所有新增数据仍带 app_code，后续其他 App 接入统一后端时必须独立 app_code 隔离。

ALTER TABLE reading_review_card
  ADD COLUMN IF NOT EXISTS content_encryption_version varchar(64),
  ADD COLUMN IF NOT EXISTS content_key_id varchar(128);

COMMENT ON COLUMN reading_review_card.encrypted_text IS '句卡正文密文或旧版编码值。P1 后 iOS 首选 enc:v1:aesgcm:keychain:*；后端不保存可解密密钥。';
COMMENT ON COLUMN reading_review_card.content_encryption_version IS '正文加密版本，例如 aesgcm_keychain_v1；用于后续密钥迁移或多设备加密方案演进。';
COMMENT ON COLUMN reading_review_card.content_key_id IS '客户端密钥标识。首发为本机 Keychain key id，不上传真实密钥，降低个人开发者密钥托管运维成本。';

CREATE TABLE IF NOT EXISTS reading_weekly_report_snapshot (
    id varchar(64) PRIMARY KEY,
    app_code varchar(64) DEFAULT 'paipai_readingcompanion' NOT NULL,
    user_id bigint NOT NULL,
    child_id varchar(64),
    scope varchar(32) NOT NULL,
    week_start date NOT NULL,
    week_end date NOT NULL,
    plan_code varchar(64) NOT NULL,
    tier varchar(32),
    payload_version integer DEFAULT 1 NOT NULL,
    report_payload_json text NOT NULL CHECK (report_payload_json::jsonb IS NOT NULL),
    report_status varchar(32) DEFAULT 'active' NOT NULL,
    generated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

COMMENT ON TABLE reading_weekly_report_snapshot IS '拍拍伴读周报历史快照。首次访问历史周报时生成，后续复用，降低重复聚合成本并保持历史报告稳定。';
COMMENT ON COLUMN reading_weekly_report_snapshot.report_payload_json IS '聚合后的周报 JSON，只包含统计、建议、模块和免责声明，不存儿童原始句卡正文。字段用 text + JSON 校验，避免 MyBatis 字符串写入 jsonb 需要自定义类型处理，降低低运维成本。';

CREATE UNIQUE INDEX IF NOT EXISTS uk_reading_weekly_report_snapshot_scope
ON reading_weekly_report_snapshot (app_code, user_id, scope, COALESCE(child_id, '__family__'), week_start, plan_code)
WHERE report_status = 'active';

CREATE INDEX IF NOT EXISTS idx_reading_weekly_report_snapshot_user_week
ON reading_weekly_report_snapshot (app_code, user_id, week_start DESC, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_reading_review_card_user_child_created
ON reading_review_card (user_id, child_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reading_review_card_encryption_version
ON reading_review_card (app_code, content_encryption_version);
