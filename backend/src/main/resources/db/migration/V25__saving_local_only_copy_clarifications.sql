-- saving V1 local-only 文案澄清。
-- 中文说明：V22 首版 onboarding 曾使用“网络恢复后再同步”的表达，容易被理解为 V1 已承诺云同步。
-- 本迁移只修正文案与审核说明，不改变业务表结构；首发继续保持 App 本机 CoreData 生成记录、看板、报告和 CSV。

UPDATE public.sys_remote_config
SET config_value_json = jsonb_set(
        config_value_json,
        '{value,screens}',
        '[
          {"code":"welcome","title":"省省星球","subtitle":"记录花费和省下的钱，先看清自己的消费习惯。","imageSystemName":"sparkles"},
          {"code":"offline_first","title":"记录默认留在本机","subtitle":"首发版本不上传记账明细；后端暂时不可用时，记录、草稿、看板和报告仍可在本机使用。","imageSystemName":"tray.and.arrow.down"},
          {"code":"review","title":"用周报月报复盘","subtitle":"报告由设备端基于本机记录生成，仅用于个人记账复盘，不构成财务、投资、税务或法律建议。","imageSystemName":"chart.bar"}
        ]'::jsonb
    ),
    updated_at = now()
WHERE app_code = 'saving'
  AND namespace_code = 'saving_onboarding_copy'
  AND config_key = 'ios_v1'
  AND status = 'active';

UPDATE public.sys_remote_config
SET config_value_json = jsonb_set(
        jsonb_set(
            jsonb_set(
                jsonb_set(config_value_json, '{value,offline}', '"首发记录、看板、周报/月报和 CSV 导出默认均在设备端基于本机 CoreData 完成；后端仅提供登录、订阅校验、远程配置与法务/审核文案；云同步暂不作为 V1 对外承诺。"'::jsonb),
                '{value,accountDeletion}', '"App 内提供账号删除入口：删除/撤销服务端账号会话与订阅绑定状态，并同步清理本机记账缓存；Apple 订阅取消仍需用户在系统订阅管理中完成。"'::jsonb
            ),
            '{value,privacy}', '"记账明细、报告聚合和 CSV 导出默认留在设备本机；后端不存储用户记账明细、不生成报告快照，也不采集高风险金融凭据。"'::jsonb
        ),
        '{value,reviewerNotes}', '"请使用 Sandbox Apple ID 测试订阅购买/恢复购买。记账记录、看板、报告与 CSV 导出默认在设备端生成；报告页会展示非财务建议免责声明。"'::jsonb
    ),
    updated_at = now()
WHERE app_code = 'saving'
  AND namespace_code = 'saving_app_review_notes'
  AND config_key = 'ios_submission_v1'
  AND status = 'active';
