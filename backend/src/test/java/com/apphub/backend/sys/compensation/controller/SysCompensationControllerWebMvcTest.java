package com.apphub.backend.sys.compensation.controller;

import com.apphub.backend.apps.common.AppCompatControllerSupport;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.auth.model.AuthenticatedSessionView;
import com.apphub.backend.sys.auth.model.CurrentUserView;
import com.apphub.backend.sys.auth.service.SessionTokenResolver;
import com.apphub.backend.sys.auth.service.SysAuthSessionService;
import com.apphub.backend.sys.compensation.model.CompensationCodeView;
import com.apphub.backend.sys.compensation.service.SysCompensationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SysCompensationController.class)
@Import({SessionTokenResolver.class, AppCompatControllerSupport.class})
class SysCompensationControllerWebMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppDefinitionService appDefinitionService;
    @MockBean
    private SysAuthSessionService sysAuthSessionService;
    @MockBean
    private SysCompensationService compensationService;

    @Test
    void createShouldReturnCode() throws Exception {
        mockOpsSession();
        when(compensationService.createCode(eq("paipai_readingcompanion"), eq(101L), any()))
            .thenReturn(sampleCode("PP-ABCDE-FGHJK-MNPQR"));

        mockMvc.perform(post("/api/v1/system/compensation-codes/apps/paipai_readingcompanion")
                .header("Authorization", "Bearer token-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "benefitType": "plan",
                      "planCode": "standard_single_child",
                      "grantCount": 1,
                      "grantValidDays": 30
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.compensationCode").value("PP-ABCDE-FGHJK-MNPQR"));
    }

    @Test
    void listShouldReturnCodes() throws Exception {
        mockOpsSession();
        when(compensationService.listCodes("paipai_readingcompanion", "unused", "plan"))
            .thenReturn(List.of(sampleCode("PP-ABCDE-FGHJK-MNPQR")));

        mockMvc.perform(get("/api/v1/system/compensation-codes/apps/paipai_readingcompanion")
                .header("Authorization", "Bearer token-123")
                .param("status", "unused")
                .param("benefitType", "plan"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].compensationCode").value("PP-ABCDE-FGHJK-MNPQR"));
    }

    @Test
    void voidShouldReturnUpdatedCode() throws Exception {
        mockOpsSession();
        when(compensationService.voidCode("paipai_readingcompanion", "PP-ABCDE-FGHJK-MNPQR", "结束"))
            .thenReturn(sampleCode("PP-ABCDE-FGHJK-MNPQR"));

        mockMvc.perform(post("/api/v1/system/compensation-codes/apps/paipai_readingcompanion/PP-ABCDE-FGHJK-MNPQR/void")
                .header("Authorization", "Bearer token-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"结束"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("unused"));
    }

    private void mockOpsSession() {
        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(
            new AppDefinition(
                "paipai_readingcompanion",
                "拍拍伴读",
                "/api/v1",
                "reading_",
                new AppDefinition.Support(true, true, true),
                Map.of()
            )
        ));
        when(sysAuthSessionService.findCurrentSession("token-123"))
            .thenReturn(Optional.of(new AuthenticatedSessionView(
                "paipai_readingcompanion",
                "demo",
                "active",
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                new CurrentUserView(101L, "paipai_readingcompanion", "parent", "Parent", "active")
            )));
    }

    private CompensationCodeView sampleCode(String code) {
        return new CompensationCodeView(
            11L,
            "paipai_readingcompanion",
            code,
            "plan",
            "standard_single_child",
            "family_access",
            null,
            1,
            30,
            OffsetDateTime.parse("2026-06-01T00:00:00Z"),
            OffsetDateTime.parse("2026-06-01T00:00:00Z"),
            "single_use",
            1,
            0,
            "unused",
            null,
            null,
            Map.<String, Object>of(),
            OffsetDateTime.parse("2026-05-01T00:00:00Z"),
            OffsetDateTime.parse("2026-05-01T00:00:00Z")
        );
    }
}
