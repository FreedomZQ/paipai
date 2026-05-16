package com.apphub.backend.sys.appstore.controller;

import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationAcceptedView;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationObservabilityView;
import com.apphub.backend.sys.appstore.service.SysAppStoreNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 针对 `SysAppStoreController` 的 WebMvc 测试。
 * 用于验证控制器路由、请求校验、响应结构以及鉴权或门禁行为是否符合预期。
 */

@WebMvcTest(SysAppStoreController.class)
class SysAppStoreControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppDefinitionService appDefinitionService;

    @MockBean
    private SysAppStoreNotificationService sysAppStoreNotificationService;

    @Test
    void notificationObservabilityShouldReturnStatsAndRecentNotifications() throws Exception {
        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition()));
        when(sysAppStoreNotificationService.describeObservability("paipai_readingcompanion"))
            .thenReturn(new AppStoreNotificationObservabilityView(
                "paipai_readingcompanion",
                8,
                5,
                1,
                2,
                4,
                1,
                java.util.List.of(
                    new AppStoreNotificationObservabilityView.RecentNotificationView(
                        "notify-1",
                        "DID_RENEW",
                        "INITIAL_BUY",
                        "verified",
                        "reconciled",
                        java.time.OffsetDateTime.parse("2026-04-16T00:00:00Z")
                    )
                )
            ));

        mockMvc.perform(get("/api/v1/system/appstore/apps/paipai_readingcompanion/notifications/observability"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.total").value(8))
            .andExpect(jsonPath("$.data.reconciled").value(4))
            .andExpect(jsonPath("$.data.recentNotifications[0].notificationUuid").value("notify-1"));
    }

    @Test
    void notificationsShouldAcceptPayload() throws Exception {
        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition()));
        when(sysAppStoreNotificationService.ingest(eq("paipai_readingcompanion"), any()))
            .thenReturn(new AppStoreNotificationAcceptedView(
                "paipai_readingcompanion",
                "notify-1",
                "DID_RENEW",
                "INITIAL_BUY",
                "pending",
                "accepted",
                false
            ));

        mockMvc.perform(post("/api/v1/system/appstore/apps/paipai_readingcompanion/notifications")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "signedPayload": "signed-jws",
                      "notificationUuid": "notify-1",
                      "notificationType": "DID_RENEW",
                      "subtype": "INITIAL_BUY"
                    }
                    """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.notificationUuid").value("notify-1"))
            .andExpect(jsonPath("$.data.processingStatus").value("accepted"));
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
