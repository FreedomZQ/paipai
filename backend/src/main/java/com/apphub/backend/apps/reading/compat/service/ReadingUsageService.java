package com.apphub.backend.apps.reading.compat.service;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.domain.entity.ReadingChildProfileEntity;
import com.apphub.backend.apps.reading.domain.entity.ReadingUsageSessionV2Entity;
import com.apphub.backend.apps.reading.domain.entity.ReadingChildUsageDailyEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingChildProfileMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingChildUsageDailyMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingUsageSessionV2Mapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReadingUsageService {
    private static final String APP_CODE = ReadingAppModule.APP_CODE;

    private final ReadingUsageSessionV2Mapper usageSessionMapper;
    private final ReadingChildUsageDailyMapper usageDailyMapper;
    private final ReadingChildProfileMapper childProfileMapper;
    private final ReadingUsagePolicyService usagePolicyService;

    public ReadingUsageService(
        ReadingUsageSessionV2Mapper usageSessionMapper,
        ReadingChildUsageDailyMapper usageDailyMapper,
        ReadingChildProfileMapper childProfileMapper,
        ReadingUsagePolicyService usagePolicyService
    ) {
        this.usageSessionMapper = usageSessionMapper;
        this.usageDailyMapper = usageDailyMapper;
        this.childProfileMapper = childProfileMapper;
        this.usagePolicyService = usagePolicyService;
    }

    @Transactional
    public UsageSessionStartReceipt startSession(ReadingAuthenticatedUser user, UsageSessionStartRequest request) {
        ReadingChildProfileEntity child = requireChild(user.userId(), request.childId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startedAt = parseOrNow(request.startedAt(), now);
        String sessionUuid = hasText(request.sessionUuid()) ? request.sessionUuid().trim() : UUID.randomUUID().toString();

        ReadingUsageSessionV2Entity existing = usageSessionMapper.selectActiveByUserAndSessionUuid(user.userId(), sessionUuid);
        if (existing != null) {
            return new UsageSessionStartReceipt(existing.getId(), existing.getChildId(), existing.getStartedAt().toString(), "already_started");
        }

        ReadingUsageSessionV2Entity entity = new ReadingUsageSessionV2Entity();
        entity.setAppCode(APP_CODE);
        entity.setUserId(user.userId());
        entity.setChildId(child.getId());
        entity.setId(sessionUuid);
        entity.setStartedAt(startedAt);
        entity.setDurationSeconds(0);
        entity.setClientPlatform(blankToNull(request.clientPlatform()));
        entity.setDeviceModel(blankToNull(request.deviceModel()));
        entity.setSourcePage(blankToNull(request.sourcePage()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        usageSessionMapper.insert(entity);
        return new UsageSessionStartReceipt(entity.getId(), entity.getChildId(), entity.getStartedAt().toString(), "started");
    }

    @Transactional
    public UsageSessionEndReceipt endSession(ReadingAuthenticatedUser user, UsageSessionEndRequest request) {
        if (!hasText(request.sessionUuid())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SESSION_UUID_REQUIRED");
        }
        ReadingUsageSessionV2Entity entity = usageSessionMapper.selectActiveByUserAndSessionUuid(user.userId(), request.sessionUuid().trim());
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "USAGE_SESSION_NOT_FOUND");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime endedAt = parseOrNow(request.endedAt(), now);
        long rawDuration = Duration.between(entity.getStartedAt(), endedAt).getSeconds();
        int maxSessionSeconds = usagePolicyService.currentPolicy().maxSessionHours() * 3600;
        int durationSeconds = (int) Math.max(1L, Math.min(rawDuration, maxSessionSeconds));
        entity.setEndedAt(endedAt);
        entity.setDurationSeconds(durationSeconds);
        entity.setUpdatedAt(now);
        usageSessionMapper.updateById(entity);

        usageDailyMapper.upsertDuration(user.userId(), entity.getChildId(), entity.getStartedAt().toLocalDate(), durationSeconds, 1, now);
        return new UsageSessionEndReceipt(entity.getId(), entity.getChildId(), entity.getStartedAt().toString(), endedAt.toString(), durationSeconds, "completed");
    }

    public ChildUsageSummaryView childSummary(ReadingAuthenticatedUser user, String childId) {
        // 低运维兜底：用户读取 usage 摘要时顺手执行一次按配置的保留期清理，
        // 避免个人开发者必须维护独立定时任务；未来有统一调度后可迁移到后台 job。
        cleanupRetentionForUser(user);
        ReadingChildProfileEntity child = requireChild(user.userId(), childId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        ReadingChildUsageDailyEntity todayRow = usageDailyMapper.selectByUserChildDate(user.userId(), child.getId(), today);
        Integer todayDuration = todayRow == null ? 0 : safe(todayRow.getDurationSeconds());
        Integer todaySessionCount = usageDailyMapper.sumSessionCountByUserChildDate(user.userId(), child.getId(), today);
        Integer totalDuration = usageDailyMapper.sumDurationByUserChild(user.userId(), child.getId());
        Integer weeklyDuration = usageDailyMapper.sumDurationByUserChildRange(user.userId(), child.getId(), weekStart, today.plusDays(1));
        OffsetDateTime lastUsedAt = usageSessionMapper.selectLastUsedAtByUserAndChild(user.userId(), child.getId());
        return new ChildUsageSummaryView(
            child.getId(),
            child.getNickname(),
            today.toString(),
            safe(todayDuration),
            safe(totalDuration),
            safe(weeklyDuration),
            safe(todaySessionCount),
            lastUsedAt == null ? null : lastUsedAt.toString(),
            recentDailyUsage(user.userId(), child.getId(), today, usagePolicyService.currentPolicy().recentSummaryDays()),
            usagePolicyService.currentPolicy().retentionDays(),
            usagePolicyService.currentPolicy().recentSummaryDays(),
            usagePolicyService.currentPolicy().dayBoundary()
        );
    }

    public FamilyUsageSummaryView familySummary(ReadingAuthenticatedUser user) {
        // 低运维兜底：家长区打开时按数据库配置清理过期 usage 数据，确保法务口径与展示口径一致。
        cleanupRetentionForUser(user);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        Integer todayDuration = usageDailyMapper.sumDurationByUserRange(user.userId(), today, today.plusDays(1));
        Integer totalDuration = usageDailyMapper.sumDurationByUser(user.userId());
        Integer weeklyDuration = usageDailyMapper.sumDurationByUserRange(user.userId(), weekStart, today.plusDays(1));
        Integer todaySessionCount = usageDailyMapper.sumSessionCountByUserDate(user.userId(), today);
        OffsetDateTime lastUsedAt = usageSessionMapper.selectLastUsedAtByUser(user.userId());
        int childCount = childProfileMapper.countActiveByUser(user.userId());
        return new FamilyUsageSummaryView(
            today.toString(),
            safe(todayDuration),
            safe(totalDuration),
            safe(weeklyDuration),
            safe(todaySessionCount),
            childCount,
            lastUsedAt == null ? null : lastUsedAt.toString(),
            recentFamilyUsage(user.userId(), today, usagePolicyService.currentPolicy().recentSummaryDays()),
            usagePolicyService.currentPolicy().retentionDays(),
            usagePolicyService.currentPolicy().recentSummaryDays(),
            usagePolicyService.currentPolicy().dayBoundary()
        );
    }

    @Transactional
    public UsageRetentionCleanupReceipt cleanupRetentionForUser(ReadingAuthenticatedUser user) {
        ReadingUsagePolicyService.UsagePolicyView policy = usagePolicyService.currentPolicy();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate cutoffDate = now.toLocalDate().minusDays(policy.retentionDays() - 1L);
        OffsetDateTime cutoffAt = cutoffDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        int deletedDailyRows = usageDailyMapper.deleteByUserBeforeDate(user.userId(), cutoffDate);
        int deletedSessionRows = usageSessionMapper.deleteByUserBefore(user.userId(), cutoffAt);
        return new UsageRetentionCleanupReceipt(policy.retentionDays(), cutoffDate.toString(), deletedSessionRows, deletedDailyRows, now.toString());
    }

    private List<DailyUsagePointView> recentDailyUsage(Long userId, String childId, LocalDate today, int recentSummaryDays) {
        int safeDays = Math.max(1, recentSummaryDays);
        LocalDate startDate = today.minusDays(safeDays - 1L);
        Map<LocalDate, Integer> durations = new LinkedHashMap<>();
        for (int i = 0; i < safeDays; i++) {
            durations.put(startDate.plusDays(i), 0);
        }
        usageDailyMapper.selectList(new LambdaQueryWrapper<ReadingChildUsageDailyEntity>()
                .eq(ReadingChildUsageDailyEntity::getUserId, userId)
                .eq(ReadingChildUsageDailyEntity::getChildId, childId)
                .ge(ReadingChildUsageDailyEntity::getUsageDate, startDate)
                .le(ReadingChildUsageDailyEntity::getUsageDate, today)
                .orderByAsc(ReadingChildUsageDailyEntity::getUsageDate))
            .forEach(row -> durations.put(row.getUsageDate(), safe(row.getDurationSeconds())));
        return durations.entrySet().stream()
            .map(entry -> new DailyUsagePointView(entry.getKey().toString(), entry.getValue()))
            .toList();
    }

    private List<DailyUsagePointView> recentFamilyUsage(Long userId, LocalDate today, int recentSummaryDays) {
        int safeDays = Math.max(1, recentSummaryDays);
        LocalDate startDate = today.minusDays(safeDays - 1L);
        Map<LocalDate, Integer> durations = new LinkedHashMap<>();
        for (int i = 0; i < safeDays; i++) {
            durations.put(startDate.plusDays(i), 0);
        }
        usageDailyMapper.selectList(new LambdaQueryWrapper<ReadingChildUsageDailyEntity>()
                .eq(ReadingChildUsageDailyEntity::getUserId, userId)
                .ge(ReadingChildUsageDailyEntity::getUsageDate, startDate)
                .le(ReadingChildUsageDailyEntity::getUsageDate, today)
                .orderByAsc(ReadingChildUsageDailyEntity::getUsageDate))
            .forEach(row -> durations.compute(row.getUsageDate(), (date, current) -> safe(current) + safe(row.getDurationSeconds())));
        return durations.entrySet().stream()
            .map(entry -> new DailyUsagePointView(entry.getKey().toString(), entry.getValue()))
            .toList();
    }

    private ReadingChildProfileEntity requireChild(Long userId, String childId) {
        if (!hasText(childId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CHILD_ID_REQUIRED");
        }
        ReadingChildProfileEntity child = childProfileMapper.selectActiveByIdAndUser(childId.trim(), userId);
        if (child == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CHILD_NOT_FOUND");
        }
        return child;
    }

    private OffsetDateTime parseOrNow(String raw, OffsetDateTime now) {
        try {
            return hasText(raw) ? OffsetDateTime.parse(raw.trim()) : now;
        } catch (Exception ignored) {
            return now;
        }
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    public record UsageSessionStartRequest(
        @Schema(description = "孩子档案 ID。", example = "child-a") String childId,
        @Schema(description = "客户端生成的会话 UUID，用于幂等。", example = "550e8400-e29b-41d4-a716-446655440000") String sessionUuid,
        @Schema(description = "会话开始时间，ISO-8601 格式。", example = "2026-04-28T09:00:00Z") String startedAt,
        @Schema(description = "客户端平台。", example = "ios") String clientPlatform,
        @Schema(description = "设备型号。", example = "iPhone16,2") String deviceModel,
        @Schema(description = "来源页面。", example = "home") String sourcePage
    ) {}

    public record UsageSessionStartReceipt(
        String sessionUuid,
        String childId,
        String startedAt,
        String status
    ) {}

    public record UsageSessionEndRequest(
        @Schema(description = "客户端生成的会话 UUID。", example = "550e8400-e29b-41d4-a716-446655440000") String sessionUuid,
        @Schema(description = "会话结束时间，ISO-8601 格式。", example = "2026-04-28T09:30:00Z") String endedAt
    ) {}

    public record UsageSessionEndReceipt(
        String sessionUuid,
        String childId,
        String startedAt,
        String endedAt,
        int durationSeconds,
        String status
    ) {}

    public record DailyUsagePointView(String usageDate, int durationSeconds) {}

    public record ChildUsageSummaryView(
        String childId,
        String childName,
        String usageDate,
        int todayDurationSeconds,
        int totalDurationSeconds,
        int weeklyDurationSeconds,
        int todaySessionCount,
        String lastUsedAt,
        List<DailyUsagePointView> recentDailyUsage,
        int retentionDays,
        int recentSummaryDays,
        String dayBoundary
    ) {}

    public record FamilyUsageSummaryView(
        String usageDate,
        int todayDurationSeconds,
        int totalDurationSeconds,
        int weeklyDurationSeconds,
        int todaySessionCount,
        int childCount,
        String lastUsedAt,
        List<DailyUsagePointView> recentDailyUsage,
        int retentionDays,
        int recentSummaryDays,
        String dayBoundary
    ) {}

    public record UsageRetentionCleanupReceipt(
        int retentionDays,
        String cutoffDate,
        int deletedSessionRows,
        int deletedDailyRows,
        String cleanedAt
    ) {}
}
