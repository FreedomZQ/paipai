package com.apphub.backend.sys.appstore.controller;

import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationObservabilityView;
import com.apphub.backend.sys.appstore.service.SysAppStoreNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 针对 `SysAppStoreOpsTokenFilter` 的 WebMvc 测试。
 * 用于验证控制器路由、请求校验、响应结构以及鉴权或门禁行为是否符合预期。
 */

@WebMvcTest(value = SysAppStoreController.class, properties = "backend.ops.token=test-ops-token")
class SysAppStoreOpsTokenFilterWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppDefinitionService appDefinitionService;

    @MockBean
    private SysAppStoreNotificationService sysAppStoreNotificationService;

    @Test
    void systemAppStoreEndpointsShouldRequireOpsToken() throws Exception {
        mockMvc.perform(get("/api/v1/system/appstore/apps/paipai_readingcompanion/notifications/observability"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Ops token required"));
    }

    @Test
    void systemAppStoreEndpointsShouldPassWithOpsToken() throws Exception {
        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition()));
        when(sysAppStoreNotificationService.describeObservability("paipai_readingcompanion"))
            .thenReturn(new AppStoreNotificationObservabilityView(
                "paipai_readingcompanion",
                1,
                1,
                0,
                0,
                1,
                0,
                java.util.List.of(
                    new AppStoreNotificationObservabilityView.RecentNotificationView(
                        "notify-1",
                        "DID_RENEW",
                        null,
                        "verified",
                        "reconciled",
                        OffsetDateTime.parse("2026-04-16T00:00:00Z")
                    )
                )
            ));

        mockMvc.perform(get("/api/v1/system/appstore/apps/paipai_readingcompanion/notifications/observability")
                .header("X-Ops-Token", "test-ops-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appCode").value("paipai_readingcompanion"));
    }

    @Test
    void systemAppStorePostShouldRequireOpsToken() throws Exception {
        mockMvc.perform(post("/api/v1/system/appstore/apps/paipai_readingcompanion/notifications")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "signedPayload": "signed-jws"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Ops token required"));
    }

    private AppDefinition appDefinition() {
        return new AppDefinition(
            "paipai_readingcompanion",
            "拍拍伴读",
            "/api/v1",
            "reading_",
            new AppDefinition.Support(true, true, true),
            Map.of()
        );
    }
}
