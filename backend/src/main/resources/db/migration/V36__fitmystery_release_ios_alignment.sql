-- FitMystery iOS release-gate alignment.
-- 中文说明：补齐 FitMystery 进入当前发布波次后 release-gate 需要读取的 release_ios 配置。
-- 生产发布前，development_team 仍需由 Apple Developer Account Owner / CI 私有配置覆盖为真实 Team ID；
-- 这里保留 __FILL_ME__，让 release-gate 在未补齐真实签名团队时继续 fail closed，避免误放行。

UPDATE public.sys_remote_config
SET status = 'inactive', updated_at = now()
WHERE app_code = 'fitmystery'
  AND namespace_code = 'release_ios';

INSERT INTO public.sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES
('fitmystery', 'release_ios', 'development_team', '{"value":"__FILL_ME__"}', 'active', now(), now()),
('fitmystery', 'release_ios', 'marketing_version', '{"value":"1.0.0"}', 'active', now(), now()),
('fitmystery', 'release_ios', 'current_project_version', '{"value":"1"}', 'active', now(), now()),
('fitmystery', 'release_ios', 'minimum_ios_version', '{"value":"15.0"}', 'active', now(), now()),
('fitmystery', 'release_ios', 'minimum_ipados_version', '{"value":"15.0"}', 'active', now(), now()),
('fitmystery', 'release_ios', 'bundle_identifier', '{"value":"com.fitmystery.app"}', 'active', now(), now()),
('fitmystery', 'release_ios', 'api_base_url', '{"value":"https://api.fitmystery.app"}', 'active', now(), now()),
('fitmystery', 'release_ios', 'fitmystery_api_base_url', '{"value":"https://api.fitmystery.app"}', 'active', now(), now()),
('fitmystery', 'release_ios', 'privacy_policy_url', '{"value":"https://www.fitmystery.app/privacy"}', 'active', now(), now()),
('fitmystery', 'release_ios', 'terms_url', '{"value":"https://www.fitmystery.app/terms"}', 'active', now(), now()),
('fitmystery', 'release_ios', 'support_url', '{"value":"https://www.fitmystery.app/support"}', 'active', now(), now()),
('fitmystery', 'release_ios', 'product_ids', '{"value":["com.fitmystery.pro.monthly","com.fitmystery.pro.yearly","com.fitmystery.box5","com.fitmystery.box10","com.fitmystery.box25"]}', 'active', now(), now());
