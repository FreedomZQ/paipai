package com.apphub.backend.apps.saving.appstore.controller;

import com.apphub.backend.apps.common.AppCompatControllerSupport;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationAcceptedView;
import com.apphub.backend.sys.appstore.service.SysAppStoreNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
 * 针对 `SavingAppStoreCompatOpsToken` 的 WebMvc 测试。
 * 用于验证控制器路由、请求校验、响应结构以及鉴权或门禁行为是否符合预期。
 */

@WebMvcTest(value = SavingAppStoreCompatController.class, properties = "backend.ops.token=test-ops-token")
class SavingAppStoreCompatOpsTokenWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppDefinitionService appDefinitionService;

    @MockBean
    private AppCompatControllerSupport appCompatControllerSupport;

    @MockBean
    private SysAppStoreNotificationService sysAppStoreNotificationService;

    @Test
    void savingWebhookShouldRemainPublicEvenWhenOpsTokenIsConfigured() throws Exception {
        when(appDefinitionService.get("saving")).thenReturn(Optional.of(appDefinition()));
        when(sysAppStoreNotificationService.ingest(eq("saving"), any()))
            .thenReturn(new AppStoreNotificationAcceptedView("saving", "notify-9", null, null, "pending", "accepted", false));

        mockMvc.perform(post("/v1/appstore/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "signedPayload": "signed-jws"
                    }
                    """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.appCode").value("saving"));
    }

    private AppDefinition appDefinition() {
        return new AppDefinition(
            "saving",
            "省钱项目",
            "/v1",
            "saving_",
            new AppDefinition.Support(true, true, true),
            Map.of()
        );
    }
}
