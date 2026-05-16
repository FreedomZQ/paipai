package com.apphub.backend.apps.saving.user.controller;

import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.auth.model.CurrentUserView;
import com.apphub.backend.sys.auth.model.DemoSessionCreatedView;
import com.apphub.backend.sys.auth.service.PublicAuthAccessPolicyService;
import com.apphub.backend.sys.auth.service.SysAuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 针对 `SavingUserBootstrapCompatController` 的 WebMvc 测试。
 * 用于验证控制器路由、请求校验、响应结构以及鉴权或门禁行为是否符合预期。
 */

@WebMvcTest(SavingUserBootstrapCompatController.class)
class SavingUserBootstrapCompatControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppDefinitionService appDefinitionService;

    @MockBean
    private SysAuthSessionService sysAuthSessionService;

    @MockBean
    private PublicAuthAccessPolicyService publicAuthAccessPolicyService;

    @Test
    void bootstrapShouldReturnForbiddenWhenDisabled() throws Exception {
        when(appDefinitionService.get("saving")).thenReturn(Optional.of(appDefinition()));
        when(publicAuthAccessPolicyService.bootstrapSessionsEnabled(appDefinition())).thenReturn(false);

        mockMvc.perform(post("/v1/users/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "platform": "ios",
                      "appVersion": "1.0.0",
                      "locale": "zh-Hans",
                      "timezone": "Asia/Shanghai",
                      "deviceInstallIdHash": "device-hash"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void bootstrapShouldCreateSavingSession() throws Exception {
        when(appDefinitionService.get("saving")).thenReturn(Optional.of(appDefinition()));
        when(publicAuthAccessPolicyService.bootstrapSessionsEnabled(appDefinition())).thenReturn(true);
        when(sysAuthSessionService.createDemoSession(eq("saving"), any()))
            .thenReturn(new DemoSessionCreatedView(
                "saving",
                "demo",
                "token-saving",
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                new CurrentUserView(401L, "saving", "guest", "Guest", "active")
            ));

        mockMvc.perform(post("/v1/users/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "platform": "ios",
                      "appVersion": "1.0.0",
                      "locale": "zh-Hans",
                      "timezone": "Asia/Shanghai",
                      "deviceInstallIdHash": "device-hash"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("401"))
            .andExpect(jsonPath("$.data.authToken").value("token-saving"));
    }

    private AppDefinition appDefinition() {
        return new AppDefinition(
            "saving",
            "省钱项目",
            "/v1",
            "saving_",
            new AppDefinition.Support(true, false, true),
            Map.of()
        );
    }
}
