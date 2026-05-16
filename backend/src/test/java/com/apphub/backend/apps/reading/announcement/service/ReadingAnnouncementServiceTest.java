package com.apphub.backend.apps.reading.announcement.service;

import com.apphub.backend.apps.reading.domain.entity.ReadingAnnouncementEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingAnnouncementMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 针对 reading 公告服务的测试。
 * 用于验证公告时间窗、近 30 天返回逻辑，以及 scene / locale / version / plan 过滤规则，
 * 避免客户端弹出过期公告或展示不属于当前用户上下文的公告。
 */
class ReadingAnnouncementServiceTest {

    @Test
    void listRecentShouldMarkActiveAnnouncements() {
        ReadingAnnouncementMapper mapper = mock(ReadingAnnouncementMapper.class);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ReadingAnnouncementEntity active = announcement("uuid-active", "当前公告", now.minusDays(1), now.plusDays(2));
        ReadingAnnouncementEntity expiredButRecent = announcement("uuid-recent", "历史公告", now.minusDays(10), now.minusDays(2));

        when(mapper.selectRecentPublished(eq("paipai_readingcompanion"), any(), any()))
            .thenReturn(List.of(active, expiredButRecent));

        ReadingAnnouncementService service = new ReadingAnnouncementService(mapper);
        var items = service.listRecent(30);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).announcementUuid()).isEqualTo("uuid-active");
        assertThat(items.get(0).active()).isTrue();
        assertThat(items.get(1).announcementUuid()).isEqualTo("uuid-recent");
        assertThat(items.get(1).active()).isFalse();
    }

    @Test
    void listRecentShouldApplySceneLocalePlanAndVersionFilters() {
        ReadingAnnouncementMapper mapper = mock(ReadingAnnouncementMapper.class);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ReadingAnnouncementEntity matching = announcement("uuid-match", "匹配公告", now.minusHours(1), now.plusDays(1));
        matching.setTriggerScene("app_launch");
        matching.setTargetLocale("zh-Hans");
        matching.setTargetPlanCode("free");
        matching.setTargetMinAppVersion("1.2.0");
        matching.setTargetMaxAppVersion("1.9.9");
        matching.setPriority(10);
        matching.setMaxDisplayCount(3);
        matching.setMinIntervalSeconds(3600);

        ReadingAnnouncementEntity wrongScene = announcement("uuid-scene", "错误 scene", now.minusHours(1), now.plusDays(1));
        wrongScene.setTriggerScene("paywall");

        ReadingAnnouncementEntity wrongLocale = announcement("uuid-locale", "错误 locale", now.minusHours(1), now.plusDays(1));
        wrongLocale.setTargetLocale("en");

        ReadingAnnouncementEntity wrongPlan = announcement("uuid-plan", "错误 plan", now.minusHours(1), now.plusDays(1));
        wrongPlan.setTargetPlanCode("family_multi_child_lifetime");

        ReadingAnnouncementEntity requiresNewerVersion = announcement("uuid-newer", "需要更新版本", now.minusHours(1), now.plusDays(1));
        requiresNewerVersion.setTargetMinAppVersion("2.0.0");

        ReadingAnnouncementEntity cappedAtOlderVersion = announcement("uuid-older", "仅旧版本", now.minusHours(1), now.plusDays(1));
        cappedAtOlderVersion.setTargetMaxAppVersion("1.0.0");

        when(mapper.selectRecentPublished(eq("paipai_readingcompanion"), any(), any()))
            .thenReturn(List.of(matching, wrongScene, wrongLocale, wrongPlan, requiresNewerVersion, cappedAtOlderVersion));

        ReadingAnnouncementService service = new ReadingAnnouncementService(mapper);
        var items = service.listRecent(30, "app_launch", "zh-Hans-CN", "1.5.0", "free");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).announcementUuid()).isEqualTo("uuid-match");
        assertThat(items.get(0).active()).isTrue();
        assertThat(items.get(0).triggerScene()).isEqualTo("app_launch");
        assertThat(items.get(0).priority()).isEqualTo(10);
        assertThat(items.get(0).maxDisplayCount()).isEqualTo(3);
        assertThat(items.get(0).minIntervalSeconds()).isEqualTo(3600);
    }

    @Test
    void listRecentShouldReturnSupportCenterAnnouncementsForSupportScene() {
        ReadingAnnouncementMapper mapper = mock(ReadingAnnouncementMapper.class);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ReadingAnnouncementEntity supportCenter = announcement("uuid-support", "支持中心公告", now.minusHours(1), now.plusDays(1));
        supportCenter.setTriggerScene("support_center");
        supportCenter.setTargetLocale("zh-Hans");
        supportCenter.setTargetPlanCode("premium_lite_monthly");

        ReadingAnnouncementEntity appLaunch = announcement("uuid-launch", "启动公告", now.minusHours(1), now.plusDays(1));
        appLaunch.setTriggerScene("app_launch");

        when(mapper.selectRecentPublished(eq("paipai_readingcompanion"), any(), any()))
            .thenReturn(List.of(supportCenter, appLaunch));

        ReadingAnnouncementService service = new ReadingAnnouncementService(mapper);
        var items = service.listRecent(30, "support_center", "zh-Hans", "1.5.0", "premium_lite_monthly");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).announcementUuid()).isEqualTo("uuid-support");
        assertThat(items.get(0).triggerScene()).isEqualTo("support_center");
    }

    @Test
    void listRecentShouldUseRequestedAppCodeAndFilterActiveOnly() {
        ReadingAnnouncementMapper mapper = mock(ReadingAnnouncementMapper.class);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ReadingAnnouncementEntity active = announcement("uuid-active", "当前公告", now.minusHours(1), now.plusDays(1));
        ReadingAnnouncementEntity expired = announcement("uuid-expired", "过期公告", now.minusDays(5), now.minusDays(1));

        when(mapper.selectRecentPublished(eq("fitmystery"), any(), any()))
            .thenReturn(List.of(active, expired));

        ReadingAnnouncementService service = new ReadingAnnouncementService(mapper);
        var items = service.listRecent("fitmystery", 7, "app_launch", null, null, null, true);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).announcementUuid()).isEqualTo("uuid-active");
        assertThat(items.get(0).active()).isTrue();
    }

    private ReadingAnnouncementEntity announcement(String uuid, String title, OffsetDateTime startAt, OffsetDateTime endAt) {
        ReadingAnnouncementEntity entity = new ReadingAnnouncementEntity();
        entity.setAnnouncementUuid(uuid);
        entity.setTitle(title);
        entity.setContent("内容-" + uuid);
        entity.setStatus("published");
        entity.setVisibleStartAt(startAt);
        entity.setVisibleEndAt(endAt);
        entity.setUpdatedAt(startAt);
        entity.setAnnouncementType("info");
        entity.setPriority(0);
        entity.setTriggerScene("app_launch");
        entity.setDismissible(true);
        return entity;
    }
}
