package com.apphub.backend.sys.auth.service;

import com.apphub.backend.sys.app.model.AppDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对 `PublicAuthAccessPolicyService` 的测试类。
 * 用于验证核心业务逻辑、配置约束或关键边界条件。
 */

class PublicAuthAccessPolicyServiceTest {

    @Test
    void demoSessionsShouldDefaultToDisabledWithoutExplicitOptIn() {
        PublicAuthAccessPolicyService service = new PublicAuthAccessPolicyService("dev");
        AppDefinition definition = new AppDefinition(
            "example",
            "示例应用",
            "/api/v1",
            "example_",
            new AppDefinition.Support(true, true, true),
            Map.of()
        );

        assertThat(service.demoSessionsEnabled(definition)).isFalse();
    }

    @Test
    void demoSessionsShouldRespectExplicitOverride() {
        PublicAuthAccessPolicyService service = new PublicAuthAccessPolicyService("prod");
        AppDefinition definition = new AppDefinition(
            "example",
            "示例应用",
            "/api/v1",
            "example_",
            new AppDefinition.Support(true, true, true),
            Map.of("app.auth.demoSessionEnabled", "true")
        );

        assertThat(service.demoSessionsEnabled(definition)).isTrue();
    }

    @Test
    void bootstrapSessionsShouldDefaultToEnabled() {
        PublicAuthAccessPolicyService service = new PublicAuthAccessPolicyService("prod");
        AppDefinition definition = new AppDefinition(
            "example",
            "示例应用",
            "/v1",
            "example_",
            new AppDefinition.Support(true, false, true),
            Map.of()
        );

        assertThat(service.bootstrapSessionsEnabled(definition)).isTrue();
    }

    @Test
    void bootstrapSessionsShouldRespectExplicitDisable() {
        PublicAuthAccessPolicyService service = new PublicAuthAccessPolicyService("dev");
        AppDefinition definition = new AppDefinition(
            "example",
            "示例应用",
            "/v1",
            "example_",
            new AppDefinition.Support(true, false, true),
            Map.of("app.auth.bootstrapSessionEnabled", "false")
        );

        assertThat(service.bootstrapSessionsEnabled(definition)).isFalse();
    }
}
