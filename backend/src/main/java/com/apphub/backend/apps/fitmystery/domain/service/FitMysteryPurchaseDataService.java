package com.apphub.backend.apps.fitmystery.domain.service;

import com.apphub.backend.apps.fitmystery.domain.entity.FitDrawChanceLedgerEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.OffsetDateTime;

public interface FitMysteryPurchaseDataService extends IService<FitDrawChanceLedgerEntity> {
    int currentChanceBalance(String appCode, Long userId);
    int countChanceLedgerByIdempotency(String appCode, Long userId, String idempotencyKey);
    void insertGrantChance(String id, String appCode, Long userId, int chanceDelta, int balanceAfter, String sourceType, String sourceId, String idempotencyKey, OffsetDateTime createdAt);
}
