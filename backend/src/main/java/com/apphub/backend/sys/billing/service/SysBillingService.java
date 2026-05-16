package com.apphub.backend.sys.billing.service;

import com.apphub.backend.common.util.Sha256HashService;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.billing.entity.SysEntitlementSnapshotEntity;
import com.apphub.backend.sys.billing.entity.SysPurchaseTransactionEntity;
import com.apphub.backend.sys.billing.mapper.SysEntitlementSnapshotMapper;
import com.apphub.backend.sys.billing.mapper.SysPurchaseTransactionMapper;
import com.apphub.backend.sys.billing.model.EntitlementItemView;
import com.apphub.backend.sys.billing.model.EntitlementObservabilityView;
import com.apphub.backend.sys.billing.model.EntitlementOverviewView;
import com.apphub.backend.sys.billing.model.EntitlementRefreshItemView;
import com.apphub.backend.sys.billing.model.EntitlementRefreshResultView;
import com.apphub.backend.sys.billing.model.PurchaseIntakeAcceptedView;
import com.apphub.backend.sys.billing.model.PurchaseRestoreAcceptedView;
import com.apphub.backend.sys.billing.model.PurchaseRestoreItemRequest;
import com.apphub.backend.sys.billing.model.PurchaseRestoreRequest;
import com.apphub.backend.sys.billing.model.PurchaseVerifyRequest;
import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import com.apphub.backend.sys.entitlement.model.ProductEntitlementMappingView;
import com.apphub.backend.sys.entitlement.service.SysEntitlementCenterService;
import com.apphub.backend.sys.appstore.service.AppStoreJwsVerificationService;
import com.apphub.backend.sys.appstore.service.AppStoreServerApiClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计费服务 `SysBillingService`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Service
public class SysBillingService {

    private static final String VERIFICATION_PENDING = "pending";
    private static final String PROCESSING_ACCEPTED = "accepted";
    private static final String BILLING_ENTITLEMENTS_NAMESPACE = "billing_entitlements";
    private static final String BILLING_REFRESH_POLICY_NAMESPACE = "billing_refresh_policy";
    private static final int DEFAULT_ENTITLEMENT_REFRESH_CANDIDATE_LIMIT = 20;
    private static final long DEFAULT_ENTITLEMENT_REFRESH_COOLDOWN_MINUTES = 5L;
    private static final int ENTITLEMENT_REFRESH_RECENT_LIMIT = 10;

    private final SysPurchaseTransactionMapper sysPurchaseTransactionMapper;
    private final SysEntitlementSnapshotMapper sysEntitlementSnapshotMapper;
    private final Sha256HashService sha256HashService;
    private final ObjectMapper objectMapper;
    private final AppStoreJwsVerificationService appStoreJwsVerificationService;
    private final AppStoreServerApiClient appStoreServerApiClient;
    private final AppDefinitionService appDefinitionService;
    private final SysRemoteConfigService sysRemoteConfigService;
    private final SysEntitlementCenterService sysEntitlementCenterService;

    public SysBillingService(
        SysPurchaseTransactionMapper sysPurchaseTransactionMapper,
        SysEntitlementSnapshotMapper sysEntitlementSnapshotMapper,
        Sha256HashService sha256HashService,
        ObjectMapper objectMapper,
        AppStoreJwsVerificationService appStoreJwsVerificationService,
        AppStoreServerApiClient appStoreServerApiClient,
        AppDefinitionService appDefinitionService,
        SysRemoteConfigService sysRemoteConfigService,
        SysEntitlementCenterService sysEntitlementCenterService
    ) {
        this.sysPurchaseTransactionMapper = sysPurchaseTransactionMapper;
        this.sysEntitlementSnapshotMapper = sysEntitlementSnapshotMapper;
        this.sha256HashService = sha256HashService;
        this.objectMapper = objectMapper;
        this.appStoreJwsVerificationService = appStoreJwsVerificationService;
        this.appStoreServerApiClient = appStoreServerApiClient;
        this.appDefinitionService = appDefinitionService;
        this.sysRemoteConfigService = sysRemoteConfigService;
        this.sysEntitlementCenterService = sysEntitlementCenterService;
    }

    @Transactional
    public PurchaseIntakeAcceptedView verify(String appCode, Long userId, PurchaseVerifyRequest request) {
        SysPurchaseTransactionEntity entity = buildTransactionEntity(
            appCode,
            userId,
            "verify",
            request.productId(),
            request.transactionId(),
            request.originalTransactionId(),
            request.environment(),
            request.storefront(),
            request.appAccountToken(),
            request.signedTransactionInfo(),
            request.signedRenewalInfo(),
            request
        );
        sysPurchaseTransactionMapper.insert(entity);
        return toAcceptedView(entity);
    }

    @Transactional
    public PurchaseRestoreAcceptedView restore(String appCode, Long userId, PurchaseRestoreRequest request) {
        List<PurchaseIntakeAcceptedView> acceptedViews = new ArrayList<>();
        for (PurchaseRestoreItemRequest item : request.transactions()) {
            SysPurchaseTransactionEntity entity = buildTransactionEntity(
                appCode,
                userId,
                "restore",
                item.productId(),
                item.transactionId(),
                item.originalTransactionId(),
                item.environment(),
                item.storefront(),
                item.appAccountToken(),
                item.signedTransactionInfo(),
                item.signedRenewalInfo(),
                item
            );
            sysPurchaseTransactionMapper.insert(entity);
            acceptedViews.add(toAcceptedView(entity));
        }
        return new PurchaseRestoreAcceptedView(appCode, userId, acceptedViews.size(), acceptedViews);
    }

