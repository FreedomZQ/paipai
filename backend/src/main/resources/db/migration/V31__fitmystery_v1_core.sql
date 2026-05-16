-- FitMystery V2 core schema. All objects are prefixed with fit_ and app-scoped by app_code='fitmystery'.
-- This migration only adds new FitMystery tables/config and does not alter reading_ or saving_ business tables.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.fit_activity_event (
    id UUID PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'fitmystery',
    user_id BIGINT NOT NULL REFERENCES public.sys_user(id),
    idempotency_key VARCHAR(128) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    quantity NUMERIC(14,2) NOT NULL,
    unit VARCHAR(16) NOT NULL,
    event_date DATE NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    client_recorded_at TIMESTAMP WITH TIME ZONE,
    trust_level VARCHAR(32) NOT NULL DEFAULT 'normal',
    raw_payload_json TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'accepted',
    reject_reason VARCHAR(256),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_fit_activity_event_idem UNIQUE (app_code, user_id, idempotency_key),
    CONSTRAINT chk_fit_activity_event_type CHECK (event_type IN ('water','steps','exercise','weight')),
    CONSTRAINT chk_fit_activity_event_source CHECK (source IN ('manual','healthkit','system')),
    CONSTRAINT chk_fit_activity_event_quantity CHECK (quantity >= 0)
);
CREATE INDEX IF NOT EXISTS idx_fit_activity_user_date ON public.fit_activity_event(app_code, user_id, event_date DESC);
CREATE INDEX IF NOT EXISTS idx_fit_activity_user_type_date ON public.fit_activity_event(app_code, user_id, event_type, event_date DESC);

