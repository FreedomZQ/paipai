package com.apphub.backend.apps.fitmystery.config;

import com.apphub.backend.apps.fitmystery.FitMysteryAppModule;
import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FitMysteryConfigService {
    private final SysRemoteConfigService remoteConfigService;

    public FitMysteryConfigService(SysRemoteConfigService remoteConfigService) {
        this.remoteConfigService = remoteConfigService;
    }

    public Map<String, Object> bootstrap(String locale) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appCode", FitMysteryAppModule.APP_CODE);
        data.put("locale", normalize(locale, "zh-Hans"));
        data.put("pointsPolicy", configOrDefault("fit_points_policy", "ios_v1", defaultPointsPolicy()));
        data.put("boxPolicy", configOrDefault("fit_box_policy", "ios_v1", defaultBoxPolicy()));
        data.put("productPolicy", configOrDefault("fit_product_policy", "ios_v1", defaultProductPolicy()));
        data.put("reportPolicy", configOrDefault("fit_report_policy", "ios_v1", defaultReportPolicy()));
        data.put("compliance", appStoreSummary());
        return data;
    }

    public Map<String, Object> pointsPolicy() {
        return configOrDefault("fit_points_policy", "ios_v1", defaultPointsPolicy());
    }

    public Map<String, Object> boxPolicy() {
        return configOrDefault("fit_box_policy", "ios_v1", defaultBoxPolicy());
    }

    public Map<String, Object> productPolicy() {
        return configOrDefault("fit_product_policy", "ios_v1", defaultProductPolicy());
    }

    public Map<String, Object> reportPolicy() {
        return configOrDefault("fit_report_policy", "ios_v1", defaultReportPolicy());
    }

    public Map<String, Object> appStoreSummary() {
        Map<String, Object> summary = configOrDefault("fit_app_store_compliance", "ios_submission_v1", defaultCompliance());
        summary.putIfAbsent("source", "sys_remote_config");
        return summary;
    }

    public Map<String, Object> oddsDisclosure() {
        return configOrDefault("fit_odds_disclosure", "starter_pool", defaultOddsDisclosure());
    }

    public Map<String, Object> namespace(String namespaceCode) {
        RemoteConfigNamespaceView view = remoteConfigService.loadNamespace(FitMysteryAppModule.APP_CODE, namespaceCode);
        return view == null || view.items() == null ? Map.of() : view.items();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> configOrDefault(String namespaceCode, String key, Map<String, Object> defaultValue) {
        Object raw = namespace(namespaceCode).get(key);
        return raw instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>(defaultValue);
    }

    private Map<String, Object> defaultPointsPolicy() {
        return Map.of(
            "version", "points_v1",
            "dailyCaps", Map.of("water", 80, "steps", 120, "exercise", 120, "total", 260),
            "rules", Map.of(
                "water", Map.of("pointsPer100ml", 2, "maxMlPerDay", 4000),
                "steps", Map.of("pointsPer1000Steps", 5, "maxStepsPerDay", 30000),
                "exercise", Map.of("pointsPer10Minutes", 8, "maxMinutesPerDay", 240)
            )
        );
    }

    private Map<String, Object> defaultBoxPolicy() {
        return Map.of(
            "version", "box_policy_v1",
            "defaultPoolCode", "starter_pool",
            "pointsPerDraw", 100,
            "dailyFreeChance", 1,
            "memberDailyBonusChance", 1,
            "allowChancePurchase", true,
            "noCashValueNotice", "仅获得 App 内虚拟收藏卡，无现金价值，不可交易、转让、提现或兑换实物。"
        );
    }

    private Map<String, Object> defaultProductPolicy() {
        return Map.of(
            "version", "product_policy_v1",
            "products", Map.of(
                "com.fitmystery.pro.monthly", Map.of("type", "subscription", "entitlements", List.of("pro_access", "report_weekly_access", "report_monthly_access", "report_history_access")),
                "com.fitmystery.pro.yearly", Map.of("type", "subscription", "entitlements", List.of("pro_access", "report_weekly_access", "report_monthly_access", "report_history_access")),
                "com.fitmystery.box5", Map.of("type", "consumable", "grantChance", 5),
                "com.fitmystery.box10", Map.of("type", "consumable", "grantChance", 10),
                "com.fitmystery.box25", Map.of("type", "consumable", "grantChance", 25)
            )
        );
    }

    private Map<String, Object> defaultReportPolicy() {
        return Map.of(
            "version", "report_policy_v1",
            "free", Map.of("weeklySummaryEnabled", true, "monthlySummaryEnabled", true, "historyLimit", 1, "advancedTrendEnabled", false),
            "pro", Map.of("weeklyFullEnabled", true, "monthlyFullEnabled", true, "historyLimit", 24, "advancedTrendEnabled", true, "exportEnabled", true)
        );
    }

    private Map<String, Object> defaultOddsDisclosure() {
        return Map.of(
            "version", "starter_pool_odds_v1",
            "poolCode", "starter_pool",
            "noCashValueNotice", "仅获得 App 内虚拟收藏卡，无现金价值，不可交易、转让、提现或兑换实物。",
            "rarityOdds", List.of(
                Map.of("rarity", "common", "displayName", "普通", "probability", "78.00%"),
                Map.of("rarity", "rare", "displayName", "稀有", "probability", "17.00%"),
                Map.of("rarity", "epic", "displayName", "史诗", "probability", "4.50%"),
                Map.of("rarity", "legendary", "displayName", "传说", "probability", "0.50%")
            ),
            "pityRule", "首版不启用保底；后续如启用，需在本页展示触发条件。"
        );
    }

    private Map<String, Object> defaultCompliance() {
        return Map.of(
            "reviewPositioning", "健康打卡游戏化收藏 App。随机开盒只产出无现金价值的虚拟收藏卡，不支持交易、转让、提现或兑换实物。",
            "iapRule", "所有数字权益均使用 Apple In-App Purchase。",
            "healthDataUse", "健康数据仅用于个人记录、积分计算和报告生成，不用于广告、追踪或出售。",
            "accountDeletionEndpoint", "DELETE /api/v1/fitmystery/account",
            "requiredReviewNotes", List.of("Apple 登录测试路径", "Sandbox IAP 商品", "概率披露入口", "账号删除入口", "隐私政策/用户协议 URL")
        );
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
