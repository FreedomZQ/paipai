package com.apphub.backend.sys.app.service;

import com.apphub.backend.sys.app.config.AppCatalogProperties;
import com.apphub.backend.sys.app.model.AppDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对 `AppDefinitionService` 的测试类。
 * 用于验证核心业务逻辑、配置约束或关键边界条件。
 */

class AppDefinitionServiceTest {

    @Test
    void shouldLoadSupportFlagsFromYamlDefinitions() {
        AppDefinitionService service = new AppDefinitionService(appCatalogProperties(), new DefaultResourceLoader(), new MockEnvironment());
        service.afterPropertiesSet();

        AppDefinition reading = service.get("paipai_readingcompanion").orElseThrow();
        assertThat(reading.support().appleSignInRequired()).isFalse();
        assertThat(reading.support().billingRequired()).isTrue();
        assertThat(reading.support().legalRequired()).isTrue();
        assertThat(reading.raw().get("app.launch.localNoBackendFirstRelease")).isEqualTo("true");
        assertThat(reading.raw().get("app.auth.bootstrapSessionEnabled")).isEqualTo("false");
    }

    @Test
    void shouldApplyEnvironmentPrefixOverridesForEntitlementMappings() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("backend.apps.paipai_readingcompanion.app.billing.entitlements.productMappings.com.paipai.readalong.family.yearly", "family_access")
            .withProperty("backend.apps.paipai_readingcompanion.app.billing.entitlements.productMappings.com.paipai.readalong.family.monthly", "family_access");
        AppDefinitionService service = new AppDefinitionService(appCatalogProperties(), new DefaultResourceLoader(), environment);
        service.afterPropertiesSet();

        AppDefinition reading = service.get("paipai_readingcompanion").orElseThrow();
        assertThat(reading.raw().get("app.billing.entitlements.productMappings.com.paipai.readalong.family.yearly")).isEqualTo("family_access");
        assertThat(reading.raw().get("app.billing.entitlements.productMappings.com.paipai.readalong.family.monthly")).isEqualTo("family_access");
    }

    @Test
    void shouldApplyEnvironmentPrefixOverridesForRefreshPolicy() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("backend.apps.paipai_readingcompanion.app.billing.entitlements.refreshPolicy.candidateLimit", "12")
            .withProperty("backend.apps.paipai_readingcompanion.app.billing.entitlements.refreshPolicy.cooldownMinutes", "9");
        AppDefinitionService service = new AppDefinitionService(appCatalogProperties(), new DefaultResourceLoader(), environment);
        service.afterPropertiesSet();

        AppDefinition reading = service.get("paipai_readingcompanion").orElseThrow();
        assertThat(reading.raw().get("app.billing.entitlements.refreshPolicy.candidateLimit")).isEqualTo("12");
        assertThat(reading.raw().get("app.billing.entitlements.refreshPolicy.cooldownMinutes")).isEqualTo("9");
    }

    @Test
    void shouldApplyEnvironmentOverridesForPublicAuthPolicy() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("backend.apps.paipai_readingcompanion.app.auth.bootstrapSessionEnabled", "false");
        AppDefinitionService service = new AppDefinitionService(appCatalogProperties(), new DefaultResourceLoader(), environment);
        service.afterPropertiesSet();

        AppDefinition reading = service.get("paipai_readingcompanion").orElseThrow();
        assertThat(reading.raw().get("app.auth.bootstrapSessionEnabled")).isEqualTo("false");
    }

    @Test
    void shouldApplyEnvironmentOverridesForAppleConfig() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("backend.apps.paipai_readingcompanion.app.auth.apple.teamId", "TEAM123")
            .withProperty("backend.apps.paipai_readingcompanion.app.auth.apple.remoteExchangeEnabled", "true")
            .withProperty("backend.apps.paipai_readingcompanion.app.auth.apple.revokeEndpoint", "https://example.com/revoke")
            .withProperty("backend.apps.paipai_readingcompanion.app.billing.appstore.issuerId", "ISSUER123");
        AppDefinitionService service = new AppDefinitionService(appCatalogProperties(), new DefaultResourceLoader(), environment);
        service.afterPropertiesSet();

        AppDefinition reading = service.get("paipai_readingcompanion").orElseThrow();
        assertThat(reading.raw().get("app.auth.apple.teamId")).isEqualTo("TEAM123");
        assertThat(reading.raw().get("app.auth.apple.remoteExchangeEnabled")).isEqualTo("true");
        assertThat(reading.raw().get("app.auth.apple.revokeEndpoint")).isEqualTo("https://example.com/revoke");
        assertThat(reading.raw().get("app.billing.appstore.issuerId")).isEqualTo("ISSUER123");
    }

    private AppCatalogProperties appCatalogProperties() {
        AppCatalogProperties properties = new AppCatalogProperties();
        properties.setSupported(List.of("paipai_readingcompanion"));
        Map<String, String> definitions = new LinkedHashMap<>();
        definitions.put("paipai_readingcompanion", "classpath:apps/reading/app-definition.yml");
        properties.setDefinitions(definitions);
        return properties;
    }
}
