-- Saving iOS release-gate baseline configuration.
-- Non-secret release metadata is seeded here; Apple Team/App Store private values must still be supplied by the operator.

UPDATE public.sys_remote_config
SET status = 'inactive', updated_at = now()
WHERE app_code = 'saving'
  AND namespace_code = 'release_ios'
  AND config_key IN (
      'development_team',
      'marketing_version',
      'current_project_version',
      'minimum_ios_version',
      'minimum_ipados_version',
      'bundle_identifier',
      'api_base_url',
      'saving_api_base_url',
      'first_release_positioning',
      'low_risk_review_notes',
      'sandbox_test_plan'
  );

INSERT INTO public.sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES
('saving', 'release_ios', 'development_team', '{"value":"__FILL_ME__"}', 'active', now(), now()),
('saving', 'release_ios', 'marketing_version', '{"value":"0.1.0"}', 'active', now(), now()),
('saving', 'release_ios', 'current_project_version', '{"value":"1"}', 'active', now(), now()),
('saving', 'release_ios', 'minimum_ios_version', '{"value":"15.0"}', 'active', now(), now()),
('saving', 'release_ios', 'minimum_ipados_version', '{"value":"15.0"}', 'active', now(), now()),
('saving', 'release_ios', 'bundle_identifier', '{"value":"com.savingsplanet.app"}', 'active', now(), now()),
('saving', 'release_ios', 'api_base_url', '{"value":"https://api.savemoney.app"}', 'active', now(), now()),
('saving', 'release_ios', 'saving_api_base_url', '{"value":"https://api.savemoney.app"}', 'active', now(), now()),
('saving', 'release_ios', 'first_release_positioning', '{"value":"首发版本按 iOS/iPadOS 15.0+、Sign in with Apple、App 内购买、恢复购买、账号删除与基础省钱记录/报告能力提审。"}', 'active', now(), now()),
('saving', 'release_ios', 'low_risk_review_notes', '{"value":"省省星球仅提供个人记账和省钱复盘，不构成财务、投资、税务或法律建议；账号删除不取消 Apple 订阅，订阅仍由用户在 Apple 账户订阅页管理。"}', 'active', now(), now()),
('saving', 'release_ios', 'sandbox_test_plan', '{"value":"TestFlight/Sandbox 回归必须覆盖：Apple 登录、月度订阅购买、购买后权益刷新、恢复购买、账号删除、本地缓存清理、旧 token 失效。"}', 'active', now(), now());
