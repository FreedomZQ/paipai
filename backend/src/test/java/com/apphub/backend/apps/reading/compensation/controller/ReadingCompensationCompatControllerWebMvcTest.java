package com.apphub.backend.apps.reading.compensation.controller;

import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.sys.auth.service.SysAuthDataService;
import com.apphub.backend.sys.compensation.model.CompensationRedeemResultView;
import com.apphub.backend.sys.compensation.service.SysCompensationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReadingCompensationCompatController.class)
class ReadingCompensationCompatControllerWebMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SysAuthDataService authDataService;
    @MockBean
    private SysCompensationService compensationService;
    @MockBean
    private ReadingCompatService readingCompatService;

    @Test
    void redeemShouldReturnAccountState() throws Exception {
        when(authDataService.identityByProvider(
            eq("paipai_readingcompanion"),
            eq("anonymous_device"),
            eq("ios-test-device-1")
        )).thenReturn(new com.apphub.backend.sys.auth.entity.SysUserIdentityEntity(
            1L,
            "paipai_readingcompanion",
            101L,
            "anonymous_device",
            "ios-test-device-1",
            null,
            null,
            null,
            "active",
            "{}",
            OffsetDateTime.now(),
            OffsetDateTime.now()
        ));
        when(compensationService.redeem("paipai_readingcompanion", 101L, "ios-test-device-1", "PP-ABCDE-FGHJK-MNPQR"))
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
        when(readingCompatService.accountState(101L, "anonymous_device")).thenReturn(new ReadingCompatService.AccountStateView(
            "101",
            "anonymous_device",
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
                .header("X-Paipai-Anonymous-Id", "ios-test-device-1")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                    {"compensationCode":"PP-ABCDE-FGHJK-MNPQR"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.compensationCode").value("PP-ABCDE-FGHJK-MNPQR"))
            .andExpect(jsonPath("$.data.accountState.signInProvider").value("anonymous_device"))
            .andExpect(jsonPath("$.data.accountState.entitlement.planCode").value("standard_single_child"));
    }

    @Test
    void redeemShouldNotRequireLoginForAnonymousDevice() throws Exception {
        when(authDataService.identityByProvider(
            eq("paipai_readingcompanion"),
            eq("anonymous_device"),
            eq("ios-test-device")
        )).thenReturn(new com.apphub.backend.sys.auth.entity.SysUserIdentityEntity(
            1L,
            "paipai_readingcompanion",
            202L,
            "anonymous_device",
            "ios-test-device",
            null,
            null,
            null,
            "active",
            "{}",
            OffsetDateTime.now(),
            OffsetDateTime.now()
        ));
        when(compensationService.redeem("paipai_readingcompanion", 202L, "ios-test-device", "PP-ABCDE-FGHJK-MNPQR"))
            .thenReturn(new CompensationRedeemResultView(
                "PP-ABCDE-FGHJK-MNPQR",
                "applied",
                "usage_credit",
                "补偿 3 次 本地朗读",
                null,
                null,
                "local_tts",
                3,
                "2026-06-01T00:00:00Z",
                "2026-05-01T00:00:00Z",
                "补偿成功",
                null
            ));
        when(readingCompatService.accountState(202L, "anonymous_device")).thenReturn(new ReadingCompatService.AccountStateView(
            "202",
            "anonymous_device",
            new ReadingCompatService.AccountEntitlementView(
                "free",
                "免费版",
                "basic_access",
                10,
                13,
                1,
                300,
                1,
                0,
                true,
                false,
                null,
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
            new ReadingCompatService.DailyQuotaView("2026-05-01", 10, 1, 9, 13, 1, 12)
        ));

        mockMvc.perform(post("/api/v1/account/compensation/redeem")
                .header("X-Paipai-Anonymous-Id", "ios-test-device")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                    {"compensationCode":"PP-ABCDE-FGHJK-MNPQR"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("applied"))
            .andExpect(jsonPath("$.data.accountState.signInProvider").value("anonymous_device"));
    }

    @Test
    void redeemShouldBeGoneInLocalOnlyLaunchMode() throws Exception {
        when(readingCompatService.isLocalOnlyLaunchMode()).thenReturn(true);

        mockMvc.perform(post("/api/v1/account/compensation/redeem")
                .header("X-Paipai-Anonymous-Id", "ios-test-device")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                    {"compensationCode":"PP-ABCDE-FGHJK-MNPQR"}
                    """))
            .andExpect(status().isGone());

        verify(compensationService, never()).redeem(any(), any(), any(), any());
    }
}
