package com.apphub.backend.sys.compensation.controller;

import com.apphub.backend.sys.compensation.model.CompensationCodeView;
import com.apphub.backend.sys.compensation.service.SysCompensationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SysCompensationApplicationController.class)
@TestPropertySource(properties = "backend.apps.paipai_readingcompanion.admin.configToken=test-admin-token")
class SysCompensationApplicationControllerWebMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SysCompensationService compensationService;

    @Test
    void generateShouldAcceptAdminConfigTokenAndReturnCompensationCode() throws Exception {
        when(compensationService.createCode(eq("paipai_readingcompanion"), isNull(), any()))
            .thenReturn(new CompensationCodeView(
                123L,
                "paipai_readingcompanion",
                "PP-ABCDE-FGHJK-MNPQR",
                "usage_credit",
                null,
                null,
                "cloud_tts",
                10,
                30,
                OffsetDateTime.parse("2026-06-15T00:00:00Z"),
                1,
                0,
                "unused",
                null,
                null,
                null,
                Map.of("note", "补偿事由：云端朗读服务异常补偿"),
                null,
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                OffsetDateTime.parse("2026-05-16T00:00:00Z")
            ));

        mockMvc.perform(post("/api/v1/system/compensation-applications")
                .queryParam("appCode", "paipai_readingcompanion")
                .header("X-Admin-Config-Token", "test-admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "云端朗读服务异常补偿",
                      "remark": "工单 TICKET-20260516-0001",
                      "benefitKey": "cloud_tts",
                      "compensationCount": 10,
                      "validDays": 30
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.compensationCode").value("PP-ABCDE-FGHJK-MNPQR"))
            .andExpect(jsonPath("$.data.serviceType").value("cloud_tts"))
            .andExpect(jsonPath("$.data.grantCount").value(10));
    }

    @Test
    void generateShouldRejectInvalidAdminConfigToken() throws Exception {
        mockMvc.perform(post("/api/v1/system/compensation-applications")
                .queryParam("appCode", "paipai_readingcompanion")
                .header("X-Admin-Config-Token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "云端朗读服务异常补偿",
                      "benefitKey": "cloud_tts",
                      "compensationCount": 10,
                      "validDays": 30
                    }
                    """))
            .andExpect(status().isForbidden());
    }
}
