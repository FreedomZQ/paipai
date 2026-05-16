-- saving 首发配置化内容补齐。
-- 中文说明：本迁移把首发中容易被硬编码的记录分类、权益对比、功能开关、引导/空状态/留存文案、App Review Notes
-- 全部下沉到 sys_remote_config。这样个人开发者上线后无需重新发版即可微调文案与权益展示，同时继续复用统一后端的 app_code/namespace/key 隔离模型。

UPDATE public.sys_remote_config
SET status = 'inactive', updated_at = now()
WHERE app_code = 'saving'
  AND namespace_code IN (
    'saving_record_categories',
    'saving_entitlement_matrix',
    'saving_feature_flags',
    'saving_onboarding_copy',
    'saving_empty_state_copy',
    'saving_retention_copy',
    'saving_app_review_notes'
  );

INSERT INTO public.sys_remote_config (app_code, namespace_code, config_key, config_value_json, status, created_at, updated_at)
VALUES
-- 记录分类 catalog：客户端必须使用 code 入库，displayName 只用于展示，后续可追加 icon/color/paidOnly 而不破坏旧版。
('saving', 'saving_record_categories', 'expense', '{"value":{"version":1,"recordType":"expense","fallbackCode":"other","items":[{"code":"food","displayName":"餐饮","titleKey":"record.category.food","sortOrder":10,"enabled":true},{"code":"transport","displayName":"交通","titleKey":"record.category.transport","sortOrder":20,"enabled":true},{"code":"shopping","displayName":"购物","titleKey":"record.category.shopping","sortOrder":30,"enabled":true},{"code":"entertainment","displayName":"娱乐","titleKey":"record.category.entertainment","sortOrder":40,"enabled":true},{"code":"household","displayName":"居家","titleKey":"record.category.household","sortOrder":50,"enabled":true},{"code":"medical","displayName":"医疗","titleKey":"record.category.medical","sortOrder":60,"enabled":true},{"code":"education","displayName":"教育","titleKey":"record.category.education","sortOrder":70,"enabled":true},{"code":"other","displayName":"其他","titleKey":"record.category.other","sortOrder":999,"enabled":true}]}}', 'active', now(), now()),
('saving', 'saving_record_categories', 'saving', '{"value":{"version":1,"recordType":"saving","fallbackCode":"other","items":[{"code":"food","displayName":"餐饮省下","titleKey":"record.category.food","sortOrder":10,"enabled":true},{"code":"transport","displayName":"交通省下","titleKey":"record.category.transport","sortOrder":20,"enabled":true},{"code":"shopping","displayName":"购物省下","titleKey":"record.category.shopping","sortOrder":30,"enabled":true},{"code":"subscription","displayName":"订阅取消","titleKey":"record.category.subscription","sortOrder":40,"enabled":true},{"code":"household","displayName":"居家省下","titleKey":"record.category.household","sortOrder":50,"enabled":true},{"code":"travel","displayName":"出行旅行","titleKey":"record.category.travel","sortOrder":60,"enabled":true},{"code":"other","displayName":"其他省下","titleKey":"record.category.other","sortOrder":999,"enabled":true}]}}', 'active', now(), now()),

-- 免费/付费权益对比页：用于会员中心和审核说明，避免前端硬编码权益表。
('saving', 'saving_entitlement_matrix', 'v1', '{"value":{"version":1,"title":"免费版与 Pro 权益对比","subtitle":"免费版可完成基础记录；Pro 解锁完整复盘能力。所有扣款以 Apple 系统弹窗为准。","plans":[{"code":"free","displayName":"免费版","badgeText":"适合开始记录","recommended":false},{"code":"pro_monthly","displayName":"Pro 月会员","badgeText":"推荐","recommended":true,"storeProductId":"com.savingsplanet.app.pro.monthly"}],"sections":[{"code":"core_recording","title":"基础记录","items":[{"code":"apple_sign_in","title":"Sign in with Apple 登录","free":"支持","pro_monthly":"支持"},{"code":"monthly_records","title":"每月记录额度","free":"100 条","pro_monthly":"不限量"},{"code":"offline_local_records","title":"后端离线时本地记录与草稿","free":"支持","pro_monthly":"支持"}]},{"code":"review_reports","title":"复盘与报告","items":[{"code":"basic_dashboard","title":"首页基础统计","free":"支持","pro_monthly":"支持"},{"code":"advanced_weekly_monthly","title":"完整周报 / 月报","free":"基础摘要","pro_monthly":"完整解锁"},{"code":"trend_review","title":"消费趋势复盘","free":"不支持","pro_monthly":"支持"},{"code":"export_csv","title":"CSV 导出","free":"不支持","pro_monthly":"支持"}]},{"code":"safety_legal","title":"安全与服务","items":[{"code":"account_delete","title":"账号删除与本地数据清理","free":"支持","pro_monthly":"支持"},{"code":"restore_purchase","title":"恢复购买","free":"支持","pro_monthly":"支持"},{"code":"legal_disclaimer","title":"低风险说明","free":"报告仅作个人记账复盘，不构成财务/投资/税务/法律建议","pro_monthly":"同免费版"}]}]}}', 'active', now(), now()),

