package com.apphub.backend.sys.billing.privacy.service;

import com.apphub.backend.common.util.Sha256HashService;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.appstore.service.AppStoreJwsVerificationService;
import com.apphub.backend.sys.appstore.service.AppStoreServerApiClient;
import com.apphub.backend.sys.auth.service.AppleCredentialEncryptionService;
import com.apphub.backend.sys.billing.entity.SysPurchaseTransactionEntity;
import com.apphub.backend.sys.billing.mapper.SysEntitlementSnapshotMapper;
import com.apphub.backend.sys.billing.mapper.SysPurchaseTransactionMapper;
import com.apphub.backend.sys.billing.privacy.entity.SysAppstoreRefundCaseEntity;
import com.apphub.backend.sys.billing.privacy.mapper.SysAppstoreConsumptionRequestMapper;
import com.apphub.backend.sys.billing.privacy.mapper.SysAppstoreRefundCaseMapper;
import com.apphub.backend.sys.billing.privacy.mapper.SysEntitlementConsumptionReportMapper;
import com.apphub.backend.sys.billing.privacy.mapper.SysEntitlementLedgerEventMapper;
import com.apphub.backend.sys.billing.privacy.mapper.SysRefundDecisionLogMapper;
import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import com.apphub.backend.sys.entitlement.entity.SysUserPlanSnapshotEntity;
import com.apphub.backend.sys.entitlement.mapper.SysUserPlanSnapshotMapper;
import com.apphub.backend.sys.entitlement.service.SysEntitlementCenterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysAppStoreRefundServiceTest {

    @Mock
    private SysPurchaseTransactionMapper purchaseTransactionMapper;

    @Mock
    private SysEntitlementSnapshotMapper entitlementSnapshotMapper;

    @Mock
    private SysPrivacyConsentService privacyConsentService;

    @Mock
    private SysAppstoreRefundCaseMapper refundCaseMapper;

    @Mock
    private SysAppstoreConsumptionRequestMapper consumptionRequestMapper;

    @Mock
    private SysRefundDecisionLogMapper refundDecisionLogMapper;

    @Mock
    private SysEntitlementLedgerEventMapper entitlementLedgerEventMapper;

    @Mock
    private SysEntitlementConsumptionReportMapper consumptionReportMapper;

    @Mock
    private SysUserPlanSnapshotMapper userPlanSnapshotMapper;

    @Mock
    private SysEntitlementCenterService entitlementCenterService;

    @Mock
    private AppDefinitionService appDefinitionService;

    @Mock
    private AppStoreServerApiClient appStoreServerApiClient;

    @Mock
    private SysRemoteConfigService remoteConfigService;

    @Mock
    private Sha256HashService sha256HashService;

    @Mock
    private AppleCredentialEncryptionService appleCredentialEncryptionService;

    private SysAppStoreRefundService service;

    @BeforeEach
    void setUp() {
        service = new SysAppStoreRefundService(
            purchaseTransactionMapper,
            entitlementSnapshotMapper,
            privacyConsentService,
            refundCaseMapper,
            consumptionRequestMapper,
            refundDecisionLogMapper,
            entitlementLedgerEventMapper,
            consumptionReportMapper,
            userPlanSnapshotMapper,
            entitlementCenterService,
            appDefinitionService,
            appStoreServerApiClient,
            remoteConfigService,
            sha256HashService,
            appleCredentialEncryptionService,
            new ObjectMapper()
        );
        lenient().when(sha256HashService.hash(any())).thenReturn("hash");
        lenient().when(remoteConfigService.loadNamespace(eq("paipai_readingcompanion"), eq("billing_refund_policy")))
            .thenReturn(new RemoteConfigNamespaceView("paipai_readingcompanion", "billing_refund_policy", Map.of()));
    }

    @Test
    void refundDeclinedShouldNotRevokeEntitlements() {
        SysAppstoreRefundCaseEntity refundCase = baseRefundCase("refund_declined");

        when(refundCaseMapper.selectByNotificationUuid("paipai_readingcompanion", "notify-1")).thenReturn(refundCase);

        AppStoreJwsVerificationService.NotificationClaims claims = new AppStoreJwsVerificationService.NotificationClaims(
            "notify-1",
            "REFUND_DECLINED",
            null,
            "production",
            "otx-1",
            "tx-1",
            "com.paipai.readalong.family.monthly",
            null,
            null
        );

        SysAppStoreRefundService.NotificationRefundResult result = service.handleVerifiedNotification(
            "paipai_readingcompanion",
            claims,
            101L
        );

        assertThat(result.status()).isEqualTo("refund_declined");
        verify(entitlementSnapshotMapper, never()).update(any(), any());
        verify(userPlanSnapshotMapper, never()).update(any(), any());
    }

    @Test
    void refundReversedShouldRestoreSnapshotsAfterServerLookup() {
        AppDefinition appDefinition = new AppDefinition(
            "paipai_readingcompanion",
            "拍拍伴读",
            "/api/v1",
            "reading_",
            new AppDefinition.Support(true, true, true),
            Map.of(
                "app.billing.appstore.bundleId", "com.paipai.readalong",
                "app.billing.appstore.environment", "production",
                "app.billing.appstore.allowSandbox", "false",
                "app.billing.appstore.issuerId", "issuer",
                "app.billing.appstore.keyId", "kid",
                "app.billing.appstore.privateKey", "privateKey"
            )
        );
        SysAppstoreRefundCaseEntity refundCase = baseRefundCase("refunded");
        AppStoreJwsVerificationService.TransactionClaims claims = new AppStoreJwsVerificationService.TransactionClaims(
            "com.paipai.readalong.family.monthly",
            "tx-1",
            "otx-1",
            "production",
            "com.paipai.readalong",
            null,
            OffsetDateTime.parse("2026-05-01T00:00:00Z"),
            null,
            null,
            null,
            null,
            5000L,
            "CNY",
            "CHN",
            "web-order-1",
            "PURCHASE",
            "PURCHASED",
            1,
            "Auto-Renewable Subscription"
        );

        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition));
        when(appStoreServerApiClient.lookup(any(), any()))
            .thenReturn(new AppStoreServerApiClient.LookupResult(
                "verified",
                true,
                "ok",
                claims,
                "signed-transaction",
                null,
                Map.of()
            ));
        when(entitlementCenterService.resolveEntitlementCodeByProduct("paipai_readingcompanion", "com.paipai.readalong.family.monthly"))
            .thenReturn("family_access");
        when(refundCaseMapper.selectByNotificationUuid("paipai_readingcompanion", "notify-1")).thenReturn(refundCase);

        AppStoreJwsVerificationService.NotificationClaims claimsPayload = new AppStoreJwsVerificationService.NotificationClaims(
            "notify-1",
            "REFUND_REVERSED",
            null,
            "production",
            "otx-1",
            "tx-1",
            "com.paipai.readalong.family.monthly",
            "signed-transaction",
            null
        );

        SysAppStoreRefundService.NotificationRefundResult result = service.handleVerifiedNotification(
            "paipai_readingcompanion",
            claimsPayload,
            101L
        );

        assertThat(result.status()).isEqualTo("refund_reversed");
        verify(entitlementSnapshotMapper).update(any(), any());
        verify(userPlanSnapshotMapper).update(any(), any());
        verify(purchaseTransactionMapper).update(eq(null), any());
    }

    private SysPurchaseTransactionEntity baseTransaction() {
        SysPurchaseTransactionEntity entity = new SysPurchaseTransactionEntity();
        entity.setId(1L);
        entity.setAppCode("paipai_readingcompanion");
        entity.setUserId(101L);
        entity.setProductId("com.paipai.readalong.family.monthly");
        entity.setTransactionId("tx-1");
        entity.setOriginalTransactionId("otx-1");
        entity.setPriceMilliAmount(5000L);
        entity.setCurrency("CNY");
        entity.setProductType("subscription");
        entity.setRefundStatus("refunded");
        entity.setUpdatedAt(OffsetDateTime.parse("2026-05-01T00:00:00Z"));
        return entity;
    }

    private SysAppstoreRefundCaseEntity baseRefundCase(String status) {
        SysAppstoreRefundCaseEntity entity = new SysAppstoreRefundCaseEntity();
        entity.setId(11L);
        entity.setAppCode("paipai_readingcompanion");
        entity.setUserId(101L);
        entity.setTransactionId("tx-1");
        entity.setOriginalTransactionId("otx-1");
        entity.setProductId("com.paipai.readalong.family.monthly");
        entity.setProductType("subscription");
        entity.setRefundCaseStatus(status);
        entity.setAppleRefundNotificationUuid("notify-1");
        entity.setPurchasePriceMilliAmount(5000L);
        entity.setCurrency("CNY");
        entity.setUpdatedAt(OffsetDateTime.parse("2026-05-01T00:00:00Z"));
        return entity;
    }
}
