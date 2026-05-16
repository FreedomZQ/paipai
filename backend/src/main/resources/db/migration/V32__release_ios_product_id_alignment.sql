-- 多 App release-gate 商品 ID 对齐配置。
-- 中文说明：统一后端可以复用 App Store 验签和权益投影能力，但每个 App 的商品 ID 必须按 appCode 隔离。
-- release_ios.product_ids 用于上线门禁比对，防止 saving 与拍拍伴读的 billing productMappings / App Store Connect 商品配置互相串用。

UPDATE public.sys_remote_config
SET status = 'inactive', updated_at = now()
WHERE namespace_code = 'release_ios'
  AND config_key = 'product_ids'
  AND app_code IN ('paipai_readingcompanion', 'saving');

INSERT INTO public.sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES
('paipai_readingcompanion', 'release_ios', 'product_ids', '{"value":["com.paipai.readalong.family.monthly","com.paipai.readalong.family.multi_child.lifetime","com.paipai.readalong.family.yearly"]}', 'active', now(), now()),
('saving', 'release_ios', 'product_ids', '{"value":["com.savingsplanet.app.pro.monthly"]}', 'active', now(), now());
