-- saving 报告访问矩阵、CSV 导出开关。
-- 中文说明：saving V1 不同步、不存储用户使用数据；周报/月报/CSV 均由 App 基于本地 CoreData 生成。
-- 后续如用户明确开启云同步，再用新的迁移增加服务端同步表/快照表。

UPDATE public.sys_remote_config
SET status = 'inactive', updated_at = now()
WHERE app_code = 'saving'
  AND namespace_code IN ('saving_report_access');

INSERT INTO public.sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES
('saving', 'saving_report_access', 'v1', '{"value":{"version":1,"defaultPlanCode":"free","reportTypes":["weekly","monthly"],"modules":{"overview":{"title":"核心指标","legalRisk":"low"},"comparison":{"title":"周期对比","legalRisk":"low"},"category_breakdown":{"title":"分类结构","legalRisk":"low"},"trend_review":{"title":"消费趋势复盘","legalRisk":"medium","disclaimer":"趋势仅基于用户个人记录，不构成财务建议。"},"top_actions":{"title":"省钱行为榜单","legalRisk":"low"},"high_risk_window":{"title":"高消费时段","legalRisk":"medium","disclaimer":"仅用于个人复盘，不用于信用、风控或任何高风险判断。"},"csv_export":{"title":"CSV 导出","legalRisk":"low"}},"plans":{"free":{"tier":"free","advancedUnlocked":false,"modules":{"overview":"full","comparison":"full","category_breakdown":"locked","trend_review":"locked","top_actions":"locked","high_risk_window":"locked","csv_export":"locked"}},"pro_monthly":{"tier":"pro","advancedUnlocked":true,"modules":{"overview":"full","comparison":"full","category_breakdown":"full","trend_review":"full","top_actions":"full","high_risk_window":"full","csv_export":"full"}}},"upgradeTrigger":"report_locked","legal":{"defaultDisclaimer":"报告仅用于个人记账复盘，不构成财务、投资、税务或法律建议。","avoidClaims":true,"avoidCreditScoring":true}}}', 'active', now(), now());

-- CSV 导出在 iOS 端按本地文件生成方式实现，后端只通过配置开关与权益矩阵控制显示，降低运维和隐私风险。
UPDATE public.sys_remote_config
SET config_value_json = jsonb_set(config_value_json, '{value,flags,csvExportEnabled}', 'true'::jsonb),
    updated_at = now()
WHERE app_code = 'saving'
  AND namespace_code = 'saving_feature_flags'
  AND config_key = 'ios_v1'
  AND status = 'active';
