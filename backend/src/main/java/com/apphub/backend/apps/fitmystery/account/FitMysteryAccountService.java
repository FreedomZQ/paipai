package com.apphub.backend.apps.fitmystery.account;

import com.apphub.backend.apps.fitmystery.FitMysteryAppModule;
import com.apphub.backend.apps.fitmystery.domain.service.FitMysteryAccountDataService;
import com.apphub.backend.sys.auth.service.SysAuthDataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FitMysteryAccountService {
    private final FitMysteryAccountDataService mapper;
    private final SysAuthDataService authDataService;

    public FitMysteryAccountService(
        FitMysteryAccountDataService mapper,
        SysAuthDataService authDataService
    ) {
        this.mapper = mapper;
        this.authDataService = authDataService;
    }

    @Transactional
    public Map<String, Object> deleteAppData(Long userId) {
        Map<String, Object> deleted = new LinkedHashMap<>();
        Map<String, Object> revoked = new LinkedHashMap<>();
        Map<String, Object> retainedForAudit = new LinkedHashMap<>();
        deleted.put("collection", mapper.deleteCollection(FitMysteryAppModule.APP_CODE, userId));
        deleted.put("draws", mapper.deleteDraws(FitMysteryAppModule.APP_CODE, userId));
        deleted.put("chanceLedger", mapper.deleteChanceLedger(FitMysteryAppModule.APP_CODE, userId));
        deleted.put("pointsLedger", mapper.deletePointsLedger(FitMysteryAppModule.APP_CODE, userId));
        deleted.put("dailySnapshots", mapper.deleteDailySnapshots(FitMysteryAppModule.APP_CODE, userId));
        deleted.put("activityEvents", mapper.deleteActivityEvents(FitMysteryAppModule.APP_CODE, userId));
        deleted.put("reportGenerationLedger", mapper.deleteReportGenerationLedger(FitMysteryAppModule.APP_CODE, userId));
        deleted.put("entitlementSnapshots", mapper.deleteEntitlementSnapshots(FitMysteryAppModule.APP_CODE, userId));
        retainedForAudit.put("purchaseTransactions", mapper.countRetainedPurchaseTransactions(FitMysteryAppModule.APP_CODE, userId));
        retainedForAudit.put("appStoreNotifications", mapper.countRetainedAppStoreNotifications(FitMysteryAppModule.APP_CODE));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        revoked.put("authSessions", authDataService.revokeSessionsByUser(FitMysteryAppModule.APP_CODE, userId, now));
        revoked.put("authProviderTokens", authDataService.revokeProviderTokensByUser(FitMysteryAppModule.APP_CODE, userId, now));
        mapper.insertDeletionRequest(FitMysteryAppModule.APP_CODE, userId, now, "User requested in-app account/data deletion for FitMystery. FitMystery business data and active app sessions/provider tokens are removed or revoked. Purchase transactions and App Store notification records are retained only for Apple billing audit/reconciliation.");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", true);
        data.put("scope", "fitmystery_app_data_plus_auth_revocation");
        data.put("deletedRows", deleted);
        data.put("revokedRows", revoked);
        data.put("retainedForAudit", retainedForAudit);
        data.put("completedAt", now.toString());
        data.put("note", "已删除 FitMystery 业务数据并撤销当前 App 会话/Apple provider token。Apple 订阅仍需用户在 Apple ID 订阅管理中取消；购买交易和 App Store 通知仅为 Apple 对账/审计保留。 ");
        return data;
    }
}
