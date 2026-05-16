package com.apphub.backend.sys.app.service;

import com.apphub.backend.sys.app.config.AppCatalogProperties;
import com.apphub.backend.sys.app.model.AppDefinition;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * 应用编排与发布门禁服务 `AppDefinitionService`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Service
public class AppDefinitionService implements InitializingBean {

    private final AppCatalogProperties appCatalogProperties;
    private final ResourceLoader resourceLoader;
    private final Environment environment;
    private final Map<String, AppDefinition> definitions = new LinkedHashMap<>();

    public AppDefinitionService(AppCatalogProperties appCatalogProperties, ResourceLoader resourceLoader, Environment environment) {
        this.appCatalogProperties = appCatalogProperties;
        this.resourceLoader = resourceLoader;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        definitions.clear();
        // 中文说明：App 清单必须来自配置，不在代码中兜底写死。
        // 这样新增第二个 App 或下架某个 App 时，只需改配置；配置缺失时 fail-closed，避免误把请求落到 Paipai。
        List<String> supportedApps = appCatalogProperties.getSupported() == null
            ? List.of()
            : appCatalogProperties.getSupported();
        for (String appCode : supportedApps) {
            String location = appCatalogProperties.getDefinitions().get(appCode);
            if (location == null || location.isBlank()) {
                continue;
            }
            AppDefinition definition = loadDefinition(location, appCode);
            if (definition != null) {
                definitions.put(appCode, definition);
            }
        }
    }

    public List<AppDefinition> list() {
        return List.copyOf(definitions.values());
    }

    public Optional<AppDefinition> get(String appCode) {
        return Optional.ofNullable(definitions.get(appCode));
    }

    private AppDefinition loadDefinition(String location, String appCode) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            return null;
        }
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(resource);
        Properties properties = factory.getObject();
        if (properties == null) {
            return null;
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            raw.put(name, properties.getProperty(name));
        }
        applyEnvironmentOverrides(raw, appCode);

        String code = trim(rawValue(raw, "app.code"));
        String name = trim(rawValue(raw, "app.name"));
        String apiPrefix = trim(rawValue(raw, "app.apiPrefix"));
        String tablePrefix = trim(rawValue(raw, "app.tablePrefix"));
        AppDefinition.Support support = new AppDefinition.Support(
            Boolean.parseBoolean(rawValueOrDefault(raw, "app.support.legalRequired", "false")),
            Boolean.parseBoolean(rawValueOrDefault(raw, "app.support.appleSignInRequired", "false")),
            Boolean.parseBoolean(rawValueOrDefault(raw, "app.support.billingRequired", "false"))
        );
        if (Objects.isNull(code) || Objects.isNull(name)) {
            return null;
        }
        return new AppDefinition(code, name, apiPrefix, tablePrefix, support, Collections.unmodifiableMap(raw));
    }

    private void applyEnvironmentOverrides(Map<String, Object> raw, String appCode) {
        for (String key : overridableKeys()) {
            String propertyKey = "backend.apps." + appCode + "." + key;
            if (environment.containsProperty(propertyKey)) {
                raw.put(key, environment.getProperty(propertyKey));
            }
        }
        applyEnvironmentPrefixOverrides(raw, appCode, "app.billing.entitlements.productMappings.");
        applyEnvironmentPrefixOverrides(raw, appCode, "app.billing.entitlements.refreshPolicy.");
    }

    private void applyEnvironmentPrefixOverrides(Map<String, Object> raw, String appCode, String rawPrefix) {
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            return;
        }
        String propertyPrefix = "backend.apps." + appCode + "." + rawPrefix;
        for (PropertySource<?> propertySource : configurableEnvironment.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource) {
                for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                    if (propertyName != null && propertyName.startsWith(propertyPrefix)) {
                        String rawKey = propertyName.substring(("backend.apps." + appCode + ".").length());
                        raw.put(rawKey, environment.getProperty(propertyName));
                    }
                }
            }
        }
    }

    private List<String> overridableKeys() {
        return List.of(
            "app.name",
            "app.apiPrefix",
            "app.tablePrefix",
            "app.support.legalRequired",
            "app.support.appleSignInRequired",
            "app.support.billingRequired",
            "app.release.requiredForCurrentWave",
            "app.release.minimumIosVersion",
            "app.release.minimumIpadosVersion",
            "app.auth.demoSessionEnabled",
            "app.auth.bootstrapSessionEnabled",
            "app.auth.apple.clientId",
            "app.auth.apple.teamId",
            "app.auth.apple.keyId",
            "app.auth.apple.privateKey",
            "app.auth.apple.audience",
            "app.auth.apple.redirectUri",
            "app.auth.apple.environment",
            "app.auth.apple.tokenEndpoint",
            "app.auth.apple.jwksUrl",
            "app.auth.apple.remoteExchangeEnabled",
            "app.auth.apple.nativeIdentityTokenSessionEnabled",
            "app.auth.apple.revokeEndpoint",
            "app.billing.appstore.bundleId",
            "app.billing.appstore.environment",
            "app.billing.appstore.allowSandbox",
            "app.billing.appstore.appAppleId",
            "app.billing.appstore.issuerId",
            "app.billing.appstore.keyId",
            "app.billing.appstore.privateKey"
        );
    }

    private String rawValue(Map<String, Object> raw, String key) {
        Object direct = raw.get(key);
        if (direct != null) {
            return String.valueOf(direct);
        }
        Object relaxed = raw.get(relaxedKey(key));
        return relaxed == null ? null : String.valueOf(relaxed);
    }

    private String rawValueOrDefault(Map<String, Object> raw, String key, String defaultValue) {
        String value = rawValue(raw, key);
        return value == null ? defaultValue : value;
    }

    private String relaxedKey(String key) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if (Character.isUpperCase(ch)) {
                builder.append('-').append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
