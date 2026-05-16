package com.apphub.backend.apps.reading.announcement.service;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.domain.entity.ReadingAnnouncementEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingAnnouncementMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
public class ReadingAnnouncementService {
    private static final String APP_CODE = ReadingAppModule.APP_CODE;

    private final ReadingAnnouncementMapper announcementMapper;

    public ReadingAnnouncementService(ReadingAnnouncementMapper announcementMapper) {
        this.announcementMapper = announcementMapper;
    }

    public List<AnnouncementView> listRecent(int windowDays) {
        return listRecent(APP_CODE, windowDays, null, null, null, null, false);
    }

    public List<AnnouncementView> listRecent(int windowDays, String scene, String locale, String appVersion, String planCode) {
        return listRecent(APP_CODE, windowDays, scene, locale, appVersion, planCode, false);
    }

    public List<AnnouncementView> listRecent(String appCode, int windowDays, String scene, String locale, String appVersion, String planCode, boolean activeOnly) {
        int safeDays = windowDays <= 0 ? 30 : Math.min(windowDays, 30);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime historyStart = now.minusDays(safeDays);
        OffsetDateTime futureEnd = now.plusDays(safeDays);
        return announcementMapper.selectRecentPublished(normalizeAppCode(appCode), futureEnd, historyStart).stream()
            .filter(item -> matchesScene(item, scene))
            .filter(item -> matchesLocale(item, locale))
            .filter(item -> matchesPlan(item, planCode))
            .filter(item -> matchesVersion(item, appVersion))
            .map(item -> toView(item, now))
            .filter(item -> !activeOnly || item.active())
            .toList();
    }

    private String normalizeAppCode(String appCode) {
        return appCode == null || appCode.isBlank() ? APP_CODE : appCode.trim();
    }

    private AnnouncementView toView(ReadingAnnouncementEntity entity, OffsetDateTime now) {
        boolean active = !entity.getVisibleStartAt().isAfter(now)
            && (entity.getVisibleEndAt() == null || !entity.getVisibleEndAt().isBefore(now));
        return new AnnouncementView(
            entity.getAnnouncementUuid(),
            entity.getTitle(),
            entity.getContent(),
            entity.getVisibleStartAt() == null ? null : entity.getVisibleStartAt().toString(),
            entity.getVisibleEndAt() == null ? null : entity.getVisibleEndAt().toString(),
            active,
            entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString(),
            entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString(),
            defaultIfBlank(entity.getAnnouncementType(), "info"),
            entity.getPriority() == null ? 0 : entity.getPriority(),
            entity.getActionUrl(),
            entity.getActionText(),
            entity.getDismissible() == null || entity.getDismissible(),
            entity.getMaxDisplayCount() == null ? 1 : entity.getMaxDisplayCount(),
            entity.getMinIntervalSeconds() == null ? 86400 : entity.getMinIntervalSeconds(),
            defaultIfBlank(entity.getTriggerScene(), "app_launch")
        );
    }

    private boolean matchesScene(ReadingAnnouncementEntity entity, String scene) {
        if (scene == null || scene.isBlank()) {
            return true;
        }
        String target = defaultIfBlank(entity.getTriggerScene(), "app_launch");
        return target.equalsIgnoreCase(scene.trim());
    }

    private boolean matchesLocale(ReadingAnnouncementEntity entity, String locale) {
        if (entity.getTargetLocale() == null || entity.getTargetLocale().isBlank()) {
            return true;
        }
        if (locale == null || locale.isBlank()) {
            return true;
        }
        String normalizedTarget = entity.getTargetLocale().trim().toLowerCase(Locale.ROOT);
        String normalizedLocale = locale.trim().toLowerCase(Locale.ROOT);
        return normalizedLocale.equals(normalizedTarget)
            || normalizedLocale.startsWith(normalizedTarget)
            || normalizedTarget.startsWith(normalizedLocale);
    }

    private boolean matchesPlan(ReadingAnnouncementEntity entity, String planCode) {
        if (entity.getTargetPlanCode() == null || entity.getTargetPlanCode().isBlank()) {
            return true;
        }
        if (planCode == null || planCode.isBlank()) {
            return true;
        }
        return entity.getTargetPlanCode().trim().equalsIgnoreCase(planCode.trim());
    }

    private boolean matchesVersion(ReadingAnnouncementEntity entity, String appVersion) {
        if (appVersion == null || appVersion.isBlank()) {
            return true;
        }
        if (entity.getTargetMinAppVersion() != null && !entity.getTargetMinAppVersion().isBlank()) {
            if (compareVersion(appVersion, entity.getTargetMinAppVersion()) < 0) {
                return false;
            }
        }
        if (entity.getTargetMaxAppVersion() != null && !entity.getTargetMaxAppVersion().isBlank()) {
            if (compareVersion(appVersion, entity.getTargetMaxAppVersion()) > 0) {
                return false;
            }
        }
        return true;
    }

    private int compareVersion(String left, String right) {
        String[] a = left.trim().split("\\.");
        String[] b = right.trim().split("\\.");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int ai = i < a.length ? parseVersionPart(a[i]) : 0;
            int bi = i < b.length ? parseVersionPart(b[i]) : 0;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return 0;
    }

    private int parseVersionPart(String raw) {
        try {
            return Integer.parseInt(raw.replaceAll("[^0-9]", ""));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record AnnouncementView(
        String announcementUuid,
        String title,
        String content,
        String visibleStartAt,
        String visibleEndAt,
        boolean active,
        String createdAt,
        String updatedAt,
        String type,
        Integer priority,
        String actionUrl,
        String actionText,
        Boolean dismissible,
        Integer maxDisplayCount,
        Integer minIntervalSeconds,
        String triggerScene
    ) {
        public AnnouncementView(
            String announcementUuid,
            String title,
            String content,
            String visibleStartAt,
            String visibleEndAt,
            boolean active,
            String updatedAt
        ) {
            this(announcementUuid, title, content, visibleStartAt, visibleEndAt, active, null, updatedAt, "info", 0, null, null, true, 1, 86400, "app_launch");
        }

        public AnnouncementView(
            String announcementUuid,
            String title,
            String content,
            String visibleStartAt,
            String visibleEndAt,
            boolean active,
            String updatedAt,
            String type,
            Integer priority,
            String actionUrl,
            String actionText,
            Boolean dismissible,
            Integer maxDisplayCount,
            Integer minIntervalSeconds,
            String triggerScene
        ) {
            this(
                announcementUuid,
                title,
                content,
                visibleStartAt,
                visibleEndAt,
                active,
                null,
                updatedAt,
                type,
                priority,
                actionUrl,
                actionText,
                dismissible,
                maxDisplayCount,
                minIntervalSeconds,
                triggerScene
            );
        }

    }
}
