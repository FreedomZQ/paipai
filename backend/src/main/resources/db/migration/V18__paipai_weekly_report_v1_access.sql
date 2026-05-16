-- paipai 周报第一版权益配置。
-- 目标：个人开发者低运维、低法律风险、App Store 首发稳定；周报权益以后端和数据库配置为准。
-- 注意：sys_remote_config 当前按 updated_at/id 倒序取同 key 第一条；这里先停用旧 key 再插入新配置，避免同 key active 过多。

UPDATE sys_remote_config
SET status = 'inactive', updated_at = now()
WHERE app_code = 'paipai_readingcompanion'
  AND namespace_code IN ('billing_entitlements', 'reading_plan_catalog', 'reading_weekly_report_access')
  AND config_key IN (
    'productMappings.com.paipai.readalong.family.monthly',
    'productMappings.com.paipai.readalong.family.yearly',
    'productMappings.com.paipai.readalong.family.multi_child.lifetime',
    'free',
    'standard_single_child',
    'family_multi_child_lifetime',
    'access_matrix_v1'
  );

-- 月付/年付先作为“标准单孩子”权益；终身多孩子独立为家庭权益。
-- 这样可避免 family_access 被误判为多孩子家庭版，降低付费权益争议和审核风险。
INSERT INTO sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES
('paipai_readingcompanion', 'billing_entitlements', 'productMappings.com.paipai.readalong.family.monthly', '{"value":"family_access"}', 'active', now(), now()),
('paipai_readingcompanion', 'billing_entitlements', 'productMappings.com.paipai.readalong.family.yearly', '{"value":"family_access"}', 'active', now(), now()),
('paipai_readingcompanion', 'billing_entitlements', 'productMappings.com.paipai.readalong.family.multi_child.lifetime', '{"value":"family_multi_child"}', 'active', now(), now());

-- 免费版：只看当前孩子基础周报；不显示高级预览；不支持历史和导出；支持页面分享。
INSERT INTO sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES (
  'paipai_readingcompanion',
  'reading_plan_catalog',
  'free',
  '{"value":{"code":"free","displayName":"免费版","entitlementCode":"free","premiumActive":false,"childLimit":1,"dailyCaptureLimit":3,"localCardLimit":20,"cloudSyncEnabled":false,"advancedVoiceEnabled":false,"multiChildEnabled":false,"dailyPlanScope":"single_child","weeklyReportScope":"child","weeklyReportTier":"free","weeklyReportHistoryWeeks":0,"historyEnabled":false,"offlineHistoryPreviewEnabled":false,"exportReportEnabled":false,"pageShareEnabled":true,"customReminderEnabled":false}}',
  'active', now(), now()
);

-- 标准版：只支持 1 个孩子；完整单孩子周报；最近 4 周历史；后端不可用时可看本地已缓存历史预览；不支持导出。
INSERT INTO sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES (
  'paipai_readingcompanion',
  'reading_plan_catalog',
  'standard_single_child',
  '{"value":{"code":"standard_single_child","displayName":"标准版","entitlementCode":"family_access","matchedEntitlementCodes":["family_access"],"appStoreProductId":"com.paipai.readalong.family.monthly","matchedProductIds":["com.paipai.readalong.family.monthly","com.paipai.readalong.family.yearly"],"premiumActive":true,"highlight":true,"badgeText":"标准版","childLimit":1,"dailyCaptureLimit":20,"localCardLimit":300,"cloudSyncEnabled":true,"advancedVoiceEnabled":true,"multiChildEnabled":false,"dailyPlanScope":"single_child","weeklyReportScope":"child","weeklyReportTier":"standard","weeklyReportHistoryWeeks":4,"historyEnabled":true,"offlineHistoryPreviewEnabled":true,"exportReportEnabled":false,"pageShareEnabled":true,"customReminderEnabled":false}}',
  'active', now(), now()
);

-- 家庭版：多孩子能力来自数据库 childLimit，默认 5；家庭总览 + 最近 12 周历史；不支持导出。
INSERT INTO sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES (
  'paipai_readingcompanion',
  'reading_plan_catalog',
  'family_multi_child_lifetime',
  '{"value":{"code":"family_multi_child_lifetime","displayName":"家庭多孩子终身版","entitlementCode":"family_multi_child","matchedEntitlementCodes":["family_multi_child"],"appStoreProductId":"com.paipai.readalong.family.multi_child.lifetime","matchedProductIds":["com.paipai.readalong.family.multi_child.lifetime"],"premiumActive":true,"highlight":false,"badgeText":"家庭版","childLimit":5,"dailyCaptureLimit":50,"localCardLimit":800,"cloudSyncEnabled":true,"advancedVoiceEnabled":true,"multiChildEnabled":true,"dailyPlanScope":"per_child","weeklyReportScope":"family","weeklyReportTier":"family","weeklyReportHistoryWeeks":12,"historyEnabled":true,"offlineHistoryPreviewEnabled":true,"exportReportEnabled":false,"pageShareEnabled":true,"customReminderEnabled":false}}',
  'active', now(), now()
);

-- 周报模块访问矩阵：不返回 locked_preview；free 不预览高级模块；standard 不预览家庭模块。
-- 文案保持“家庭陪读参考”，避免学习诊断、排名、保证效果等高风险表达。
INSERT INTO sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES (
  'paipai_readingcompanion',
  'reading_weekly_report_access',
  'access_matrix_v1',
  '{"value":{"version":1,"defaultPlanCode":"free","previewMode":"disabled","historyEnabled":true,"exportReportEnabled":false,"pageShareEnabled":true,"offlineHistoryPreviewEnabled":true,"plans":{"free":{"tier":"free","historyWeeks":0,"modules":{"basic_stats":{"access":"full"},"safe_summary":{"access":"full"},"basic_suggestions":{"access":"full","maxItems":2},"single_child_detail":{"access":"hidden"},"review_focus":{"access":"hidden"},"family_overview":{"access":"hidden"},"child_summaries":{"access":"hidden"},"history_reports":{"access":"hidden"},"offline_history_preview":{"access":"hidden"},"export_report":{"access":"hidden"},"page_share":{"access":"full"}}},"standard_single_child":{"tier":"standard","historyWeeks":4,"modules":{"basic_stats":{"access":"full"},"safe_summary":{"access":"full"},"basic_suggestions":{"access":"full","maxItems":4},"single_child_detail":{"access":"full"},"review_focus":{"access":"full"},"family_overview":{"access":"hidden"},"child_summaries":{"access":"hidden"},"history_reports":{"access":"full","weeks":4},"offline_history_preview":{"access":"full"},"export_report":{"access":"hidden"},"page_share":{"access":"full"}}},"family_multi_child_lifetime":{"tier":"family","historyWeeks":12,"modules":{"basic_stats":{"access":"full"},"safe_summary":{"access":"full"},"basic_suggestions":{"access":"full","maxItems":5},"single_child_detail":{"access":"full"},"review_focus":{"access":"full"},"family_overview":{"access":"full"},"child_summaries":{"access":"full"},"history_reports":{"access":"full","weeks":12},"offline_history_preview":{"access":"full"},"export_report":{"access":"hidden"},"page_share":{"access":"full"}}}},"legal":{"defaultDisclaimer":"本报告仅用于家庭陪读参考，不用于学业评价、排名、诊断、医疗、心理或任何高风险判断。","avoidClaims":true,"avoidRanking":true}}}',
  'active', now(), now()
);
