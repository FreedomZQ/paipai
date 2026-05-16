package com.apphub.backend.apps.reading.compat.service;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 拍拍伴读周报权益配置服务。
 *
 * <p>设计目标：
 * <ul>
 *   <li>周报模块可见性以后端和数据库配置为准，避免客户端自行解锁付费内容。</li>
 *   <li>使用 sys_remote_config 承载配置，个人开发者无需维护额外后台或复杂任务系统。</li>
 *   <li>本地短缓存降低数据库读取频率；配置异常时使用保守 fallback，避免影响 App Store 首发稳定性。</li>
 *   <li>命名空间按 appCode 隔离，后续其他 App 共用统一后端时，只需增加自己的配置，不复用拍拍伴读权益口径。</li>
 * </ul>
 */
@Service
public class ReadingWeeklyReportAccessConfigService {
    private static final String APP_CODE = ReadingAppModule.APP_CODE;
    private static final String NAMESPACE = "reading_weekly_report_access";
    private static final String ACCESS_MATRIX_KEY = "access_matrix_v1";
    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000L;

    private final SysRemoteConfigService sysRemoteConfigService;
    private volatile CachedWeeklyReportAccessConfig cached;

    public ReadingWeeklyReportAccessConfigService(SysRemoteConfigService sysRemoteConfigService) {
        this.sysRemoteConfigService = sysRemoteConfigService;
    }

    public WeeklyReportAccessConfig current() {
        CachedWeeklyReportAccessConfig local = cached;
        long now = System.currentTimeMillis();
        if (local != null && local.expiresAtMillis() > now) {
            return local.config();
        }
        synchronized (this) {
            local = cached;
            if (local == null || local.expiresAtMillis() <= now) {
                cached = new CachedWeeklyReportAccessConfig(loadConfig(), now + CACHE_TTL_MILLIS);
            }
            return cached.config();
        }
    }

    public WeeklyReportAccessConfig refresh() {
        synchronized (this) {
            cached = new CachedWeeklyReportAccessConfig(loadConfig(), System.currentTimeMillis() + CACHE_TTL_MILLIS);
            return cached.config();
        }
    }

    private WeeklyReportAccessConfig loadConfig() {
        try {
            RemoteConfigNamespaceView namespace = sysRemoteConfigService.loadNamespace(APP_CODE, NAMESPACE);
            Map<String, Object> items = namespace == null || namespace.items() == null ? Map.of() : namespace.items();
            Map<String, Object> root = asMap(items.get(ACCESS_MATRIX_KEY));
            if (root.isEmpty()) {
                return fallback();
            }
            Map<String, PlanWeeklyReportAccess> plans = new LinkedHashMap<>();
            asMap(root.get("plans")).forEach((planCode, rawPlan) -> {
                Map<String, Object> plan = asMap(rawPlan);
                if (plan.isEmpty()) {
                    return;
                }
                Map<String, ModuleAccess> modules = new LinkedHashMap<>();
                asMap(plan.get("modules")).forEach((moduleCode, rawModule) -> {
                    Map<String, Object> module = asMap(rawModule);
                    modules.put(moduleCode, new ModuleAccess(
                        stringValue(module, "access", "hidden"),
                        intValue(module, "maxItems", null),
                        intValue(module, "weeks", null)
                    ));
                });
                plans.put(planCode, new PlanWeeklyReportAccess(
                    stringValue(plan, "tier", planCode),
                    intValue(plan, "historyWeeks", 0),
                    modules
                ));
            });
            if (plans.isEmpty()) {
                return fallback();
            }
            Map<String, Object> legal = asMap(root.get("legal"));
            return new WeeklyReportAccessConfig(
                intValue(root, "version", 1),
                boolValue(root, "historyEnabled", true),
                boolValue(root, "exportReportEnabled", false),
                boolValue(root, "pageShareEnabled", true),
                boolValue(root, "offlineHistoryPreviewEnabled", true),
                plans,
                stringValue(legal, "defaultDisclaimer", defaultDisclaimer()),
                OffsetDateTime.now(ZoneOffset.UTC).toString()
            );
        } catch (Exception ignored) {
            // 配置中心异常时保持保守可用：不抛 500，避免影响 App 启动和周报页打开。
            return fallback();
        }
    }

