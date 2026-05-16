package com.apphub.backend.sys.app.service;

import com.apphub.backend.sys.app.config.AppCatalogProperties;
import com.apphub.backend.sys.app.model.AppAppleReadinessView;
import com.apphub.backend.sys.app.model.AppDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对 `AppAppleReadinessService` 的测试类。
 * 用于验证核心业务逻辑、配置约束或关键边界条件。
 */

class AppAppleReadinessServiceTest {

    private final AppAppleReadinessService readinessService = new AppAppleReadinessService();

    @Test
    void shouldMarkReadingAsBlockedWhenSecretsAreMissing() {
        AppDefinition definition = loadReading(new MockEnvironment());

        AppAppleReadinessView readiness = readinessService.inspect(definition);

        assertThat(readiness.appCode()).isEqualTo("paipai_readingcompanion");
        assertThat(readiness.auth().required()).isTrue();
        assertThat(readiness.appStore().required()).isTrue();
        assertThat(readiness.overallStatus()).isEqualTo("blocked");
        assertThat(readiness.blockers()).anyMatch(item -> item.contains("teamId"));
        assertThat(readiness.blockers()).anyMatch(item -> item.contains("issuerId"));
        assertThat(readiness.auth().bundleIdentityAligned()).isTrue();
        assertThat(readiness.auth().formalSessionReady()).isFalse();
        assertThat(readiness.appStore().productionSandboxSafe()).isTrue();
    }

    @Test
    void shouldBecomeReadyWhenAppleCredentialsAreOverridden() {
        String previous = System.getProperty("APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY");
        System.setProperty("APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY", Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8)));
        try {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("backend.apps.paipai_readingcompanion.app.auth.apple.teamId", "TEAM123")
            .withProperty("backend.apps.paipai_readingcompanion.app.auth.apple.keyId", "KEY123")
            .withProperty("backend.apps.paipai_readingcompanion.app.auth.apple.privateKey", "PRIVATEKEY")
            .withProperty("backend.apps.paipai_readingcompanion.app.auth.apple.redirectUri", "https://example.com/apple/callback")
            .withProperty("backend.apps.paipai_readingcompanion.app.auth.apple.remoteExchangeEnabled", "true")
            .withProperty("backend.apps.paipai_readingcompanion.app.auth.apple.revokeEndpoint", "https://example.com/revoke")
            .withProperty("backend.apps.paipai_readingcompanion.app.billing.appstore.issuerId", "ISSUER123")
            .withProperty("backend.apps.paipai_readingcompanion.app.billing.appstore.keyId", "KEY123")
            .withProperty("backend.apps.paipai_readingcompanion.app.billing.appstore.privateKey", "PRIVATEKEY")
            .withProperty("backend.apps.paipai_readingcompanion.app.billing.appstore.appAppleId", "1234567890")
            .withProperty("backend.apps.paipai_readingcompanion.app.billing.appstore.allowSandbox", "false");
        AppDefinition definition = loadReading(environment);

        AppAppleReadinessView readiness = readinessService.inspect(definition);

        assertThat(readiness.auth().status()).isEqualTo("ready");
        assertThat(readiness.auth().revokeEndpointConfigured()).isTrue();
        assertThat(readiness.auth().credentialEncryptionReady()).isTrue();
        assertThat(readiness.auth().formalSessionReady()).isTrue();
        assertThat(readiness.auth().bundleIdentityAligned()).isTrue();
        assertThat(readiness.appStore().status()).isEqualTo("ready");
        assertThat(readiness.appStore().productionSandboxSafe()).isTrue();
        assertThat(readiness.overallStatus()).isEqualTo("ready");
        assertThat(readiness.blockers()).isEmpty();
        } finally {
            if (previous == null) {
                System.clearProperty("APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY");
            } else {
                System.setProperty("APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY", previous);
            }
        }
    }

    private AppDefinition loadReading(MockEnvironment environment) {
        AppDefinitionService definitionService = new AppDefinitionService(appCatalogProperties(), new DefaultResourceLoader(), environment);
        definitionService.afterPropertiesSet();
        return definitionService.get("paipai_readingcompanion").orElseThrow();
    }

    private AppCatalogProperties appCatalogProperties() {
        AppCatalogProperties properties = new AppCatalogProperties();
        properties.setSupported(List.of("paipai_readingcompanion", "saving"));
        Map<String, String> definitions = new LinkedHashMap<>();
        definitions.put("paipai_readingcompanion", "classpath:apps/reading/app-definition.yml");
        definitions.put("saving", "classpath:apps/saving/app-definition.yml");
        properties.setDefinitions(definitions);
        return properties;
    }
}