    @Transactional
    public NotificationReconcileResult reconcileVerifiedNotification(
        String appCode,
        AppStoreJwsVerificationService.NotificationClaims claims,
        Object requestPayload
    ) {
        if (claims == null) {
            return new NotificationReconcileResult("skipped_missing_claims", false, null, null, null);
        }
        AppStoreServerApiClient.LookupResult lookupResult = appStoreServerApiClient.lookup(
            new AppStoreServerApiClient.LookupCommand(
                claims.transactionId(),
                claims.originalTransactionId(),
                claims.productId(),
                claims.environment()
            ),
            appStoreConfiguration(appDefinitionService.get(appCode).orElseThrow())
        );
        Long userId = resolveUserIdForNotification(appCode, lookupResult);
        if (userId == null) {
            return new NotificationReconcileResult(
                lookupResult == null ? "skipped_missing_lookup" : lookupResult.status(),
                false,
                null,
                lookupResult == null ? null : lookupResult.status(),
                lookupResult == null ? null : lookupResult.note()
            );
        }
        if (lookupResult == null) {
            return new NotificationReconcileResult("skipped_missing_lookup", false, userId, null, null);
        }

        OffsetDateTime now = now();
        AppStoreJwsVerificationService.TransactionClaims authoritativeClaims = lookupResult.claims();
        SysPurchaseTransactionEntity entity = new SysPurchaseTransactionEntity();
        entity.setAppCode(appCode);
        entity.setUserId(userId);
        entity.setSourceType("notification");
        entity.setProductId(firstNonBlank(authoritativeClaims == null ? null : authoritativeClaims.productId(), claims.productId()));
        entity.setTransactionId(firstNonBlank(authoritativeClaims == null ? null : authoritativeClaims.transactionId(), claims.transactionId()));
        entity.setOriginalTransactionId(firstNonBlank(authoritativeClaims == null ? null : authoritativeClaims.originalTransactionId(), claims.originalTransactionId()));
        entity.setStoreEnvironment(firstNonBlank(authoritativeClaims == null ? null : authoritativeClaims.environment(), claims.environment()));
        entity.setStorefront(null);
        entity.setAppAccountToken(authoritativeClaims == null ? null : authoritativeClaims.appAccountToken());
        entity.setSignedTransactionInfoHash(sha256HashService.hash(lookupResult.signedTransactionInfo()));
        entity.setSignedRenewalInfoHash(sha256HashService.hash(lookupResult.signedRenewalInfo()));
        entity.setVerificationStatus(lookupResult.isVerified() ? "verified" : resolveLookupVerificationStatus(lookupResult));
        entity.setProcessingStatus(resolveProcessingStatus(entity.getVerificationStatus()));
        entity.setPayloadJson(toJson(mapOfNonNull(
            "notificationClaims", claims,
            "lookupStatus", lookupResult.status(),
            "lookupNote", lookupResult.note(),
            "lookupDiagnostics", lookupResult.diagnostics(),
            "authoritativeClaims", authoritativeClaims,
            "request", requestPayload
        )));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        sysPurchaseTransactionMapper.insert(entity);

        projectEntitlementSnapshot(appCode, userId, "notification", requestPayload, lookupResult, now);

        return new NotificationReconcileResult(
            lookupResult.status(),
            lookupResult.isVerified(),
            userId,
            authoritativeClaims == null ? null : authoritativeClaims.originalTransactionId(),
            lookupResult.note()
        );
    }

    public EntitlementOverviewView getEntitlements(String appCode, Long userId) {
        List<SysEntitlementSnapshotEntity> entitlements = sysEntitlementSnapshotMapper.selectList(
            new LambdaQueryWrapper<SysEntitlementSnapshotEntity>()
                .eq(SysEntitlementSnapshotEntity::getAppCode, appCode)
                .eq(SysEntitlementSnapshotEntity::getUserId, userId)
                .orderByDesc(SysEntitlementSnapshotEntity::getUpdatedAt)
                .orderByDesc(SysEntitlementSnapshotEntity::getId)
        );

        Long pendingCount = sysPurchaseTransactionMapper.selectCount(
            new LambdaQueryWrapper<SysPurchaseTransactionEntity>()
                .eq(SysPurchaseTransactionEntity::getAppCode, appCode)
                .eq(SysPurchaseTransactionEntity::getUserId, userId)
                .eq(SysPurchaseTransactionEntity::getProcessingStatus, PROCESSING_ACCEPTED)
                .eq(SysPurchaseTransactionEntity::getVerificationStatus, VERIFICATION_PENDING)
        );

        List<EntitlementItemView> items = entitlements.stream()
            .map(entity -> new EntitlementItemView(
                entity.getEntitlementCode(),
                entity.getStatus(),
                entity.getSourceType(),
                entity.getExpiresAt()
            ))
            .toList();

        return new EntitlementOverviewView(appCode, userId, pendingCount == null ? 0L : pendingCount, items);
    }