CREATE TABLE IF NOT EXISTS public.fit_points_ledger (
    id UUID PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'fitmystery',
    user_id BIGINT NOT NULL REFERENCES public.sys_user(id),
    ledger_type VARCHAR(32) NOT NULL,
    points_delta INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    related_event_id UUID,
    related_draw_id UUID,
    idempotency_key VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    note VARCHAR(256),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_fit_points_ledger_idem UNIQUE (app_code, user_id, idempotency_key),
    CONSTRAINT chk_fit_points_ledger_type CHECK (ledger_type IN ('earn','spend','adjust','expire'))
);
CREATE INDEX IF NOT EXISTS idx_fit_points_user_created ON public.fit_points_ledger(app_code, user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.fit_daily_score_snapshot (
    id UUID PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'fitmystery',
    user_id BIGINT NOT NULL REFERENCES public.sys_user(id),
    score_date DATE NOT NULL,
    water_ml NUMERIC(14,2) NOT NULL DEFAULT 0,
    steps INTEGER NOT NULL DEFAULT 0,
    exercise_minutes INTEGER NOT NULL DEFAULT 0,
    points_earned INTEGER NOT NULL DEFAULT 0,
    points_spent INTEGER NOT NULL DEFAULT 0,
    points_balance INTEGER NOT NULL DEFAULT 0,
    box_chance_balance INTEGER NOT NULL DEFAULT 0,
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_fit_daily_score UNIQUE (app_code, user_id, score_date)
);
CREATE INDEX IF NOT EXISTS idx_fit_daily_score_user_date ON public.fit_daily_score_snapshot(app_code, user_id, score_date DESC);

CREATE TABLE IF NOT EXISTS public.fit_draw_chance_ledger (
    id UUID PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'fitmystery',
    user_id BIGINT NOT NULL REFERENCES public.sys_user(id),
    ledger_type VARCHAR(32) NOT NULL,
    chance_delta INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128),
    idempotency_key VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_fit_draw_chance_idem UNIQUE (app_code, user_id, idempotency_key),
    CONSTRAINT chk_fit_draw_chance_type CHECK (ledger_type IN ('grant','consume','adjust','expire'))
);
CREATE INDEX IF NOT EXISTS idx_fit_draw_chance_user_created ON public.fit_draw_chance_ledger(app_code, user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.fit_blind_box_pool (
    id UUID PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'fitmystery',
    pool_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    access_rule_json TEXT NOT NULL,
    probability_rule_json TEXT NOT NULL,
    odds_version VARCHAR(64) NOT NULL,
    odds_disclosure_json TEXT NOT NULL,
    no_cash_value_notice VARCHAR(256) NOT NULL DEFAULT 'Virtual collectible items only. No cash value. Not transferable or redeemable.',
    starts_at TIMESTAMP WITH TIME ZONE,
    ends_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_fit_pool_code UNIQUE (app_code, pool_code)
);

CREATE TABLE IF NOT EXISTS public.fit_blind_box_item (
    id UUID PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'fitmystery',
    item_code VARCHAR(64) NOT NULL,
    pool_code VARCHAR(64) NOT NULL,
    rarity VARCHAR(32) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description TEXT,
    image_key VARCHAR(256),
    weight INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_fit_item_code UNIQUE (app_code, item_code),
    CONSTRAINT chk_fit_item_rarity CHECK (rarity IN ('common','rare','epic','legendary')),
    CONSTRAINT chk_fit_item_weight CHECK (weight >= 0)
);
CREATE INDEX IF NOT EXISTS idx_fit_item_pool ON public.fit_blind_box_item(app_code, pool_code, status);

CREATE TABLE IF NOT EXISTS public.fit_blind_box_draw (
    id UUID PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'fitmystery',
    user_id BIGINT NOT NULL REFERENCES public.sys_user(id),
    pool_code VARCHAR(64) NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    rarity VARCHAR(32) NOT NULL,
    consume_type VARCHAR(32) NOT NULL,
    points_spent INTEGER NOT NULL DEFAULT 0,
    chances_spent INTEGER NOT NULL DEFAULT 0,
    rng_version VARCHAR(64) NOT NULL,
    odds_version VARCHAR(64) NOT NULL,
    server_seed_hash VARCHAR(128),
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_fit_draw_idem UNIQUE (app_code, user_id, idempotency_key),
    CONSTRAINT chk_fit_draw_consume_type CHECK (consume_type IN ('points','chance','free'))
);
CREATE INDEX IF NOT EXISTS idx_fit_draw_user_created ON public.fit_blind_box_draw(app_code, user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.fit_user_collection (
    id UUID PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'fitmystery',
    user_id BIGINT NOT NULL REFERENCES public.sys_user(id),
    item_code VARCHAR(64) NOT NULL,
    first_draw_id UUID NOT NULL,
    first_obtained_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_obtained_at TIMESTAMP WITH TIME ZONE NOT NULL,
    obtain_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_fit_collection_user_item UNIQUE (app_code, user_id, item_code)
);
CREATE INDEX IF NOT EXISTS idx_fit_collection_user ON public.fit_user_collection(app_code, user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS public.fit_report_generation_ledger (
    id UUID PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'fitmystery',
    user_id BIGINT NOT NULL REFERENCES public.sys_user(id),
    ledger_type VARCHAR(32) NOT NULL,
    report_type VARCHAR(32) NOT NULL,
    quota_delta INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    period_key VARCHAR(64),
    local_data_hash VARCHAR(128),
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_fit_report_generation_idem UNIQUE (app_code, user_id, idempotency_key),
    CONSTRAINT chk_fit_report_generation_type CHECK (ledger_type IN ('grant','consume','adjust','expire')),
    CONSTRAINT chk_fit_report_generation_report_type CHECK (report_type IN ('weekly','monthly','all'))
);
CREATE INDEX IF NOT EXISTS idx_fit_report_generation_user_created ON public.fit_report_generation_ledger(app_code, user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.fit_account_deletion_request (
    id UUID PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL DEFAULT 'fitmystery',
    user_id BIGINT NOT NULL REFERENCES public.sys_user(id),
    request_status VARCHAR(32) NOT NULL DEFAULT 'completed',
    deletion_scope VARCHAR(64) NOT NULL DEFAULT 'fitmystery_app_data',
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    note VARCHAR(256)
);
CREATE INDEX IF NOT EXISTS idx_fit_account_deletion_user ON public.fit_account_deletion_request(app_code, user_id, requested_at DESC);

INSERT INTO public.fit_blind_box_pool (id, app_code, pool_code, display_name, status, access_rule_json, probability_rule_json, odds_version, odds_disclosure_json, no_cash_value_notice, created_at, updated_at)
VALUES (
  gen_random_uuid(), 'fitmystery', 'starter_pool', '健康初心者', 'active',
  '{"requiredEntitlements":[]}',
  '{"version":"server_weighted_v1","note":"Server-side weighted RNG. Client never provides item or rarity."}',
  'starter_pool_odds_v1',
  '{"version":"starter_pool_odds_v1","poolCode":"starter_pool","noCashValueNotice":"仅获得 App 内虚拟收藏卡，无现金价值，不可交易、转让、提现或兑换实物。","rarityOdds":[{"rarity":"common","displayName":"普通","probability":"78.00%"},{"rarity":"rare","displayName":"稀有","probability":"17.00%"},{"rarity":"epic","displayName":"史诗","probability":"4.50%"},{"rarity":"legendary","displayName":"传说","probability":"0.50%"}],"pityRule":"首版不启用保底；后续如启用，需在本页展示触发条件。"}',
  '仅获得 App 内虚拟收藏卡，无现金价值，不可交易、转让、提现或兑换实物。', now(), now()
)
ON CONFLICT (app_code, pool_code) DO NOTHING;

INSERT INTO public.fit_blind_box_item (id, app_code, item_code, pool_code, rarity, display_name, description, image_key, weight, status, created_at, updated_at)
VALUES
(gen_random_uuid(), 'fitmystery', 'card_water_001', 'starter_pool', 'common', '补水小星', '完成一次认真补水。', 'cards/water_001', 7800, 'active', now(), now()),
(gen_random_uuid(), 'fitmystery', 'card_steps_001', 'starter_pool', 'rare', '步履闪光', '今天也向前多走了一点。', 'cards/steps_001', 1700, 'active', now(), now()),
(gen_random_uuid(), 'fitmystery', 'card_exercise_001', 'starter_pool', 'epic', '燃动徽章', '运动让今日能量满格。', 'cards/exercise_001', 450, 'active', now(), now()),
(gen_random_uuid(), 'fitmystery', 'card_balance_001', 'starter_pool', 'legendary', '自律星环', '连续记录的高光时刻。', 'cards/balance_001', 50, 'active', now(), now())
ON CONFLICT (app_code, item_code) DO NOTHING;

INSERT INTO public.sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES
('fitmystery', 'fit_points_policy', 'ios_v1', '{"value":{"version":"points_v1","dailyCaps":{"water":80,"steps":120,"exercise":120,"total":260},"rules":{"water":{"pointsPer100ml":2,"maxMlPerDay":4000},"steps":{"pointsPer1000Steps":5,"maxStepsPerDay":30000},"exercise":{"pointsPer10Minutes":8,"maxMinutesPerDay":240}}}}', 'active', now(), now()),
('fitmystery', 'fit_box_policy', 'ios_v1', '{"value":{"version":"box_policy_v1","defaultPoolCode":"starter_pool","pointsPerDraw":100,"dailyFreeChance":1,"memberDailyBonusChance":1,"allowChancePurchase":true,"noCashValueNotice":"仅获得 App 内虚拟收藏卡，无现金价值，不可交易、转让、提现或兑换实物。"}}', 'active', now(), now()),
('fitmystery', 'fit_odds_disclosure', 'starter_pool', '{"value":{"version":"starter_pool_odds_v1","poolCode":"starter_pool","noCashValueNotice":"仅获得 App 内虚拟收藏卡，无现金价值，不可交易、转让、提现或兑换实物。","rarityOdds":[{"rarity":"common","displayName":"普通","probability":"78.00%"},{"rarity":"rare","displayName":"稀有","probability":"17.00%"},{"rarity":"epic","displayName":"史诗","probability":"4.50%"},{"rarity":"legendary","displayName":"传说","probability":"0.50%"}],"pityRule":"首版不启用保底；后续如启用，需在本页展示触发条件。"}}', 'active', now(), now()),
('fitmystery', 'fit_product_policy', 'ios_v1', '{"value":{"version":"product_policy_v1","products":{"com.fitmystery.pro.monthly":{"type":"subscription","entitlements":["pro_access","report_weekly_access","report_monthly_access","report_history_access"]},"com.fitmystery.pro.yearly":{"type":"subscription","entitlements":["pro_access","report_weekly_access","report_monthly_access","report_history_access"]},"com.fitmystery.box5":{"type":"consumable","grantChance":5},"com.fitmystery.box10":{"type":"consumable","grantChance":10},"com.fitmystery.box25":{"type":"consumable","grantChance":25}}}}', 'active', now(), now()),
('fitmystery', 'fit_report_generation_policy', 'ios_v1', '{"value":{"version":"report_generation_policy_v1","freeInitialQuota":1,"quotaReportTypes":["weekly","monthly"],"proUnlimited":true,"serverStoresReportPayload":false,"note":"报告正文和明细数据由 App 基于本机记录生成；后端只验证生成权益和记录额度流水。"}}', 'active', now(), now()),
('fitmystery', 'fit_report_policy', 'ios_v1', '{"value":{"version":"report_policy_v1","free":{"weeklySummaryEnabled":true,"monthlySummaryEnabled":true,"historyLimit":1,"advancedTrendEnabled":false},"pro":{"weeklyFullEnabled":true,"monthlyFullEnabled":true,"historyLimit":24,"advancedTrendEnabled":true,"exportEnabled":true}}}', 'active', now(), now()),
('fitmystery', 'fit_app_store_compliance', 'ios_submission_v1', '{"value":{"reviewPositioning":"健康打卡游戏化收藏 App。随机开盒只产出无现金价值的虚拟收藏卡，不支持交易、转让、提现或兑换实物。","iapRule":"所有数字权益均使用 Apple In-App Purchase。","healthDataUse":"健康数据仅用于个人记录、积分计算和报告生成，不用于广告、追踪或出售。","accountDeletionEndpoint":"DELETE /api/v1/fitmystery/account","requiredReviewNotes":["Apple 登录测试路径","Sandbox IAP 商品","概率披露入口","账号删除入口","隐私政策/用户协议 URL"]}}', 'active', now(), now()),
('fitmystery', 'billing_entitlements', 'productMappings.com.fitmystery.pro.monthly', '{"value":"pro_access"}', 'active', now(), now()),
('fitmystery', 'billing_entitlements', 'productMappings.com.fitmystery.pro.yearly', '{"value":"pro_access"}', 'active', now(), now()),
('fitmystery', 'billing_entitlements', 'productMappings.com.fitmystery.box5', '{"value":"box_chance_pack_5"}', 'active', now(), now()),
('fitmystery', 'billing_entitlements', 'productMappings.com.fitmystery.box10', '{"value":"box_chance_pack_10"}', 'active', now(), now()),
('fitmystery', 'billing_entitlements', 'productMappings.com.fitmystery.box25', '{"value":"box_chance_pack_25"}', 'active', now(), now())
ON CONFLICT DO NOTHING;
