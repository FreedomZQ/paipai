package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCloudUsageService;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.apps.reading.provider.ReadingBailianTtsProvider;
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
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @MockBean
    private ReadingCloudUsageService cloudUsageService;
    @MockBean
    private ReadingBailianTtsProvider bailianTtsProvider;

    @Test
    void speakShouldReturnCloudAudioWhenQuotaAllowed() throws Exception {
        when(userResolver.require(any())).thenReturn(authenticatedUser());
        when(cloudUsageService.ensureQuota(anyLong(), anyString()))
            .thenReturn(new ReadingCloudUsageService.CloudUsageDecision(true, ReadingCloudUsageService.CLOUD_TTS, 5, null, null, List.of("upgrade")));
        when(bailianTtsProvider.synthesize(anyString(), anyString(), anyFloat()))
            .thenReturn(new ReadingBailianTtsProvider.TtsProviderResult(true, "alibaba_bailian", "cosyvoice-v1", "cn", "YmFzZTY0", "audio/mpeg", "hello", "en-US", 0.45f, null));
        when(cloudUsageService.consume(anyLong(), anyString()))
            .thenReturn(new ReadingCloudUsageService.CloudUsageDecision(true, ReadingCloudUsageService.CLOUD_TTS, 4, null, null, List.of("upgrade")));
        when(readingCompatService.buildCloudSpeechResult(any(), any()))
            .thenReturn(new ReadingCompatService.CloudSpeechReceipt(true, "succeeded", 4, "alibaba_bailian", "cosyvoice-v1", "YmFzZTY0", "audio/mpeg", "hello", "en-US", 0.45f, null, null, List.of("upgrade")));

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
            .andExpect(jsonPath("$.data.serviceStatus").value("succeeded"))
            .andExpect(jsonPath("$.data.audioBase64").value("YmFzZTY0"));

        verify(cloudUsageService).consume(anyLong(), anyString());
    }

    @Test
    void speakShouldReturnQuotaBlockedWhenNoCreditsRemain() throws Exception {
        when(userResolver.require(any())).thenReturn(authenticatedUser());
        when(cloudUsageService.ensureQuota(anyLong(), anyString()))
            .thenReturn(new ReadingCloudUsageService.CloudUsageDecision(false, ReadingCloudUsageService.CLOUD_TTS, 0, "quota", "used up", List.of("upgrade")));
        when(readingCompatService.buildCloudSpeechQuotaBlocked(any(), any()))
            .thenReturn(new ReadingCompatService.CloudSpeechReceipt(false, "quota_blocked", 0, "cloud_service", "quota_blocked", null, null, "hello", "en-US", 0.45f, "quota", "used up", List.of("upgrade")));

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
            .andExpect(jsonPath("$.data.serviceStatus").value("quota_blocked"));

        verify(bailianTtsProvider, never()).synthesize(anyString(), anyString(), anyFloat());
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
