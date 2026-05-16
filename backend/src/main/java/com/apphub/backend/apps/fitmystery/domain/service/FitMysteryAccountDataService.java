package com.apphub.backend.apps.fitmystery.domain.service;

import com.apphub.backend.apps.fitmystery.domain.entity.FitAccountDeletionRequestEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.OffsetDateTime;

public interface FitMysteryAccountDataService extends IService<FitAccountDeletionRequestEntity> {
    int deleteCollection(String appCode, Long userId);
    int deleteDraws(String appCode, Long userId);
    int deleteChanceLedger(String appCode, Long userId);
    int deletePointsLedger(String appCode, Long userId);
    int deleteDailySnapshots(String appCode, Long userId);
    int deleteActivityEvents(String appCode, Long userId);
    int deleteReportGenerationLedger(String appCode, Long userId);
    int deleteEntitlementSnapshots(String appCode, Long userId);
    int countRetainedPurchaseTransactions(String appCode, Long userId);
    int countRetainedAppStoreNotifications(String appCode);
    void insertDeletionRequest(String appCode, Long userId, OffsetDateTime now, String note);
}
