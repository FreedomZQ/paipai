package com.apphub.backend.apps.saving.service;

import com.apphub.backend.apps.saving.SavingAppModule;
import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SavingConfigService {
    private final SysRemoteConfigService remoteConfigService;

    public SavingConfigService(SysRemoteConfigService remoteConfigService) {
        this.remoteConfigService = remoteConfigService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> paywall(String trigger, String platform, String locale) {
        Map<String, Object> namespace = namespace("saving_paywall");
        Object raw = namespace.getOrDefault(normalize(trigger, "default"), namespace.get("default"));
        Map<String, Object> template = raw instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
        template.putIfAbsent("trigger", normalize(trigger, "default"));
        template.putIfAbsent("templateCode", "missing_remote_paywall");
        template.putIfAbsent("title", "");
        template.putIfAbsent("subtitle", "");
        template.putIfAbsent("recommendedPlanCode", null);
        template.putIfAbsent("ctaText", "");
        template.putIfAbsent("secondaryCtaText", null);
        template.putIfAbsent("plans", java.util.List.of());
        mergePaywallAbPolicy(template, normalize(trigger, "default"));
        return template;
    }

    @SuppressWarnings("unchecked")
    private void mergePaywallAbPolicy(Map<String, Object> template, String trigger) {
        Map<String, Object> policy = configOrDefault("saving_paywall_ab_policy", "ios_v1", defaultPaywallAbPolicy());
        Object triggersRaw = policy.get("triggers");
        if (!(triggersRaw instanceof Map<?, ?> triggers)) {
            return;
        }
        Object triggerRaw = ((Map<String, Object>) triggers).get(trigger);
        if (!(triggerRaw instanceof Map<?, ?> triggerPolicy)) {
            return;
        }
        String variantCode = triggerPolicy.get("defaultVariant") instanceof String value ? value : "default";
        Object variantsRaw = triggerPolicy.get("variants");
        Map<String, Object> selectedVariant = null;
        if (variantsRaw instanceof List<?> variants) {
            for (Object variant : variants) {
                if (variant instanceof Map<?, ?> map && variantCode.equals(map.get("code"))) {
                    selectedVariant = new LinkedHashMap<>((Map<String, Object>) map);
                    break;
                }
            }
        }
        template.put("abTest", Map.of(
            "enabled", Boolean.TRUE.equals(policy.get("enabled")),
            "assignment", policy.getOrDefault("assignment", "deterministic_local"),
            "variantCode", variantCode,
            "legalNote", policy.getOrDefault("legalNote", "价格与扣款以 Apple 系统弹窗为准；权益以后端校验为准。")
        ));
        if (selectedVariant != null) {
            template.putIfAbsent("trustBullets", selectedVariant.getOrDefault("trustBullets", List.of()));
            template.putIfAbsent("conversionCards", selectedVariant.getOrDefault("conversionCards", List.of()));
            template.put("variantCode", variantCode);
        }
    }

    /**
     * 中文说明：记录分类是 saving 首发最容易被客户端硬编码的内容之一。
     * 这里统一从 sys_remote_config 读取，iOS 只把 code 写入业务表，展示名随配置变化，
     * 后续增加其他 APP 时可复用 app_code + namespace 的隔离方式，避免互相污染。
     */
    public Map<String, Object> recordCategories(String locale) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", 1);
        data.put("locale", normalize(locale, "zh-Hans"));
        data.put("source", "sys_remote_config");
        data.put("expense", configOrDefault("saving_record_categories", "expense", defaultExpenseCategories()));
        data.put("saving", configOrDefault("saving_record_categories", "saving", defaultSavingCategories()));
        return data;
    }

    /**
     * 中文说明：免费/付费权益对比页以后端配置为准，降低 App Store 文案和实际权益不一致的风险。
     */
    public Map<String, Object> entitlementMatrix(String locale) {
        Map<String, Object> matrix = configOrDefault("saving_entitlement_matrix", "v1", defaultEntitlementMatrix());
        matrix.putIfAbsent("locale", normalize(locale, "zh-Hans"));
        matrix.putIfAbsent("source", "sys_remote_config");
        return matrix;
    }

    /**
     * 中文说明：功能开关用于首发灰度和低运维回滚。客户端必须有本地兜底，不能因为配置服务异常导致主功能不可用。
     */
    public Map<String, Object> featureFlags(String platform, String appVersion) {
        Map<String, Object> flags = configOrDefault("saving_feature_flags", "ios_v1", defaultFeatureFlags());
        flags.putIfAbsent("platform", normalize(platform, "ios"));
        flags.putIfAbsent("appVersion", normalize(appVersion, "unknown"));
        flags.putIfAbsent("source", "sys_remote_config");
        return flags;
    }

    /**
     * 中文说明：报告访问矩阵用于控制免费版/Pro 版展示哪些模块。权益和展示均以后端配置为准，避免 App Store 文案与实际能力不一致。
     */
    public Map<String, Object> reportAccess() {
        Map<String, Object> access = configOrDefault("saving_report_access", "v1", defaultReportAccess());
        access.putIfAbsent("source", "sys_remote_config");
        return access;
    }

    /**
     * 中文说明：历史报告只保存在 App 本机；后端返回 UI/留存/删除策略，避免把报告快照上传到服务端。
     */
    public Map<String, Object> reportHistoryPolicy(String locale) {
        Map<String, Object> policy = configOrDefault("saving_report_history_policy", "ios_v1", defaultReportHistoryPolicy());
        policy.putIfAbsent("locale", normalize(locale, "zh-Hans"));
        policy.putIfAbsent("source", "sys_remote_config");
        return policy;
    }

    /**
     * 中文说明：onboarding/空状态/留存文案统一从 DB 返回，文案原则是低风险事实表达：鼓励记录，不承诺收益。
     */
    public Map<String, Object> onboarding(String locale) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", 1);
        data.put("locale", normalize(locale, "zh-Hans"));
        data.put("source", "sys_remote_config");
        data.put("onboarding", configOrDefault("saving_onboarding_copy", "ios_v1", defaultOnboardingCopy()));
        data.put("emptyStates", configOrDefault("saving_empty_state_copy", "ios_v1", defaultEmptyStateCopy()));
        data.put("retention", configOrDefault("saving_retention_copy", "ios_v1", defaultRetentionCopy()));
        return data;
    }

    /**
     * 中文说明：App Review Notes 只存放可给审核/发布清单使用的非敏感说明，禁止放密钥、测试账号密码等秘密。
     */
    public Map<String, Object> appReviewNotes() {
        Map<String, Object> notes = configOrDefault("saving_app_review_notes", "ios_submission_v1", defaultAppReviewNotes());
        notes.putIfAbsent("source", "sys_remote_config");
        notes.putIfAbsent("secretPolicy", "该配置不得包含密钥、密码或私有证书。生产凭证必须通过环境变量/密钥管理注入。");
        return notes;
    }

    /** 中文说明：App Store 隐私标签建议 DB 化生成，避免提审材料与实际 local-only 实现漂移。 */
    public Map<String, Object> appStorePrivacyLabels() {
        Map<String, Object> labels = configOrDefault("saving_app_store_privacy_labels", "ios_submission_v1", defaultAppStorePrivacyLabels());
        labels.putIfAbsent("source", "sys_remote_config");
        return labels;
    }

    /** 中文说明：审核材料门禁 DB 化，脚本根据该配置检查敏感词、local-only 口径和 Apple 官方路径。 */
    public Map<String, Object> appReviewMaterials() {
        Map<String, Object> materials = configOrDefault("saving_app_review_materials", "ios_submission_v1", defaultAppReviewMaterials());
        materials.putIfAbsent("source", "sys_remote_config");
        return materials;
    }

    /** 中文说明：App Store Connect 字段映射用于提交前人工核对，避免审核材料和实际实现不一致。 */
    public Map<String, Object> appStoreConnectFieldMapping() {
        Map<String, Object> mapping = configOrDefault("saving_app_store_connect_field_mapping", "ios_submission_v1", defaultAppStoreConnectFieldMapping());
        mapping.putIfAbsent("source", "sys_remote_config");
        return mapping;
    }

    /** 中文说明：App Store 提交清单 DB 化，帮助个人开发者低成本减少提审漏项。 */
    public Map<String, Object> appStoreSubmissionChecklist() {
        Map<String, Object> checklist = configOrDefault("saving_app_store_submission_checklist", "ios_submission_v1", defaultAppStoreSubmissionChecklist());
        checklist.putIfAbsent("source", "sys_remote_config");
        return checklist;
    }

    /** 中文说明：法务/提审文档一致性策略只检查公开文案口径，不读取用户数据。 */
    public Map<String, Object> legalDocumentConsistencyPolicy() {
        Map<String, Object> policy = configOrDefault("saving_legal_document_consistency_policy", "ios_submission_v1", defaultLegalDocumentConsistencyPolicy());
        policy.putIfAbsent("source", "sys_remote_config");
        return policy;
    }

    /** 中文说明：历史报告 Pro 增强仍只基于本机报告快照，不上传后端。 */
    public Map<String, Object> reportHistoryProPolicy() {
        Map<String, Object> policy = configOrDefault("saving_report_history_pro_policy", "ios_v1", defaultReportHistoryProPolicy());
        policy.putIfAbsent("source", "sys_remote_config");
        return policy;
    }

    public Object copy(String key, Object defaultValue) {
        Object value = namespace("saving_copy").get(key);
        return value == null ? defaultValue : value;
    }

    public Map<String, Object> namespace(String namespaceCode) {
        RemoteConfigNamespaceView view = remoteConfigService.loadNamespace(SavingAppModule.APP_CODE, namespaceCode);
        return view == null || view.items() == null ? Map.of() : view.items();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> configOrDefault(String namespaceCode, String key, Map<String, Object> defaultValue) {
        Object raw = namespace(namespaceCode).get(key);
        return raw instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>(defaultValue);
    }

    private Map<String, Object> defaultExpenseCategories() {
        return Map.of("version", 1, "recordType", "expense", "fallbackCode", "other", "items", List.of(
            category("food", "餐饮", "record.category.food", 10),
            category("transport", "交通", "record.category.transport", 20),
            category("shopping", "购物", "record.category.shopping", 30),
            category("entertainment", "娱乐", "record.category.entertainment", 40),
            category("household", "居家", "record.category.household", 50),
            category("medical", "医疗", "record.category.medical", 60),
            category("education", "教育", "record.category.education", 70),
            category("other", "其他", "record.category.other", 999)
        ));
    }

    private Map<String, Object> defaultSavingCategories() {
        return Map.of("version", 1, "recordType", "saving", "fallbackCode", "other", "items", List.of(
            category("food", "餐饮省下", "record.category.food", 10),
            category("transport", "交通省下", "record.category.transport", 20),
            category("shopping", "购物省下", "record.category.shopping", 30),
            category("subscription", "订阅取消", "record.category.subscription", 40),
            category("household", "居家省下", "record.category.household", 50),
            category("travel", "出行旅行", "record.category.travel", 60),
            category("other", "其他省下", "record.category.other", 999)
        ));
    }

    private Map<String, Object> defaultEntitlementMatrix() {
        return Map.of(
            "version", 1,
            "title", "免费版与 Pro 权益对比",
            "subtitle", "免费版可完成基础记录；Pro 解锁完整复盘能力。",
            "plans", List.of(
                Map.of("code", "free", "displayName", "免费版", "recommended", false),
                Map.of("code", "pro_monthly", "displayName", "Pro 月会员", "recommended", true, "storeProductId", "com.savingsplanet.app.pro.monthly")
            ),
            "sections", List.of()
        );
    }

    private Map<String, Object> defaultReportAccess() {
        return Map.of(
            "version", 1,
            "defaultPlanCode", "free",
            "upgradeTrigger", "report_locked",
            "plans", Map.of(
                "free", Map.of("tier", "free", "advancedUnlocked", false, "modules", Map.of("overview", "full", "comparison", "full", "category_breakdown", "locked", "trend_review", "locked", "top_actions", "locked", "high_risk_window", "locked", "csv_export", "locked")),
                "pro_monthly", Map.of("tier", "pro", "advancedUnlocked", true, "modules", Map.of("overview", "full", "comparison", "full", "category_breakdown", "full", "trend_review", "full", "top_actions", "full", "high_risk_window", "full", "csv_export", "full"))
            )
        );
    }

    private Map<String, Object> defaultFeatureFlags() {
        Map<String, Object> flags = new LinkedHashMap<>();
        flags.put("recordCategoriesRemoteEnabled", true);
        flags.put("entitlementComparisonEnabled", true);
        flags.put("weeklyReportEnabled", true);
        flags.put("monthlyReportEnabled", true);
        flags.put("trendReviewEnabled", true);
        flags.put("reportHistoryEnabled", true);
        flags.put("csvExportEnabled", false);
        flags.put("onboardingRemoteCopyEnabled", true);
        flags.put("emptyStateRemoteCopyEnabled", true);
        flags.put("retentionCopyEnabled", true);
        flags.put("appReviewNotesConfigured", true);
        flags.put("cloudSyncVisible", false);
        return Map.of("version", 1, "flags", flags);
    }

    private Map<String, Object> defaultOnboardingCopy() {
        return Map.of(
            "version", 1,
            "signInTitle", "省省星球",
            "signInSubtitle", "登录后记录花费、省下的钱和会员权益。免费版可继续使用，订阅由 Apple 统一处理。",
            "screens", List.of(
                Map.of("code", "offline_first", "title", "记录默认留在本机", "subtitle", "首发版本不上传记账明细；后端暂时不可用时，记录、草稿、看板和报告仍可在本机使用。", "imageSystemName", "tray.and.arrow.down"),
                Map.of("code", "review", "title", "用周报月报复盘", "subtitle", "报告由设备端基于本机记录生成，仅用于个人记账复盘，不构成财务、投资、税务或法律建议。", "imageSystemName", "chart.bar")
            )
        );
    }

    private Map<String, Object> defaultEmptyStateCopy() {
        return Map.of("version", 1, "records", Map.of(
            "all", Map.of("title", "还没有记录", "description", "先记一笔花费或一条省钱行为，周报和月报才会更有意义。"),
            "expense", Map.of("title", "还没有花费记录", "description", "记录今天的一笔真实花费，后续会自动进入分类统计。"),
            "saving", Map.of("title", "还没有省钱记录", "description", "少买一次、取消一个订阅、比价省下的钱都可以记下来。")
        ));
    }

    private Map<String, Object> defaultRetentionCopy() {
        return Map.of("version", 1, "messages", List.of(), "legalNote", "所有留存文案只鼓励记录，不承诺省钱收益。");
    }

    private Map<String, Object> defaultReportHistoryPolicy() {
        return Map.of(
            "version", 1,
            "enabled", true,
            "maxSnapshots", 50,
            "defaultFilter", "all",
            "filters", List.of("all", "weekly", "monthly"),
            "showDeleteAction", true,
            "upgradeHintEnabled", true,
            "localOnlyNotice", "历史报告仅保存在本机，不上传到后端；删除账号时会一并清理。",
            "retentionDescription", "本机最多保留最近 50 份报告快照，便于回看，不作为云同步承诺。"
        );
    }

    private Map<String, Object> defaultAppStorePrivacyLabels() {
        return Map.of(
            "version", 1,
            "appName", "省省星球",
            "bundleId", "com.savingsplanet.app",
            "dataLinkedToUser", List.of(
                Map.of("category", "Purchases", "purpose", "App Functionality", "note", "仅用于 Apple 订阅校验与权益恢复，由 Apple IAP 处理。"),
                Map.of("category", "User ID", "purpose", "App Functionality", "note", "Sign in with Apple 会话与订阅绑定。")
            ),
            "dataNotCollected", List.of("Financial Information", "Contacts", "Location", "Health", "Browsing History", "Sensitive Info"),
            "localOnlyData", List.of("记账明细", "报告快照", "CSV 导出文件", "草稿"),
            "tracking", false,
            "thirdPartyAdvertising", false,
            "privacySummary", "记账明细、报告和导出文件默认仅保存在设备本机；后端只处理登录、订阅校验、远程配置和审核文案。",
            "reviewWarning", "提交 App Store Connect 前必须按 Apple 表单逐项核对；该配置是低风险建议，不替代法律意见。"
        );
    }

    private Map<String, Object> defaultAppReviewMaterials() {
        return Map.of(
            "version", 1,
            "generatedDocumentTitle", "省省星球 App Review Notes",
            "requiredSections", List.of("positioning", "login", "subscription", "restore", "accountDeletion", "privacy", "localOnly", "disclaimer", "contact"),
            "forbiddenTerms", List.of("privateKey", "BEGIN PRIVATE KEY", "password", "secret", "apiKey", "密钥", "密码"),
            "requiredPhrases", List.of("Sign in with Apple", "Apple In-App Purchase", "不上传记账明细", "不构成财务、投资、税务或法律建议", "删除账号"),
            "outputNote", "生成材料不得包含密钥、密码或私有证书；Sandbox 测试账号由 App Store Connect 单独配置。",
            "localOnly", "首发记录、报告快照和 CSV 导出默认在设备端生成和保存，后端不存储用户记账明细或报告快照。"
        );
    }

    private Map<String, Object> defaultAppStoreConnectFieldMapping() {
        return Map.of(
            "version", 1,
            "bundleId", "com.savingsplanet.app",
            "fields", List.of(
                Map.of("section", "Privacy", "field", "Tracking", "value", "No", "source", "saving_app_store_privacy_labels.tracking", "manualVerify", true),
                Map.of("section", "Privacy", "field", "Data Linked to You", "value", "Purchases, User ID", "source", "saving_app_store_privacy_labels.dataLinkedToUser", "manualVerify", true),
                Map.of("section", "Review Notes", "field", "Local-only explanation", "value", "首发版本不上传记账明细，报告快照仅保存在本机。", "source", "saving_app_review_materials.localOnly", "manualVerify", false)
            ),
            "manualChecklist", List.of("隐私政策 URL、服务条款 URL、支持 URL 均可访问", "IAP 商品已在 App Store Connect 审核页关联"),
            "opsNote", "该映射用于生成提交前核对表，不自动写入 App Store Connect。"
        );
    }

    private Map<String, Object> defaultAppStoreSubmissionChecklist() {
        return Map.of(
            "version", 1,
            "generatedDocumentTitle", "省省星球 App Store 提交清单",
            "sections", List.of(
                Map.of("code", "privacy", "title", "隐私与法务", "items", List.of("说明首发版本不上传记账明细", "说明报告快照仅保存在本机", "说明不构成财务、投资、税务或法律建议")),
                Map.of("code", "iap", "title", "订阅与恢复购买", "items", List.of("IAP 商品已关联审核", "App 内恢复购买入口可用", "价格和扣款以 Apple 系统弹窗为准"))
            ),
            "requiredPhrases", List.of("Sign in with Apple", "Apple In-App Purchase", "不上传记账明细", "报告快照仅保存在本机", "不构成财务、投资、税务或法律建议", "删除账号"),
            "manualBlockers", List.of("Apple Team ID / DEVELOPMENT_TEAM", "App Store Connect IAP 审核关联", "Sandbox 真机购买恢复验证", "Xcode archive 与 TestFlight 上传"),
            "opsNote", "提交清单由 DB 配置生成，Apple 后台字段仍需人工最终核对。"
        );
    }

    private Map<String, Object> defaultLegalDocumentConsistencyPolicy() {
        return Map.of(
            "version", 1,
            "documentGlobs", List.of("docs/app-store-*.md", "qa/evidence/app-store/*.md"),
            "requiredPhrases", List.of("不上传记账明细", "Apple In-App Purchase", "恢复购买", "删除账号", "不构成财务、投资、税务或法律建议"),
            "forbiddenPhrases", List.of("稳赚", "强制云同步", "信用评分"),
            "localOnlyPhrases", List.of("本机", "不上传", "后端不存储用户记账明细"),
            "opsNote", "文档一致性门禁只检查提审/说明文档口径，不读取用户数据。"
        );
    }

    private Map<String, Object> defaultPaywallAbPolicy() {
        return Map.of(
            "version", 2,
            "enabled", true,
            "assignment", "deterministic_local",
            "legalNote", "价格与扣款以 Apple 系统弹窗为准；权益以后端校验为准。",
            "triggers", Map.of(
                "default", defaultPaywallVariant("balanced", "本机历史报告", "周报/月报会保存在本机，方便之后回看。"),
                "membership_center", defaultPaywallVariant("balanced", "恢复购买清晰", "换机或重装后可在 App 内恢复购买。"),
                "report_locked", defaultPaywallVariant("report_unlock", "解锁高级模块", "分类结构、趋势复盘和 Top 省钱行为。"),
                "history_trend_locked", defaultPaywallVariant("history_value", "长期复盘更直观", "把周报/月报串起来，看总花费、总省下和净结果变化。"),
                "csv_export_locked", defaultPaywallVariant("local_export", "本机导出更自由", "Pro 解锁 CSV 导出，便于个人复盘和备份。"),
                "record_limit_reached", defaultPaywallVariant("unlimited_records", "继续记录不断档", "Pro 帮你保留更完整的复盘链路。")
            )
        );
    }

    private Map<String, Object> defaultPaywallVariant(String code, String cardTitle, String cardBody) {
        return Map.of(
            "defaultVariant", code,
            "variants", List.of(Map.of(
                "code", code,
                "weight", 100,
                "trustBullets", List.of("订阅由 Apple 处理。", "记录仍优先保存在本机。", "报告不构成财务、投资、税务或法律建议。"),
                "conversionCards", List.of(Map.of("code", code, "title", cardTitle, "body", cardBody))
            ))
        );
    }

    private Map<String, Object> defaultReportHistoryProPolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("version", 2);
        policy.put("enabled", true);
        policy.put("freePreviewLimit", 3);
        policy.put("proTrendEnabled", true);
        policy.put("upgradeTrigger", "history_trend_locked");
        policy.put("trendMetricCodes", List.of("expense", "saved", "net"));
        policy.put("localOnlyNotice", "高级历史趋势仍基于本机报告快照生成，不上传到后端。");
        policy.put("freePreviewNotice", "免费版可预览最近 3 份本机报告快照；Pro 可查看完整本机历史趋势。");
        policy.put("lockedTitle", "历史趋势已预留");
        policy.put("lockedDescription", "免费版可查看最近 3 份报告快照；Pro 解锁更完整的本机历史趋势复盘。");
        policy.put("proCards", List.of(Map.of("code", "history_trend", "title", "历史趋势复盘", "body", "基于本机周报/月报快照，查看总花费、总省下和净结果变化。", "requiresPro", true)));
        policy.put("opsNote", "该策略只控制 App 本机 UI，不开启云同步。");
        return policy;
    }

    private Map<String, Object> defaultAppReviewNotes() {
        return Map.of(
            "version", 1,
            "appName", "省省星球",
            "bundleId", "com.savingsplanet.app",
            "positioning", "个人记账与省钱行为复盘工具。报告仅用于个人记录复盘，不提供投资、税务、法律或财务建议。",
            "login", "使用 Sign in with Apple；首发不启用匿名账号。",
            "subscription", "订阅由 Apple In-App Purchase 处理，价格与扣款以 Apple 系统弹窗为准。",
            "offline", "首发记录、看板、周报/月报和 CSV 导出默认均在设备端基于本机 CoreData 完成；后端仅提供登录、订阅校验、远程配置与法务/审核文案；云同步暂不作为 V1 对外承诺。",
            "accountDeletion", "App 内提供账号删除入口：删除/撤销服务端账号会话与订阅绑定状态，并同步清理本机记账缓存；Apple 订阅取消仍需用户在系统订阅管理中完成。",
            "privacy", "记账明细、报告聚合和 CSV 导出默认留在设备本机；后端不存储用户记账明细、不生成报告快照，也不采集高风险金融凭据。",
            "secretPolicy", "App Review Notes 不得包含密钥、密码或私有证书；生产凭证必须通过环境变量或密钥管理注入。"
        );
    }

    private Map<String, Object> category(String code, String displayName, String titleKey, int sortOrder) {
        return Map.of("code", code, "displayName", displayName, "titleKey", titleKey, "sortOrder", sortOrder, "enabled", true);
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