    public EntitlementObservabilityView describeEntitlementObservability(String appCode) {
        AppDefinition appDefinition = appDefinitionService.get(appCode).orElse(null);
        LinkedHashMap<String, String> definitionMappings = appDefinition == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(definitionMappings(appDefinition));
        LinkedHashMap<String, String> remoteMappings = new LinkedHashMap<>(remoteConfigMappings(appCode));
        LinkedHashMap<String, EntitlementObservabilityView.EntitlementMappingItemView> effectiveMappings = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : definitionMappings.entrySet()) {
            effectiveMappings.put(entry.getKey(), new EntitlementObservabilityView.EntitlementMappingItemView(entry.getKey(), entry.getValue(), "app_definition"));
        }
        for (Map.Entry<String, String> entry : remoteMappings.entrySet()) {
            effectiveMappings.put(entry.getKey(), new EntitlementObservabilityView.EntitlementMappingItemView(entry.getKey(), entry.getValue(), "remote_config"));
        }

        EntitlementRefreshPolicy refreshPolicy = resolveRefreshPolicy(appCode, appDefinition);
        return new EntitlementObservabilityView(
            appCode,
            definitionMappings.size(),
            remoteMappings.size(),
            effectiveMappings.size(),
            List.copyOf(effectiveMappings.values()),
            new EntitlementObservabilityView.EntitlementRefreshPolicyView(
                refreshPolicy.candidateLimit(),
                refreshPolicy.candidateLimitSource(),
                refreshPolicy.cooldownMinutes(),
                refreshPolicy.cooldownMinutesSource()
            ),
            new EntitlementObservabilityView.EntitlementRefreshStatsView(
                sysPurchaseTransactionMapper.countByAppAndSourceType(appCode, "entitlement_refresh"),
                sysPurchaseTransactionMapper.countByAppSourceTypeAndVerificationStatus(appCode, "entitlement_refresh", "verified"),
                sysPurchaseTransactionMapper.countByAppSourceTypeAndVerificationStatus(appCode, "entitlement_refresh", "pending"),
                sysPurchaseTransactionMapper.countByAppSourceTypeAndVerificationStatus(appCode, "entitlement_refresh", "failed"),
                sysPurchaseTransactionMapper.countByAppSourceTypeAndVerificationStatus(appCode, "entitlement_refresh", "rejected")
            ),
            recentRefreshes(appCode)
        );
    }

    @Transactional
    public EntitlementRefreshResultView refreshEntitlements(String appCode, Long userId) {
        AppDefinition appDefinition = appDefinitionService.get(appCode).orElse(null);
        if (appDefinition == null) {
            return new EntitlementRefreshResultView(appCode, userId, 0, 0, 0, List.of());
        }
        OffsetDateTime now = now();
        EntitlementRefreshPolicy refreshPolicy = resolveRefreshPolicy(appCode, appDefinition);
        List<SysPurchaseTransactionEntity> candidates = sysPurchaseTransactionMapper.selectList(
            new LambdaQueryWrapper<SysPurchaseTransactionEntity>()
                .eq(SysPurchaseTransactionEntity::getAppCode, appCode)
                .eq(SysPurchaseTransactionEntity::getUserId, userId)
                .isNotNull(SysPurchaseTransactionEntity::getOriginalTransactionId)
                .orderByDesc(SysPurchaseTransactionEntity::getUpdatedAt)
                .orderByDesc(SysPurchaseTransactionEntity::getId)
                .last("LIMIT " + refreshPolicy.candidateLimit())
        );
        if (candidates == null || candidates.isEmpty()) {
            return new EntitlementRefreshResultView(appCode, userId, 0, 0, 0, List.of());
        }

        Map<String, SysPurchaseTransactionEntity> uniqueByOriginalTransactionId = new LinkedHashMap<>();
        for (SysPurchaseTransactionEntity candidate : candidates) {
            if (candidate != null && hasText(candidate.getOriginalTransactionId())) {
                uniqueByOriginalTransactionId.putIfAbsent(candidate.getOriginalTransactionId(), candidate);
            }
        }

        List<EntitlementRefreshItemView> results = new ArrayList<>();
        int refreshedCount = 0;
        int insertedTransactionCount = 0;
        for (SysPurchaseTransactionEntity candidate : uniqueByOriginalTransactionId.values()) {
            SysPurchaseTransactionEntity recentRefresh = sysPurchaseTransactionMapper.selectLatestByUserSourceTypeAndOriginalTransactionId(
                appCode,
                userId,
                "entitlement_refresh",
                candidate.getOriginalTransactionId()
            );
            if (recentRefresh != null && recentRefresh.getUpdatedAt() != null
                && !recentRefresh.getUpdatedAt().isBefore(now.minusMinutes(refreshPolicy.cooldownMinutes()))) {
                results.add(new EntitlementRefreshItemView(
                    candidate.getOriginalTransactionId(),
                    candidate.getProductId(),
                    "skipped_recent_refresh",
                    "verified".equalsIgnoreCase(recentRefresh.getVerificationStatus()),
                    "A recent entitlement_refresh already ran for this originalTransactionId."
                ));
                continue;
            }

            AppStoreServerApiClient.LookupResult lookupResult = appStoreServerApiClient.lookup(
                new AppStoreServerApiClient.LookupCommand(
                    candidate.getTransactionId(),
                    candidate.getOriginalTransactionId(),
                    candidate.getProductId(),
                    candidate.getStoreEnvironment()
                ),
                appStoreConfiguration(appDefinition)
            );
            if (lookupResult != null) {
                insertRefreshTransaction(appCode, userId, candidate, lookupResult, now);
                insertedTransactionCount++;
                if (lookupResult.isVerified()) {
                    projectEntitlementSnapshot(appCode, userId, "entitlement_refresh", candidate, lookupResult, now);
                    refreshedCount++;
                }
            }
            results.add(new EntitlementRefreshItemView(
                candidate.getOriginalTransactionId(),
                firstNonBlank(authoritativeProductId(lookupResult), candidate.getProductId()),
                lookupResult == null ? "skipped_missing_lookup" : lookupResult.status(),
                lookupResult != null && lookupResult.isVerified(),
                lookupResult == null ? null : lookupResult.note()
            ));
        }

        return new EntitlementRefreshResultView(
            appCode,
            userId,
            uniqueByOriginalTransactionId.size(),
            refreshedCount,
            insertedTransactionCount,
            results
        );
    }

    private void insertRefreshTransaction(
        String appCode,
        Long userId,
        SysPurchaseTransactionEntity candidate,
        AppStoreServerApiClient.LookupResult lookupResult,
        OffsetDateTime now
    ) {
        AppStoreJwsVerificationService.TransactionClaims authoritativeClaims = lookupResult == null ? null : lookupResult.claims();
        SysPurchaseTransactionEntity entity = new SysPurchaseTransactionEntity();
        entity.setAppCode(appCode);
        entity.setUserId(userId);
        entity.setSourceType("entitlement_refresh");
        entity.setProductId(firstNonBlank(authoritativeClaims == null ? null : authoritativeClaims.productId(), candidate.getProductId()));
        entity.setTransactionId(firstNonBlank(authoritativeClaims == null ? null : authoritativeClaims.transactionId(), candidate.getTransactionId()));
        entity.setOriginalTransactionId(firstNonBlank(authoritativeClaims == null ? null : authoritativeClaims.originalTransactionId(), candidate.getOriginalTransactionId()));
        entity.setStoreEnvironment(firstNonBlank(authoritativeClaims == null ? null : authoritativeClaims.environment(), candidate.getStoreEnvironment()));
        entity.setStorefront(candidate.getStorefront());
        entity.setAppAccountToken(firstNonBlank(authoritativeClaims == null ? null : authoritativeClaims.appAccountToken(), candidate.getAppAccountToken()));
        entity.setSignedTransactionInfoHash(sha256HashService.hash(lookupResult == null ? null : lookupResult.signedTransactionInfo()));
        entity.setSignedRenewalInfoHash(sha256HashService.hash(lookupResult == null ? null : lookupResult.signedRenewalInfo()));
        entity.setVerificationStatus(lookupResult != null && lookupResult.isVerified() ? "verified" : resolveLookupVerificationStatus(lookupResult));
        entity.setProcessingStatus(resolveProcessingStatus(entity.getVerificationStatus()));
        entity.setPayloadJson(toJson(mapOfNonNull(
            "refreshCandidateId", candidate.getId(),
            "lookupStatus", lookupResult == null ? null : lookupResult.status(),
            "lookupNote", lookupResult == null ? null : lookupResult.note(),
            "lookupDiagnostics", lookupResult == null ? null : lookupResult.diagnostics(),
            "authoritativeClaims", authoritativeClaims
        )));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        sysPurchaseTransactionMapper.insert(entity);
    }

    private SysPurchaseTransactionEntity buildTransactionEntity(
        String appCode,
        Long userId,
        String sourceType,
        String productId,
        String transactionId,
        String originalTransactionId,
        String environment,
        String storefront,
        String appAccountToken,
        String signedTransactionInfo,
        String signedRenewalInfo,
        Object payload
    ) {
        OffsetDateTime now = now();
        AppStoreJwsVerificationService.TransactionVerificationResult verificationResult = appStoreJwsVerificationService.verifyTransaction(
            signedTransactionInfo,
            new AppStoreJwsVerificationService.TransactionExpectation(productId, transactionId, originalTransactionId, environment, userId)
        );
        AppStoreServerApiClient.LookupResult lookupResult = lookupAuthoritativeTransaction(
            appCode,
            transactionId,
            originalTransactionId,
            productId,
            environment,
            verificationResult
        );

        String finalVerificationStatus = resolveVerificationStatus(verificationResult, lookupResult);
        String resolvedProductId = firstNonBlank(authoritativeProductId(lookupResult), productId);
        String resolvedTransactionId = firstNonBlank(authoritativeTransactionId(lookupResult), transactionId);
        String resolvedOriginalTransactionId = firstNonBlank(authoritativeOriginalTransactionId(lookupResult), originalTransactionId);
        String resolvedEnvironment = firstNonBlank(authoritativeEnvironment(lookupResult), environment);

        SysPurchaseTransactionEntity entity = new SysPurchaseTransactionEntity();
        entity.setAppCode(appCode);
        entity.setUserId(userId);
        entity.setSourceType(sourceType);
        entity.setProductId(resolvedProductId);
        entity.setTransactionId(resolvedTransactionId);
        entity.setOriginalTransactionId(resolvedOriginalTransactionId);
        entity.setStoreEnvironment(resolvedEnvironment);
        entity.setStorefront(storefront);
        entity.setAppAccountToken(appAccountToken);
        entity.setSignedTransactionInfoHash(sha256HashService.hash(signedTransactionInfo));
        entity.setSignedRenewalInfoHash(sha256HashService.hash(signedRenewalInfo));
        entity.setVerificationStatus(finalVerificationStatus);
        entity.setProcessingStatus(resolveProcessingStatus(finalVerificationStatus));
        entity.setPayloadJson(toJson(buildTransactionPayload(appCode, payload, verificationResult, lookupResult)));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        projectEntitlementSnapshot(appCode, userId, sourceType, payload, lookupResult, now);
        return entity;
    }

    private AppStoreServerApiClient.LookupResult lookupAuthoritativeTransaction(
        String appCode,
        String transactionId,
        String originalTransactionId,
        String productId,
        String environment,
        AppStoreJwsVerificationService.TransactionVerificationResult verificationResult
    ) {
        if (verificationResult == null || !"pending_server_api_reconciliation".equalsIgnoreCase(verificationResult.detailStatus())) {
            return null;
        }
        AppDefinition appDefinition = appDefinitionService.get(appCode).orElse(null);
        if (appDefinition == null) {
            return null;
        }
        return appStoreServerApiClient.lookup(
            new AppStoreServerApiClient.LookupCommand(transactionId, originalTransactionId, productId, environment),
            appStoreConfiguration(appDefinition)
        );
    }

    private AppStoreServerApiClient.AppStoreConfiguration appStoreConfiguration(AppDefinition appDefinition) {
        return new AppStoreServerApiClient.AppStoreConfiguration(
            rawValue(appDefinition, "app.billing.appstore.bundleId"),
            firstNonBlank(rawValue(appDefinition, "app.billing.appstore.environment"), "production"),
            parseBoolean(rawValue(appDefinition, "app.billing.appstore.allowSandbox")),
            rawValue(appDefinition, "app.billing.appstore.appAppleId"),
            rawValue(appDefinition, "app.billing.appstore.issuerId"),
            rawValue(appDefinition, "app.billing.appstore.keyId"),
            rawValue(appDefinition, "app.billing.appstore.privateKey")
        );
    }

    private Object buildTransactionPayload(
        String appCode,
        Object requestPayload,
        AppStoreJwsVerificationService.TransactionVerificationResult verificationResult,
        AppStoreServerApiClient.LookupResult lookupResult
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request", requestPayload == null ? Map.of() : requestPayload);
        payload.put("verification", mapOfNonNull(
            "verificationStatus", verificationResult == null ? null : verificationResult.verificationStatus(),
            "detailStatus", verificationResult == null ? null : verificationResult.detailStatus(),
            "note", verificationResult == null ? null : verificationResult.note(),
            "diagnostics", verificationResult == null ? null : verificationResult.diagnostics(),
            "claims", verificationResult == null || verificationResult.claims() == null ? null : verificationResult.claims()
        ));
        AppStoreJwsVerificationService.TransactionClaims authoritativeClaims = lookupResult == null ? null : lookupResult.claims();
        ResolvedEntitlementCode entitlementResolution = resolveEntitlementCode(appCode, authoritativeClaims);
        payload.put("entitlementResolution", mapOfNonNull(
            "resolved", entitlementResolution != null && entitlementResolution.entitlementCode() != null,
            "entitlementCode", entitlementResolution == null ? null : entitlementResolution.entitlementCode(),
            "mappingSource", entitlementResolution == null ? null : entitlementResolution.source(),
            "subject", entitlementResolution == null ? null : entitlementResolution.subject(),
            "note", entitlementResolution == null ? "No authoritative claims available for entitlement resolution." : entitlementResolution.note()
        ));
        if (lookupResult != null) {
            payload.put("serverLookup", mapOfNonNull(
                "status", lookupResult.status(),
                "remoteLookupAttempted", lookupResult.remoteLookupAttempted(),
                "note", lookupResult.note(),
                "diagnostics", lookupResult.diagnostics(),
                "claims", lookupResult.claims(),
                "signedTransactionInfoPresent", lookupResult.signedTransactionInfo() != null,
                "signedRenewalInfoPresent", lookupResult.signedRenewalInfo() != null
            ));
        }
        return payload;
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

    private String resolveVerificationStatus(
        AppStoreJwsVerificationService.TransactionVerificationResult verificationResult,
        AppStoreServerApiClient.LookupResult lookupResult
    ) {
        if (lookupResult == null) {
            return verificationResult.verificationStatus();
        }
        if (lookupResult.isVerified()) {
            return "verified";
        }
        if (lookupResult.status() != null && lookupResult.status().startsWith("rejected")) {
            return "rejected";
        }
        if (lookupResult.status() != null && lookupResult.status().startsWith("failed")) {
            return "failed";
        }
        return verificationResult.verificationStatus();
    }

    private String authoritativeProductId(AppStoreServerApiClient.LookupResult lookupResult) {
        return lookupResult == null || lookupResult.claims() == null ? null : lookupResult.claims().productId();
    }

    private String authoritativeTransactionId(AppStoreServerApiClient.LookupResult lookupResult) {
        return lookupResult == null || lookupResult.claims() == null ? null : lookupResult.claims().transactionId();
    }

    private String authoritativeOriginalTransactionId(AppStoreServerApiClient.LookupResult lookupResult) {
        return lookupResult == null || lookupResult.claims() == null ? null : lookupResult.claims().originalTransactionId();
    }

    private String authoritativeEnvironment(AppStoreServerApiClient.LookupResult lookupResult) {
        return lookupResult == null || lookupResult.claims() == null ? null : lookupResult.claims().environment();
    }

    private Boolean parseBoolean(String value) {
        return value == null ? null : Boolean.parseBoolean(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String rawValue(AppDefinition appDefinition, String key) {
        Object value = appDefinition.raw().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long resolveUserIdForNotification(String appCode, AppStoreServerApiClient.LookupResult lookupResult) {
        if (lookupResult == null || lookupResult.claims() == null) {
            return null;
        }
        AppStoreJwsVerificationService.TransactionClaims claims = lookupResult.claims();
        if (hasText(claims.originalTransactionId())) {
            Long userId = sysPurchaseTransactionMapper.selectLatestUserIdByOriginalTransactionId(appCode, claims.originalTransactionId());
            if (userId != null) {
                return userId;
            }
        }
        if (hasText(claims.appAccountToken())) {
            return sysPurchaseTransactionMapper.selectLatestUserIdByAppAccountToken(appCode, claims.appAccountToken());
        }
        return null;
    }

    private String resolveLookupVerificationStatus(AppStoreServerApiClient.LookupResult lookupResult) {
        if (lookupResult == null || lookupResult.status() == null) {
            return "pending";
        }
        if (lookupResult.status().startsWith("rejected")) {
            return "rejected";
        }
        if (lookupResult.status().startsWith("failed")) {
            return "failed";
        }
        if (lookupResult.status().startsWith("not_configured") || lookupResult.status().startsWith("skipped")) {
            return "pending";
        }
        return lookupResult.status();
    }

    private void projectEntitlementSnapshot(
        String appCode,
        Long userId,
        String sourceType,
        Object requestPayload,
        AppStoreServerApiClient.LookupResult lookupResult,
        OffsetDateTime now
    ) {
        if (lookupResult == null || !lookupResult.isVerified() || lookupResult.claims() == null) {
            return;
        }
        AppStoreJwsVerificationService.TransactionClaims claims = lookupResult.claims();
        ResolvedEntitlementCode resolvedEntitlementCode = resolveEntitlementCode(appCode, claims);
        if (resolvedEntitlementCode == null || resolvedEntitlementCode.entitlementCode() == null) {
            return;
        }
        String entitlementCode = resolvedEntitlementCode.entitlementCode();
        SysEntitlementSnapshotEntity snapshot = sysEntitlementSnapshotMapper.selectOne(
            new LambdaQueryWrapper<SysEntitlementSnapshotEntity>()
                .eq(SysEntitlementSnapshotEntity::getAppCode, appCode)
                .eq(SysEntitlementSnapshotEntity::getUserId, userId)
                .eq(SysEntitlementSnapshotEntity::getEntitlementCode, entitlementCode)
                .orderByDesc(SysEntitlementSnapshotEntity::getUpdatedAt)
                .orderByDesc(SysEntitlementSnapshotEntity::getId)
                .last("LIMIT 1")
        );
        boolean isNew = snapshot == null;
        if (isNew) {
            snapshot = new SysEntitlementSnapshotEntity();
            snapshot.setAppCode(appCode);
            snapshot.setUserId(userId);
            snapshot.setEntitlementCode(entitlementCode);
            snapshot.setCreatedAt(now);
        }
        snapshot.setSourceType(resolveEntitlementSourceType(sourceType, claims));
        snapshot.setStatus(resolveEntitlementStatus(claims, now));
        snapshot.setExpiresAt(claims.expiresDate());
        snapshot.setPayloadJson(toJson(mapOfNonNull(
            "request", requestPayload,
            "entitlementCode", resolvedEntitlementCode.entitlementCode(),
            "entitlementMappingSource", resolvedEntitlementCode.source(),
            "lookupStatus", lookupResult.status(),
            "lookupNote", lookupResult.note(),
            "claims", claims,
            "diagnostics", lookupResult.diagnostics()
        )));
        snapshot.setUpdatedAt(now);
        if (isNew) {
            sysEntitlementSnapshotMapper.insert(snapshot);
        } else {
            sysEntitlementSnapshotMapper.updateById(snapshot);
        }

        // 中文说明：sys_entitlement_snapshot 仍是 App Store/计费内核的当前投影；统一权益中心另建
        // sys_user_plan_snapshot 保存“购买当时权益包”，只在有效期内参与合并，用于保护已付款老用户不被后台降配影响。
        if ("active".equalsIgnoreCase(snapshot.getStatus())) {
            ProductEntitlementMappingView mapping = sysEntitlementCenterService.resolveProductMapping(appCode, claims.productId());
            String productType = mapping == null ? null : mapping.productType();
            if (productType == null || !"consumable".equalsIgnoreCase(productType)) {
                sysEntitlementCenterService.createOrRefreshPurchaseSnapshot(
                    appCode,
                    userId,
                    entitlementCode,
                    mapping == null ? null : mapping.planCode(),
                    snapshot.getSourceType(),
                    firstNonBlank(claims.originalTransactionId(), claims.transactionId()),
                    claims.expiresDate()
                );
            }
        }
    }

    private String resolveEntitlementSourceType(String sourceType, AppStoreJwsVerificationService.TransactionClaims claims) {
        if (claims.type() != null && !claims.type().isBlank()) {
            return "appstore_" + claims.type().toLowerCase();
        }
        return "appstore_" + sourceType;
    }

    private String resolveEntitlementStatus(AppStoreJwsVerificationService.TransactionClaims claims, OffsetDateTime now) {
        if (claims.revocationDate() != null) {
            return "revoked";
        }
        if (claims.expiresDate() != null && claims.expiresDate().isBefore(now)) {
            return "expired";
        }
        return "active";
    }

    private ResolvedEntitlementCode resolveEntitlementCode(String appCode, AppStoreJwsVerificationService.TransactionClaims claims) {
        if (claims == null) {
            return new ResolvedEntitlementCode(null, "missing_claims", null, "No authoritative transaction claims were available for entitlement mapping.");
        }
        String productId = firstNonBlank(claims.productId());
        if (productId != null) {
            String structuredMapped = sysEntitlementCenterService.resolveEntitlementCodeByProduct(appCode, productId);
            if (structuredMapped != null) {
                return new ResolvedEntitlementCode(structuredMapped, "sys_product_entitlement_mapping", productId, "Resolved entitlement mapping from unified entitlement center product mapping.");
            }
            String remoteConfigMapped = entitlementCodeFromRemoteConfig(appCode, productId);
            if (remoteConfigMapped != null) {
                return new ResolvedEntitlementCode(remoteConfigMapped, "remote_config", productId, "Resolved entitlement mapping from remote config.");
            }
            AppDefinition appDefinition = appDefinitionService.get(appCode).orElse(null);
            if (appDefinition != null) {
                String mapped = entitlementCodeFromDefinition(appDefinition, productId);
                if (mapped != null) {
                    return new ResolvedEntitlementCode(mapped, "app_definition", productId, "Resolved entitlement mapping from app definition.");
                }
            }
            return new ResolvedEntitlementCode(null, "missing_mapping", productId, "No explicit entitlement mapping is configured for this productId. Projection is skipped until mapping is added.");
        }
        String originalTransactionId = firstNonBlank(claims.originalTransactionId());
        if (originalTransactionId != null) {
            return new ResolvedEntitlementCode(null, "missing_product_id", originalTransactionId, "Authoritative transaction is missing productId, so entitlement mapping cannot be resolved.");
        }
        return new ResolvedEntitlementCode(null, "missing_subject", null, "Neither productId nor originalTransactionId is available for entitlement mapping.");
    }

    private String entitlementCodeFromRemoteConfig(String appCode, String productId) {
        if (appCode == null || appCode.isBlank() || productId == null || productId.isBlank()) {
            return null;
        }
        RemoteConfigNamespaceView namespaceView = sysRemoteConfigService.loadNamespace(appCode, BILLING_ENTITLEMENTS_NAMESPACE);
        if (namespaceView == null || namespaceView.items() == null || namespaceView.items().isEmpty()) {
            return null;
        }
        Object namespaced = namespaceView.items().get("productMappings." + productId);
        if (namespaced != null && !String.valueOf(namespaced).isBlank()) {
            return String.valueOf(namespaced).trim();
        }
        Object direct = namespaceView.items().get(productId);
        if (direct != null && !String.valueOf(direct).isBlank()) {
            return String.valueOf(direct).trim();
        }
        return null;
    }

    private EntitlementRefreshPolicy resolveRefreshPolicy(String appCode, AppDefinition appDefinition) {
        RemoteConfigNamespaceView namespaceView = sysRemoteConfigService.loadNamespace(appCode, BILLING_REFRESH_POLICY_NAMESPACE);
        ParsedRefreshPolicyValue candidateLimit = firstConfigured(
            integerValue(namespaceView, "candidateLimit"),
            integerValue(appDefinition, "app.billing.entitlements.refreshPolicy.candidateLimit", "app.billing.entitlements.refreshPolicy.candidate-limit")
        );
        ParsedRefreshPolicyValue cooldownMinutes = firstConfigured(
            longValue(namespaceView, "cooldownMinutes"),
            longValue(appDefinition, "app.billing.entitlements.refreshPolicy.cooldownMinutes", "app.billing.entitlements.refreshPolicy.cooldown-minutes")
        );
        return new EntitlementRefreshPolicy(
            candidateLimit == null ? DEFAULT_ENTITLEMENT_REFRESH_CANDIDATE_LIMIT : Math.max(1, (int) candidateLimit.numericValue()),
            candidateLimit == null ? "default" : candidateLimit.source(),
            cooldownMinutes == null ? DEFAULT_ENTITLEMENT_REFRESH_COOLDOWN_MINUTES : Math.max(0L, cooldownMinutes.numericValue()),
            cooldownMinutes == null ? "default" : cooldownMinutes.source()
        );
    }

    private ParsedRefreshPolicyValue firstConfigured(ParsedRefreshPolicyValue... values) {
        if (values == null) {
            return null;
        }
        for (ParsedRefreshPolicyValue value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private ParsedRefreshPolicyValue integerValue(RemoteConfigNamespaceView namespaceView, String key) {
        if (namespaceView == null || namespaceView.items() == null) {
            return null;
        }
        Object value = namespaceView.items().get(key);
        if (value == null) {
            return null;
        }
        try {
            return new ParsedRefreshPolicyValue(Integer.parseInt(String.valueOf(value).trim()), "remote_config");
        } catch (Exception ignored) {
            return null;
        }
    }

    private ParsedRefreshPolicyValue integerValue(AppDefinition appDefinition, String... keys) {
        if (appDefinition == null || appDefinition.raw() == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = appDefinition.raw().get(key);
            if (value != null) {
                try {
                    return new ParsedRefreshPolicyValue(Integer.parseInt(String.valueOf(value).trim()), "app_definition");
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private ParsedRefreshPolicyValue longValue(RemoteConfigNamespaceView namespaceView, String key) {
        if (namespaceView == null || namespaceView.items() == null) {
            return null;
        }
        Object value = namespaceView.items().get(key);
        if (value == null) {
            return null;
        }
        try {
            return new ParsedRefreshPolicyValue(Long.parseLong(String.valueOf(value).trim()), "remote_config");
        } catch (Exception ignored) {
            return null;
        }
    }

    private ParsedRefreshPolicyValue longValue(AppDefinition appDefinition, String... keys) {
        if (appDefinition == null || appDefinition.raw() == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = appDefinition.raw().get(key);
            if (value != null) {
                try {
                    return new ParsedRefreshPolicyValue(Long.parseLong(String.valueOf(value).trim()), "app_definition");
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private Map<String, String> remoteConfigMappings(String appCode) {
        RemoteConfigNamespaceView namespaceView = sysRemoteConfigService.loadNamespace(appCode, BILLING_ENTITLEMENTS_NAMESPACE);
        LinkedHashMap<String, String> mappings = new LinkedHashMap<>();
        if (namespaceView == null || namespaceView.items() == null) {
            return mappings;
        }
        for (Map.Entry<String, Object> entry : namespaceView.items().entrySet()) {
            String normalizedProductId = normalizeRemoteMappingKey(entry.getKey());
            if (normalizedProductId != null && entry.getValue() != null && !String.valueOf(entry.getValue()).isBlank()) {
                mappings.put(normalizedProductId, String.valueOf(entry.getValue()).trim());
            }
        }
        return mappings;
    }

    private Map<String, String> definitionMappings(AppDefinition appDefinition) {
        LinkedHashMap<String, String> mappings = new LinkedHashMap<>();
        if (appDefinition == null || appDefinition.raw() == null) {
            return mappings;
        }
        for (Map.Entry<String, Object> entry : appDefinition.raw().entrySet()) {
            String productId = normalizeDefinitionMappingKey(entry.getKey());
            if (productId != null && entry.getValue() != null && !String.valueOf(entry.getValue()).isBlank()) {
                mappings.put(productId, String.valueOf(entry.getValue()).trim());
            }
        }
        return mappings;
    }

    private String normalizeRemoteMappingKey(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            return null;
        }
        if (configKey.startsWith("productMappings.")) {
            return configKey.substring("productMappings.".length());
        }
        return configKey;
    }

    private String normalizeDefinitionMappingKey(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            return null;
        }
        if (configKey.startsWith("app.billing.entitlements.productMappings.")) {
            return configKey.substring("app.billing.entitlements.productMappings.".length());
        }
        if (configKey.startsWith("app.billing.entitlements.product-mappings.")) {
            return configKey.substring("app.billing.entitlements.product-mappings.".length());
        }
        return null;
    }

    private List<EntitlementObservabilityView.EntitlementRefreshRecentItemView> recentRefreshes(String appCode) {
        List<SysPurchaseTransactionEntity> entities = sysPurchaseTransactionMapper.selectList(
            new LambdaQueryWrapper<SysPurchaseTransactionEntity>()
                .eq(SysPurchaseTransactionEntity::getAppCode, appCode)
                .eq(SysPurchaseTransactionEntity::getSourceType, "entitlement_refresh")
                .orderByDesc(SysPurchaseTransactionEntity::getUpdatedAt)
                .orderByDesc(SysPurchaseTransactionEntity::getId)
                .last("LIMIT " + ENTITLEMENT_REFRESH_RECENT_LIMIT)
        );
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
            .map(entity -> new EntitlementObservabilityView.EntitlementRefreshRecentItemView(
                entity.getId(),
                entity.getOriginalTransactionId(),
                entity.getProductId(),
                entity.getVerificationStatus(),
                entity.getProcessingStatus(),
                entity.getUpdatedAt()
            ))
            .toList();
    }

    private String entitlementCodeFromDefinition(AppDefinition appDefinition, String productId) {
        if (appDefinition == null || productId == null || productId.isBlank()) {
            return null;
        }
        Object direct = appDefinition.raw().get("app.billing.entitlements.productMappings." + productId);
        if (direct != null && !String.valueOf(direct).isBlank()) {
            return String.valueOf(direct).trim();
        }
        Object relaxed = appDefinition.raw().get("app.billing.entitlements.product-mappings." + productId);
        if (relaxed != null && !String.valueOf(relaxed).isBlank()) {
            return String.valueOf(relaxed).trim();
        }
        return null;
    }

    public record NotificationReconcileResult(
        String status,
        boolean verified,
        Long userId,
        String originalTransactionId,
        String note
    ) {
    }

    private String resolveProcessingStatus(String verificationStatus) {
        if ("failed".equalsIgnoreCase(verificationStatus) || "rejected".equalsIgnoreCase(verificationStatus)) {
            return "rejected";
        }
        return PROCESSING_ACCEPTED;
    }

    private PurchaseIntakeAcceptedView toAcceptedView(SysPurchaseTransactionEntity entity) {
        return new PurchaseIntakeAcceptedView(
            entity.getId(),
            entity.getSourceType(),
            entity.getProductId(),
            entity.getTransactionId(),
            entity.getOriginalTransactionId(),
            entity.getVerificationStatus(),
            entity.getProcessingStatus()
        );
    }

    private String toJson(Object payload) {
        try {
            if (payload == null) {
                return objectMapper.writeValueAsString(Map.of());
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private record ParsedRefreshPolicyValue(long numericValue, String source) {
    }

    private record EntitlementRefreshPolicy(int candidateLimit, String candidateLimitSource, long cooldownMinutes, String cooldownMinutesSource) {
    }

    private record ResolvedEntitlementCode(String entitlementCode, String source, String subject, String note) {
    }
}
