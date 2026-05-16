package com.apphub.backend.apps.saving.controller;

import com.apphub.backend.apps.saving.service.SavingAccountDeletionService;
import com.apphub.backend.apps.saving.service.SavingRequestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * saving 账号删除入口测试。
 *
 * 中文说明：App Store 审核要求使用 Apple 登录的 App 必须在 App 内提供账号删除入口。
 * 这里固定 `/v1/account DELETE` 的兼容路由和响应语义，防止后续多 APP 共用后端时误删或误改该 P0 能力。
 */
@WebMvcTest(SavingAccountController.class)
class SavingAccountControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SavingRequestSupport requestSupport;

    @MockBean
    private SavingAccountDeletionService deletionService;

    @Test
    void deleteCurrentAccountShouldReturnCompatibilityEnvelope() throws Exception {
        when(requestSupport.requireUserId(any())).thenReturn(202L);
        when(requestSupport.requestId()).thenReturn("req-test");
        when(deletionService.deleteCurrentSavingAccount(202L)).thenReturn(Map.of(
            "success", true,
            "deleted", true,
            "message", "账号删除已完成",
            "providerTokenRevokedCount", 1
        ));

        mockMvc.perform(delete("/v1/account")
                .header("Authorization", "Bearer token-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.success").value(true))
            .andExpect(jsonPath("$.data.deleted").value(true))
            .andExpect(jsonPath("$.data.providerTokenRevokedCount").value(1));
    }
}