    private WeeklyReportAccessConfig fallback() {
        Map<String, PlanWeeklyReportAccess> plans = new LinkedHashMap<>();
        plans.put("free", new PlanWeeklyReportAccess("free", 0, Map.of(
            "basic_stats", full(),
            "safe_summary", full(),
            "basic_suggestions", new ModuleAccess("full", 2, null),
            "page_share", full()
        )));
        plans.put("standard_single_child", new PlanWeeklyReportAccess("standard", 4, Map.of(
            "basic_stats", full(),
            "safe_summary", full(),
            "basic_suggestions", new ModuleAccess("full", 4, null),
            "single_child_detail", full(),
            "review_focus", full(),
            "history_reports", new ModuleAccess("full", null, 4),
            "offline_history_preview", full(),
            "page_share", full()
        )));
        plans.put("family_multi_child_lifetime", new PlanWeeklyReportAccess("family", 12, Map.of(
            "basic_stats", full(),
            "safe_summary", full(),
            "basic_suggestions", new ModuleAccess("full", 5, null),
            "single_child_detail", full(),
            "review_focus", full(),
            "family_overview", full(),
            "child_summaries", full(),
            "history_reports", new ModuleAccess("full", null, 12),
            "offline_history_preview", full(),
            "page_share", full()
        )));
        return new WeeklyReportAccessConfig(1, true, false, true, true, plans, defaultDisclaimer(), OffsetDateTime.now(ZoneOffset.UTC).toString());
    }

    private ModuleAccess full() {
        return new ModuleAccess("full", null, null);
    }

    private String defaultDisclaimer() {
        return "本报告仅用于家庭陪读参考，不用于学业评价、排名、诊断、医疗、心理或任何高风险判断。";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String stringValue(Map<String, Object> config, String key, String fallback) {
        if (config == null) {
            return fallback;
        }
        Object raw = config.get(key);
        return raw == null || raw.toString().isBlank() ? fallback : raw.toString().trim();
    }

    private Integer intValue(Map<String, Object> config, String key, Integer fallback) {
        if (config == null) {
            return fallback;
        }
        Object raw = config.get(key);
        if (raw == null || raw.toString().isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Boolean boolValue(Map<String, Object> config, String key, Boolean fallback) {
        if (config == null) {
            return fallback;
        }
        Object raw = config.get(key);
        if (raw == null) {
            return fallback;
        }
        return switch (raw.toString().trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> fallback;
        };
    }

    private record CachedWeeklyReportAccessConfig(WeeklyReportAccessConfig config, long expiresAtMillis) {}

    public record WeeklyReportAccessConfig(
        Integer version,
        Boolean historyEnabled,
        Boolean exportReportEnabled,
        Boolean pageShareEnabled,
        Boolean offlineHistoryPreviewEnabled,
        Map<String, PlanWeeklyReportAccess> plans,
        String disclaimer,
        String loadedAt
    ) {
        public PlanWeeklyReportAccess plan(String planCode) {
            if (plans == null || plans.isEmpty()) {
                return null;
            }
            PlanWeeklyReportAccess exact = plans.get(planCode);
            return exact != null ? exact : plans.get("free");
        }

        public String tierFor(String planCode) {
            PlanWeeklyReportAccess plan = plan(planCode);
            return plan == null ? "free" : plan.tier();
        }

        public boolean moduleEnabled(String planCode, String moduleCode) {
            ModuleAccess module = module(planCode, moduleCode);
            return module != null && "full".equalsIgnoreCase(module.access());
        }

        public ModuleAccess module(String planCode, String moduleCode) {
            PlanWeeklyReportAccess plan = plan(planCode);
            if (plan == null || plan.modules() == null) {
                return null;
            }
            return plan.modules().get(moduleCode);
        }

        public int maxItems(String planCode, String moduleCode, int fallback) {
            ModuleAccess module = module(planCode, moduleCode);
            return module == null || module.maxItems() == null ? fallback : module.maxItems();
        }

        public int historyWeeksFor(String planCode) {
            PlanWeeklyReportAccess plan = plan(planCode);
            if (plan == null || plan.historyWeeks() == null) {
                return 0;
            }
            return Math.max(plan.historyWeeks(), 0);
        }

        public List<String> planCodes() {
            return plans == null ? List.of() : List.copyOf(plans.keySet());
        }
    }

    public record PlanWeeklyReportAccess(String tier, Integer historyWeeks, Map<String, ModuleAccess> modules) {}
    public record ModuleAccess(String access, Integer maxItems, Integer weeks) {}
}
