package com.apphub.backend.sys.app.service;

import com.apphub.backend.apps.saving.SavingAppModule;
import com.apphub.backend.sys.app.model.AppAppleReadinessView;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.auth.service.PublicAuthAccessPolicyService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 应用编排与发布门禁服务 `SystemProductionConfigurationGuard`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Service
public class SystemProductionConfigurationGuard implements InitializingBean {

    private final AppDefinitionService appDefinitionService;
    private final AppAppleReadinessService appAppleReadinessService;
    private final PublicAuthAccessPolicyService publicAuthAccessPolicyService;
    private final String environment;
    private final String opsToken;
    private final String actuatorExposure;
    private final boolean apiDocsEnabled;
    private final boolean swaggerUiEnabled;

    public SystemProductionConfigurationGuard(
        AppDefinitionService appDefinitionService,
        AppAppleReadinessService appAppleReadinessService,
        PublicAuthAccessPolicyService publicAuthAccessPolicyService,
        @Value("${backend.environment:${BACKEND_ENV:dev}}") String environment,
        @Value("${backend.ops.token:${BACKEND_OPS_TOKEN:}}") String opsToken,
        @Value("${management.endpoints.web.exposure.include:health,info,metrics}") String actuatorExposure,
        @Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled,
        @Value("${springdoc.swagger-ui.enabled:true}") boolean swaggerUiEnabled
    ) {
        this.appDefinitionService = appDefinitionService;
        this.appAppleReadinessService = appAppleReadinessService;
        this.publicAuthAccessPolicyService = publicAuthAccessPolicyService;
        this.environment = environment;
        this.opsToken = opsToken;
        this.actuatorExposure = actuatorExposure;
        this.apiDocsEnabled = apiDocsEnabled;
        this.swaggerUiEnabled = swaggerUiEnabled;
    }

    @Override
    public void afterPropertiesSet() {
        validateOrThrow();
    }

    public void validateOrThrow() {
        if (!"prod".equalsIgnoreCase(environment)) {
            return;
        }
        List<String> blockers = new ArrayList<>();

        if (opsToken == null || opsToken.isBlank()) {
            blockers.add("system.ops.token missing in prod");
        }
        String exposure = actuatorExposure == null ? "" : actuatorExposure.trim().toLowerCase(Locale.ROOT);
        if (!"health".equals(exposure)) {
            blockers.add("management.endpoints.web.exposure.include must be health in prod");
        }
        if (apiDocsEnabled || swaggerUiEnabled) {
            blockers.add("springdoc api-docs/swagger-ui must be disabled in prod");
        }

        for (AppDefinition definition : appDefinitionService.list()) {
            AppAppleReadinessView readiness = appAppleReadinessService.inspect(definition);
            for (String blocker : readiness.blockers()) {
                blockers.add(definition.code() + ": " + blocker);
            }
            if (definition.support().billingRequired() && !readiness.appStore().productionSandboxSafe()) {
                blockers.add(definition.code() + ": billing.appstore.allowSandbox must be false in production");
            }
            if (SavingAppModule.APP_CODE.equals(definition.code())
                && !definition.support().appleSignInRequired()
                && !publicAuthAccessPolicyService.bootstrapSessionsEnabled(definition)) {
                blockers.add(SavingAppModule.APP_CODE + ": public.bootstrapSession disabled");
            }
        }

        if (!blockers.isEmpty()) {
            throw new IllegalStateException("Production configuration guard failed: " + String.join(" | ", blockers));
        }
    }
}
