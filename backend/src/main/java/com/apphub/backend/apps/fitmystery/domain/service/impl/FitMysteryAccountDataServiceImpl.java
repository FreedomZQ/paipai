package com.apphub.backend.apps.fitmystery.domain.service.impl;

import com.apphub.backend.apps.fitmystery.domain.entity.FitAccountDeletionRequestEntity;
import com.apphub.backend.apps.fitmystery.domain.mapper.FitMysteryAccountMapper;
import com.apphub.backend.apps.fitmystery.domain.service.FitMysteryAccountDataService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class FitMysteryAccountDataServiceImpl extends ServiceImpl<FitMysteryAccountMapper, FitAccountDeletionRequestEntity> implements FitMysteryAccountDataService {
    @Override public int deleteCollection(String appCode, Long userId) { return baseMapper.deleteCollection(appCode, userId); }
    @Override public int deleteDraws(String appCode, Long userId) { return baseMapper.deleteDraws(appCode, userId); }
    @Override public int deleteChanceLedger(String appCode, Long userId) { return baseMapper.deleteChanceLedger(appCode, userId); }
    @Override public int deletePointsLedger(String appCode, Long userId) { return baseMapper.deletePointsLedger(appCode, userId); }
    @Override public int deleteDailySnapshots(String appCode, Long userId) { return baseMapper.deleteDailySnapshots(appCode, userId); }
    @Override public int deleteActivityEvents(String appCode, Long userId) { return baseMapper.deleteActivityEvents(appCode, userId); }
    @Override public int deleteReportGenerationLedger(String appCode, Long userId) { return baseMapper.deleteReportGenerationLedger(appCode, userId); }
    @Override public int deleteEntitlementSnapshots(String appCode, Long userId) { return baseMapper.deleteEntitlementSnapshots(appCode, userId); }
    @Override public int countRetainedPurchaseTransactions(String appCode, Long userId) { return baseMapper.countRetainedPurchaseTransactions(appCode, userId); }
    @Override public int countRetainedAppStoreNotifications(String appCode) { return baseMapper.countRetainedAppStoreNotifications(appCode); }
    @Override public void insertDeletionRequest(String appCode, Long userId, OffsetDateTime now, String note) { baseMapper.insertDeletionRequest(appCode, userId, now, note); }
}
