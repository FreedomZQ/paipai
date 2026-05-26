-- 拍拍伴读历史“次数权益”数据清理脚本
-- 执行日期: 2026-05-25
-- 目标:
-- 1. 备份历史次数权益相关数据，保留可追溯和可回滚依据。
-- 2. 清空旧会员/次数权益配置与云端次数包记录。
-- 3. 保留双积分持久化恢复与预留功能相关表:
--    reading_entitlement_token / reading_entitlement_wallet /
--    reading_entitlement_reservation / reading_entitlement_ledger /
--    reading_entitlement_snapshot。

BEGIN;

CREATE SCHEMA IF NOT EXISTS backup_20260525_legacy_count_entitlement;

CREATE TABLE IF NOT EXISTS backup_20260525_legacy_count_entitlement.reading_cloud_service_credit_grant AS
SELECT *
FROM public.reading_cloud_service_credit_grant
WHERE app_code = 'paipai_readingcompanion';

CREATE TABLE IF NOT EXISTS backup_20260525_legacy_count_entitlement.reading_daily_quota_config AS
SELECT *
FROM public.reading_daily_quota_config
WHERE app_code = 'paipai_readingcompanion';

CREATE TABLE IF NOT EXISTS backup_20260525_legacy_count_entitlement.sys_entitlement_feature AS
SELECT *
FROM public.sys_entitlement_feature
WHERE app_code = 'paipai_readingcompanion';

CREATE TABLE IF NOT EXISTS backup_20260525_legacy_count_entitlement.sys_membership_plan AS
SELECT *
FROM public.sys_membership_plan
WHERE app_code = 'paipai_readingcompanion';

CREATE TABLE IF NOT EXISTS backup_20260525_legacy_count_entitlement.sys_plan_feature_rule AS
SELECT *
FROM public.sys_plan_feature_rule
WHERE app_code = 'paipai_readingcompanion';

CREATE TABLE IF NOT EXISTS backup_20260525_legacy_count_entitlement.sys_product_entitlement_mapping AS
SELECT *
FROM public.sys_product_entitlement_mapping
WHERE app_code = 'paipai_readingcompanion'
  AND product_type <> 'consumable';

CREATE TABLE IF NOT EXISTS backup_20260525_legacy_count_entitlement.sys_user_entitlement_grant AS
SELECT *
FROM public.sys_user_entitlement_grant
WHERE app_code = 'paipai_readingcompanion';

CREATE TABLE IF NOT EXISTS backup_20260525_legacy_count_entitlement.sys_user_plan_snapshot AS
SELECT *
FROM public.sys_user_plan_snapshot
WHERE app_code = 'paipai_readingcompanion';

DELETE FROM public.reading_cloud_service_credit_grant
WHERE app_code = 'paipai_readingcompanion';

DELETE FROM public.reading_daily_quota_config
WHERE app_code = 'paipai_readingcompanion';

DELETE FROM public.sys_plan_feature_rule
WHERE app_code = 'paipai_readingcompanion';

DELETE FROM public.sys_entitlement_feature
WHERE app_code = 'paipai_readingcompanion';

DELETE FROM public.sys_membership_plan
WHERE app_code = 'paipai_readingcompanion';

DELETE FROM public.sys_product_entitlement_mapping
WHERE app_code = 'paipai_readingcompanion'
  AND product_type <> 'consumable';

DELETE FROM public.sys_user_entitlement_grant
WHERE app_code = 'paipai_readingcompanion';

DELETE FROM public.sys_user_plan_snapshot
WHERE app_code = 'paipai_readingcompanion';

COMMIT;

-- 回滚方式:
-- 1. BEGIN;
-- 2. INSERT INTO public.<table_name> SELECT * FROM backup_20260525_legacy_count_entitlement.<table_name>
--    ON CONFLICT DO NOTHING;
-- 3. COMMIT;
