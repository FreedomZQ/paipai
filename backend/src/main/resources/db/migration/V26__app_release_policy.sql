-- 多 App 通用版本更新策略。
-- 中文说明：该配置由 app_code + namespace_code 隔离，所有 App 均可复用 namespace `app_release_policy`。
-- 客户端仅可展示普通 App Store 更新提示并跳转 Apple 官方页面；不得分发安装包、伪装系统更新弹窗、绕过 App Store 审核或把更新提示与订阅诱导混淆。

UPDATE public.sys_remote_config
SET status = 'inactive', updated_at = now()
WHERE app_code = 'saving'
  AND namespace_code = 'app_release_policy';

INSERT INTO public.sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES
('saving', 'app_release_policy', 'ios', '{"value":{"version":1,"appName":"省省星球","platform":"ios","latestVersion":"0.1.0","latestBuild":"1","minimumSupportedVersion":"0.1.0","severity":"recommended","blocking":false,"appStoreId":"","appStoreUrl":"","title":"发现新版本","message":"有新版本可用。你可以前往 App Store 查看并决定是否更新。","ctaText":"前往 App Store","releaseNotes":["优化体验与稳定性"],"opsNote":"拿到 App Store Apple ID 后填写 appStoreId 或 appStoreUrl；latestVersion 高于客户端版本时才展示升级提示。"}}', 'active', now(), now());
