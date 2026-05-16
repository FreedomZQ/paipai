package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.announcement.service.ReadingAnnouncementService;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 针对 reading 公告兼容控制器的 WebMvc 测试。
 * 用于验证公告接口路由、鉴权要求和返回结构，避免后续兼容层调整时把公告能力误伤。
 */
@WebMvcTest(ReadingAnnouncementCompatController.class)
class ReadingAnnouncementCompatControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReadingAuthenticatedUserResolver readingAuthenticatedUserResolver;

    @MockBean
    private ReadingAnnouncementService readingAnnouncementService;

    @Test
    void announcementsShouldReturnRecentItems() throws Exception {
        when(readingAuthenticatedUserResolver.require(any())).thenReturn(readingUser());
        when(readingAnnouncementService.listRecent(30))
            .thenReturn(List.of(
                new ReadingAnnouncementService.AnnouncementView(
                    "uuid-1",
                    "权益升级通知",
                    "新的家庭多孩子权益已经开放。",
                    "2026-04-17T00:00:00Z",
                    "2026-04-24T00:00:00Z",
                    true,
                    "2026-04-17T00:00:00Z"
                )
            ));

        mockMvc.perform(get("/api/v1/announcements").param("windowDays", "30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].announcementUuid").value("uuid-1"))
            .andExpect(jsonPath("$.data[0].title").value("权益升级通知"))
            .andExpect(jsonPath("$.data[0].active").value(true));
    }

    @Test
    void announcementsShouldForwardAudienceFilters() throws Exception {
        when(readingAuthenticatedUserResolver.require(any())).thenReturn(readingUser());
        when(readingAnnouncementService.listRecent("paipai_readingcompanion", 14, "app_launch", "zh-Hans", "1.5.0", "free", true))
            .thenReturn(List.of(
                new ReadingAnnouncementService.AnnouncementView(
                    "uuid-filtered",
                    "定向公告",
                    "仅当前场景、语种、版本和套餐可见。",
                    "2026-04-17T00:00:00Z",
                    "2026-04-24T00:00:00Z",
                    true,
                    "2026-04-17T00:00:00Z",
                    "update",
                    9,
                    "https://www.paipai.app/help",
                    "了解更多",
                    true,
                    2,
                    3600,
                    "app_launch"
                )
            ));

        mockMvc.perform(get("/api/v1/announcements")
                .param("windowDays", "14")
                .param("scene", "app_launch")
                .param("locale", "zh-Hans")
                .param("appVersion", "1.5.0")
                .param("planCode", "free")
                .param("activeOnly", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].announcementUuid").value("uuid-filtered"))
            .andExpect(jsonPath("$.data[0].type").value("update"))
            .andExpect(jsonPath("$.data[0].priority").value(9))
            .andExpect(jsonPath("$.data[0].maxDisplayCount").value(2))
            .andExpect(jsonPath("$.data[0].minIntervalSeconds").value(3600))
            .andExpect(jsonPath("$.data[0].triggerScene").value("app_launch"));
    }

    @Test
    void announcementsShouldForwardSupportCenterScene() throws Exception {
        when(readingAuthenticatedUserResolver.require(any())).thenReturn(readingUser());
        when(readingAnnouncementService.listRecent("paipai_readingcompanion", 30, "support_center", "zh-Hans", "1.5.0", "premium_lite_monthly", false))
            .thenReturn(List.of(
                new ReadingAnnouncementService.AnnouncementView(
                    "uuid-support",
                    "支持中心公告",
                    "仅支持中心展示。",
                    "2026-04-17T00:00:00Z",
                    "2026-04-24T00:00:00Z",
                    true,
                    "2026-04-17T00:00:00Z",
                    "info",
                    3,
                    null,
                    null,
                    true,
                    1,
                    0,
                    "support_center"
                )
            ));

        mockMvc.perform(get("/api/v1/announcements")
                .param("windowDays", "30")
                .param("scene", "support_center")
                .param("locale", "zh-Hans")
                .param("appVersion", "1.5.0")
                .param("planCode", "premium_lite_monthly"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].announcementUuid").value("uuid-support"))
            .andExpect(jsonPath("$.data[0].triggerScene").value("support_center"));
    }

    @Test
    void announcementsShouldForwardAppCodeForSharedBackendEndpoint() throws Exception {
        when(readingAuthenticatedUserResolver.require(any())).thenReturn(readingUser());
        when(readingAnnouncementService.listRecent("paipai_readingcompanion", 7, "app_launch", null, null, null, true))
            .thenReturn(List.of(
                new ReadingAnnouncementService.AnnouncementView(
                    "reading-notice",
                    "拍拍伴读通知",
                    "后端按 appCode 返回拍拍伴读通知。",
                    "2026-04-17T00:00:00Z",
                    "2026-06-24T00:00:00Z",
                    true,
                    "2026-04-17T00:00:00Z"
                )
            ));

        mockMvc.perform(get("/api/v1/announcements")
                .param("appCode", "paipai_readingcompanion")
                .param("windowDays", "7")
                .param("scene", "app_launch")
                .param("activeOnly", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].announcementUuid").value("reading-notice"))
            .andExpect(jsonPath("$.data[0].active").value(true));
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
