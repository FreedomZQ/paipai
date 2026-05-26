package com.apphub.backend.sys.app.service;

import com.apphub.backend.sys.app.model.AppAppleReadinessView;
import com.apphub.backend.sys.app.model.AppDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 针对 `SystemProductionConfigurationGuard` 的测试类。
 * 用于验证核心业务逻辑、配置约束或关键边界条件。
 */

@ExtendWith(MockitoExtension.class)
class SystemProductionConfigurationGuardTest {

    @Mock
    private AppDefinitionService appDefinitionService;

    @Mock
    private AppAppleReadinessService appAppleReadinessService;

    @Test
    void shouldDoNothingOutsideProd() {
        SystemProductionConfigurationGuard guard = new SystemProductionConfigurationGuard(
            appDefinitionService,
            appAppleReadinessService,
            "dev",
            "",
            "health,info,metrics",
            true,
            true
        );

        assertThatCode(guard::validateOrThrow).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenProdConfigurationIsUnsafe() {
        AppDefinition reading = readingDefinition();
        when(appDefinitionService.list()).thenReturn(List.of(reading));
        when(appAppleReadinessService.inspect(reading)).thenReturn(blockedReadiness(true));

        SystemProductionConfigurationGuard guard = new SystemProductionConfigurationGuard(
            appDefinitionService,
            appAppleReadinessService,
            "prod",
            "",
            "health,info",
            true,
            true
        );

        assertThatThrownBy(guard::validateOrThrow)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("system.ops.token missing in prod")
            .hasMessageContaining("management.endpoints.web.exposure.include must be health in prod")
            .hasMessageContaining("springdoc api-docs/swagger-ui must be disabled in prod")
            .hasMessageContaining("paipai_readingcompanion: auth.apple.teamId missing")
            .hasMessageContaining("paipai_readingcompanion: billing.appstore.allowSandbox must be false in production");
    }

    @Test
    void shouldPassWhenProdConfigurationIsSafe() {
        AppDefinition reading = readingDefinition();
        when(appDefinitionService.list()).thenReturn(List.of(reading));
        when(appAppleReadinessService.inspect(reading)).thenReturn(readyReadiness(false));

        SystemProductionConfigurationGuard guard = new SystemProductionConfigurationGuard(
            appDefinitionService,
            appAppleReadinessService,
            "prod",
            "ops-token",
            "health",
            false,
            false
        );

        assertThatCode(guard::validateOrThrow).doesNotThrowAnyException();
    }

    private AppDefinition readingDefinition() {
        return new AppDefinition(
            "paipai_readingcompanion",
            "拍拍伴读",
            "/api/v1",
            "reading_",
            new AppDefinition.Support(true, true, true),
            Map.of()
        );
    }

    private AppAppleReadinessView blockedReadiness(boolean allowSandbox) {
        return new AppAppleReadinessView(
            "paipai_readingcompanion",
            "blocked",
            new AppAppleReadinessView.AppleAuthReadiness("blocked", true, false, true, true, false, false, false, false, true, true, false, false, true),
            new AppAppleReadinessView.AppStoreReadiness("blocked", true, true, false, false, false, false, false, false, false, true, true, allowSandbox, false, false, false, false, !allowSandbox),
            List.of("auth.apple.teamId missing"),
            List.of()
        );
    }

    private AppAppleReadinessView readyReadiness(boolean allowSandbox) {
        return new AppAppleReadinessView(
            "paipai_readingcompanion",
            "ready",
            new AppAppleReadinessView.AppleAuthReadiness("ready", true, true, true, true, true, true, true, true, true, true, true, true, true),
            new AppAppleReadinessView.AppStoreReadiness("ready", true, true, false, false, false, false, false, false, false, true, true, allowSandbox, true, true, true, true, !allowSandbox),
            List.of(),
            List.of()
        );
    }

}
