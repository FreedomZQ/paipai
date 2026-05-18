package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReadingTtsCompatController.class)
@Import(com.apphub.backend.common.filter.TraceFilter.class)
class ReadingTtsCompatControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReadingAuthenticatedUserResolver userResolver;
    @MockBean
    private ReadingCompatService readingCompatService;

    @Test
    void speakShouldReturnCloudDisabledResponse() throws Exception {
        when(userResolver.require(any())).thenReturn(authenticatedUser());
        when(readingCompatService.buildCloudSpeechUnavailable(any(), any()))
            .thenReturn(new ReadingCompatService.CloudSpeechReceipt(false, "not_configured", 0, "cloud_service", "not_configured", null, null, null, "en-US", 0.45f, "云端朗读暂未启用", "当前版本仅使用设备端朗读。", List.of("使用设备端朗读")));

        mockMvc.perform(post("/api/v1/tts/speak")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "text":"hello",
                      "languageCode":"en-US",
                      "rate":0.45
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.serviceStatus").value("not_configured"))
            .andExpect(jsonPath("$.data.audioBase64").doesNotExist());
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
