-- saving V1 local-only user data policy.
-- 中文说明：首发不进行用户记账数据云同步，后端不得保存用户使用数据或派生报告快照。
-- 未来如用户明确开启云同步，使用新的迁移新增同步表/任务/审计表，避免首发误写用户数据。

UPDATE public.sys_remote_config
SET status = 'inactive', updated_at = now()
WHERE app_code = 'saving'
  AND namespace_code = 'saving_sync_policy';

INSERT INTO public.sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES
('saving', 'saving_sync_policy', 'ios_v1', '{"value":{"version":1,"mode":"local_only","serverRecordStorageEnabled":false,"serverReportAggregationEnabled":false,"serverReportSnapshotEnabled":false,"futureCloudSyncCompatible":true,"notes":"saving V1 记录、看板、报告、导出均由 App 基于本地 CoreData 生成；后端仅用于登录、订阅校验、远程配置和法务/审核文案。未来云同步需用户明确开启并通过新迁移新增服务端同步表。"}}', 'active', now(), now());

UPDATE public.sys_remote_config
SET config_value_json = jsonb_set(
        jsonb_set(
            jsonb_set(config_value_json, '{value,offline}', '"首发记录和报告默认仅保存在设备本地；后端不可用时 App 仍可本地创建/编辑记录与草稿；云同步暂不作为 V1 对外承诺。"'::jsonb),
            '{value,reviewerNotes}', '"请使用 Sandbox Apple ID 测试订阅购买/恢复购买。记账记录与报告默认仅保存在设备本地，报告页中的免责声明会说明其非财务建议属性。"'::jsonb
        ),
        '{value,secretPolicy}', '"该 App Review Notes 配置不得包含密钥、密码或私有证书；生产凭证必须通过环境变量或密钥管理注入。"'::jsonb
    ),
    updated_at = now()
WHERE app_code = 'saving'
  AND namespace_code = 'saving_app_review_notes'
  AND config_key = 'ios_submission_v1'
  AND status = 'active';

DROP TABLE IF EXISTS public.saving_report_snapshot;
DROP TABLE IF EXISTS public.saving_export_audit;
