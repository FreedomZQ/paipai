package com.apphub.backend.apps.fitmystery.domain.service;

import com.apphub.backend.apps.fitmystery.domain.entity.FitBlindBoxDrawEntity;
import com.apphub.backend.apps.fitmystery.domain.entity.FitBlindBoxItemEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface FitMysteryBoxDataService extends IService<FitBlindBoxDrawEntity> {
    int currentPointsBalance(String appCode, Long userId);
    int currentChanceBalance(String appCode, Long userId);
    List<FitBlindBoxItemEntity> selectActiveItems(String appCode, String poolCode);
    FitBlindBoxDrawEntity selectDrawByIdempotencyKey(String appCode, Long userId, String idempotencyKey);
    Integer obtainCount(String appCode, Long userId, String itemCode);
    void insertSpendPoints(String id, String appCode, Long userId, int pointsDelta, int balanceAfter, String relatedDrawId, String idempotencyKey, OffsetDateTime createdAt);
    void insertSpendChance(String id, String appCode, Long userId, int chanceDelta, int balanceAfter, String sourceId, String idempotencyKey, OffsetDateTime createdAt);
    void insertDraw(String id, String appCode, Long userId, String poolCode, String itemCode, String rarity, String consumeType, int pointsSpent, int chancesSpent, String rngVersion, String oddsVersion, String idempotencyKey, OffsetDateTime createdAt);
    void upsertCollection(String appCode, Long userId, String itemCode, String drawId, OffsetDateTime now);
    List<Map<String, Object>> selectCollection(String appCode, Long userId, int limit);
    List<Map<String, Object>> selectDrawHistory(String appCode, Long userId, int limit);
}
