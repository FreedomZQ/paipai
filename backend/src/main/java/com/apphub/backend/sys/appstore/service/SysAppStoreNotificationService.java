package com.apphub.backend.sys.appstore.service;

import com.apphub.backend.common.util.Sha256HashService;
import com.apphub.backend.sys.billing.service.SysBillingService;
import com.apphub.backend.sys.appstore.entity.SysAppStoreNotificationEntity;
import com.apphub.backend.sys.appstore.service.crud.SysAppStoreNotificationCrudService;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationAcceptedView;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationIngestRequest;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationObservabilityView;
import com.apphub.backend.sys.billing.privacy.service.SysAppStoreRefundService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final SysAppStoreRefundService appStoreRefundService;

    @Autowired
    public SysAppStoreNotificationService(
        SysAppStoreNotificationCrudService appStoreNotificationCrudService,
        Sha256HashService sha256HashService,
        ObjectMapper objectMapper,
        AppStoreJwsVerificationService appStoreJwsVerificationService,
        SysBillingService sysBillingService,
        SysAppStoreRefundService appStoreRefundService
    ) {
        this.appStoreNotificationCrudService = appStoreNotificationCrudService;
        this.sha256HashService = sha256HashService;
        this.objectMapper = objectMapper;
        this.appStoreJwsVerificationService = appStoreJwsVerificationService;
        this.sysBillingService = sysBillingService;
        this.appStoreRefundService = appStoreRefundService;
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

        Map<String, Object> minimizedPayload = payloadBody(request);
        SysBillingService.NotificationReconcileResult reconcileResult = verificationResult.claims() == null || !"verified".equalsIgnoreCase(verificationResult.verificationStatus())
            ? new SysBillingService.NotificationReconcileResult("skipped_notification_not_verified", false, null, null, null)
            : sysBillingService.reconcileVerifiedNotification(appCode, verificationResult.claims(), minimizedPayload);
        SysAppStoreRefundService.NotificationRefundResult refundResult = verificationResult.claims() == null || !"verified".equalsIgnoreCase(verificationResult.verificationStatus())
            ? new SysAppStoreRefundService.NotificationRefundResult("skipped_notification_not_verified", null, null)
            : appStoreRefundService.handleVerifiedNotification(appCode, verificationResult.claims(), reconcileResult.userId());
        Map<String, Object> appSpecificGrant = Map.of("status", "skipped");

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
                "claims", minimizeNotificationClaims(verificationResult.claims())
            ),
            "reconcile", mapOfNonNull(
                "status", reconcileResult.status(),
                "verified", reconcileResult.verified(),
                "userId", reconcileResult.userId(),
                "originalTransactionId", reconcileResult.originalTransactionId(),
                "note", reconcileResult.note()
            ),
            "refund", mapOfNonNull(
                "status", refundResult.status(),
                "consumptionRequestId", refundResult.consumptionRequestId(),
                "refundCaseId", refundResult.refundCaseId()
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

    private Map<String, Object> payloadBody(AppStoreNotificationIngestRequest request) {
        // Apple 入站通知的完整 signedPayload 只允许进入哈希/密文审计域；普通 raw_payload_json 只保存可检索摘要。
        if (request.payload() != null && !request.payload().isEmpty()) {
            return sanitizePayloadMap(request.payload());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signedPayloadHash", sha256HashService.hash(request.signedPayload()));
        payload.put("signedPayloadPresent", request.signedPayload() != null && !request.signedPayload().isBlank());
        payload.put("notificationUuid", request.notificationUuid());
        payload.put("notificationType", request.notificationType());
        payload.put("subtype", request.subtype());
        return payload;
    }

    private Map<String, Object> minimizeNotificationClaims(AppStoreJwsVerificationService.NotificationClaims claims) {
        if (claims == null) {
            return null;
        }
        return mapOfNonNull(
            "notificationUuid", claims.notificationUuid(),
            "notificationType", claims.notificationType(),
            "subtype", claims.subtype(),
            "environment", claims.environment(),
            "originalTransactionId", claims.originalTransactionId(),
            "transactionId", claims.transactionId(),
            "productId", claims.productId(),
            "signedTransactionInfoHash", sha256HashService.hash(claims.signedTransactionInfo()),
            "signedRenewalInfoHash", sha256HashService.hash(claims.signedRenewalInfo()),
            "signedTransactionInfoPresent", claims.signedTransactionInfo() != null && !claims.signedTransactionInfo().isBlank(),
            "signedRenewalInfoPresent", claims.signedRenewalInfo() != null && !claims.signedRenewalInfo().isBlank()
        );
    }

    private Map<String, Object> sanitizePayloadMap(Map<String, Object> payload) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            sanitized.put(entry.getKey(), sanitizePayloadValue(entry.getKey(), entry.getValue()));
        }
        return sanitized;
    }

    @SuppressWarnings("unchecked")
    private Object sanitizePayloadValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (isSensitivePayloadKey(key)) {
            String raw = String.valueOf(value);
            return mapOfNonNull(
                "present", !raw.isBlank(),
                "hash", sha256HashService.hash(raw),
                "length", raw.length()
            );
        }
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> nestedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> nestedEntry : nested.entrySet()) {
                String nestedKey = String.valueOf(nestedEntry.getKey());
                nestedMap.put(nestedKey, sanitizePayloadValue(nestedKey, nestedEntry.getValue()));
            }
            return nestedMap;
        }
        return value;
    }

    private boolean isSensitivePayloadKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return normalized.contains("signedpayload")
            || normalized.contains("signedtransactioninfo")
            || normalized.contains("signedrenewalinfo")
            || normalized.contains("authorization")
            || normalized.contains("privatekey");
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
