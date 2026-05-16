package com.apphub.backend.apps.common;

import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 中文说明：多 App 通用的版本更新策略服务。
 *
 * <p>该服务只读取 app_code 隔离后的远程配置并返回普通 App Store 更新提示所需字段：
 * 当前版本、最新版本、App Store 链接、提示文案和是否存在更新。客户端应把它展示为“可升级提示”，
 * 不得伪装系统弹窗、不得绕过 Apple 的 App Store / StoreKit 流程，也不得把升级提示与订阅购买混淆。</p>
 */
@Service
public class AppVersionPolicyService {
    public static final String NAMESPACE_CODE = "app_release_policy";

    private final AppModuleRegistry appModuleRegistry;
    private final SysRemoteConfigService remoteConfigService;

    public AppVersionPolicyService(AppModuleRegistry appModuleRegistry, SysRemoteConfigService remoteConfigService) {
        this.appModuleRegistry = appModuleRegistry;
        this.remoteConfigService = remoteConfigService;
    }

    public Map<String, Object> policy(String appCode, String platform, String currentVersion, String currentBuild) {
        AppModule module = appModuleRegistry.require(appCode);
        String normalizedPlatform = normalize(platform, "ios").toLowerCase(Locale.ROOT);
        Map<String, Object> configured = configuredPolicy(appCode, normalizedPlatform);

        String clientVersion = normalize(currentVersion, "unknown");
        String clientBuild = normalize(currentBuild, "unknown");
        String latestVersion = stringValue(configured.get("latestVersion"), clientVersion);
        String latestBuild = stringValue(configured.get("latestBuild"), "");
        String minimumSupportedVersion = stringValue(configured.get("minimumSupportedVersion"), "");
        String configuredAppStoreId = stringValue(configured.get("appStoreId"), "");
        String appStoreId = sanitizePublicAppStoreId(configuredAppStoreId.isBlank()
            ? module.definition().map(definition -> rawString(definition.raw(), "app.billing.appstore.appAppleId", "")).orElse("")
            : configuredAppStoreId);
        String definitionStoreUrl = module.definition()
            .map(definition -> rawString(definition.raw(), "app.billing.appstore.appStoreUrl", ""))
            .orElse("");
        String configuredStoreUrl = firstNonBlank(
            stringValue(configured.get("appStoreUrl"), ""),
            stringValue(configured.get("downloadUrl"), ""),
            stringValue(configured.get("storeUrl"), ""),
            stringValue(configured.get("updateUrl"), ""),
            definitionStoreUrl
        );
        String sanitizedConfiguredStoreUrl = sanitizePublicAppStoreUrl(configuredStoreUrl);
        String appStoreUrl = sanitizedConfiguredStoreUrl.isBlank() && !appStoreId.isBlank()
            ? "https://apps.apple.com/app/id" + appStoreId
            : sanitizedConfiguredStoreUrl;

        boolean versionDiffers = !clientVersion.equals(latestVersion);
        boolean buildDiffers = !latestBuild.isBlank() && !"unknown".equals(clientBuild) && !latestBuild.equals(clientBuild);
        boolean updateAvailable = versionDiffers || buildDiffers;
        boolean minimumVersionNotMet = !minimumSupportedVersion.isBlank() && isGreaterVersion(minimumSupportedVersion, clientVersion);

        String severity = updateAvailable
            ? stringValue(configured.get("severity"), minimumVersionNotMet ? "required" : "recommended")
            : "none";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", intValue(configured.get("version"), 1));
        data.put("appCode", module.appCode());
        data.put("appName", stringValue(configured.get("appName"), module.definition().map(definition -> definition.name()).orElse(module.appCode())));
        data.put("platform", normalizedPlatform);
        data.put("currentVersion", clientVersion);
        data.put("currentBuild", clientBuild);
        data.put("latestVersion", latestVersion);
        data.put("latestBuild", latestBuild);
        data.put("minimumSupportedVersion", minimumSupportedVersion);
        data.put("updateAvailable", updateAvailable);
        data.put("severity", severity);
        data.put("blocking", Boolean.TRUE.equals(configured.get("blocking")) && minimumVersionNotMet);
        data.put("appStoreId", appStoreId);
        data.put("appStoreUrl", appStoreUrl);
        data.put("downloadUrl", appStoreUrl);
        data.put("storeUrl", appStoreUrl);
        data.put("updateUrl", appStoreUrl);
        data.put("title", stringValue(configured.get("title"), ""));
        data.put("message", stringValue(configured.get("message"), ""));
        data.put("releaseNotes", configured.getOrDefault("releaseNotes", List.of()));
        data.put("ctaText", stringValue(configured.get("ctaText"), ""));
        data.put("source", "sys_remote_config");
        data.put("complianceNote", "普通 App Store 更新提示；客户端不得伪装系统弹窗、不得强制诱导订阅或绕过 Apple 更新流程。");
        return data;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> configuredPolicy(String appCode, String platform) {
        RemoteConfigNamespaceView view = remoteConfigService.loadNamespace(appCode, NAMESPACE_CODE);
        Map<String, Object> items = view == null || view.items() == null ? Map.of() : view.items();
        Object raw = items.getOrDefault(platform, items.get("default"));
        return raw instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
    }

    private boolean isGreaterVersion(String candidate, String current) {
        if (candidate == null || candidate.isBlank() || current == null || current.isBlank() || "unknown".equals(current)) {
            return false;
        }
        List<Integer> left = versionParts(candidate);
        List<Integer> right = versionParts(current);
        int size = Math.max(left.size(), right.size());
        for (int index = 0; index < size; index++) {
            int l = index < left.size() ? left.get(index) : 0;
            int r = index < right.size() ? right.get(index) : 0;
            if (l != r) {
                return l > r;
            }
        }
        return false;
    }

    private List<Integer> versionParts(String version) {
        return java.util.Arrays.stream(version.split("\\."))
            .map(part -> part.replaceAll("[^0-9].*$", ""))
            .map(part -> part.isBlank() ? "0" : part)
            .map(part -> {
                try {
                    return Integer.parseInt(part);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            })
            .toList();
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String stringValue(Object value, String fallback) {
        return value instanceof String string ? string : fallback;
    }

    private int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private String sanitizePublicAppStoreId(String value) {
        String candidate = normalize(value, "");
        if (candidate.startsWith("__FILL") || candidate.contains("__FILL_") || !candidate.matches("[0-9]+")) {
            return "";
        }
        return candidate;
    }

    private String sanitizePublicAppStoreUrl(String value) {
        String candidate = normalize(value, "");
        if (candidate.isBlank()
            || candidate.contains("__FILL")
            || candidate.contains("__PLACEHOLDER")
            || !candidate.startsWith("https://apps.apple.com/")) {
            return "";
        }
        return candidate;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private String rawString(Map<String, Object> raw, String dottedPath, String fallback) {
        Object current = raw;
        for (String segment : dottedPath.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return fallback;
            }
            current = ((Map<String, Object>) map).get(segment);
        }
        return stringValue(current, fallback);
    }
}
