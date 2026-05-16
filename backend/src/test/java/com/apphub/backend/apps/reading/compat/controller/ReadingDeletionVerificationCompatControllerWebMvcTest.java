package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import com.apphub.backend.sys.auth.service.SysEmailVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReadingDeletionVerificationCompatController.class)
@Import(com.apphub.backend.common.filter.TraceFilter.class)
class ReadingDeletionVerificationCompatControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReadingAuthenticatedUserResolver userResolver;
    @MockBean
    private ReadingCompatService readingCompatService;

    @Test
    void requestDeletionCodeShouldReturnTicketForCurrentAccountEmail() throws Exception {
        when(userResolver.require(any())).thenReturn(authenticatedUser());
        when(readingCompatService.requestDeletionCode(any(), any()))
            .thenReturn(new SysEmailVerificationService.EmailVerificationTicketView(
                "p***t@example.com",
                "delete_account",
                "2026-04-20T16:00:00Z",
                "logged_only",
                "654321",
                "debug code emitted"
            ));

        mockMvc.perform(post("/api/v1/account/deletion/request-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sceneCode").value("delete_account"))
            .andExpect(jsonPath("$.data.debugCode").value("654321"));
    }

    @Test
    void confirmDeletionShouldReturnDeletionReceipt() throws Exception {
        when(userResolver.require(any())).thenReturn(authenticatedUser());
        when(readingCompatService.confirmDeletionByCode(any(), anyString(), any(), anyBoolean(), any()))
            .thenReturn(new ReadingCompatService.DeletionRequestResponse(
                "req-1",
                "completed",
                "completed",
                "2026-04-20T15:00:00Z",
                "2026-04-20T15:00:01Z",
                "2026-04-20T15:00:02Z",
                null,
                "email",
                true,
                false,
                "not_applicable",
                "ok",
                1,
                1,
                1,
                2,
                3,
                null,
                null,
                "deleted"
            ));

        mockMvc.perform(post("/api/v1/account/deletion/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code":"123456",
                      "confirmDataDeletion":true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.requestId").value("req-1"))
            .andExpect(jsonPath("$.data.status").value("completed"));
    }

    private ReadingAuthenticatedUser authenticatedUser() {
        SysUserEntity user = new SysUserEntity();
        user.setId(101L);
        user.setAppCode("paipai_readingcompanion");
        user.setStatus("active");

        SysAuthSessionEntity session = new SysAuthSessionEntity();
        session.setUserId(101L);
        session.setAppCode("paipai_readingcompanion");
        session.setSessionSource("email");

        return new ReadingAuthenticatedUser(session, user, "token");
    }
}
