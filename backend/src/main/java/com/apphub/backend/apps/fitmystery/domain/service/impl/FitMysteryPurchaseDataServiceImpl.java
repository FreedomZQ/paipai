package com.apphub.backend.apps.fitmystery.domain.service.impl;

import com.apphub.backend.apps.fitmystery.domain.entity.FitDrawChanceLedgerEntity;
import com.apphub.backend.apps.fitmystery.domain.mapper.FitMysteryPurchaseMapper;
import com.apphub.backend.apps.fitmystery.domain.service.FitMysteryPurchaseDataService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class FitMysteryPurchaseDataServiceImpl extends ServiceImpl<FitMysteryPurchaseMapper, FitDrawChanceLedgerEntity> implements FitMysteryPurchaseDataService {
    @Override public int currentChanceBalance(String appCode, Long userId) { return baseMapper.currentChanceBalance(appCode, userId); }
    @Override public int countChanceLedgerByIdempotency(String appCode, Long userId, String idempotencyKey) { return baseMapper.countChanceLedgerByIdempotency(appCode, userId, idempotencyKey); }
    @Override public void insertGrantChance(String id, String appCode, Long userId, int chanceDelta, int balanceAfter, String sourceType, String sourceId, String idempotencyKey, OffsetDateTime createdAt) { baseMapper.insertGrantChance(id, appCode, userId, chanceDelta, balanceAfter, sourceType, sourceId, idempotencyKey, createdAt); }
}
