package com.apphub.backend.apps.fitmystery.domain.service.impl;

import com.apphub.backend.apps.fitmystery.domain.entity.FitActivityEventEntity;
import com.apphub.backend.apps.fitmystery.domain.mapper.FitMysteryActivityMapper;
import com.apphub.backend.apps.fitmystery.domain.service.FitMysteryActivityDataService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class FitMysteryActivityDataServiceImpl extends ServiceImpl<FitMysteryActivityMapper, FitActivityEventEntity> implements FitMysteryActivityDataService {
    @Override public int countEventByIdempotencyKey(String appCode, Long userId, String idempotencyKey) { return baseMapper.countEventByIdempotencyKey(appCode, userId, idempotencyKey); }
    @Override public void insertEvent(FitActivityEventEntity entity) { baseMapper.insertEvent(entity); }
    @Override public int currentPointsBalance(String appCode, Long userId) { return baseMapper.currentPointsBalance(appCode, userId); }
    @Override public int currentChanceBalance(String appCode, Long userId) { return baseMapper.currentChanceBalance(appCode, userId); }
    @Override public void insertPointsLedger(String id, String appCode, Long userId, String ledgerType, int pointsDelta, int balanceAfter, String relatedEventId, String idempotencyKey, String reasonCode, String note, OffsetDateTime createdAt) { baseMapper.insertPointsLedger(id, appCode, userId, ledgerType, pointsDelta, balanceAfter, relatedEventId, idempotencyKey, reasonCode, note, createdAt); }
    @Override public void upsertDailySnapshot(String appCode, Long userId, LocalDate scoreDate, int waterMl, int steps, int exerciseMinutes, int pointsEarned, int pointsBalance, int chanceBalance, OffsetDateTime now) { baseMapper.upsertDailySnapshot(appCode, userId, scoreDate, waterMl, steps, exerciseMinutes, pointsEarned, pointsBalance, chanceBalance, now); }
    @Override public Map<String, Object> selectToday(String appCode, Long userId, LocalDate scoreDate) { return baseMapper.selectToday(appCode, userId, scoreDate); }
}
