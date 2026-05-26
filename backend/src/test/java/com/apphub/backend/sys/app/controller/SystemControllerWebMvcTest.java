package com.apphub.backend.sys.app.controller;

import com.apphub.backend.apps.common.AppModule;
import com.apphub.backend.apps.common.AppModuleRegistry;
import com.apphub.backend.sys.app.config.AppCatalogProperties;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.app.service.AppAppleReadinessService;
import com.apphub.backend.sys.billing.model.EntitlementObservabilityView;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationObservabilityView;
import com.apphub.backend.sys.auth.service.PublicAuthAccessPolicyService;
import com.apphub.backend.sys.auth.service.SysAuthDataService;
import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 针对 `SystemController` 的 WebMvc 测试。
 * 用于验证控制器路由、请求校验、响应结构以及鉴权或门禁行为是否符合预期。
 */

@WebMvcTest(SystemController.class)
@Import({AppDefinitionService.class, AppAppleReadinessService.class})
class SystemControllerWebMvcTest {
    private static final List<String> READING_APP_STORE_PRODUCT_IDS = List.of(
        "com.paipai.readalong.local.ocr.100",
        "com.paipai.readalong.local.ocr.300",
        "com.paipai.readalong.local.tts.100",
        "com.paipai.readalong.local.tts.300"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemController systemController;

    @Autowired
    private AppDefinitionService appDefinitionService;

    @MockBean
    private com.apphub.backend.sys.billing.service.SysBillingService sysBillingService;

    @MockBean
    private com.apphub.backend.sys.appstore.service.SysAppStoreNotificationService sysAppStoreNotificationService;

    @MockBean
    private PublicAuthAccessPolicyService publicAuthAccessPolicyService;

    @MockBean
    private SysAuthDataService authDataService;

    @MockBean
    private AppModuleRegistry appModuleRegistry;

    @MockBean
    private SysRemoteConfigService sysRemoteConfigService;

    @BeforeEach
    void setUp() {
        AppModule readingModule = appModule("paipai_readingcompanion", "reading", "reading_", "/api/v1");
        org.mockito.BDDMockito.given(appModuleRegistry.activeModules()).willReturn(List.of(readingModule));
        org.mockito.BDDMockito.given(sysRemoteConfigService.loadNamespace("paipai_readingcompanion", "release_ios"))
            .willReturn(new RemoteConfigNamespaceView(
                "paipai_readingcompanion",
                "release_ios",
                Map.of(
                    "development_team", "TEAM123",
                    "marketing_version", "1.0.0",
                    "current_project_version", "1",
                    "minimum_ios_version", "18.0",
                    "minimum_ipados_version", "18.0",
                    "bundle_identifier", "com.paipai.readalong.v2",
                    "product_ids", READING_APP_STORE_PRODUCT_IDS
                )
            ));
        overrideReadingDefinition(raw -> {
            raw.put("app.auth.apple.clientId", "com.paipai.readalong.v2");
            raw.put("app.billing.appstore.bundleId", "com.paipai.readalong.v2");
            raw.put("app.auth.apple.remoteExchangeEnabled", "false");
            raw.put("app.billing.appstore.allowSandbox", "false");
        });
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        AppCatalogProperties appCatalogProperties() {
            AppCatalogProperties properties = new AppCatalogProperties();
            properties.setSupported(List.of("paipai_readingcompanion"));
            Map<String, String> definitions = new LinkedHashMap<>();
            definitions.put("paipai_readingcompanion", "classpath:apps/reading/app-definition.yml");
            properties.setDefinitions(definitions);
            return properties;
        }
    }

    private AppModule appModule(String appCode, String internalDomain, String tablePrefix, String apiPrefix) {
        return new AppModule() {
            @Override
            public String appCode() {
                return appCode;
            }

            @Override
            public String internalDomain() {
                return internalDomain;
            }

            @Override
            public String tablePrefix() {
                return tablePrefix;
            }

            @Override
            public String apiPrefix() {
                return apiPrefix;
            }

            @Override
            public java.util.Optional<com.apphub.backend.sys.app.model.AppDefinition> definition() {
                return java.util.Optional.empty();
            }
        };
    }

    @Test
    void healthzShouldReturnSupportedApps() throws Exception {
        mockMvc.perform(get("/api/v1/system/healthz"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("ok"))
            .andExpect(jsonPath("$.data.supportedApps[0]").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.supportedApps.length()").value(1));
    }

    @Test
    void appsShouldReturnDefinitions() throws Exception {
        mockMvc.perform(get("/api/v1/system/apps"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void appShouldReturnSingleDefinition() throws Exception {
        mockMvc.perform(get("/api/v1/system/apps/paipai_readingcompanion"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.tablePrefix").value("reading_"));
    }
    @Test
    void appleReadinessShouldReturnConfigStatus() throws Exception {
        mockMvc.perform(get("/api/v1/system/apps/paipai_readingcompanion/apple/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.overallStatus").value("ready"))
            .andExpect(jsonPath("$.data.auth.status").value("local_no_backend"))
            .andExpect(jsonPath("$.data.auth.required").value(false))
            .andExpect(jsonPath("$.data.auth.remoteExchangeEnabled").value(false))
            .andExpect(jsonPath("$.data.auth.clientIdConfigured").value(true))
            .andExpect(jsonPath("$.data.auth.revokeEndpointConfigured").value(true))
            .andExpect(jsonPath("$.data.auth.credentialEncryptionReady").value(false))
            .andExpect(jsonPath("$.data.auth.formalSessionReady").value(false))
            .andExpect(jsonPath("$.data.auth.bundleIdentityAligned").value(true))
            .andExpect(jsonPath("$.data.appStore.status").value("local_iap_only"))
            .andExpect(jsonPath("$.data.appStore.required").value(true))
            .andExpect(jsonPath("$.data.appStore.serverApiRequired").value(false))
            .andExpect(jsonPath("$.data.appStore.localIapOnly").value(true))
            .andExpect(jsonPath("$.data.appStore.localDeviceCreditsEnabled").value(true))
            .andExpect(jsonPath("$.data.appStore.apiCreditsReservedOnly").value(true))
            .andExpect(jsonPath("$.data.appStore.paidApiCreditsEnabled").value(false))
            .andExpect(jsonPath("$.data.appStore.externalCloudProcessingEnabled").value(false))
            .andExpect(jsonPath("$.data.appStore.serverWalletEnabled").value(false))
            .andExpect(jsonPath("$.data.appStore.consumableHistoryRestoreEnabled").value(false))
            .andExpect(jsonPath("$.data.appStore.bundleIdConfigured").value(true))
            .andExpect(jsonPath("$.data.appStore.productionSandboxSafe").value(true));
    }

    @Test
    void appleTokenStorageShouldReturnFallbackCounts() throws Exception {
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.demoSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.bootstrapSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(authDataService.countProviderTokens("paipai_readingcompanion", "apple")).willReturn(5);
        org.mockito.BDDMockito.given(authDataService.countEncryptedRefreshTokens("paipai_readingcompanion", "apple")).willReturn(3);
        org.mockito.BDDMockito.given(authDataService.countPlaintextRefreshTokenFallbacks("paipai_readingcompanion", "apple")).willReturn(2);

        mockMvc.perform(get("/api/v1/system/apps/paipai_readingcompanion/apple/token-storage"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.totalAppleProviderTokens").value(5))
            .andExpect(jsonPath("$.data.encryptedRefreshTokenCount").value(3))
            .andExpect(jsonPath("$.data.plaintextRefreshTokenFallbackCount").value(2))
            .andExpect(jsonPath("$.data.plaintextFallbackPresent").value(true));
    }

    @Test
    void appleOpsGateShouldAggregateReadinessTokenStorageAndEntitlements() throws Exception {
        org.mockito.BDDMockito.given(authDataService.countProviderTokens("paipai_readingcompanion", "apple")).willReturn(5);
        org.mockito.BDDMockito.given(authDataService.countEncryptedRefreshTokens("paipai_readingcompanion", "apple")).willReturn(3);
        org.mockito.BDDMockito.given(authDataService.countPlaintextRefreshTokenFallbacks("paipai_readingcompanion", "apple")).willReturn(2);
        org.mockito.BDDMockito.given(sysBillingService.describeEntitlementObservability("paipai_readingcompanion"))
            .willReturn(new EntitlementObservabilityView(
                "paipai_readingcompanion",
                1,
                1,
                1,
                java.util.List.of(
                    new EntitlementObservabilityView.EntitlementMappingItemView("com.paipai.readalong.family.yearly", "family_access", "remote_config")
                ),
                new EntitlementObservabilityView.EntitlementRefreshPolicyView(12, "remote_config", 9, "remote_config"),
                new EntitlementObservabilityView.EntitlementRefreshStatsView(8, 5, 1, 1, 1),
                java.util.List.of()
            ));
        org.mockito.BDDMockito.given(sysAppStoreNotificationService.describeObservability("paipai_readingcompanion"))
            .willReturn(new AppStoreNotificationObservabilityView(
                "paipai_readingcompanion",
                8,
                5,
                1,
                2,
                4,
                1,
                java.util.List.of()
            ));

        mockMvc.perform(get("/api/v1/system/apps/paipai_readingcompanion/apple/ops-gate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.status").value("blocked"))
            .andExpect(jsonPath("$.data.tokenStorage.plaintextFallbackPresent").value(true))
            .andExpect(jsonPath("$.data.entitlementObservability.effectiveMappingCount").value(1))
            .andExpect(jsonPath("$.data.notificationObservability.reconciled").value(4))
            .andExpect(jsonPath("$.data.checks[0].key").value("authReadiness"))
            .andExpect(jsonPath("$.data.checks[0].status").value("local_no_backend"))
            .andExpect(jsonPath("$.data.checks[2].key").value("tokenStorage"))
            .andExpect(jsonPath("$.data.checks[2].status").value("blocked"))
            .andExpect(jsonPath("$.data.blockers[0]").value("auth.apple.refreshTokenPlaintextFallback present"));
    }

    @Test
    void appleOpsGateShouldBlockDemoSessionForLocalOnlyApp() throws Exception {
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.demoSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(true);
        org.mockito.BDDMockito.given(authDataService.countProviderTokens("paipai_readingcompanion", "apple")).willReturn(1);
        org.mockito.BDDMockito.given(authDataService.countEncryptedRefreshTokens("paipai_readingcompanion", "apple")).willReturn(1);
        org.mockito.BDDMockito.given(authDataService.countPlaintextRefreshTokenFallbacks("paipai_readingcompanion", "apple")).willReturn(0);
        org.mockito.BDDMockito.given(sysBillingService.describeEntitlementObservability("paipai_readingcompanion"))
            .willReturn(new EntitlementObservabilityView(
                "paipai_readingcompanion",
                1,
                0,
                1,
                java.util.List.of(
                    new EntitlementObservabilityView.EntitlementMappingItemView("product", "access", "app_definition")
                ),
                new EntitlementObservabilityView.EntitlementRefreshPolicyView(12, "remote_config", 9, "remote_config"),
                new EntitlementObservabilityView.EntitlementRefreshStatsView(0, 0, 0, 0, 0),
                java.util.List.of()
            ));
        org.mockito.BDDMockito.given(sysAppStoreNotificationService.describeObservability("paipai_readingcompanion"))
            .willReturn(new AppStoreNotificationObservabilityView(
                "paipai_readingcompanion",
                0,
                0,
                0,
                0,
                0,
                0,
                java.util.List.of()
            ));

        mockMvc.perform(get("/api/v1/system/apps/paipai_readingcompanion/apple/ops-gate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.checks[3].key").value("localOnlyPublicAuth"))
            .andExpect(jsonPath("$.data.checks[3].status").value("blocked"))
            .andExpect(jsonPath("$.data.blockers", hasItem("public.demoSession must be false in local-only launch mode")));
    }

    @Test
    void appleOpsGateShouldBlockSandboxWhenEnvironmentIsProd() throws Exception {
        AppDefinition original = overrideReadingDefinition(raw -> raw.put("app.billing.appstore.allowSandbox", "true"));
        ReflectionTestUtils.setField(systemController, "environment", "prod");
        try {
            org.mockito.BDDMockito.given(publicAuthAccessPolicyService.demoSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
            org.mockito.BDDMockito.given(publicAuthAccessPolicyService.bootstrapSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
            org.mockito.BDDMockito.given(authDataService.countProviderTokens("paipai_readingcompanion", "apple")).willReturn(1);
            org.mockito.BDDMockito.given(authDataService.countEncryptedRefreshTokens("paipai_readingcompanion", "apple")).willReturn(1);
            org.mockito.BDDMockito.given(authDataService.countPlaintextRefreshTokenFallbacks("paipai_readingcompanion", "apple")).willReturn(0);
            org.mockito.BDDMockito.given(sysBillingService.describeEntitlementObservability("paipai_readingcompanion"))
                .willReturn(new EntitlementObservabilityView(
                    "paipai_readingcompanion",
                    1,
                    1,
                    1,
                    java.util.List.of(
                        new EntitlementObservabilityView.EntitlementMappingItemView("com.paipai.readalong.family.yearly", "family_access", "remote_config")
                    ),
                    new EntitlementObservabilityView.EntitlementRefreshPolicyView(12, "remote_config", 9, "remote_config"),
                    new EntitlementObservabilityView.EntitlementRefreshStatsView(1, 1, 0, 0, 0),
                    java.util.List.of()
                ));
            org.mockito.BDDMockito.given(sysAppStoreNotificationService.describeObservability("paipai_readingcompanion"))
                .willReturn(new AppStoreNotificationObservabilityView(
                    "paipai_readingcompanion",
                    1,
                    1,
                    0,
                    0,
                    1,
                    0,
                    java.util.List.of()
                ));

            mockMvc.perform(get("/api/v1/system/apps/paipai_readingcompanion/apple/ops-gate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("blocked"))
                .andExpect(jsonPath("$.data.blockers", hasItem("billing.appstore.allowSandbox must be false in production")));
        } finally {
            restoreReadingDefinition(original);
            ReflectionTestUtils.setField(systemController, "environment", "dev");
        }
    }

    @Test
    void appleOpsGateShouldBlockMissingEntitlementMappingsInProd() throws Exception {
        ReflectionTestUtils.setField(systemController, "environment", "prod");
        try {
            org.mockito.BDDMockito.given(publicAuthAccessPolicyService.demoSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
            org.mockito.BDDMockito.given(publicAuthAccessPolicyService.bootstrapSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
            org.mockito.BDDMockito.given(authDataService.countProviderTokens("paipai_readingcompanion", "apple")).willReturn(1);
            org.mockito.BDDMockito.given(authDataService.countEncryptedRefreshTokens("paipai_readingcompanion", "apple")).willReturn(1);
            org.mockito.BDDMockito.given(authDataService.countPlaintextRefreshTokenFallbacks("paipai_readingcompanion", "apple")).willReturn(0);
            org.mockito.BDDMockito.given(sysBillingService.describeEntitlementObservability("paipai_readingcompanion"))
                .willReturn(new EntitlementObservabilityView(
                    "paipai_readingcompanion",
                    0,
                    0,
                    0,
                    java.util.List.of(),
                    new EntitlementObservabilityView.EntitlementRefreshPolicyView(12, "remote_config", 9, "remote_config"),
                    new EntitlementObservabilityView.EntitlementRefreshStatsView(0, 0, 0, 0, 0),
                    java.util.List.of()
                ));
            org.mockito.BDDMockito.given(sysAppStoreNotificationService.describeObservability("paipai_readingcompanion"))
                .willReturn(new AppStoreNotificationObservabilityView(
                    "paipai_readingcompanion",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    java.util.List.of()
                ));

            mockMvc.perform(get("/api/v1/system/apps/paipai_readingcompanion/apple/ops-gate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checks[4].key").value("entitlementMappings"))
                .andExpect(jsonPath("$.data.checks[4].status").value("blocked"))
                .andExpect(jsonPath("$.data.blockers", hasItem("billing.entitlements.mapping empty; productId fallback is not allowed in prod")));
        } finally {
            ReflectionTestUtils.setField(systemController, "environment", "dev");
        }
    }

    @Test
    void appleOpsGateShouldTreatNotificationsAsNotRequiredForLocalOnlyLaunch() throws Exception {
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.demoSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.bootstrapSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(authDataService.countProviderTokens("paipai_readingcompanion", "apple")).willReturn(1);
        org.mockito.BDDMockito.given(authDataService.countEncryptedRefreshTokens("paipai_readingcompanion", "apple")).willReturn(1);
        org.mockito.BDDMockito.given(authDataService.countPlaintextRefreshTokenFallbacks("paipai_readingcompanion", "apple")).willReturn(0);
        org.mockito.BDDMockito.given(sysBillingService.describeEntitlementObservability("paipai_readingcompanion"))
            .willReturn(new EntitlementObservabilityView(
                "paipai_readingcompanion",
                1,
                1,
                1,
                java.util.List.of(
                    new EntitlementObservabilityView.EntitlementMappingItemView("com.paipai.readalong.family.yearly", "family_access", "remote_config")
                ),
                new EntitlementObservabilityView.EntitlementRefreshPolicyView(12, "remote_config", 9, "remote_config"),
                new EntitlementObservabilityView.EntitlementRefreshStatsView(0, 0, 0, 0, 0),
                java.util.List.of()
            ));
        org.mockito.BDDMockito.given(sysAppStoreNotificationService.describeObservability("paipai_readingcompanion"))
            .willReturn(new AppStoreNotificationObservabilityView(
                "paipai_readingcompanion",
                0,
                0,
                0,
                0,
                0,
                0,
                java.util.List.of()
            ));

        mockMvc.perform(get("/api/v1/system/apps/paipai_readingcompanion/apple/ops-gate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ready"))
            .andExpect(jsonPath("$.data.checks[5].key").value("notificationPipeline"))
            .andExpect(jsonPath("$.data.checks[5].status").value("not_required_local_only"))
            .andExpect(jsonPath("$.data.warnings", hasSize(0)));
    }

    @Test
    void appleOpsGatesShouldReturnAllSupportedApps() throws Exception {
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.demoSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.bootstrapSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(sysBillingService.describeEntitlementObservability(org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new EntitlementObservabilityView(
                "any",
                1,
                1,
                1,
                java.util.List.of(
                    new EntitlementObservabilityView.EntitlementMappingItemView("product", "access", "remote_config")
                ),
                new EntitlementObservabilityView.EntitlementRefreshPolicyView(12, "remote_config", 9, "remote_config"),
                new EntitlementObservabilityView.EntitlementRefreshStatsView(0, 0, 0, 0, 0),
                java.util.List.of()
            ));
        org.mockito.BDDMockito.given(sysAppStoreNotificationService.describeObservability(org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new AppStoreNotificationObservabilityView(
                "any",
                1,
                1,
                0,
                0,
                1,
                0,
                java.util.List.of()
            ));

        mockMvc.perform(get("/api/v1/system/apple/ops-gates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void releaseGateShouldReturnAggregatedAppStatuses() throws Exception {
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.demoSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.bootstrapSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(authDataService.countProviderTokens(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("apple"))).willReturn(1);
        org.mockito.BDDMockito.given(authDataService.countEncryptedRefreshTokens(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("apple"))).willReturn(1);
        org.mockito.BDDMockito.given(authDataService.countPlaintextRefreshTokenFallbacks(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("apple"))).willReturn(0);
        org.mockito.BDDMockito.given(sysBillingService.describeEntitlementObservability(org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new EntitlementObservabilityView(
                "any",
                1,
                1,
                1,
                java.util.List.of(
                    new EntitlementObservabilityView.EntitlementMappingItemView("product", "access", "remote_config")
                ),
                new EntitlementObservabilityView.EntitlementRefreshPolicyView(12, "remote_config", 9, "remote_config"),
                new EntitlementObservabilityView.EntitlementRefreshStatsView(0, 0, 0, 0, 0),
                java.util.List.of()
            ));
        org.mockito.BDDMockito.given(sysAppStoreNotificationService.describeObservability(org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new AppStoreNotificationObservabilityView(
                "any",
                1,
                1,
                0,
                0,
                1,
                0,
                java.util.List.of()
            ));

        mockMvc.perform(get("/api/v1/system/release-gate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("warning"))
            .andExpect(jsonPath("$.data.codeStatus").value("ready"))
            .andExpect(jsonPath("$.data.externalStatus").value("ready"))
            .andExpect(jsonPath("$.data.codeBlockers", hasSize(0)))
            .andExpect(jsonPath("$.data.externalBlockers", hasSize(0)))
            .andExpect(jsonPath("$.data.environment").value("dev"))
            .andExpect(jsonPath("$.data.appCount").value(1))
            .andExpect(jsonPath("$.data.blockedAppCount").value(0))
            .andExpect(jsonPath("$.data.checks[0].key").value("opsToken"))
            .andExpect(jsonPath("$.data.checks[0].status").value("warning"))
            .andExpect(jsonPath("$.data.checks[0].currentValue").value("missing"))
            .andExpect(jsonPath("$.data.checks[0].expectedValue").value("configured"))
            .andExpect(jsonPath("$.data.checks[4].key").value("publicSurface"))
            .andExpect(jsonPath("$.data.checks[4].currentValue").value("5 endpoints"))
            .andExpect(jsonPath("$.data.checks[5].key").value("releaseScope"))
            .andExpect(jsonPath("$.data.checks[5].currentValue").value("included=[paipai_readingcompanion], excluded=[]"))
            .andExpect(jsonPath("$.data.checks[?(@.key=='paipai_readingcompanion.release_ios.reading_api_base_url')].status", hasItem("ready")))
            .andExpect(jsonPath("$.data.apps", hasSize(1)))
            .andExpect(jsonPath("$.data.apps[0].appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.apps[0].status").value("ready"))
            .andExpect(jsonPath("$.data.apps[0].blockers").isArray());
    }

    @Test
    void releaseGateShouldBlockApiBaseUrlForLocalOnlyLaunch() throws Exception {
        org.mockito.BDDMockito.given(sysRemoteConfigService.loadNamespace("paipai_readingcompanion", "release_ios"))
            .willReturn(new RemoteConfigNamespaceView(
                "paipai_readingcompanion",
                "release_ios",
                Map.of(
                    "development_team", "TEAM123",
                    "marketing_version", "1.0.0",
                    "current_project_version", "1",
                    "minimum_ios_version", "18.0",
                    "minimum_ipados_version", "18.0",
                    "bundle_identifier", "com.paipai.readalong.v2",
                    "paipai_api_base_url", "https://api.example.com",
                    "product_ids", READING_APP_STORE_PRODUCT_IDS
                )
            ));
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.demoSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.bootstrapSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(authDataService.countProviderTokens(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).willReturn(1);
        org.mockito.BDDMockito.given(authDataService.countEncryptedRefreshTokens(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).willReturn(1);
        org.mockito.BDDMockito.given(authDataService.countPlaintextRefreshTokenFallbacks(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).willReturn(0);
        org.mockito.BDDMockito.given(sysBillingService.describeEntitlementObservability(org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new EntitlementObservabilityView(
                "any",
                1,
                1,
                1,
                java.util.List.of(
                    new EntitlementObservabilityView.EntitlementMappingItemView("product", "access", "remote_config")
                ),
                new EntitlementObservabilityView.EntitlementRefreshPolicyView(12, "remote_config", 9, "remote_config"),
                new EntitlementObservabilityView.EntitlementRefreshStatsView(0, 0, 0, 0, 0),
                java.util.List.of()
            ));
        org.mockito.BDDMockito.given(sysAppStoreNotificationService.describeObservability(org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new AppStoreNotificationObservabilityView(
                "any",
                1,
                1,
                0,
                0,
                1,
                0,
                java.util.List.of()
            ));

        mockMvc.perform(get("/api/v1/system/release-gate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.checks[?(@.key=='paipai_readingcompanion.release_ios.bundle_identifier')].status", hasItem("ready")))
            .andExpect(jsonPath("$.data.checks[?(@.key=='paipai_readingcompanion.release_ios.paipai_api_base_url')].status", hasItem("blocked")))
            .andExpect(jsonPath("$.data.codeBlockers", hasItem("paipai_readingcompanion.release_ios.paipai_api_base_url must be absent in local-only launch mode")));
    }

    @Test
    void releaseGateShouldBlockWhenReleaseMinimumIosVersionIsLowerThanAppPolicy() throws Exception {
        org.mockito.BDDMockito.given(sysRemoteConfigService.loadNamespace("paipai_readingcompanion", "release_ios"))
            .willReturn(new RemoteConfigNamespaceView(
                "paipai_readingcompanion",
                "release_ios",
                Map.of(
                    "development_team", "TEAM123",
                    "marketing_version", "1.0.0",
                    "current_project_version", "1",
                    "minimum_ios_version", "17.4",
                    "minimum_ipados_version", "18.0",
                    "bundle_identifier", "com.paipai.readalong.v2",
                    "product_ids", READING_APP_STORE_PRODUCT_IDS
                )
            ));
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.demoSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.bootstrapSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(authDataService.countProviderTokens(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).willReturn(1);
        org.mockito.BDDMockito.given(authDataService.countEncryptedRefreshTokens(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).willReturn(1);
        org.mockito.BDDMockito.given(authDataService.countPlaintextRefreshTokenFallbacks(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).willReturn(0);
        org.mockito.BDDMockito.given(sysBillingService.describeEntitlementObservability(org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new EntitlementObservabilityView(
                "any",
                1,
                1,
                1,
                java.util.List.of(
                    new EntitlementObservabilityView.EntitlementMappingItemView("product", "access", "remote_config")
                ),
                new EntitlementObservabilityView.EntitlementRefreshPolicyView(12, "remote_config", 9, "remote_config"),
                new EntitlementObservabilityView.EntitlementRefreshStatsView(0, 0, 0, 0, 0),
                java.util.List.of()
            ));
        org.mockito.BDDMockito.given(sysAppStoreNotificationService.describeObservability(org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new AppStoreNotificationObservabilityView(
                "any",
                1,
                1,
                0,
                0,
                1,
                0,
                java.util.List.of()
            ));

        mockMvc.perform(get("/api/v1/system/release-gate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.codeStatus").value("blocked"))
            .andExpect(jsonPath("$.data.codeBlockers", hasItem("paipai_readingcompanion.release_ios.minimum_ios_version lower than app-definition minimum")))
            .andExpect(jsonPath("$.data.checks[?(@.key=='paipai_readingcompanion.release_ios.minimum_ios_version')].status", hasItem("blocked")))
            .andExpect(jsonPath("$.data.blockers", hasItem("paipai_readingcompanion.release_ios.minimum_ios_version lower than app-definition minimum")));
    }

    @Test
    void releaseGateShouldBlockWhenReleaseBundleIdentifierDriftsFromBackendIdentity() throws Exception {
        org.mockito.BDDMockito.given(sysRemoteConfigService.loadNamespace("paipai_readingcompanion", "release_ios"))
            .willReturn(new RemoteConfigNamespaceView(
                "paipai_readingcompanion",
                "release_ios",
                Map.of(
                    "development_team", "TEAM123",
                    "marketing_version", "1.0.0",
                    "current_project_version", "1",
                    "minimum_ios_version", "18.0",
                    "minimum_ipados_version", "18.0",
                    "bundle_identifier", "com.paipai.readalong.drift",
                    "product_ids", READING_APP_STORE_PRODUCT_IDS
                )
            ));
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.demoSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.bootstrapSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(authDataService.countProviderTokens(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).willReturn(1);
        org.mockito.BDDMockito.given(authDataService.countEncryptedRefreshTokens(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).willReturn(1);
        org.mockito.BDDMockito.given(authDataService.countPlaintextRefreshTokenFallbacks(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).willReturn(0);
        org.mockito.BDDMockito.given(sysBillingService.describeEntitlementObservability(org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new EntitlementObservabilityView(
                "any",
                1,
                1,
                1,
                java.util.List.of(
                    new EntitlementObservabilityView.EntitlementMappingItemView("product", "access", "remote_config")
                ),
                new EntitlementObservabilityView.EntitlementRefreshPolicyView(12, "remote_config", 9, "remote_config"),
                new EntitlementObservabilityView.EntitlementRefreshStatsView(0, 0, 0, 0, 0),
                java.util.List.of()
            ));
        org.mockito.BDDMockito.given(sysAppStoreNotificationService.describeObservability(org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new AppStoreNotificationObservabilityView(
                "any",
                1,
                1,
                0,
                0,
                1,
                0,
                java.util.List.of()
            ));

        mockMvc.perform(get("/api/v1/system/release-gate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.codeStatus").value("blocked"))
            .andExpect(jsonPath("$.data.codeBlockers", hasItem("paipai_readingcompanion.release_ios.bundle_identifier mismatch against backend Apple config")))
            .andExpect(jsonPath("$.data.checks[?(@.key=='paipai_readingcompanion.release_ios.bundle_identifier')].status", hasItem("blocked")))
            .andExpect(jsonPath("$.data.blockers", hasItem("paipai_readingcompanion.release_ios.bundle_identifier mismatch against backend Apple config")));
    }

    @Test
    void releaseGateShouldBlockWhenReleaseProductIdsDriftFromBillingMappings() throws Exception {
        org.mockito.BDDMockito.given(sysRemoteConfigService.loadNamespace("paipai_readingcompanion", "release_ios"))
            .willReturn(new RemoteConfigNamespaceView(
                "paipai_readingcompanion",
                "release_ios",
                Map.of(
                    "development_team", "TEAM123",
                    "marketing_version", "1.0.0",
                    "current_project_version", "1",
                    "minimum_ios_version", "18.0",
                    "minimum_ipados_version", "18.0",
                    "bundle_identifier", "com.paipai.readalong.v2",
                    "product_ids", List.of("com.paipai.readalong.family.yearly")
                )
            ));
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.demoSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.bootstrapSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(false);
        org.mockito.BDDMockito.given(authDataService.countProviderTokens(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).willReturn(1);
        org.mockito.BDDMockito.given(authDataService.countEncryptedRefreshTokens(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).willReturn(1);
        org.mockito.BDDMockito.given(authDataService.countPlaintextRefreshTokenFallbacks(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).willReturn(0);
        org.mockito.BDDMockito.given(sysBillingService.describeEntitlementObservability(org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new EntitlementObservabilityView(
                "any",
                1,
                1,
                1,
                java.util.List.of(
                    new EntitlementObservabilityView.EntitlementMappingItemView("product", "access", "remote_config")
                ),
                new EntitlementObservabilityView.EntitlementRefreshPolicyView(12, "remote_config", 9, "remote_config"),
                new EntitlementObservabilityView.EntitlementRefreshStatsView(0, 0, 0, 0, 0),
                java.util.List.of()
            ));
        org.mockito.BDDMockito.given(sysAppStoreNotificationService.describeObservability(org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new AppStoreNotificationObservabilityView(
                "any",
                1,
                1,
                0,
                0,
                1,
                0,
                java.util.List.of()
            ));

        mockMvc.perform(get("/api/v1/system/release-gate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.codeStatus").value("blocked"))
            .andExpect(jsonPath("$.data.codeBlockers", hasItem("paipai_readingcompanion.release_ios.product_ids mismatch against billing productMappings")))
            .andExpect(jsonPath("$.data.checks[?(@.key=='paipai_readingcompanion.release_ios.product_ids')].status", hasItem("blocked")));
    }

    @Test
    void publicSurfaceShouldReturnIntentionalPublicEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/system/public-surface"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.endpoints", hasSize(5)))
            .andExpect(jsonPath("$.data.endpoints[0].path").value("/api/v1/system/healthz"))
            .andExpect(jsonPath("$.data.endpoints[1].path").value("/actuator/health"))
            .andExpect(jsonPath("$.data.endpoints[2].path").value("/api/v1/system/auth/apps/{appCode}/apple/exchange"))
            .andExpect(jsonPath("$.data.endpoints[3].path").value("/api/v1/system/appstore/apps/{appCode}/notifications"))
            .andExpect(jsonPath("$.data.endpoints[4].path").value("/api/v1/webhooks/app-store/notifications"));
    }

    @Test
    void publicSurfaceShouldInventoryDemoEndpointOnlyWhenExplicitlyEnabled() throws Exception {
        org.mockito.BDDMockito.given(publicAuthAccessPolicyService.demoSessionsEnabled(org.mockito.ArgumentMatchers.any())).willReturn(true);

        mockMvc.perform(get("/api/v1/system/public-surface"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.endpoints", hasSize(6)))
            .andExpect(jsonPath("$.data.endpoints[3].path").value("/api/v1/system/auth/apps/{appCode}/sessions/demo"))
            .andExpect(jsonPath("$.data.endpoints[3].exposure").value("public_when_app_explicitly_enables_demo"));
    }

    @Test
    void entitlementObservabilityShouldReturnEffectiveMappingsAndRefreshStats() throws Exception {
        org.mockito.BDDMockito.given(sysBillingService.describeEntitlementObservability("paipai_readingcompanion"))
            .willReturn(new EntitlementObservabilityView(
                "paipai_readingcompanion",
                1,
                2,
                2,
                java.util.List.of(
                    new EntitlementObservabilityView.EntitlementMappingItemView("com.paipai.readalong.family.yearly", "family_access", "remote_config"),
                    new EntitlementObservabilityView.EntitlementMappingItemView("com.paipai.readalong.family.monthly", "family_access", "app_definition")
                ),
                new EntitlementObservabilityView.EntitlementRefreshPolicyView(12, "remote_config", 9, "remote_config"),
                new EntitlementObservabilityView.EntitlementRefreshStatsView(8, 5, 1, 1, 1),
                java.util.List.of(
                    new EntitlementObservabilityView.EntitlementRefreshRecentItemView(91L, "otx-1", "family_yearly", "verified", "accepted", java.time.OffsetDateTime.parse("2026-04-16T00:00:00Z"))
                )
            ));

        mockMvc.perform(get("/api/v1/system/apps/paipai_readingcompanion/billing/entitlements/observability"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.definitionMappingCount").value(1))
            .andExpect(jsonPath("$.data.remoteConfigMappingCount").value(2))
            .andExpect(jsonPath("$.data.effectiveMappingCount").value(2))
            .andExpect(jsonPath("$.data.refreshPolicy.candidateLimit").value(12))
            .andExpect(jsonPath("$.data.refreshPolicy.cooldownMinutes").value(9))
            .andExpect(jsonPath("$.data.refreshStats.total").value(8))
            .andExpect(jsonPath("$.data.recentRefreshes[0].originalTransactionId").value("otx-1"))
            .andExpect(jsonPath("$.data.effectiveMappings[0].source").value("remote_config"));
    }

    private AppDefinition overrideReadingDefinition(java.util.function.Consumer<Map<String, Object>> mutator) {
        return overrideAppDefinition("paipai_readingcompanion", mutator);
    }

    @SuppressWarnings("unchecked")
    private AppDefinition overrideAppDefinition(String appCode, java.util.function.Consumer<Map<String, Object>> mutator) {
        Map<String, AppDefinition> definitions = (Map<String, AppDefinition>) ReflectionTestUtils.getField(appDefinitionService, "definitions");
        AppDefinition original = definitions.get(appCode);
        if (original == null) {
            return null;
        }
        Map<String, Object> raw = new LinkedHashMap<>(original.raw());
        mutator.accept(raw);
        definitions.put(appCode, new AppDefinition(
            original.code(),
            original.name(),
            original.apiPrefix(),
            original.tablePrefix(),
            original.support(),
            java.util.Collections.unmodifiableMap(raw)
        ));
        return original;
    }

    @SuppressWarnings("unchecked")
    private void restoreReadingDefinition(AppDefinition definition) {
        Map<String, AppDefinition> definitions = (Map<String, AppDefinition>) ReflectionTestUtils.getField(appDefinitionService, "definitions");
        definitions.put("paipai_readingcompanion", definition);
    }
}
