package com.apphub.backend.sys.powersync.controller;

import com.apphub.backend.sys.powersync.model.PowerSyncAcceptedItem;
import com.apphub.backend.sys.powersync.model.PowerSyncRejectedItem;
import com.apphub.backend.sys.powersync.model.PowerSyncUploadResult;
import com.apphub.backend.sys.powersync.service.SysPowerSyncUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SysPowerSyncUploadController.class)
class SysPowerSyncUploadControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SysPowerSyncUploadService uploadService;

    @Test
    void uploadShouldReturnAcceptedAndRejectedLists() throws Exception {
        when(uploadService.upload(eq("paipai_readingcompanion"), any(), any(), any()))
            .thenReturn(new PowerSyncUploadResult(
                List.of(new PowerSyncAcceptedItem("review_card", "card-1", "2026-04-21T00:00:00Z")),
                List.of(new PowerSyncRejectedItem("child_profile", "child-9", "CHILD_LIMIT_EXCEEDED", "limit exceeded"))
            ));

        mockMvc.perform(post("/api/v1/powersync/paipai_readingcompanion/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "installationId": "install-1",
                      "changes": [
                        {
                          "entityType": "review_card",
                          "operation": "upsert",
                          "entityId": "card-1",
                          "clientUpdatedAt": "2026-04-21T00:00:00Z",
                          "payload": {"id": "card-1"}
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accepted[0].entityId").value("card-1"))
            .andExpect(jsonPath("$.data.rejected[0].reasonCode").value("CHILD_LIMIT_EXCEEDED"));
    }
}
