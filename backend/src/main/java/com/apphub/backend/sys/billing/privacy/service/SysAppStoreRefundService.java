package com.apphub.backend.sys.billing.privacy.service;

import com.apphub.backend.common.util.Sha256HashService;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.appstore.service.AppStoreJwsVerificationService;
import com.apphub.backend.sys.appstore.service.AppStoreServerApiClient;
import com.apphub.backend.sys.auth.service.AppleCredentialEncryptionService;
import com.apphub.backend.sys.billing.entity.SysEntitlementSnapshotEntity;
import com.apphub.backend.sys.billing.entity.SysPurchaseTransactionEntity;
import com.apphub.backend.sys.billing.mapper.SysEntitlementSnapshotMapper;
import com.apphub.backend.sys.billing.mapper.SysPurchaseTransactionMapper;
import com.apphub.backend.sys.billing.privacy.entity.SysAppstoreConsumptionRequestEntity;
import com.apphub.backend.sys.billing.privacy.entity.SysAppstoreRefundCaseEntity;
import com.apphub.backend.sys.billing.privacy.entity.SysEntitlementLedgerEventEntity;
import com.apphub.backend.sys.billing.privacy.entity.SysRefundDecisionLogEntity;
import com.apphub.backend.sys.billing.privacy.mapper.SysAppstoreConsumptionRequestMapper;
import com.apphub.backend.sys.billing.privacy.mapper.SysAppstoreRefundCaseMapper;
import com.apphub.backend.sys.billing.privacy.mapper.SysEntitlementConsumptionReportMapper;
import com.apphub.backend.sys.billing.privacy.mapper.SysEntitlementLedgerEventMapper;
import com.apphub.backend.sys.billing.privacy.mapper.SysRefundDecisionLogMapper;
import com.apphub.backend.sys.billing.privacy.model.RefundTicketView;
import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import com.apphub.backend.sys.entitlement.entity.SysUserPlanSnapshotEntity;
import com.apphub.backend.sys.entitlement.mapper.SysUserPlanSnapshotMapper;
import com.apphub.backend.sys.entitlement.service.SysEntitlementCenterService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SysAppStoreRefundService {
    private static final String NAMESPACE_REFUND_POLICY = "billing_refund_policy";
    private static final int DEFAULT_USAGE_RATIO_LIMIT_MILLI = 10_000;
    private static final int CONSUMPTION_REPLY_LIMIT = 50;

    private final SysPurchaseTransactionMapper purchaseTransactionMapper;
    private final SysEntitlementSnapshotMapper entitlementSnapshotMapper;
    private final SysPrivacyConsentService privacyConsentService;
    private final SysAppstoreRefundCaseMapper refundCaseMapper;
    private final SysAppstoreConsumptionRequestMapper consumptionRequestMapper;
    private final SysRefundDecisionLogMapper refundDecisionLogMapper;
    private final SysEntitlementLedgerEventMapper entitlementLedgerEventMapper;
    private final SysEntitlementConsumptionReportMapper consumptionReportMapper;
    private final SysUserPlanSnapshotMapper userPlanSnapshotMapper;
    private final SysEntitlementCenterService entitlementCenterService;
    private final AppDefinitionService appDefinitionService;
    private final AppStoreServerApiClient appStoreServerApiClient;
    private final SysRemoteConfigService remoteConfigService;
    private final Sha256HashService sha256HashService;
    private final AppleCredentialEncryptionService appleCredentialEncryptionService;
    private final ObjectMapper objectMapper;

    public SysAppStoreRefundService(
        SysPurchaseTransactionMapper purchaseTransactionMapper,
        SysEntitlementSnapshotMapper entitlementSnapshotMapper,
        SysPrivacyConsentService privacyConsentService,
        SysAppstoreRefundCaseMapper refundCaseMapper,
        SysAppstoreConsumptionRequestMapper consumptionRequestMapper,
        SysRefundDecisionLogMapper refundDecisionLogMapper,
        SysEntitlementLedgerEventMapper entitlementLedgerEventMapper,
        SysEntitlementConsumptionReportMapper consumptionReportMapper,
        SysUserPlanSnapshotMapper userPlanSnapshotMapper,
        SysEntitlementCenterService entitlementCenterService,
        AppDefinitionService appDefinitionService,
        AppStoreServerApiClient appStoreServerApiClient,
        SysRemoteConfigService remoteConfigService,
        Sha256HashService sha256HashService,
        AppleCredentialEncryptionService appleCredentialEncryptionService,
        ObjectMapper objectMapper
    ) {
        this.purchaseTransactionMapper = purchaseTransactionMapper;
        this.entitlementSnapshotMapper = entitlementSnapshotMapper;
        this.privacyConsentService = privacyConsentService;
        this.refundCaseMapper = refundCaseMapper;
        this.consumptionRequestMapper = consumptionRequestMapper;
        this.refundDecisionLogMapper = refundDecisionLogMapper;
        this.entitlementLedgerEventMapper = entitlementLedgerEventMapper;
        this.consumptionReportMapper = consumptionReportMapper;
        this.userPlanSnapshotMapper = userPlanSnapshotMapper;
        this.entitlementCenterService = entitlementCenterService;
        this.appDefinitionService = appDefinitionService;
        this.appStoreServerApiClient = appStoreServerApiClient;
        this.remoteConfigService = remoteConfigService;
        this.sha256HashService = sha256HashService;
        this.appleCredentialEncryptionService = appleCredentialEncryptionService;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据 Apple 通知类型进入最小化处理分支。
     *
     * 中文说明：CONSUMPTION_REQUEST 只生成待回传任务，不改变权益；REFUND/REVOKE/REFUND_REVERSED
     * 才能改变服务端权益投影，并且恢复权益前必须重新读取 App Store Server API 的权威交易状态。
     */
    @Transactional
    public NotificationRefundResult handleVerifiedNotification(
        String appCode,
        AppStoreJwsVerificationService.NotificationClaims claims,
        Long reconciledUserId
    ) {
        if (claims == null || claims.notificationType() == null) {
            return new NotificationRefundResult("skipped_missing_claims", null, null);
        }
        String notificationType = claims.notificationType().trim().toUpperCase(Locale.ROOT);
        return switch (notificationType) {
            case "CONSUMPTION_REQUEST" -> createConsumptionRequest(appCode, claims, reconciledUserId);
            case "REFUND", "REVOKE" -> applyRefund(appCode, claims, reconciledUserId, notificationType, lookupAuthoritativeTransaction(appCode, claims));
            case "REFUND_DECLINED" -> closeRefundCase(appCode, claims, "refund_declined");
            case "REFUND_REVERSED" -> reverseRefund(appCode, claims, reconciledUserId, lookupAuthoritativeTransaction(appCode, claims));
            default -> new NotificationRefundResult("skipped_not_refund_related", null, null);
        };
    }

    @Scheduled(fixedDelayString = "${backend.billing.appstore.consumption-reply.fixed-delay-ms:60000}")
    public void processDueConsumptionReplies() {
        OffsetDateTime now = now();
        List<SysAppstoreConsumptionRequestEntity> due = consumptionRequestMapper.selectDue(now, CONSUMPTION_REPLY_LIMIT);
        if (due == null || due.isEmpty()) {
            return;
        }
        for (SysAppstoreConsumptionRequestEntity request : due) {
            try {
                processConsumptionReply(request.getId(), now());
            } catch (Exception ignored) {
            }
        }
    }

    @Transactional
    public void processConsumptionReply(Long consumptionRequestId, OffsetDateTime now) {
        SysAppstoreConsumptionRequestEntity task = consumptionRequestMapper.selectById(consumptionRequestId);
        if (task == null || !List.of("pending", "retry").contains(firstNonBlank(task.getReplyStatus(), ""))) {
            return;
        }
        if (task.getDeadlineAt() != null && !task.getDeadlineAt().isAfter(now)) {
            task.setReplyStatus("expired");
            task.setUpdatedAt(now);
            consumptionRequestMapper.updateById(task);
            return;
        }
        boolean consented = task.getUserId() != null && privacyConsentService.hasActiveConsent(
            task.getAppCode(),
            task.getUserId(),
            SysPrivacyConsentService.CONSENT_REFUND_CONSUMPTION_SHARING
        );
        // 合规控制：每次发送 Apple consumption 信息前都重新读取家长/付款人当前同意状态；撤回后不再发送新数据。
        task.setConsentStatus(consented ? "granted" : "not_granted");
        if (!consented) {
            task.setReplyStatus("not_sent_no_consent");
            task.setReplyContextJson(toJson(Map.of(
                "reason", "payer_or_guardian_consent_missing_or_revoked",
                "childrenDataExcluded", true
            )));
            task.setUpdatedAt(now);
            consumptionRequestMapper.updateById(task);
            ensureDecisionLog(task, null, "not_sent_no_consent", "NOT_SENT_NO_CONSENT", null);
            return;
        }

        RefundDecision decision = decideRefundPreference(task);
        AppStoreServerApiClient.ConsumptionRequestBody payload = decision.applePayload();
        AppStoreServerApiClient.ConsumptionSendResult sendResult = appStoreServerApiClient.sendConsumptionInformation(
            task.getTransactionId(),
            payload,
            appStoreConfiguration(appDefinitionService.get(task.getAppCode()).orElse(null))
        );
        task.setDeliveryStatus(payload.deliveryStatus());
        task.setConsumptionPercentage(payload.consumptionPercentage());
        task.setSampleContentProvided(payload.sampleContentProvided());
        task.setRefundPreference(payload.refundPreference());
        task.setAppleRequestPayloadJson(toJson(payload));
        task.setReplyContextJson(toJson(decision.context()));
        task.setAttemptCount((task.getAttemptCount() == null ? 0 : task.getAttemptCount()) + 1);
        task.setLastAttemptAt(now);
        task.setLastHttpStatus(sendResult.httpStatus());
        task.setLastErrorCode(sendResult.accepted() ? null : sendResult.status());
        task.setLastErrorMessage(sendResult.accepted() ? null : sendResult.note());
        task.setReplyStatus(sendResult.accepted() ? "sent" : (sendResult.retryable() ? "retry" : "failed"));
        task.setNextRetryAt(sendResult.accepted() ? null : now.plusMinutes(10));
        task.setUpdatedAt(now);
        consumptionRequestMapper.updateById(task);

        ensureDecisionLog(task, decision, decision.decisionCode(), payload.refundPreference(), sendResult);
        SysAppstoreRefundCaseEntity refundCase = refundCaseMapper.selectLatestByTransactionId(task.getAppCode(), task.getTransactionId());
        if (refundCase != null) {
            refundCase.setRefundCaseStatus(sendResult.accepted() ? "decision_sent" : task.getReplyStatus());
            refundCase.setDecisionCode(decision.decisionCode());
            refundCase.setDecisionMessage(decision.displayMessage());
            refundCase.setUsageCountUsed(decision.usedCount());
            refundCase.setUsageCountTotal(decision.totalCount());
            refundCase.setUsageRatioMilli(decision.usageRatioMilli());
            refundCase.setUsageRatioThresholdMilli(decision.thresholdMilli());
            refundCase.setAppleRefundPreference(payload.refundPreference());
            refundCase.setDecisionContextJson(toJson(decision.context()));
            refundCase.setUpdatedAt(now);
            refundCaseMapper.updateById(refundCase);
        }
    }

    public RefundTicketView lookupTicketsByUser(String appCode, Long userId) {
        List<SysPurchaseTransactionEntity> transactions = purchaseTransactionMapper.selectRecentByUser(appCode, userId, 50);
        return toTicketView(appCode, userId, "user_id", userId == null ? null : String.valueOf(userId), transactions);
    }

    private NotificationRefundResult createConsumptionRequest(
        String appCode,
        AppStoreJwsVerificationService.NotificationClaims claims,
        Long reconciledUserId
    ) {
        SysAppstoreConsumptionRequestEntity existing = consumptionRequestMapper.selectByNotificationUuid(appCode, claims.notificationUuid());
        if (existing != null) {
            return new NotificationRefundResult("duplicate_consumption_request", existing.getId(), null);
        }
        OffsetDateTime now = now();
        SysPurchaseTransactionEntity transaction = findTransaction(appCode, claims.transactionId(), claims.originalTransactionId());
        Long userId = reconciledUserId == null && transaction != null ? transaction.getUserId() : reconciledUserId;
        OffsetDateTime appleSignedDate = now;
        OffsetDateTime deadlineAt = appleSignedDate.plusHours(12);

        SysAppstoreConsumptionRequestEntity task = new SysAppstoreConsumptionRequestEntity();
        task.setAppCode(appCode);
        task.setUserId(userId);
        task.setNotificationUuid(claims.notificationUuid());
        task.setTransactionId(firstNonBlank(claims.transactionId(), transaction == null ? null : transaction.getTransactionId()));
        task.setOriginalTransactionId(firstNonBlank(claims.originalTransactionId(), transaction == null ? null : transaction.getOriginalTransactionId()));
        task.setProductId(firstNonBlank(claims.productId(), transaction == null ? null : transaction.getProductId()));
        task.setProductType(transaction == null ? null : transaction.getProductType());
        task.setAppleSignedDate(appleSignedDate);
        task.setReceivedAt(now);
        task.setDeadlineAt(deadlineAt);
        task.setConsentStatus(userId != null && privacyConsentService.hasActiveConsent(appCode, userId, SysPrivacyConsentService.CONSENT_REFUND_CONSUMPTION_SHARING) ? "granted" : "unknown");
        task.setReplyStatus("pending");
        task.setAttemptCount(0);
        task.setNextRetryAt(now);
        task.setReplyContextJson(toJson(Map.of(
            "privacyBaseline", "no child names, no raw images, no raw audio, no OCR text, no learning text",
            "childrenDataExcluded", true
        )));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        consumptionRequestMapper.insert(task);

        SysAppstoreRefundCaseEntity refundCase = new SysAppstoreRefundCaseEntity();
        refundCase.setAppCode(appCode);
        refundCase.setUserId(userId);
        refundCase.setTransactionId(task.getTransactionId());
        refundCase.setOriginalTransactionId(task.getOriginalTransactionId());
        refundCase.setProductId(task.getProductId());
        refundCase.setProductType(task.getProductType());
        refundCase.setNotificationUuid(claims.notificationUuid());
        refundCase.setRefundCaseStatus("consumption_requested");
        refundCase.setConsumptionRequestId(task.getId());
        refundCase.setRefundRequestedAt(now);
        refundCase.setPurchasePriceMilliAmount(transaction == null ? null : transaction.getPriceMilliAmount());
        refundCase.setCurrency(transaction == null ? null : transaction.getCurrency());
        refundCase.setMetadataJson(toJson(Map.of("childrenDataExcluded", true)));
        refundCase.setCreatedAt(now);
        refundCase.setUpdatedAt(now);
        refundCaseMapper.insert(refundCase);
        return new NotificationRefundResult("consumption_request_queued", task.getId(), refundCase.getId());
    }

    private NotificationRefundResult applyRefund(
        String appCode,
        AppStoreJwsVerificationService.NotificationClaims claims,
        Long reconciledUserId,
        String notificationType,
        AppStoreServerApiClient.LookupResult lookupResult
    ) {
        OffsetDateTime now = now();
        SysPurchaseTransactionEntity transaction = findTransaction(appCode, claims.transactionId(), claims.originalTransactionId());
        AppStoreJwsVerificationService.TransactionClaims authoritativeClaims = lookupResult != null && lookupResult.claims() != null
            ? lookupResult.claims()
            : null;
        // 退款和撤销只按父账号/交易维度处理，不读取儿童档案、图片、音频、OCR 文本或逐句学习内容。
        Long userId = reconciledUserId == null && transaction != null ? transaction.getUserId() : reconciledUserId;
        SysAppstoreRefundCaseEntity refundCase = firstNonNull(
            refundCaseMapper.selectByNotificationUuid(appCode, claims.notificationUuid()),
            transaction == null ? null : refundCaseMapper.selectLatestByTransactionId(appCode, transaction.getTransactionId())
        );
        if (refundCase == null) {
            refundCase = new SysAppstoreRefundCaseEntity();
            refundCase.setAppCode(appCode);
            refundCase.setCreatedAt(now);
        }
        refundCase.setUserId(userId);
        refundCase.setTransactionId(firstNonBlank(authoritativeClaims == null ? null : authoritativeClaims.transactionId(), claims.transactionId(), transaction == null ? null : transaction.getTransactionId()));
        refundCase.setOriginalTransactionId(firstNonBlank(authoritativeClaims == null ? null : authoritativeClaims.originalTransactionId(), claims.originalTransactionId(), transaction == null ? null : transaction.getOriginalTransactionId()));
        refundCase.setProductId(firstNonBlank(authoritativeClaims == null ? null : authoritativeClaims.productId(), claims.productId(), transaction == null ? null : transaction.getProductId()));
        refundCase.setProductType(resolveProductType(authoritativeClaims, transaction));
        refundCase.setAppleRefundNotificationUuid(claims.notificationUuid());
        refundCase.setRefundCaseStatus("REVOKE".equals(notificationType) ? "revoked" : "refunded");
        refundCase.setRefundResolvedAt(now);
        refundCase.setRevocationAt(authoritativeClaims == null ? now : authoritativeClaims.revocationDate());
        refundCase.setRevocationReason(authoritativeClaims == null ? null : authoritativeClaims.revocationReason());
        refundCase.setRevocationPercentage(authoritativeClaims == null ? null : authoritativeClaims.revocationPercentage());
        refundCase.setPurchasePriceMilliAmount(firstNonNullLong(authoritativeClaims == null ? null : authoritativeClaims.price(), transaction == null ? null : transaction.getPriceMilliAmount()));
        refundCase.setCurrency(firstNonBlank(authoritativeClaims == null ? null : authoritativeClaims.currency(), transaction == null ? null : transaction.getCurrency()));
        refundCase.setUpdatedAt(now);
        if (refundCase.getId() == null) {
            refundCaseMapper.insert(refundCase);
        } else {
            refundCaseMapper.updateById(refundCase);
        }

        markTransactionRefunded(
            appCode,
            refundCase.getTransactionId(),
            refundCase.getOriginalTransactionId(),
            refundCase.getRefundCaseStatus(),
            refundCase.getRevocationAt(),
            refundCase.getRevocationReason(),
            refundCase.getRevocationPercentage(),
            now
        );
        revokeEntitlementSnapshots(appCode, userId, refundCase.getProductId(), now, refundCase.getRefundCaseStatus());
        revokePurchaseSnapshots(appCode, userId, refundCase.getProductId(), transaction, now, refundCase.getRefundCaseStatus());
        writeRefundLedgerEvent(appCode, userId, refundCase, notificationType, now);
        return new NotificationRefundResult(refundCase.getRefundCaseStatus(), null, refundCase.getId());
    }

    private NotificationRefundResult reverseRefund(
        String appCode,
        AppStoreJwsVerificationService.NotificationClaims claims,
        Long reconciledUserId,
        AppStoreServerApiClient.LookupResult lookupResult
    ) {
        OffsetDateTime now = now();
        AppStoreJwsVerificationService.TransactionClaims authoritativeClaims = lookupResult != null && lookupResult.claims() != null
            ? lookupResult.claims()
            : null;
        // REFUND_REVERSED 不能盲目恢复权益；只有 Apple Server API 权威交易仍有效且未再次撤销时才恢复。
        SysAppstoreRefundCaseEntity refundCase = firstNonNull(
            refundCaseMapper.selectByNotificationUuid(appCode, claims.notificationUuid()),
            refundCaseMapper.selectLatestByTransactionId(appCode, claims.transactionId())
        );
        if (refundCase != null) {
            refundCase.setRefundCaseStatus(lookupResult != null && lookupResult.isVerified() ? "refund_reversed" : "manual_review");
            refundCase.setRefundResolvedAt(now);
            refundCase.setUpdatedAt(now);
            refundCaseMapper.updateById(refundCase);
        }
        String transactionId = firstNonBlank(
            authoritativeClaims == null ? null : authoritativeClaims.transactionId(),
            claims.transactionId()
        );
        String originalTransactionId = firstNonBlank(
            authoritativeClaims == null ? null : authoritativeClaims.originalTransactionId(),
            claims.originalTransactionId()
        );
        String productId = firstNonBlank(
            authoritativeClaims == null ? null : authoritativeClaims.productId(),
            claims.productId(),
            refundCase == null ? null : refundCase.getProductId()
        );
        if (lookupResult != null && lookupResult.isVerified() && authoritativeClaims != null && hasText(transactionId) && hasText(productId)) {
            markTransactionRefunded(
                appCode,
                transactionId,
                originalTransactionId,
                "refund_reversed",
                null,
                null,
                null,
                now
            );
            restoreEntitlementSnapshots(appCode, reconciledUserId, productId, authoritativeClaims, now);
            restorePurchaseSnapshots(appCode, reconciledUserId, productId, authoritativeClaims, now);
            writeRefundLedgerEvent(appCode, reconciledUserId, refundCase, "REFUND_REVERSED", now);
            return new NotificationRefundResult("refund_reversed", null, refundCase == null ? null : refundCase.getId());
        }
        markTransactionRefunded(appCode, transactionId, originalTransactionId, "manual_review", null, null, null, now);
        return new NotificationRefundResult("manual_review", null, refundCase == null ? null : refundCase.getId());
    }

    private NotificationRefundResult closeRefundCase(
        String appCode,
        AppStoreJwsVerificationService.NotificationClaims claims,
        String status
    ) {
        OffsetDateTime now = now();
        SysAppstoreRefundCaseEntity refundCase = firstNonNull(
            refundCaseMapper.selectByNotificationUuid(appCode, claims.notificationUuid()),
            refundCaseMapper.selectLatestByTransactionId(appCode, claims.transactionId())
        );
        if (refundCase == null) {
            return new NotificationRefundResult("skipped_missing_refund_case", null, null);
        }
        refundCase.setRefundCaseStatus(status);
        refundCase.setRefundResolvedAt(now);
        refundCase.setUpdatedAt(now);
        refundCaseMapper.updateById(refundCase);
        markTransactionRefunded(appCode, claims.transactionId(), claims.originalTransactionId(), status, null, null, null, now);
        return new NotificationRefundResult(status, null, refundCase.getId());
    }

    private RefundDecision decideRefundPreference(SysAppstoreConsumptionRequestEntity task) {
        UsageSnapshot usage = usageSnapshot(task.getAppCode(), task.getTransactionId());
        int threshold = thresholdMilli(task.getAppCode(), task.getProductId());
        String deliveryStatus = "DELIVERED";
        Integer consumptionPercentage = null;
        String preference;
        String decisionCode;
        if (usage.totalCount() <= 0) {
            decisionCode = "manual_review";
            preference = "GRANT_PRORATED";
        } else if (usage.usageRatioMilli() > threshold) {
            decisionCode = "not_support_refund";
            preference = "DECLINE";
        } else if (usage.usageRatioMilli() == 0) {
            decisionCode = "support_full_refund";
            preference = "GRANT_FULL";
        } else {
            decisionCode = "support_prorated_refund";
            preference = "GRANT_PRORATED";
        }
        if (!"subscription".equalsIgnoreCase(task.getProductType())) {
            consumptionPercentage = Math.max(0, Math.min(100_000, usage.usageRatioMilli()));
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("usedEntitlementCount", usage.usedCount());
        context.put("totalEntitlementCount", usage.totalCount());
        context.put("usageRatioMilli", usage.usageRatioMilli());
        context.put("usageRatioLimitMilli", threshold);
        context.put("childrenDataExcluded", true);
        context.put("negativeList", List.of("child_name", "birth_date", "raw_image", "raw_audio", "ocr_text", "learning_text", "precise_location"));
        return new RefundDecision(
            decisionCode,
            "not_support_refund".equals(decisionCode) ? "不支持退款" : "退款偏好已按聚合使用情况生成",
            usage.usedCount(),
            usage.totalCount(),
            usage.usageRatioMilli(),
            threshold,
            context,
            new AppStoreServerApiClient.ConsumptionRequestBody(
                true,
                consumptionPercentage,
                deliveryStatus,
                true,
                preference
            )
        );
    }

    private UsageSnapshot usageSnapshot(String appCode, String transactionId) {
        Integer granted = entitlementLedgerEventMapper.sumGrantedByTransaction(appCode, transactionId);
        Integer consumed = entitlementLedgerEventMapper.sumConsumedByTransaction(appCode, transactionId);
        Integer reported = consumptionReportMapper.sumAcceptedQuantityByTransaction(appCode, transactionId);
        int total = granted == null ? 0 : Math.max(granted, 0);
        int used = Math.max(consumed == null ? 0 : consumed, reported == null ? 0 : reported);
        int ratio = total <= 0 ? 0 : (int) Math.min(100_000L, Math.floorDiv((long) used * 100_000L, total));
        return new UsageSnapshot(used, total, ratio);
    }

    private int thresholdMilli(String appCode, String productId) {
        RemoteConfigNamespaceView namespace = remoteConfigService.loadNamespace(appCode, NAMESPACE_REFUND_POLICY);
        Object productValue = namespace == null || namespace.items() == null || productId == null
            ? null
            : namespace.items().get("products." + productId + ".usage_ratio_limit_milli");
        Object globalValue = namespace == null || namespace.items() == null
            ? null
            : namespace.items().get("usage_ratio_limit_milli");
        return intValue(firstNonNull(productValue, globalValue), DEFAULT_USAGE_RATIO_LIMIT_MILLI);
    }

    private void ensureDecisionLog(
        SysAppstoreConsumptionRequestEntity task,
        RefundDecision decision,
        String decisionCode,
        String appleRefundPreference,
        AppStoreServerApiClient.ConsumptionSendResult sendResult
    ) {
        SysRefundDecisionLogEntity log = new SysRefundDecisionLogEntity();
        log.setAppCode(task.getAppCode());
        log.setUserId(task.getUserId());
        SysAppstoreRefundCaseEntity refundCase = refundCaseMapper.selectLatestByTransactionId(task.getAppCode(), task.getTransactionId());
        log.setRefundCaseId(refundCase == null ? null : refundCase.getId());
        log.setConsumptionRequestId(task.getId());
        log.setTransactionId(task.getTransactionId());
        log.setOriginalTransactionId(task.getOriginalTransactionId());
        log.setProductId(task.getProductId());
        log.setLookupInputJson(toJson(Map.of("transactionId", firstNonBlank(task.getTransactionId(), ""), "originalTransactionId", firstNonBlank(task.getOriginalTransactionId(), ""))));
        log.setTicketSnapshotJson(toJson(mapOfNonNull(
            "productId", firstNonBlank(task.getProductId(), ""),
            "productType", firstNonBlank(task.getProductType(), ""),
            "transactionId", firstNonBlank(task.getTransactionId(), ""),
            "originalTransactionId", firstNonBlank(task.getOriginalTransactionId(), ""),
            "purchasePriceMilliAmount", refundCase == null ? null : refundCase.getPurchasePriceMilliAmount(),
            "currency", refundCase == null ? null : refundCase.getCurrency(),
            "refundCaseStatus", refundCase == null ? null : refundCase.getRefundCaseStatus()
        )));
        log.setUsageCountUsed(decision == null ? null : decision.usedCount());
        log.setUsageCountTotal(decision == null ? null : decision.totalCount());
        log.setUsageRatioMilli(decision == null ? null : decision.usageRatioMilli());
        log.setUsageRatioThresholdMilli(decision == null ? null : decision.thresholdMilli());
        log.setUsageSnapshotJson(decision == null ? null : toJson(decision.context()));
        log.setPolicySnapshotJson(toJson(buildPolicySnapshot(task.getAppCode(), task.getProductId(), decision == null ? DEFAULT_USAGE_RATIO_LIMIT_MILLI : decision.thresholdMilli())));
        log.setComparisonResultJson(decision == null ? null : toJson(decision.context()));
        log.setDecisionCode(decisionCode);
        log.setDisplayMessage(decision == null ? null : decision.displayMessage());
        log.setAppleRefundPreference(appleRefundPreference);
        log.setApplePayloadHash(task.getAppleRequestPayloadJson() == null ? null : sha256HashService.hash(task.getAppleRequestPayloadJson()));
        AppleCredentialEncryptionService.EncryptionEnvelope payloadEnvelope = encryptAuditPayload(task.getAppleRequestPayloadJson());
        log.setApplePayloadCiphertext(payloadEnvelope == null ? null : toJson(payloadEnvelope));
        log.setAppleHttpStatus(sendResult == null ? null : sendResult.httpStatus());
        AppleCredentialEncryptionService.EncryptionEnvelope responseEnvelope = encryptAuditPayload(sendResult == null ? null : toJson(Map.of(
            "status", sendResult.status(),
            "httpStatus", sendResult.httpStatus(),
            "retryable", sendResult.retryable(),
            "note", sendResult.note(),
            "diagnostics", sendResult.diagnostics()
        )));
        log.setAppleResponseCiphertext(responseEnvelope == null ? null : toJson(responseEnvelope));
        log.setPiiKeyVersion(firstNonBlank(payloadEnvelope == null ? null : payloadEnvelope.keyId(), responseEnvelope == null ? null : responseEnvelope.keyId()));
        log.setCreatedAt(now());
        refundDecisionLogMapper.insert(log);
    }

    private AppleCredentialEncryptionService.EncryptionEnvelope encryptAuditPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return appleCredentialEncryptionService.encrypt(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private RefundTicketView toTicketView(String appCode, Long userId, String lookupType, String lookupValue, List<SysPurchaseTransactionEntity> transactions) {
        List<RefundTicketView.TicketItem> items = transactions == null ? List.of() : transactions.stream()
            .map(transaction -> {
                UsageSnapshot usage = usageSnapshot(appCode, transaction.getTransactionId());
                return new RefundTicketView.TicketItem(
                    transaction.getId(),
                    transaction.getOrderNo(),
                    transaction.getTransactionId(),
                    transaction.getOriginalTransactionId(),
                    transaction.getProductId(),
                    transaction.getProductType(),
                    transaction.getPurchaseAt() == null ? null : transaction.getPurchaseAt().toString(),
                    transaction.getPriceMilliAmount(),
                    transaction.getCurrency(),
                    transaction.getVerificationStatus(),
                    firstNonBlank(transaction.getRefundStatus(), "none"),
                    transaction.getRevocationAt() == null ? null : transaction.getRevocationAt().toString(),
                    usage.usedCount(),
                    usage.totalCount(),
                    usage.usageRatioMilli()
                );
            })
            .toList();
        return new RefundTicketView(appCode, userId, lookupType, lookupValue, items);
    }

    private SysPurchaseTransactionEntity findTransaction(String appCode, String transactionId, String originalTransactionId) {
        if (hasText(transactionId)) {
            // 中文说明：这里使用显式列名 QueryWrapper，而不是 LambdaQueryWrapper。
            // 退款服务的纯 Mockito 单测不会初始化 MyBatis-Plus 实体 lambda 缓存；显式列名也避免测试环境
            // 因元数据未加载而误报，同时 SQL 字段固定不可由外部输入控制。
            SysPurchaseTransactionEntity byTransaction = purchaseTransactionMapper.selectOne(
                new QueryWrapper<SysPurchaseTransactionEntity>()
                    .eq("app_code", appCode)
                    .eq("transaction_id", transactionId)
                    .orderByDesc("updated_at")
                    .orderByDesc("id")
                    .last("LIMIT 1")
            );
            if (byTransaction != null) {
                return byTransaction;
            }
        }
        if (!hasText(originalTransactionId)) {
            return null;
        }
        return purchaseTransactionMapper.selectOne(
            new QueryWrapper<SysPurchaseTransactionEntity>()
                .eq("app_code", appCode)
                .eq("original_transaction_id", originalTransactionId)
                .orderByDesc("updated_at")
                .orderByDesc("id")
                .last("LIMIT 1")
        );
    }

    private void markTransactionRefunded(
        String appCode,
        String transactionId,
        String originalTransactionId,
        String refundStatus,
        OffsetDateTime revocationAt,
        String revocationReason,
        Integer revocationPercentage,
        OffsetDateTime now
    ) {
        UpdateWrapper<SysPurchaseTransactionEntity> wrapper = new UpdateWrapper<SysPurchaseTransactionEntity>()
            .eq("app_code", appCode)
            .set("refund_status", refundStatus)
            .set("revocation_at", revocationAt)
            .set("revocation_reason", revocationReason)
            .set("revocation_percentage", revocationPercentage)
            .set("updated_at", now);
        if (hasText(transactionId)) {
            wrapper.eq("transaction_id", transactionId);
        } else if (hasText(originalTransactionId)) {
            wrapper.eq("original_transaction_id", originalTransactionId);
        } else {
            return;
        }
        purchaseTransactionMapper.update(null, wrapper);
    }

    private void revokeEntitlementSnapshots(String appCode, Long userId, String productId, OffsetDateTime now, String reasonCode) {
        if (userId == null) {
            return;
        }
        String entitlementCode = resolveEntitlementCode(appCode, productId);
        if (!hasText(entitlementCode)) {
            return;
        }
        // 只撤销本次商品映射到的权益码，避免单笔退款误伤同一用户的其他 App Store 或后台赠送权益。
        entitlementSnapshotMapper.update(null, new UpdateWrapper<SysEntitlementSnapshotEntity>()
            .eq("app_code", appCode)
            .eq("user_id", userId)
            .eq("entitlement_code", entitlementCode)
            .eq("status", "active")
            .set("status", "revoked")
            .set("updated_at", now)
            .set("payload_json", toJson(Map.of(
                "reasonCode", reasonCode,
                "productId", firstNonBlank(productId, ""),
                "entitlementCode", entitlementCode,
                "childrenDataExcluded", true
            ))));
    }

    private void restoreEntitlementSnapshots(
        String appCode,
        Long userId,
        String productId,
        AppStoreJwsVerificationService.TransactionClaims claims,
        OffsetDateTime now
    ) {
        if (userId == null) {
            return;
        }
        String entitlementCode = resolveEntitlementCode(appCode, productId);
        if (!hasText(entitlementCode)) {
            return;
        }
        entitlementSnapshotMapper.update(null, new UpdateWrapper<SysEntitlementSnapshotEntity>()
            .eq("app_code", appCode)
            .eq("user_id", userId)
            .eq("entitlement_code", entitlementCode)
            .eq("status", "revoked")
            .set("status", resolveEntitlementStatus(claims, now))
            .set("expires_at", claims == null ? null : claims.expiresDate())
            .set("updated_at", now)
            .set("payload_json", toJson(Map.of(
                "reasonCode", "refund_reversed",
                "productId", firstNonBlank(productId, ""),
                "entitlementCode", entitlementCode,
                "childrenDataExcluded", true
            ))));
    }

    private void revokePurchaseSnapshots(
        String appCode,
        Long userId,
        String productId,
        SysPurchaseTransactionEntity transaction,
        OffsetDateTime now,
        String reasonCode
    ) {
        if (userId == null) {
            return;
        }
        String entitlementCode = resolveEntitlementCode(appCode, productId);
        if (!hasText(entitlementCode)) {
            return;
        }
        // 购买时权益快照也必须失效，否则退款后仍会被统一权益中心合并成付费权益。
        userPlanSnapshotMapper.update(null, new UpdateWrapper<SysUserPlanSnapshotEntity>()
            .eq("app_code", appCode)
            .eq("user_id", userId)
            .eq("entitlement_code", entitlementCode)
            .eq("status", "active")
            .set("status", "revoked")
            .set("updated_at", now)
            .set("feature_snapshot_json", toJson(mapOfNonNull(
                "reasonCode", reasonCode,
                "transactionId", transaction == null ? null : transaction.getTransactionId(),
                "originalTransactionId", transaction == null ? null : transaction.getOriginalTransactionId(),
                "childrenDataExcluded", true
            ))));
    }

    private void restorePurchaseSnapshots(
        String appCode,
        Long userId,
        String productId,
        AppStoreJwsVerificationService.TransactionClaims claims,
        OffsetDateTime now
    ) {
        if (userId == null) {
            return;
        }
        String entitlementCode = resolveEntitlementCode(appCode, productId);
        if (!hasText(entitlementCode)) {
            return;
        }
        userPlanSnapshotMapper.update(null, new UpdateWrapper<SysUserPlanSnapshotEntity>()
            .eq("app_code", appCode)
            .eq("user_id", userId)
            .eq("entitlement_code", entitlementCode)
            .eq("status", "revoked")
            .set("status", claims != null && claims.expiresDate() != null && !claims.expiresDate().isAfter(now) ? "expired" : "active")
            .set("expires_at", claims == null ? null : claims.expiresDate())
            .set("updated_at", now)
            .set("feature_snapshot_json", toJson(mapOfNonNull(
                "reasonCode", "refund_reversed",
                "transactionId", claims == null ? null : claims.transactionId(),
                "originalTransactionId", claims == null ? null : claims.originalTransactionId(),
                "childrenDataExcluded", true
            ))));
    }

    private String resolveEntitlementStatus(AppStoreJwsVerificationService.TransactionClaims claims, OffsetDateTime now) {
        if (claims == null) {
            return "active";
        }
        if (claims.revocationDate() != null) {
            return "revoked";
        }
        if (claims.expiresDate() != null && !claims.expiresDate().isAfter(now)) {
            return "expired";
        }
        return "active";
    }

    private String resolveProductType(AppStoreJwsVerificationService.TransactionClaims claims, SysPurchaseTransactionEntity transaction) {
        String normalizedType = claims == null ? null : normalizeProductType(claims.type());
        if (hasText(normalizedType)) {
            return normalizedType;
        }
        return transaction == null ? null : transaction.getProductType();
    }

    private String normalizeProductType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (normalized.contains("consumable")) {
            return "consumable";
        }
        if (normalized.contains("non_consumable")) {
            return "non_consumable";
        }
        if (normalized.contains("subscription")) {
            return "subscription";
        }
        return normalized;
    }

    private String resolveEntitlementCode(String appCode, String productId) {
        if (!hasText(appCode) || !hasText(productId)) {
            return null;
        }
        return entitlementCenterService.resolveEntitlementCodeByProduct(appCode, productId);
    }

    private Long firstNonNullLong(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private AppStoreServerApiClient.LookupResult lookupAuthoritativeTransaction(
        String appCode,
        AppStoreJwsVerificationService.NotificationClaims claims
    ) {
        if (claims == null) {
            return null;
        }
        AppDefinition appDefinition = appDefinitionService.get(appCode).orElse(null);
        if (appDefinition == null) {
            return null;
        }
        return appStoreServerApiClient.lookup(
            new AppStoreServerApiClient.LookupCommand(
                claims.transactionId(),
                claims.originalTransactionId(),
                claims.productId(),
                claims.environment()
            ),
            appStoreConfiguration(appDefinition)
        );
    }

    private void writeRefundLedgerEvent(
        String appCode,
        Long userId,
        SysAppstoreRefundCaseEntity refundCase,
        String notificationType,
        OffsetDateTime now
    ) {
        if (userId == null || refundCase == null) {
            return;
        }
        SysEntitlementLedgerEventEntity event = new SysEntitlementLedgerEventEntity();
        event.setAppCode(appCode);
        event.setUserId(userId);
        event.setEventId(UUID.randomUUID());
        event.setEventType("refund_reversed".equalsIgnoreCase(refundCase.getRefundCaseStatus()) ? "restore" : "revoke");
        event.setEntitlementCode(resolveEntitlementCode(appCode, refundCase.getProductId()) == null
            ? firstNonBlank(refundCase.getProductId(), "appstore_purchase")
            : resolveEntitlementCode(appCode, refundCase.getProductId()));
        event.setTransactionId(refundCase.getTransactionId());
        event.setOriginalTransactionId(refundCase.getOriginalTransactionId());
        event.setRefundCaseId(refundCase.getId());
        event.setRefundStatus(refundCase.getRefundCaseStatus());
        event.setRefundEffectType("refund_reversed".equalsIgnoreCase(refundCase.getRefundCaseStatus())
            ? "restore_after_reversal"
            : "subscription".equalsIgnoreCase(refundCase.getProductType()) ? "subscription_cutoff" : "revoke_remaining");
        event.setRefundedQuantity(0);
        event.setRefundedAt(now);
        event.setQuantityDelta(0);
        event.setEntitlementVersion(System.currentTimeMillis());
        event.setReasonCode(notificationType.toLowerCase(Locale.ROOT));
        event.setSourceType("appstore_notification");
        event.setSourceRef(refundCase.getAppleRefundNotificationUuid());
        event.setMetadataJson(toJson(mapOfNonNull("childrenDataExcluded", true)));
        event.setCreatedAt(now);
        entitlementLedgerEventMapper.insert(event);
    }

    private Map<String, Object> buildPolicySnapshot(String appCode, String productId, int thresholdMilli) {
        RemoteConfigNamespaceView namespace = remoteConfigService.loadNamespace(appCode, NAMESPACE_REFUND_POLICY);
        String resolvedKey = "usage_ratio_limit_milli";
        Object resolvedValue = thresholdMilli;
        if (namespace != null && namespace.items() != null) {
            Object productValue = productId == null ? null : namespace.items().get("products." + productId + ".usage_ratio_limit_milli");
            Object globalValue = namespace.items().get("usage_ratio_limit_milli");
            if (productValue != null) {
                resolvedKey = "products." + productId + ".usage_ratio_limit_milli";
                resolvedValue = productValue;
            } else if (globalValue != null) {
                resolvedKey = "usage_ratio_limit_milli";
                resolvedValue = globalValue;
            }
        }
        return mapOfNonNull(
            "namespace", NAMESPACE_REFUND_POLICY,
            "productId", productId,
            "resolvedKey", resolvedKey,
            "resolvedValueMilli", resolvedValue,
            "defaultUsageRatioLimitMilli", DEFAULT_USAGE_RATIO_LIMIT_MILLI
        );
    }

    private AppStoreServerApiClient.AppStoreConfiguration appStoreConfiguration(AppDefinition appDefinition) {
        if (appDefinition == null) {
            return new AppStoreServerApiClient.AppStoreConfiguration(null, null, false, null, null, null, null);
        }
        return new AppStoreServerApiClient.AppStoreConfiguration(
            rawValue(appDefinition, "app.billing.appstore.bundleId"),
            firstNonBlank(rawValue(appDefinition, "app.billing.appstore.environment"), "production"),
            Boolean.parseBoolean(firstNonBlank(rawValue(appDefinition, "app.billing.appstore.allowSandbox"), "false")),
            rawValue(appDefinition, "app.billing.appstore.appAppleId"),
            rawValue(appDefinition, "app.billing.appstore.issuerId"),
            rawValue(appDefinition, "app.billing.appstore.keyId"),
            rawValue(appDefinition, "app.billing.appstore.privateKey")
        );
    }

    private String rawValue(AppDefinition appDefinition, String key) {
        Object value = appDefinition == null || appDefinition.raw() == null ? null : appDefinition.raw().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
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

    private int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Math.max(0, Math.min(100_000, Integer.parseInt(String.valueOf(value).trim())));
        } catch (Exception ex) {
            return fallback;
        }
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record NotificationRefundResult(String status, Long consumptionRequestId, Long refundCaseId) {
    }

    private record UsageSnapshot(int usedCount, int totalCount, int usageRatioMilli) {
    }

    private record RefundDecision(
        String decisionCode,
        String displayMessage,
        int usedCount,
        int totalCount,
        int usageRatioMilli,
        int thresholdMilli,
        Map<String, Object> context,
        AppStoreServerApiClient.ConsumptionRequestBody applePayload
    ) {
    }
}
