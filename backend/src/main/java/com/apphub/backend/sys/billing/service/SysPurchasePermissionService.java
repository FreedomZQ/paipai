package com.apphub.backend.sys.billing.service;

import com.apphub.backend.sys.billing.model.PurchasePermissionDecision;
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
 * 多应用通用购买权限服务。
 *
 * <p>该服务只读取 `sys_remote_config` 中的 `billing_purchase_control` 命名空间，不绑定拍拍伴读、
 * 省钱星球或 FitMystery 的业务表。每个 App 可通过 appCode 配置全局购买开关，也可通过
 * `products.{productCode}` 配置单个购买项状态，前端和购买提交接口共用同一个实时判定结果。
 */
@Service
public class SysPurchasePermissionService {
    private static final String NAMESPACE = "billing_purchase_control";
    private static final String DEFAULT_MESSAGE_KEY = "service_unavailable";
    private static final Map<String, String> DEFAULT_MESSAGES = Map.of(
        "zh-Hans", "服务暂不可用",
        "en", "Service is temporarily unavailable"
    );

    private final SysRemoteConfigService remoteConfigService;

    public SysPurchasePermissionService(SysRemoteConfigService remoteConfigService) {
        this.remoteConfigService = remoteConfigService;
    }

    /**
     * 读取数据库配置并计算购买权限。
     *
     * @param appCode 应用编码，用于隔离不同 App 的购买控制配置。
     * @param productCode 具体购买项编码；为空时只判断 App 全局购买开关。
     * @param locale 前端当前语言，服务端用它选择兜底展示文案。
     * @return 统一购买权限判定结果，allowed=false 时前端必须禁用付款按钮。
     */
    public PurchasePermissionDecision decide(String appCode, String productCode, String locale) {
        Map<String, Object> items = loadItems(appCode);
        DecisionConfig global = configFrom(items.get("global"));
        DecisionConfig product = productCode == null || productCode.isBlank()
            ? DecisionConfig.empty()
            : configFrom(items.get("products." + productCode.trim()));

        DecisionConfig effective = product.hasExplicitAllowed() ? product : global;
        boolean allowed = effective.allowed() == null || Boolean.TRUE.equals(effective.allowed());
        String status = allowed ? "available" : firstNonBlank(effective.status(), "disabled");
        String reasonCode = allowed ? "available" : firstNonBlank(effective.reasonCode(), "purchase_disabled");
        String messageKey = allowed ? "purchase_available" : firstNonBlank(effective.messageKey(), DEFAULT_MESSAGE_KEY);
        Map<String, String> messages = effective.messages().isEmpty() ? DEFAULT_MESSAGES : effective.messages();
        String message = allowed ? "" : localizedMessage(messages, locale);

        return new PurchasePermissionDecision(
            appCode,
            productCode,
            allowed,
            status,
            reasonCode,
            messageKey,
            messages,
            message
        );
    }

    public String checkedAt() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    private Map<String, Object> loadItems(String appCode) {
        try {
            RemoteConfigNamespaceView namespace = remoteConfigService.loadNamespace(appCode, NAMESPACE);
            return namespace == null || namespace.items() == null ? Map.of() : namespace.items();
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private DecisionConfig configFrom(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return DecisionConfig.empty();
        }
        Boolean allowed = booleanValue(map.get("allowed"));
        String status = stringValue(map.get("status"));
        String reasonCode = stringValue(map.get("reasonCode"));
        String messageKey = stringValue(map.get("messageKey"));
        Map<String, String> messages = new LinkedHashMap<>();
        Object rawMessages = map.get("messages");
        if (rawMessages instanceof Map<?, ?> messageMap) {
            messageMap.forEach((key, value) -> {
                String text = stringValue(value);
                if (key != null && text != null && !text.isBlank()) {
                    messages.put(String.valueOf(key), text);
                }
            });
        }
        return new DecisionConfig(allowed, status, reasonCode, messageKey, messages);
    }

    private Boolean booleanValue(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    private String stringValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private String localizedMessage(Map<String, String> messages, String locale) {
        for (String key : localeKeys(locale)) {
            String value = messages.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return DEFAULT_MESSAGES.get("zh-Hans");
    }

    private List<String> localeKeys(String locale) {
        String normalized = locale == null ? "" : locale.trim().replace("_", "-").toLowerCase(Locale.ROOT);
        if (normalized.startsWith("zh")) {
            return List.of("zh-Hans", "zh", "en");
        }
        if (normalized.startsWith("ja")) {
            return List.of("ja", "en", "zh-Hans");
        }
        if (normalized.startsWith("ko")) {
            return List.of("ko", "en", "zh-Hans");
        }
        if (normalized.startsWith("es")) {
            return List.of("es", "en", "zh-Hans");
        }
        return List.of("en", "zh-Hans");
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private record DecisionConfig(
        Boolean allowed,
        String status,
        String reasonCode,
        String messageKey,
        Map<String, String> messages
    ) {
        private static DecisionConfig empty() {
            return new DecisionConfig(null, null, null, null, Map.of());
        }

        private boolean hasExplicitAllowed() {
            return allowed != null;
        }
    }
}
