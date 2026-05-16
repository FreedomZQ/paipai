package com.apphub.backend.sys.app.controller;

import com.apphub.backend.apps.common.AppModuleRegistry;
import com.apphub.backend.sys.powersync.support.PowerSyncAppAdapterRegistry;
import com.apphub.backend.sys.app.config.AppCatalogProperties;
import com.apphub.backend.sys.app.service.AppAppleReadinessService;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.auth.service.PublicAuthAccessPolicyService;
import com.apphub.backend.sys.auth.service.SysAuthDataService;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 针对 `SystemOpsTokenFailClosed` 的 WebMvc 测试。
 * 用于验证控制器路由、请求校验、响应结构以及鉴权或门禁行为是否符合预期。
 */

@WebMvcTest(value = SystemController.class, properties = {
    "backend.environment=prod",
    "backend.ops.token="
})
@Import({AppDefinitionService.class, AppAppleReadinessService.class})
class SystemOpsTokenFailClosedWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

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
    private PowerSyncAppAdapterRegistry powerSyncAppAdapterRegistry;

    @MockBean
    private SysRemoteConfigService sysRemoteConfigService;

    @TestConfiguration
    static class TestConfig {
        @Bean
        AppCatalogProperties appCatalogProperties() {
            AppCatalogProperties properties = new AppCatalogProperties();
            properties.setSupported(List.of("paipai_readingcompanion", "saving"));
            Map<String, String> definitions = new LinkedHashMap<>();
            definitions.put("paipai_readingcompanion", "classpath:apps/reading/app-definition.yml");
            definitions.put("saving", "classpath:apps/saving/app-definition.yml");
            properties.setDefinitions(definitions);
            return properties;
        }
    }

    @Test
    void prodSystemEndpointsShouldFailClosedWhenOpsTokenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/system/apps"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Ops token is not configured"));
    }

    @Test
    void prodHealthzShouldRemainPublicWhenOpsTokenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/system/healthz"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ok"))
            .andExpect(jsonPath("$.data.supportedApps").doesNotExist())
            .andExpect(jsonPath("$.data.definitionResources").doesNotExist());
    }
}
