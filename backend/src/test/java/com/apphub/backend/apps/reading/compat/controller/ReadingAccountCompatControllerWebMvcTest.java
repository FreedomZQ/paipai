package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCloudUsageService;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReadingAccountCompatController.class)
class ReadingAccountCompatControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReadingAuthenticatedUserResolver readingAuthenticatedUserResolver;

    @MockBean
    private ReadingCompatService readingCompatService;

    @MockBean
    private ReadingCloudUsageService readingCloudUsageService;

    @Test
    void homeSummaryShouldReturnAggregatedUsageShape() throws Exception {
        when(readingAuthenticatedUserResolver.require(any())).thenReturn(readingUser());
        when(readingCompatService.homeSummary(any())).thenReturn(
            new ReadingCompatService.HomeSummaryView(
                new ReadingCompatService.HomeChildView("child-a", "小宝", "age_5_7", "🧸"),
                2,
                5,
                List.of(),
                new ReadingCompatService.DailyQuotaView("2026-04-22", 12, 4, 8, 600, 10, 590),
                new ReadingCompatService.AccountEntitlementView(
                    "premium_lite_monthly",
                    "轻量月付版",
                    "premium_lite_access",
                    12,
                    10,
                    3,
                    120,
                    2,
                    1,
                    true,
                    true,
                    true,
                    "2026-05-22T00:00:00Z",
                    true,
                    true,
                    "per_child",
                    "family",
                    4,
                    true,
                    false,
                    true,
                    "backend_sys_billing",
                    Map.<String, Object>of("allowed", true)
                ),
                new ReadingCompatService.LearningGrowthView(1, 3, 4, "这周已经有复习记录了，继续保持短句高频回看。"),
                List.of(
                    new ReadingCompatService.ChildProgressView("child-a", "小宝", "age_5_7", "🧸", 4, 7, 2),
                    new ReadingCompatService.ChildProgressView("child-b", "大宝", "age_8_10", "🚀", 1, 2, 0)
                )
            )
        );

        mockMvc.perform(get("/api/v1/account/me/home-summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentChild.childId").value("child-a"))
            .andExpect(jsonPath("$.data.todayCompletedCount").value(2))
            .andExpect(jsonPath("$.data.reviewDueCount").value(5))
            .andExpect(jsonPath("$.data.growth.weeklyActiveDays").value(3))
            .andExpect(jsonPath("$.data.growth.weeklyReviewCount").value(4))
            .andExpect(jsonPath("$.data.childSummaries[0].reviewDueCount").value(4))
            .andExpect(jsonPath("$.data.childSummaries[0].savedCardCount").value(7))
            .andExpect(jsonPath("$.data.childSummaries[0].todayCompletedCount").value(2))
            .andExpect(jsonPath("$.data.childSummaries[1].reviewDueCount").value(1))
            .andExpect(jsonPath("$.data.childSummaries[1].savedCardCount").value(2))
            .andExpect(jsonPath("$.data.childSummaries[1].todayCompletedCount").value(0));
    }

    private ReadingAuthenticatedUser readingUser() {
        SysAuthSessionEntity session = new SysAuthSessionEntity();
        session.setId(11L);
        session.setAppCode("paipai_readingcompanion");
        session.setUserId(101L);
        session.setSessionSource("apple");
        session.setStatus("active");
        session.setExpiresAt(OffsetDateTime.parse("2026-05-16T00:00:00Z"));
        SysUserEntity user = new SysUserEntity();
        user.setId(101L);
        user.setAppCode("paipai_readingcompanion");
        user.setUserType("formal");
        user.setDisplayName("Apple User");
        user.setStatus("active");
        return new ReadingAuthenticatedUser(session, user, "token-123");
    }
}
