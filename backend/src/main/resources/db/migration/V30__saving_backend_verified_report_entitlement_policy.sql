-- saving 报告权益必须以后端 entitlement 验证为准。
-- 中文说明：首发报告仍在 App 本机基于 CoreData 生成，不上传记账明细；但是否展示 Pro 高级报告模块，必须依赖后端计费内核返回的 serverVerified entitlement。
-- 这样可以避免普通用户仅通过修改本地配置或本地缓存获得官方 App 中不符合会员等级的权益。后端离线时，基础记录、基础看板、基础周/月报仍可用；Pro 高级模块降级为锁定态。

UPDATE public.sys_remote_config
SET status = 'inactive', updated_at = now()
WHERE app_code = 'saving'
  AND namespace_code = 'saving_entitlement_access_policy';

INSERT INTO public.sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES
('saving', 'saving_entitlement_access_policy', 'ios_v1', '{"value":{"version":1,"reportGeneration":{"basicReportsOfflineEnabled":true,"advancedModulesRequireBackendVerifiedEntitlement":true,"requiredPlanCodes":["pro_monthly"],"requiredEntitlements":["advanced_report","trend_review"],"backendOfflineBehavior":"lock_advanced_modules_keep_basic_report","maxServerVerifiedAgeHours":24},"securityNote":"客户端不得仅根据本地 feature flag、Paywall 配置或 StoreKit 本地交易解锁高级报告；必须以后端 entitlement 响应中的 serverVerified=true 和有效 entitlement 为准。修改客户端代码无法获得后端签发权益。","opsNote":"不上传用户记账明细；只校验订阅权益。未来如开启云同步，需新增显式同步开关和隐私标签更新。"}}', 'active', now(), now());
