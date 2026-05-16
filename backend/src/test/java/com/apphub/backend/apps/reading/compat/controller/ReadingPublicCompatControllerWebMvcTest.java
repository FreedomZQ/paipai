package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.apps.reading.compat.service.ReadingUsagePolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReadingPublicCompatController.class)
@Import(com.apphub.backend.common.filter.TraceFilter.class)
class ReadingPublicCompatControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReadingCompatService readingCompatService;

    @Test
    void bootstrapShouldExposeSupportedLocalesAndLearningTracks() throws Exception {
        when(readingCompatService.bootstrap()).thenReturn(
            new ReadingCompatService.BootstrapConfigView(
                "拍拍伴读",
                true,
                120,
                "zh-Hans",
                List.of("zh-Hans", "en"),
                List.of(
                    new ReadingCompatService.LearningTrackView("zh_to_en", "中文家庭学英语"),
                    new ReadingCompatService.LearningTrackView("en_to_zh", "English families learn Chinese")
                ),
                new ReadingCompatService.PaywallView(
                    "family_multi_child_lifetime",
                    false,
                    "解锁家庭伴读节奏",
                    "多孩子档案、更多拍读额度、云同步和周报历史，帮助家长长期看到孩子的进步。",
                    List.of("扣款以 Apple 确认弹窗为准", "云同步由家长主动开启"),
                    "权益以后端校验结果为准；价格与扣款以 Apple 确认弹窗为准。"
                ),
                new ReadingUsagePolicyService.UsagePolicyView(30, 7, "client_local", 24),
                "support@paipai.app",
                "https://www.paipai.app/support",
                "https://www.paipai.app/delete-account"
            )
        );

        mockMvc.perform(get("/api/v1/bootstrap/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.supportedLocales[0]").value("zh-Hans"))
            .andExpect(jsonPath("$.data.supportedLocales[1]").value("en"))
            .andExpect(jsonPath("$.data.learningTracks[0].code").value("zh_to_en"))
            .andExpect(jsonPath("$.data.paywall.headline").value("解锁家庭伴读节奏"))
            .andExpect(jsonPath("$.data.paywall.trustBullets[0]").value("扣款以 Apple 确认弹窗为准"))
            .andExpect(jsonPath("$.data.usagePolicy.retentionDays").value(30))
            .andExpect(jsonPath("$.data.usagePolicy.recentSummaryDays").value(7));
    }

    @Test
    void plansShouldReturnFreeAndFamilyWithDynamicFields() throws Exception {
        when(readingCompatService.plans()).thenReturn(List.of(
            new ReadingCompatService.PlanView(
                "free", "免费版", 1, 3, 20, false, false, null, false, "¥0", null, null,
                false, List.of("zh-Hans", "en"), List.of("zh_to_en", "en_to_zh")
            ),
            new ReadingCompatService.PlanView(
                "family_multi_child_lifetime", "家庭多孩子终身版", 5, 50, 800, true, true,
                "com.paipai.readalong.family.multi_child.lifetime", true, "¥68", "¥98", "一次开通",
                true, List.of("zh-Hans", "en"), List.of("zh_to_en", "en_to_zh")
            )
        ));

        mockMvc.perform(get("/api/v1/plans"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code").value("free"))
            .andExpect(jsonPath("$.data[1].code").value("family_multi_child_lifetime"))
            .andExpect(jsonPath("$.data[1].historyEnabled").value(true))
            .andExpect(jsonPath("$.data[1].supportedLocales[0]").value("zh-Hans"));
    }
    @Test
    void legalDocsShouldAbsolutizeRelativePaths() throws Exception {
        when(readingCompatService.legalDocs()).thenReturn(List.of(
            new ReadingCompatService.LegalDocView("privacy", "zh-Hans", "/legal/privacy-policy.html"),
            new ReadingCompatService.LegalDocView("terms", "zh-Hans", "/legal/terms-of-service.html"),
            new ReadingCompatService.LegalDocView("child_data", "zh-Hans", "/legal/child-data.html")
        ));

        mockMvc.perform(get("/api/v1/legal/docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[0].locale").value("zh-Hans"))
            .andExpect(jsonPath("$.data[0].url").value("http://localhost/legal/privacy-policy.html"))
            .andExpect(jsonPath("$.data[1].url").value("http://localhost/legal/terms-of-service.html"))
            .andExpect(jsonPath("$.data[2].type").value("child_data"))
            .andExpect(jsonPath("$.data[2].url").value("http://localhost/legal/child-data.html"));
    }

}
