-- Saving / SaveMoney first-release compatibility layer.
-- Tables are prefixed with saving_ and all rows are scoped by sys_user.id to avoid affecting other apps.

CREATE TABLE IF NOT EXISTS public.saving_expense_record (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES public.sys_user(id),
    amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    category_code VARCHAR(64) NOT NULL,
    category_name VARCHAR(128),
    merchant_name VARCHAR(128),
    note TEXT,
    source VARCHAR(32) NOT NULL DEFAULT 'ios',
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_saving_expense_amount_positive CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_saving_expense_user_occurred ON public.saving_expense_record(user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_saving_expense_user_category ON public.saving_expense_record(user_id, category_code);

CREATE TABLE IF NOT EXISTS public.saving_saving_record (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES public.sys_user(id),
    amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    saving_type VARCHAR(32) NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    category_name VARCHAR(128),
    scenario VARCHAR(128),
    note TEXT,
    source VARCHAR(32) NOT NULL DEFAULT 'ios',
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_saving_saving_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_saving_saving_type CHECK (saving_type IN ('confirmed', 'avoided'))
);

CREATE INDEX IF NOT EXISTS idx_saving_saving_user_occurred ON public.saving_saving_record(user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_saving_saving_user_category ON public.saving_saving_record(user_id, category_code);
CREATE INDEX IF NOT EXISTS idx_saving_saving_user_type ON public.saving_saving_record(user_id, saving_type);

INSERT INTO public.sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES
('saving', 'saving_paywall', 'default', '{"value":{"trigger":"default","templateCode":"saving_default_v1","title":"升级省省星球 Pro","subtitle":"解锁周/月报告、更多记录额度和持续省钱复盘。权益以后端校验为准，扣款以 Apple 弹窗为准。","recommendedPlanCode":"pro_monthly","ctaText":"开始月度会员","secondaryCtaText":"继续免费使用","plans":[{"planCode":"pro_monthly","storeProductId":"com.savingsplanet.app.pro.monthly","recommended":true,"trialEnabled":false,"benefitKeys":["advanced_report","unlimited_records","trend_review"]}]}}', 'active', now(), now()),
('saving', 'saving_paywall', 'membership_center', '{"value":{"trigger":"membership_center","templateCode":"saving_member_center_v1","title":"省钱复盘更完整","subtitle":"月度会员适合先验证价值；免费版仍可记录和查看基础数据。","recommendedPlanCode":"pro_monthly","ctaText":"订阅月度会员","secondaryCtaText":"稍后再说","plans":[{"planCode":"pro_monthly","storeProductId":"com.savingsplanet.app.pro.monthly","recommended":true,"trialEnabled":false,"benefitKeys":["advanced_report","unlimited_records","trend_review"]}]}}', 'active', now(), now()),
('saving', 'saving_paywall', 'report_locked', '{"value":{"trigger":"report_locked","templateCode":"saving_report_locked_v1","title":"解锁完整报告","subtitle":"用真实记录生成周报和月报，帮助发现最值得优化的消费习惯。","recommendedPlanCode":"pro_monthly","ctaText":"解锁月度会员","secondaryCtaText":"返回记录","plans":[{"planCode":"pro_monthly","storeProductId":"com.savingsplanet.app.pro.monthly","recommended":true,"trialEnabled":false,"benefitKeys":["advanced_report","trend_review"]}]}}', 'active', now(), now()),
('saving', 'saving_entitlement_limits', 'free', '{"value":{"monthlyRecordLimit":100,"advancedReportEnabled":false,"exportEnabled":false}}', 'active', now(), now()),
('saving', 'saving_entitlement_limits', 'pro_monthly', '{"value":{"monthlyRecordLimit":-1,"advancedReportEnabled":true,"exportEnabled":true}}', 'active', now(), now()),
('saving', 'billing_entitlements', 'productMappings.com.savingsplanet.app.pro.monthly', '{"value":"pro_access"}', 'active', now(), now()),
('saving', 'bootstrap', 'recommended_plan_code', '{"value":"pro_monthly"}', 'active', now(), now()),
('saving', 'saving_copy', 'dashboard.empty.title', '{"value":"开始记录第一笔"}', 'active', now(), now()),
('saving', 'saving_copy', 'dashboard.empty.subtitle', '{"value":"记录花费和省下的钱，仪表盘会自动生成。"}', 'active', now(), now()),
('saving', 'saving_copy', 'dashboard.default.title', '{"value":"今天也在变会省"}', 'active', now(), now()),
('saving', 'saving_copy', 'dashboard.default.subtitle', '{"value":"继续记录，周报和月报会更准确。"}', 'active', now(), now()),
('saving', 'saving_copy', 'report.weekly.title', '{"value":"周度省钱报告"}', 'active', now(), now()),
('saving', 'saving_copy', 'report.monthly.title', '{"value":"月度省钱报告"}', 'active', now(), now()),
('saving', 'saving_copy', 'report.disclaimer', '{"value":"报告仅用于个人记账复盘，不构成财务、投资、税务或法律建议。"}', 'active', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO public.sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES
('saving', 'saving_copy', 'dashboard.period.today', '{"value":"今日"}', 'active', now(), now()),
('saving', 'saving_copy', 'dashboard.period.week', '{"value":"本周"}', 'active', now(), now()),
('saving', 'saving_copy', 'dashboard.period.month', '{"value":"本月"}', 'active', now(), now()),
('saving', 'saving_copy', 'report.summary.prefix', '{"value":"本期已生成基于真实记录的报告。"}', 'active', now(), now()),
('saving', 'saving_copy', 'report.empty.summary', '{"value":"本期还没有记录，先添加几笔花费和省钱记录。"}', 'active', now(), now()),
('saving', 'saving_copy', 'report.suggestion.keep.title', '{"value":"保持记录"}', 'active', now(), now()),
('saving', 'saving_copy', 'report.suggestion.keep.description', '{"value":"持续记录能提升报告准确度。"}', 'active', now(), now()),
('saving', 'saving_copy', 'report.suggestion.start.title', '{"value":"先完成第一批记录"}', 'active', now(), now()),
('saving', 'saving_copy', 'report.suggestion.start.description', '{"value":"添加 3 笔花费和 1 笔省钱记录后，趋势会更可信。"}', 'active', now(), now()),
('saving', 'saving_copy', 'report.section.coreMetrics.title', '{"value":"核心指标"}', 'active', now(), now())
ON CONFLICT DO NOTHING;
