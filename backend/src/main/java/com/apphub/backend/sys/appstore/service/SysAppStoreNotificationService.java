package com.apphub.backend.sys.appstore.service;

import com.apphub.backend.apps.fitmystery.FitMysteryAppModule;
import com.apphub.backend.apps.fitmystery.purchase.FitMysteryPurchaseService;
import com.apphub.backend.common.util.Sha256HashService;
import com.apphub.backend.sys.billing.service.SysBillingService;
import com.apphub.backend.sys.appstore.entity.SysAppStoreNotificationEntity;
import com.apphub.backend.sys.appstore.service.crud.SysAppStoreNotificationCrudService;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationAcceptedView;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationIngestRequest;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationObservabilityView;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * App Store服务 `SysAppStoreNotificationService`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Service
public class SysAppStoreNotificationService {

    private static final String VERIFICATION_PENDING = "pending";
    private static final String PROCESSING_ACCEPTED = "accepted";
    private static final int RECENT_NOTIFICATION_LIMIT = 10;

    private final SysAppStoreNotificationCrudService appStoreNotificationCrudService;
    private final Sha256HashService sha256HashService;
    private final ObjectMapper objectMapper;
    private final AppStoreJwsVerificationService appStoreJwsVerificationService;
    private final SysBillingService sysBillingService;
    private final ObjectProvider<FitMysteryPurchaseService> fitMysteryPurchaseServiceProvider;

    @Autowired
    public SysAppStoreNotificationService(
        SysAppStoreNotificationCrudService appStoreNotificationCrudService,
        Sha256HashService sha256HashService,
        ObjectMapper objectMapper,
        AppStoreJwsVerificationService appStoreJwsVerificationService,
        SysBillingService sysBillingService,
        ObjectProvider<FitMysteryPurchaseService> fitMysteryPurchaseServiceProvider
    ) {
        this.appStoreNotificationCrudService = appStoreNotificationCrudService;
        this.sha256HashService = sha256HashService;
        this.objectMapper = objectMapper;
        this.appStoreJwsVerificationService = appStoreJwsVerificationService;
        this.sysBillingService = sysBillingService;
        this.fitMysteryPurchaseServiceProvider = fitMysteryPurchaseServiceProvider;
    }

    public AppStoreNotificationObservabilityView describeObservability(String appCode) {
        List<SysAppStoreNotificationEntity> recent = appStoreNotificationCrudService.selectRecentByApp(appCode, RECENT_NOTIFICATION_LIMIT);
        List<AppStoreNotificationObservabilityView.RecentNotificationView> recentViews = recent == null
            ? List.of()
            : recent.stream()
                .map(entity -> new AppStoreNotificationObservabilityView.RecentNotificationView(
                    entity.getNotificationUuid(),
                    entity.getNotificationType(),
                    entity.getSubtype(),
                    entity.getVerificationStatus(),
                    entity.getProcessingStatus(),
                    entity.getReceivedAt()
                ))
                .toList();
        return new AppStoreNotificationObservabilityView(
            appCode,
            appStoreNotificationCrudService.countByApp(appCode),
            appStoreNotificationCrudService.countByAppAndVerificationStatus(appCode, "verified"),
            appStoreNotificationCrudService.countByAppAndVerificationStatus(appCode, "failed"),
            appStoreNotificationCrudService.countByAppAndProcessingStatus(appCode, "accepted"),
            appStoreNotificationCrudService.countByAppAndProcessingStatus(appCode, "reconciled"),
            appStoreNotificationCrudService.countByAppAndProcessingStatus(appCode, "rejected"),
            recentViews
        );
    }

