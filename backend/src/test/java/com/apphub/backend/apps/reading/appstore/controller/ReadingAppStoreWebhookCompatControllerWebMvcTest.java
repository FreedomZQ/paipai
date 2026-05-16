package com.apphub.backend.apps.reading.appstore.controller;

import com.apphub.backend.apps.common.AppCompatControllerSupport;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationAcceptedView;
import com.apphub.backend.sys.appstore.service.SysAppStoreNotificationService;
import com.apphub.backend.sys.auth.service.SessionTokenResolver;
import com.apphub.backend.sys.auth.service.SysAuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 针对 `ReadingAppStoreWebhookCompatController` 的 WebMvc 测试。
 * 用于验证控制器路由、请求校验、响应结构以及鉴权或门禁行为是否符合预期。
 */

@WebMvcTest(ReadingAppStoreWebhookCompatController.class)
@Import({AppCompatControllerSupport.class, SessionTokenResolver.class})
class ReadingAppStoreWebhookCompatControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppDefinitionService appDefinitionService;

    @MockBean
    private SysAppStoreNotificationService sysAppStoreNotificationService;

    @MockBean
    private SysAuthSessionService sysAuthSessionService;

    @Test
    void notificationsShouldAcceptReadingWebhookPayload() throws Exception {
        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition()));
        when(sysAppStoreNotificationService.ingest(eq("paipai_readingcompanion"), any()))
            .thenReturn(new AppStoreNotificationAcceptedView("paipai_readingcompanion", "notify-2", "DID_RENEW", null, "pending", "accepted", false));

        mockMvc.perform(post("/api/v1/webhooks/app-store/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "signedPayload": "signed-jws",
                      "notificationUuid": "notify-2",
                      "notificationType": "DID_RENEW"
                    }
                    """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.notificationUuid").value("notify-2"));
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
