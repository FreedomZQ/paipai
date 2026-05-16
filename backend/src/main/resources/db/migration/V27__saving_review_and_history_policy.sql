-- saving App Store 审核材料与本机历史报告策略配置化。
-- 中文说明：这些配置只保存审核/展示策略，不保存用户记账明细或报告内容。
-- 目的：个人开发者用较低运维成本生成 App Store 隐私标签建议、Review Notes 草稿和本机历史报告 UI 策略，降低提审口径漂移风险。

UPDATE public.sys_remote_config
SET status = 'inactive', updated_at = now()
WHERE app_code = 'saving'
  AND namespace_code IN (
    'saving_app_store_privacy_labels',
    'saving_app_review_materials',
    'saving_report_history_policy'
  );

INSERT INTO public.sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES
('saving', 'saving_app_store_privacy_labels', 'ios_submission_v1', '{"value":{"version":1,"appName":"省省星球","bundleId":"com.savingsplanet.app","tracking":false,"thirdPartyAdvertising":false,"dataLinkedToUser":[{"category":"Purchases","purpose":"App Functionality","note":"仅用于 Apple 订阅校验与权益恢复，由 Apple IAP 处理。"},{"category":"User ID","purpose":"App Functionality","note":"Sign in with Apple 会话与订阅绑定。"}],"dataNotCollected":["Financial Information","Contacts","Location","Health","Browsing History","Sensitive Info"],"localOnlyData":["记账明细","报告快照","CSV 导出文件","草稿"],"privacySummary":"记账明细、报告和导出文件默认仅保存在设备本机；后端只处理登录、订阅校验、远程配置和审核文案。","reviewWarning":"提交 App Store Connect 前必须按 Apple 表单逐项核对；该配置是低风险建议，不替代法律意见。"}}', 'active', now(), now()),
('saving', 'saving_app_review_materials', 'ios_submission_v1', '{"value":{"version":1,"generatedDocumentTitle":"省省星球 App Review Notes","requiredSections":["positioning","login","subscription","restore","accountDeletion","privacy","localOnly","disclaimer","contact"],"forbiddenTerms":["privateKey","BEGIN PRIVATE KEY","password","secret","apiKey","密钥","密码"],"requiredPhrases":["Sign in with Apple","Apple In-App Purchase","不上传记账明细","不构成财务、投资、税务或法律建议","删除账号"],"outputNote":"生成材料不得包含密钥、密码或私有证书；Sandbox 测试账号由 App Store Connect 单独配置。","localOnly":"首发记录、报告快照和 CSV 导出默认在设备端生成和保存，后端不存储用户记账明细或报告快照。","restore":"App 内提供恢复购买入口；订阅状态以 Apple 系统与 App Store Server API 校验结果为准。","contact":"支持入口与隐私政策/服务条款 URL 在 App Store Connect 中填写并保持可访问。"}}', 'active', now(), now()),
('saving', 'saving_report_history_policy', 'ios_v1', '{"value":{"version":1,"enabled":true,"maxSnapshots":50,"defaultFilter":"all","filters":["all","weekly","monthly"],"showDeleteAction":true,"upgradeHintEnabled":true,"localOnlyNotice":"历史报告仅保存在本机，不上传到后端；删除账号时会一并清理。","retentionDescription":"本机最多保留最近 50 份报告快照，便于回看，不作为云同步承诺。","emptyState":{"title":"还没有本机报告快照","description":"打开周报或月报后，App 会在本机保存最近生成的报告，方便之后回看；这些内容不会上传到后端。"},"opsNote":"如未来开启云同步，必须新增显式同步开关和用户确认流程，不得复用首发 local-only 口径悄悄上传。"}}', 'active', now(), now());
