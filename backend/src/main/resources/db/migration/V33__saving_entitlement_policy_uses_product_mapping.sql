-- saving 权益访问策略与统一计费 productMappings 对齐。
-- 中文说明：billing_entitlements / app-definition 当前把 com.savingsplanet.app.pro.monthly 投影为 pro_access。
-- 高级报告、趋势复盘、CSV 等业务权益由 saving 自己解释为 Pro 能力，但后端 verified entitlement 校验必须比对计费内核实际签发的 entitlementCode，避免前端永远无法解锁或被不同 App 的业务 benefit key 污染。

UPDATE public.sys_remote_config
SET status = 'inactive', updated_at = now()
WHERE app_code = 'saving'
  AND namespace_code = 'saving_entitlement_access_policy';

INSERT INTO public.sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES
('saving', 'saving_entitlement_access_policy', 'ios_v1', '{"value":{"version":2,"reportGeneration":{"basicReportsOfflineEnabled":true,"advancedModulesRequireBackendVerifiedEntitlement":true,"requiredPlanCodes":["pro_monthly"],"requiredEntitlements":["pro_access"],"featureBenefitKeys":["advanced_report","trend_review","csv_export","history_trend"],"backendOfflineBehavior":"lock_advanced_modules_keep_basic_report","maxServerVerifiedAgeHours":24},"securityNote":"客户端不得仅根据本地 feature flag、Paywall 配置或 StoreKit 本地交易解锁高级报告；必须以后端 entitlement 响应中的 serverVerified=true 和 pro_access 为准。advanced_report/trend_review/csv_export 是 saving 业务 benefit key，不是统一计费 entitlementCode。","opsNote":"不上传用户记账明细；只校验订阅权益。未来如开启云同步，需新增显式同步开关和隐私标签更新。"}}', 'active', now(), now());