    @Transactional
    public AppStoreNotificationAcceptedView ingest(String appCode, AppStoreNotificationIngestRequest request) {
        AppStoreJwsVerificationService.NotificationVerificationResult verificationResult = appStoreJwsVerificationService.verifyNotification(request.signedPayload());
        String notificationUuid = resolveNotificationUuid(request, verificationResult);
        SysAppStoreNotificationEntity existing = appStoreNotificationCrudService.getOne(
            new LambdaQueryWrapper<SysAppStoreNotificationEntity>()
                .eq(SysAppStoreNotificationEntity::getAppCode, appCode)
                .eq(SysAppStoreNotificationEntity::getNotificationUuid, notificationUuid)
                .last("LIMIT 1")
        );
        if (existing != null) {
            return toView(existing, true);
        }

        SysBillingService.NotificationReconcileResult reconcileResult = verificationResult.claims() == null || !"verified".equalsIgnoreCase(verificationResult.verificationStatus())
            ? new SysBillingService.NotificationReconcileResult("skipped_notification_not_verified", false, null, null, null)
            : sysBillingService.reconcileVerifiedNotification(appCode, verificationResult.claims(), payloadBody(request));
        Map<String, Object> appSpecificGrant = grantFitMysteryConsumableIfNeeded(appCode, verificationResult, reconcileResult);

        OffsetDateTime now = now();
        SysAppStoreNotificationEntity entity = new SysAppStoreNotificationEntity();
        entity.setAppCode(appCode);
        entity.setNotificationUuid(notificationUuid);
        entity.setNotificationType(firstNonBlank(request.notificationType(), verificationResult.claims() != null ? verificationResult.claims().notificationType() : null));
        entity.setSubtype(firstNonBlank(request.subtype(), verificationResult.claims() != null ? verificationResult.claims().subtype() : null));
        entity.setSignedPayloadHash(sha256HashService.hash(request.signedPayload()));
        entity.setVerificationStatus(verificationResult.verificationStatus());
        entity.setProcessingStatus(resolveNotificationProcessingStatus(verificationResult.verificationStatus(), reconcileResult));
        entity.setRawPayloadJson(toJson(mapOfNonNull(
            "request", payloadBody(request),
            "verification", mapOfNonNull(
                "verificationStatus", verificationResult.verificationStatus(),
                "detailStatus", verificationResult.detailStatus(),
                "note", verificationResult.note(),
                "diagnostics", verificationResult.diagnostics(),
                "claims", verificationResult.claims()
            ),
            "reconcile", mapOfNonNull(
                "status", reconcileResult.status(),
                "verified", reconcileResult.verified(),
                "userId", reconcileResult.userId(),
                "originalTransactionId", reconcileResult.originalTransactionId(),
                "note", reconcileResult.note()
            ),
            "appSpecificGrant", appSpecificGrant
        )));
        entity.setReceivedAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            appStoreNotificationCrudService.save(entity);
            return toView(entity, false);
        } catch (DataIntegrityViolationException ex) {
            SysAppStoreNotificationEntity duplicate = appStoreNotificationCrudService.getOne(
                new LambdaQueryWrapper<SysAppStoreNotificationEntity>()
                    .eq(SysAppStoreNotificationEntity::getAppCode, appCode)
                    .eq(SysAppStoreNotificationEntity::getNotificationUuid, notificationUuid)
                    .last("LIMIT 1")
            );
            if (duplicate != null) {
                return toView(duplicate, true);
            }
            throw ex;
        }
    }

    private Map<String, Object> grantFitMysteryConsumableIfNeeded(
        String appCode,
        AppStoreJwsVerificationService.NotificationVerificationResult verificationResult,
        SysBillingService.NotificationReconcileResult reconcileResult
    ) {
        if (!FitMysteryAppModule.APP_CODE.equals(appCode)
            || verificationResult == null
            || verificationResult.claims() == null
            || reconcileResult == null
            || !reconcileResult.verified()
            || reconcileResult.userId() == null) {
            return Map.of("status", "skipped");
        }
        FitMysteryPurchaseService service = fitMysteryPurchaseServiceProvider.getIfAvailable();
        if (service == null) {
            return Map.of("status", "skipped_service_unavailable");
        }
        AppStoreJwsVerificationService.NotificationClaims claims = verificationResult.claims();
        return service.grantConsumableFromVerifiedNotification(
            reconcileResult.userId(),
            claims.productId(),
            claims.transactionId(),
            firstNonBlank(claims.originalTransactionId(), reconcileResult.originalTransactionId())
        );
    }

    private Map<String, Object> payloadBody(AppStoreNotificationIngestRequest request) {
        if (request.payload() != null && !request.payload().isEmpty()) {
            return request.payload();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signedPayload", request.signedPayload());
        payload.put("notificationUuid", request.notificationUuid());
        payload.put("notificationType", request.notificationType());
        payload.put("subtype", request.subtype());
        return payload;
    }

    private String resolveNotificationUuid(AppStoreNotificationIngestRequest request, AppStoreJwsVerificationService.NotificationVerificationResult verificationResult) {
        if (request.notificationUuid() != null && !request.notificationUuid().isBlank()) {
            return request.notificationUuid().trim();
        }
        if (verificationResult.claims() != null && verificationResult.claims().notificationUuid() != null && !verificationResult.claims().notificationUuid().isBlank()) {
            return verificationResult.claims().notificationUuid();
        }
        String payloadHash = sha256HashService.hash(request.signedPayload());
        return payloadHash == null ? UUID.randomUUID().toString() : payloadHash;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String resolveNotificationProcessingStatus(String verificationStatus, SysBillingService.NotificationReconcileResult reconcileResult) {
        if (reconcileResult != null && reconcileResult.verified()) {
            return "reconciled";
        }
        if ("failed".equalsIgnoreCase(verificationStatus) || "rejected".equalsIgnoreCase(verificationStatus)) {
            return "rejected";
        }
        return PROCESSING_ACCEPTED;
    }

    private Map<String, Object> mapOfNonNull(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String key = String.valueOf(pairs[i]);
            Object value = pairs[i + 1];
            if (value != null) {
                values.put(key, value);
            }
        }
        return values;
    }

    private String resolveProcessingStatus(String verificationStatus) {
        if ("failed".equalsIgnoreCase(verificationStatus) || "rejected".equalsIgnoreCase(verificationStatus)) {
            return "rejected";
        }
        return PROCESSING_ACCEPTED;
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private AppStoreNotificationAcceptedView toView(SysAppStoreNotificationEntity entity, boolean duplicate) {
        return new AppStoreNotificationAcceptedView(
            entity.getAppCode(),
            entity.getNotificationUuid(),
            entity.getNotificationType(),
            entity.getSubtype(),
            entity.getVerificationStatus(),
            entity.getProcessingStatus(),
            duplicate
        );
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