-- Feature flags：新能力先配置后发布，支持灰度、降级和低运维回滚。
('saving', 'saving_feature_flags', 'ios_v1', '{"value":{"version":1,"flags":{"recordCategoriesRemoteEnabled":true,"entitlementComparisonEnabled":true,"weeklyReportEnabled":true,"monthlyReportEnabled":true,"trendReviewEnabled":true,"reportHistoryEnabled":true,"csvExportEnabled":false,"onboardingRemoteCopyEnabled":true,"emptyStateRemoteCopyEnabled":true,"retentionCopyEnabled":true,"appReviewNotesConfigured":true,"cloudSyncVisible":false},"opsNote":"首发不承诺云同步；导出能力先保留配置开关，完成本地 CSV 后再打开。"}}', 'active', now(), now()),

-- onboarding / 空状态 / 留存文案：只做低风险事实表达，不承诺收益、不构成财务建议。
('saving', 'saving_onboarding_copy', 'ios_v1', '{"value":{"version":1,"screens":[{"code":"welcome","title":"省省星球","subtitle":"记录花费和省下的钱，先看清自己的消费习惯。","imageSystemName":"sparkles"},{"code":"offline_first","title":"记录默认留在本机","subtitle":"首发版本不上传记账明细；后端暂时不可用时，记录、草稿、看板和报告仍可在本机使用。","imageSystemName":"tray.and.arrow.down"},{"code":"review","title":"用周报月报复盘","subtitle":"报告由设备端基于本机记录生成，仅用于个人记账复盘，不构成财务、投资、税务或法律建议。","imageSystemName":"chart.bar"}],"signInTitle":"省省星球","signInSubtitle":"登录后记录花费、省下的钱和会员权益。免费版可继续使用，订阅由 Apple 统一处理。"}}', 'active', now(), now()),
('saving', 'saving_empty_state_copy', 'ios_v1', '{"value":{"version":1,"records":{"all":{"title":"还没有记录","description":"先记一笔花费或一条省钱行为，周报和月报才会更有意义。"},"expense":{"title":"还没有花费记录","description":"记录今天的一笔真实花费，后续会自动进入分类统计。"},"saving":{"title":"还没有省钱记录","description":"少买一次、取消一个订阅、比价省下的钱都可以记下来。"}},"reports":{"weekly":{"title":"本周报告还不完整","description":"添加 3 笔花费和 1 条省钱记录后，趋势会更可信。"},"monthly":{"title":"本月报告还不完整","description":"持续记录几天后，月报会展示更完整的消费结构。"}}}}', 'active', now(), now()),
('saving', 'saving_retention_copy', 'ios_v1', '{"value":{"version":1,"messages":[{"code":"day1","title":"今天先记一笔","body":"只要 10 秒，先把最确定的一笔花费记下来。"},{"code":"day3","title":"连续记录会更有用","body":"记录越稳定，周报里的分类和趋势越可信。"},{"code":"before_report","title":"补齐本周记录","body":"本周还有几天可以补记，周报会更完整。"}],"legalNote":"所有留存文案只鼓励记录，不承诺省钱收益。"}}', 'active', now(), now()),

-- App Review Notes：给发布/审核清单使用，不包含密钥、测试账号密码等敏感信息。
('saving', 'saving_app_review_notes', 'ios_submission_v1', '{"value":{"version":1,"appName":"省省星球","bundleId":"com.savingsplanet.app","positioning":"个人记账与省钱行为复盘工具。报告仅用于个人记录复盘，不提供投资、税务、法律或财务建议。","login":"使用 Sign in with Apple；首发不启用匿名账号。","subscription":"订阅由 Apple In-App Purchase 处理，商品 com.savingsplanet.app.pro.monthly 对应 Pro 月会员。","offline":"首发记录和报告默认仅保存在设备本地；后端不可用时 App 仍可本地创建/编辑记录与草稿；云同步暂不作为 V1 对外承诺。","accountDeletion":"App 内提供账号删除入口，删除 saving 业务记录并撤销会话；本地缓存同步清理。","privacy":"仅保存实现记账复盘所需的最小业务数据；不采集高风险金融凭据。","secretPolicy":"该 App Review Notes 配置不得包含密钥、密码或私有证书；生产凭证必须通过环境变量或密钥管理注入。","reviewerNotes":"请使用 Sandbox Apple ID 测试订阅购买/恢复购买。记账记录与报告默认仅保存在设备本地，报告页中的免责声明会说明其非财务建议属性。","externalBlockers":["Apple Team ID 需在生产配置填写","App Store Server API Key/Issuer/Key ID 需生产环境覆盖","隐私政策与服务条款 URL 需在 App Store Connect 填写"]}}', 'active', now(), now());
