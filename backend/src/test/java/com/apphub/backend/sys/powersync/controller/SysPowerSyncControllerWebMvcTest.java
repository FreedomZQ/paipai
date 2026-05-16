package com.apphub.backend.sys.powersync.controller;

import com.apphub.backend.sys.powersync.model.PowerSyncBootstrapView;
import com.apphub.backend.sys.powersync.model.PowerSyncRebuildView;
import com.apphub.backend.sys.powersync.model.PowerSyncTokenClaimsView;
import com.apphub.backend.sys.powersync.model.PowerSyncTokenView;
import com.apphub.backend.sys.powersync.service.SysPowerSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SysPowerSyncController.class)
class SysPowerSyncControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SysPowerSyncService sysPowerSyncService;

    @Test
    void bootstrapShouldReturnSyncBootstrapView() throws Exception {
        when(sysPowerSyncService.bootstrap(eq("paipai_readingcompanion"), any(), any(), any()))
            .thenReturn(new PowerSyncBootstrapView(
                "paipai_readingcompanion",
                "install-1",
                true,
                false,
                "https://sync.example.com",
                "2026-04-21T00:30:00Z",
                false,
                "2026-04-21T00:00:00Z"
            ));

        mockMvc.perform(post("/api/v1/powersync/paipai_readingcompanion/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "installationId": "install-1",
                      "clientPlatform": "ios",
                      "cloudSyncEnabled": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.installationId").value("install-1"))
            .andExpect(jsonPath("$.data.cloudSyncEnabled").value(true));
    }

    @Test
    void tokenShouldReturnClaims() throws Exception {
        when(sysPowerSyncService.issueToken(eq("paipai_readingcompanion"), any(), any(), any()))
            .thenReturn(new PowerSyncTokenView(
                "https://sync.example.com",
                "jwt-token",
                "2026-04-21T00:30:00Z",
                new PowerSyncTokenClaimsView("paipai_readingcompanion", 101L, "install-1")
            ));

        mockMvc.perform(post("/api/v1/powersync/paipai_readingcompanion/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "installationId": "install-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").value("jwt-token"))
            .andExpect(jsonPath("$.data.claims.userId").value(101));
    }

    @Test
    void rebuildShouldReturnAcceptedFlag() throws Exception {
        when(sysPowerSyncService.requestRebuild(eq("paipai_readingcompanion"), any(), any(), any()))
            .thenReturn(new PowerSyncRebuildView("install-1", true, "Rebuild has been scheduled."));

        mockMvc.perform(post("/api/v1/powersync/paipai_readingcompanion/rebuild")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "installationId": "install-1",
                      "reason": "user_requested"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.shouldRebuild").value(true));
    }
}
