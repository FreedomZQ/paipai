package com.apphub.backend.apps.reading.compensation.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import com.apphub.backend.sys.compensation.model.CompensationRedeemResultView;
import com.apphub.backend.sys.compensation.service.SysCompensationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReadingCompensationCompatController.class)
class ReadingCompensationCompatControllerWebMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReadingAuthenticatedUserResolver userResolver;
    @MockBean
    private SysCompensationService compensationService;
    @MockBean
    private ReadingCompatService readingCompatService;

    @Test
    void redeemShouldReturnAccountState() throws Exception {
        ReadingAuthenticatedUser user = new ReadingAuthenticatedUser(
            new SysAuthSessionEntity(),
            new SysUserEntity(101L, "paipai_readingcompanion", "parent", "Parent", "active", null, null),
            "token-123"
        );
        when(userResolver.require(org.mockito.ArgumentMatchers.any())).thenReturn(user);
        when(compensationService.redeem("paipai_readingcompanion", 101L, "PP-ABCDE-FGHJK-MNPQR"))
            .thenReturn(new CompensationRedeemResultView(
                "PP-ABCDE-FGHJK-MNPQR",
                "applied",
                "plan",
                "补偿权益方案 标准版",
                "standard_single_child",
                "family_access",
                null,
                1,
                "2026-06-01T00:00:00Z",
                "2026-05-01T00:00:00Z",
                "补偿成功",
                null
            ));
        when(readingCompatService.accountState(user)).thenReturn(new ReadingCompatService.AccountStateView(
            "101",
            "apple",
            new ReadingCompatService.AccountEntitlementView(
                "standard_single_child",
                "标准版",
                "family_access",
                20,
                20,
                1,
                300,
                1,
                0,
                true,
                true,
                true,
                "2026-06-01T00:00:00Z",
                true,
                false,
                "single_child",
                "child",
                4,
                true,
                false,
                true,
                "backend_sys_billing",
                Map.of()
            ),
            new ReadingCompatService.DailyQuotaView("2026-05-01", 20, 1, 19, 20, 1, 19)
        ));

        mockMvc.perform(post("/api/v1/account/compensation/redeem")
                .header("Authorization", "Bearer token-123")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                    {"compensationCode":"PP-ABCDE-FGHJK-MNPQR"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.compensationCode").value("PP-ABCDE-FGHJK-MNPQR"))
            .andExpect(jsonPath("$.data.accountState.entitlement.planCode").value("standard_single_child"));
    }
}
