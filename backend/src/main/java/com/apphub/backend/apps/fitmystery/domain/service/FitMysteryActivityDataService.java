package com.apphub.backend.apps.fitmystery.domain.service;

import com.apphub.backend.apps.fitmystery.domain.entity.FitActivityEventEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

public interface FitMysteryActivityDataService extends IService<FitActivityEventEntity> {
    int countEventByIdempotencyKey(String appCode, Long userId, String idempotencyKey);
    void insertEvent(FitActivityEventEntity entity);
    int currentPointsBalance(String appCode, Long userId);
    int currentChanceBalance(String appCode, Long userId);
    void insertPointsLedger(String id, String appCode, Long userId, String ledgerType, int pointsDelta, int balanceAfter, String relatedEventId, String idempotencyKey, String reasonCode, String note, OffsetDateTime createdAt);
    void upsertDailySnapshot(String appCode, Long userId, LocalDate scoreDate, int waterMl, int steps, int exerciseMinutes, int pointsEarned, int pointsBalance, int chanceBalance, OffsetDateTime now);
    Map<String, Object> selectToday(String appCode, Long userId, LocalDate scoreDate);
}
