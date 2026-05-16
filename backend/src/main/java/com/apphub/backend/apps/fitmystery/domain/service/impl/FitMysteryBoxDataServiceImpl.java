package com.apphub.backend.apps.fitmystery.domain.service.impl;

import com.apphub.backend.apps.fitmystery.domain.entity.FitBlindBoxDrawEntity;
import com.apphub.backend.apps.fitmystery.domain.entity.FitBlindBoxItemEntity;
import com.apphub.backend.apps.fitmystery.domain.mapper.FitMysteryBoxMapper;
import com.apphub.backend.apps.fitmystery.domain.service.FitMysteryBoxDataService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class FitMysteryBoxDataServiceImpl extends ServiceImpl<FitMysteryBoxMapper, FitBlindBoxDrawEntity> implements FitMysteryBoxDataService {
    @Override public int currentPointsBalance(String appCode, Long userId) { return baseMapper.currentPointsBalance(appCode, userId); }
    @Override public int currentChanceBalance(String appCode, Long userId) { return baseMapper.currentChanceBalance(appCode, userId); }
    @Override public List<FitBlindBoxItemEntity> selectActiveItems(String appCode, String poolCode) { return baseMapper.selectActiveItems(appCode, poolCode); }
    @Override public FitBlindBoxDrawEntity selectDrawByIdempotencyKey(String appCode, Long userId, String idempotencyKey) { return baseMapper.selectDrawByIdempotencyKey(appCode, userId, idempotencyKey); }
    @Override public Integer obtainCount(String appCode, Long userId, String itemCode) { return baseMapper.obtainCount(appCode, userId, itemCode); }
    @Override public void insertSpendPoints(String id, String appCode, Long userId, int pointsDelta, int balanceAfter, String relatedDrawId, String idempotencyKey, OffsetDateTime createdAt) { baseMapper.insertSpendPoints(id, appCode, userId, pointsDelta, balanceAfter, relatedDrawId, idempotencyKey, createdAt); }
    @Override public void insertSpendChance(String id, String appCode, Long userId, int chanceDelta, int balanceAfter, String sourceId, String idempotencyKey, OffsetDateTime createdAt) { baseMapper.insertSpendChance(id, appCode, userId, chanceDelta, balanceAfter, sourceId, idempotencyKey, createdAt); }
    @Override public void insertDraw(String id, String appCode, Long userId, String poolCode, String itemCode, String rarity, String consumeType, int pointsSpent, int chancesSpent, String rngVersion, String oddsVersion, String idempotencyKey, OffsetDateTime createdAt) { baseMapper.insertDraw(id, appCode, userId, poolCode, itemCode, rarity, consumeType, pointsSpent, chancesSpent, rngVersion, oddsVersion, idempotencyKey, createdAt); }
    @Override public void upsertCollection(String appCode, Long userId, String itemCode, String drawId, OffsetDateTime now) { baseMapper.upsertCollection(appCode, userId, itemCode, drawId, now); }
    @Override public List<Map<String, Object>> selectCollection(String appCode, Long userId, int limit) { return baseMapper.selectCollection(appCode, userId, limit); }
    @Override public List<Map<String, Object>> selectDrawHistory(String appCode, Long userId, int limit) { return baseMapper.selectDrawHistory(appCode, userId, limit); }
}
