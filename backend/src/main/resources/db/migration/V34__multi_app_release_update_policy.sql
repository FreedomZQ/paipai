-- 多 App 通用 App Store 更新提示配置补齐。
-- 中文说明：
-- 1. 该 namespace 只承载普通版本提示与 App Store 跳转配置，客户端不得硬编码下载地址。
-- 2. appStoreUrl / appStoreId 需要在 App Store Connect 生成真实 App Apple ID 后由生产配置覆盖；
--    未配置真实 URL 时，客户端只展示当前/最新版本，不展示可点击跳转入口。
-- 3. 各 App 使用相同 namespace，但按 app_code 隔离，避免统一后端下跳转到其他 App 的商店页。

UPDATE public.sys_remote_config
SET status = 'inactive', updated_at = now()
WHERE app_code IN ('paipai_readingcompanion', 'fitmystery')
  AND namespace_code = 'app_release_policy';

INSERT INTO public.sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES
('paipai_readingcompanion', 'app_release_policy', 'ios', '{"value":{"version":1,"appName":"拍拍伴读","platform":"ios","latestVersion":"1.0.0","latestBuild":"1","minimumSupportedVersion":"1.0.0","severity":"recommended","blocking":false,"appStoreId":"","appStoreUrl":"","downloadUrl":"","storeUrl":"","updateUrl":"","title":"发现新版本","message":"拍拍伴读有新版本可用。你可以前往 App Store 查看并决定是否更新。","ctaText":"前往 App Store","releaseNotes":["优化陪读体验与稳定性"],"opsNote":"生产环境必须填入本 App 的真实 App Store URL 或 appStoreId；客户端只消费该配置，不在前端硬编码下载地址。"}}', 'active', now(), now()),
('fitmystery', 'app_release_policy', 'ios', '{"value":{"version":1,"appName":"FitMystery","platform":"ios","latestVersion":"1.0.0","latestBuild":"1","minimumSupportedVersion":"1.0.0","severity":"recommended","blocking":false,"appStoreId":"","appStoreUrl":"","downloadUrl":"","storeUrl":"","updateUrl":"","title":"发现新版本","message":"FitMystery 有新版本可用。你可以前往 App Store 查看并决定是否更新。","ctaText":"前往 App Store","releaseNotes":["优化体验与稳定性"],"opsNote":"生产环境必须填入本 App 的真实 App Store URL 或 appStoreId；客户端只消费该配置，不在前端硬编码下载地址。"}}', 'active', now(), now());
