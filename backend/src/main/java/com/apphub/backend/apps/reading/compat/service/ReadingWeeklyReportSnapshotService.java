package com.apphub.backend.apps.reading.compat.service;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.domain.entity.ReadingWeeklyReportSnapshotEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingWeeklyReportSnapshotMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * 周报历史快照服务。
 *
 * <p>历史报告采用“读快照优先、缺失时即时生成并保存”的策略：
 * <ul>
 *   <li>个人开发者无需维护复杂定时任务，运维成本低。</li>
 *   <li>历史报告一旦生成后保持稳定，降低因数据回填导致的用户争议。</li>
 *   <li>所有记录带 app_code，后续多个 App 共用统一后端时不会互相污染。</li>
 *   <li>快照 JSON 只存聚合后的低风险统计与建议，不存儿童原始句卡正文。</li>
 * </ul>
 */
@Service
public class ReadingWeeklyReportSnapshotService {
    private static final Logger log = LoggerFactory.getLogger(ReadingWeeklyReportSnapshotService.class);
    private static final String APP_CODE = ReadingAppModule.APP_CODE;
    private static final int PAYLOAD_VERSION = 1;

    private final ReadingWeeklyReportSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;

    public ReadingWeeklyReportSnapshotService(ReadingWeeklyReportSnapshotMapper snapshotMapper, ObjectMapper objectMapper) {
        this.snapshotMapper = snapshotMapper;
        this.objectMapper = objectMapper;
    }

    public Optional<ReadingCompatService.WeeklyParentReportView> load(
        Long userId,
        String childId,
        String scope,
        LocalDate weekStart,
        String planCode
    ) {
        ReadingWeeklyReportSnapshotEntity snapshot = snapshotMapper.selectActiveSnapshot(APP_CODE, userId, childId, scope, weekStart, planCode);
        if (snapshot == null || snapshot.getReportPayloadJson() == null || snapshot.getReportPayloadJson().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(snapshot.getReportPayloadJson(), ReadingCompatService.WeeklyParentReportView.class));
        } catch (Exception exception) {
            log.warn("reading weekly report snapshot decode failed, appCode={}, userId={}, scope={}, childId={}, weekStart={}", APP_CODE, userId, scope, childId, weekStart, exception);
            return Optional.empty();
        }
    }

    @Transactional
    public void save(
        Long userId,
        String childId,
        String scope,
        LocalDate weekStart,
        String planCode,
        String tier,
        ReadingCompatService.WeeklyParentReportView report
    ) {
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            ReadingWeeklyReportSnapshotEntity existing = snapshotMapper.selectActiveSnapshot(APP_CODE, userId, childId, scope, weekStart, planCode);
            ReadingWeeklyReportSnapshotEntity entity = existing == null ? new ReadingWeeklyReportSnapshotEntity() : existing;
            if (existing == null) {
                entity.setId(UUID.randomUUID().toString());
                entity.setAppCode(APP_CODE);
                entity.setUserId(userId);
                entity.setChildId(childId);
                entity.setScope(scope);
                entity.setWeekStart(weekStart);
                entity.setPlanCode(planCode);
                entity.setCreatedAt(now);
            }
            entity.setWeekEnd(weekStart.plusDays(6));
            entity.setTier(tier);
            entity.setPayloadVersion(PAYLOAD_VERSION);
            entity.setReportPayloadJson(objectMapper.writeValueAsString(report));
            entity.setReportStatus("active");
            entity.setGeneratedAt(now);
            entity.setUpdatedAt(now);
            if (existing == null) {
                snapshotMapper.insert(entity);
            } else {
                snapshotMapper.updateById(entity);
            }
        } catch (JsonProcessingException exception) {
            log.warn("reading weekly report snapshot encode failed, appCode={}, userId={}, scope={}, childId={}, weekStart={}", APP_CODE, userId, scope, childId, weekStart, exception);
        }
    }
}
