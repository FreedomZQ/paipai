package com.apphub.backend.sys.billing.service;

import com.apphub.backend.common.util.Sha256HashService;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.billing.entity.SysEntitlementSnapshotEntity;
import com.apphub.backend.sys.billing.entity.SysPurchaseTransactionEntity;
import com.apphub.backend.sys.billing.mapper.SysEntitlementSnapshotMapper;
import com.apphub.backend.sys.billing.mapper.SysPurchaseTransactionMapper;
import com.apphub.backend.sys.billing.model.EntitlementObservabilityView;
import com.apphub.backend.sys.billing.model.EntitlementRefreshResultView;
import com.apphub.backend.sys.billing.model.PurchaseVerifyRequest;
import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import com.apphub.backend.sys.entitlement.service.SysEntitlementCenterService;
import com.apphub.backend.sys.appstore.service.AppStoreJwsVerificationService;
import com.apphub.backend.sys.appstore.service.AppStoreServerApiClient;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 针对 `SysBillingService` 的测试类。
 * 用于验证核心业务逻辑、配置约束或关键边界条件。
 */

@ExtendWith(MockitoExtension.class)
class SysBillingServiceTest {

    @Mock
    private SysPurchaseTransactionMapper sysPurchaseTransactionMapper;

    @Mock
    private SysEntitlementSnapshotMapper sysEntitlementSnapshotMapper;

    @Mock
    private Sha256HashService sha256HashService;

    @Mock
    private AppStoreJwsVerificationService appStoreJwsVerificationService;

    @Mock
    private AppStoreServerApiClient appStoreServerApiClient;

    @Mock
    private AppDefinitionService appDefinitionService;

    @Mock
    private SysRemoteConfigService sysRemoteConfigService;

    @Mock
    private SysEntitlementCenterService sysEntitlementCenterService;

    private SysBillingService sysBillingService;

    @BeforeEach
    void setUp() {
        sysBillingService = new SysBillingService(
            sysPurchaseTransactionMapper,
            sysEntitlementSnapshotMapper,
            sha256HashService,
            new ObjectMapper(),
            appStoreJwsVerificationService,
            appStoreServerApiClient,
            appDefinitionService,
            sysRemoteConfigService,
            sysEntitlementCenterService
        );
    }

    @Test
    void verifyShouldProjectConfiguredEntitlementCodeInsteadOfRawProductId() {
        AppDefinition appDefinition = new AppDefinition(
            "paipai_readingcompanion",
            "拍拍伴读",
            "/api/v1",
            "reading_",
            new AppDefinition.Support(true, true, true),
            Map.of(
                "app.billing.entitlements.productMappings.com.paipai.readalong.family.yearly", "family_access_file"
            )
        );
        AppStoreJwsVerificationService.TransactionClaims claims = new AppStoreJwsVerificationService.TransactionClaims(
            "com.paipai.readalong.family.yearly",
            "tx-1",
            "otx-1",
            "production",
            "com.paipai.readalong",
            null,
            OffsetDateTime.parse("2026-04-16T00:00:00Z"),
            OffsetDateTime.parse("2026-05-16T00:00:00Z"),
            null,
            "Auto-Renewable Subscription"
        );

        when(appStoreJwsVerificationService.verifyTransaction(any(), any()))
            .thenReturn(new AppStoreJwsVerificationService.TransactionVerificationResult(
                "pending",
                "pending_server_api_reconciliation",
                "pending",
                claims,
                Map.of()
            ));
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
        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition));
        when(sysRemoteConfigService.loadNamespace("paipai_readingcompanion", "billing_entitlements"))
            .thenReturn(new RemoteConfigNamespaceView("paipai_readingcompanion", "billing_entitlements", Map.of(
                "productMappings.com.paipai.readalong.family.yearly", "family_access_db"
            )));
        when(sysEntitlementSnapshotMapper.selectOne(any())).thenReturn(null);
        when(sha256HashService.hash(any())).thenReturn("hash");

        sysBillingService.verify(
            "paipai_readingcompanion",
            101L,
            new PurchaseVerifyRequest(
                "com.paipai.readalong.family.yearly",
                "tx-1",
                "otx-1",
                "production",
                null,
                null,
                "signed-jws",
                null
            )
        );

        ArgumentCaptor<SysEntitlementSnapshotEntity> snapshotCaptor = ArgumentCaptor.forClass(SysEntitlementSnapshotEntity.class);
        verify(sysEntitlementSnapshotMapper).insert(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().getEntitlementCode()).isEqualTo("family_access_db");
    }

    @Test
    void refreshEntitlementsShouldLookupLatestTransactionsAndProjectSnapshots() {
        AppDefinition appDefinition = new AppDefinition(
            "paipai_readingcompanion",
            "拍拍伴读",
            "/api/v1",
            "reading_",
            new AppDefinition.Support(true, true, true),
            Map.of(
                "app.billing.entitlements.productMappings.com.paipai.readalong.family.yearly", "family_access"
            )
        );
        SysPurchaseTransactionEntity candidate = new SysPurchaseTransactionEntity();
        candidate.setId(91L);
        candidate.setAppCode("paipai_readingcompanion");
        candidate.setUserId(101L);
        candidate.setProductId("com.paipai.readalong.family.yearly");
        candidate.setTransactionId("tx-91");
        candidate.setOriginalTransactionId("otx-91");
        candidate.setStoreEnvironment("production");
        candidate.setUpdatedAt(OffsetDateTime.parse("2026-04-16T00:00:00Z"));
        AppStoreJwsVerificationService.TransactionClaims claims = new AppStoreJwsVerificationService.TransactionClaims(
            "com.paipai.readalong.family.yearly",
            "tx-91",
            "otx-91",
            "production",
            "com.paipai.readalong",
            null,
            OffsetDateTime.parse("2026-04-16T00:00:00Z"),
            OffsetDateTime.parse("2026-05-16T00:00:00Z"),
            null,
            "Auto-Renewable Subscription"
        );

        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition));
        when(sysPurchaseTransactionMapper.selectList(any())).thenReturn(java.util.List.of(candidate));
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
        when(sysRemoteConfigService.loadNamespace("paipai_readingcompanion", "billing_entitlements"))
            .thenReturn(new RemoteConfigNamespaceView("paipai_readingcompanion", "billing_entitlements", Map.of()));
        when(sysRemoteConfigService.loadNamespace("paipai_readingcompanion", "billing_refresh_policy"))
            .thenReturn(new RemoteConfigNamespaceView("paipai_readingcompanion", "billing_refresh_policy", Map.of()));
        when(sysEntitlementSnapshotMapper.selectOne(any())).thenReturn(null);
        when(sha256HashService.hash(any())).thenReturn("hash");

        EntitlementRefreshResultView result = sysBillingService.refreshEntitlements("paipai_readingcompanion", 101L);

        assertThat(result.appCode()).isEqualTo("paipai_readingcompanion");
        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.refreshedCount()).isEqualTo(1);
        assertThat(result.insertedTransactionCount()).isEqualTo(1);
        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).lookupStatus()).isEqualTo("verified");
        verify(sysPurchaseTransactionMapper).insert(any(SysPurchaseTransactionEntity.class));
        verify(sysEntitlementSnapshotMapper).insert(any(SysEntitlementSnapshotEntity.class));
    }

    @Test
    void describeEntitlementObservabilityShouldMergeMappingsAndExposeRefreshStats() {
        AppDefinition appDefinition = new AppDefinition(
            "paipai_readingcompanion",
            "拍拍伴读",
            "/api/v1",
            "reading_",
            new AppDefinition.Support(true, true, true),
            Map.of(
                "app.billing.entitlements.productMappings.com.paipai.readalong.family.monthly", "family_access"
            )
        );
        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition));
        when(sysRemoteConfigService.loadNamespace("paipai_readingcompanion", "billing_entitlements"))
            .thenReturn(new RemoteConfigNamespaceView("paipai_readingcompanion", "billing_entitlements", Map.of(
                "productMappings.com.paipai.readalong.family.yearly", "family_access",
                "com.paipai.readalong.family.monthly", "family_access_promo"
            )));
        when(sysRemoteConfigService.loadNamespace("paipai_readingcompanion", "billing_refresh_policy"))
            .thenReturn(new RemoteConfigNamespaceView("paipai_readingcompanion", "billing_refresh_policy", Map.of(
                "candidateLimit", 12,
                "cooldownMinutes", 9
            )));
        SysPurchaseTransactionEntity recent = new SysPurchaseTransactionEntity();
        recent.setId(91L);
        recent.setOriginalTransactionId("otx-1");
        recent.setProductId("com.paipai.readalong.family.yearly");
        recent.setVerificationStatus("verified");
        recent.setProcessingStatus("accepted");
        recent.setUpdatedAt(OffsetDateTime.parse("2026-04-16T00:00:00Z"));
        when(sysPurchaseTransactionMapper.selectList(any())).thenReturn(java.util.List.of(recent));
        when(sysPurchaseTransactionMapper.countByAppAndSourceType("paipai_readingcompanion", "entitlement_refresh")).thenReturn(8);
        when(sysPurchaseTransactionMapper.countByAppSourceTypeAndVerificationStatus("paipai_readingcompanion", "entitlement_refresh", "verified")).thenReturn(5);
        when(sysPurchaseTransactionMapper.countByAppSourceTypeAndVerificationStatus("paipai_readingcompanion", "entitlement_refresh", "pending")).thenReturn(1);
        when(sysPurchaseTransactionMapper.countByAppSourceTypeAndVerificationStatus("paipai_readingcompanion", "entitlement_refresh", "failed")).thenReturn(1);
        when(sysPurchaseTransactionMapper.countByAppSourceTypeAndVerificationStatus("paipai_readingcompanion", "entitlement_refresh", "rejected")).thenReturn(1);

        EntitlementObservabilityView result = sysBillingService.describeEntitlementObservability("paipai_readingcompanion");

        assertThat(result.appCode()).isEqualTo("paipai_readingcompanion");
        assertThat(result.definitionMappingCount()).isEqualTo(1);
        assertThat(result.remoteConfigMappingCount()).isEqualTo(2);
        assertThat(result.effectiveMappingCount()).isEqualTo(2);
        assertThat(result.refreshStats().total()).isEqualTo(8);
        assertThat(result.refreshPolicy().candidateLimit()).isEqualTo(12);
        assertThat(result.refreshPolicy().candidateLimitSource()).isEqualTo("remote_config");
        assertThat(result.refreshPolicy().cooldownMinutes()).isEqualTo(9);
        assertThat(result.refreshPolicy().cooldownMinutesSource()).isEqualTo("remote_config");
        assertThat(result.recentRefreshes()).hasSize(1);
        assertThat(result.recentRefreshes().get(0).originalTransactionId()).isEqualTo("otx-1");
        assertThat(result.effectiveMappings()).extracting(EntitlementObservabilityView.EntitlementMappingItemView::productId)
            .containsExactly("com.paipai.readalong.family.monthly", "com.paipai.readalong.family.yearly");
        assertThat(result.effectiveMappings()).extracting(EntitlementObservabilityView.EntitlementMappingItemView::source)
            .containsExactly("remote_config", "remote_config");
    }

    @Test
    void refreshEntitlementsShouldUseConfiguredRefreshPolicyFromRemoteConfig() {
        AppDefinition appDefinition = new AppDefinition(
            "paipai_readingcompanion",
            "拍拍伴读",
            "/api/v1",
            "reading_",
            new AppDefinition.Support(true, true, true),
            Map.of(
                "app.billing.entitlements.refreshPolicy.candidateLimit", "50",
                "app.billing.entitlements.refreshPolicy.cooldownMinutes", "30"
            )
        );
        SysPurchaseTransactionEntity candidate = new SysPurchaseTransactionEntity();
        candidate.setId(301L);
        candidate.setAppCode("paipai_readingcompanion");
        candidate.setUserId(101L);
        candidate.setProductId("com.paipai.readalong.family.yearly");
        candidate.setTransactionId("tx-301");
        candidate.setOriginalTransactionId("otx-301");
        candidate.setStoreEnvironment("production");
        candidate.setUpdatedAt(OffsetDateTime.parse("2026-04-16T00:00:00Z"));
        SysPurchaseTransactionEntity recentRefresh = new SysPurchaseTransactionEntity();
        recentRefresh.setId(302L);
        recentRefresh.setAppCode("paipai_readingcompanion");
        recentRefresh.setUserId(101L);
        recentRefresh.setSourceType("entitlement_refresh");
        recentRefresh.setOriginalTransactionId("otx-301");
        recentRefresh.setVerificationStatus("verified");
        recentRefresh.setUpdatedAt(OffsetDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(6));

        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition));
        when(sysRemoteConfigService.loadNamespace("paipai_readingcompanion", "billing_refresh_policy"))
            .thenReturn(new RemoteConfigNamespaceView("paipai_readingcompanion", "billing_refresh_policy", Map.of(
                "candidateLimit", 7,
                "cooldownMinutes", 10
            )));
        when(sysPurchaseTransactionMapper.selectList(any())).thenReturn(java.util.List.of(candidate));
        when(sysPurchaseTransactionMapper.selectLatestByUserSourceTypeAndOriginalTransactionId("paipai_readingcompanion", 101L, "entitlement_refresh", "otx-301"))
            .thenReturn(recentRefresh);

        EntitlementRefreshResultView result = sysBillingService.refreshEntitlements("paipai_readingcompanion", 101L);

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).lookupStatus()).isEqualTo("skipped_recent_refresh");
        org.mockito.Mockito.verify(appStoreServerApiClient, org.mockito.Mockito.never()).lookup(any(), any());
    }

    @Test
    void refreshEntitlementsShouldSkipRecentRefreshWithinCooldown() {
        AppDefinition appDefinition = new AppDefinition(
            "paipai_readingcompanion",
            "拍拍伴读",
            "/api/v1",
            "reading_",
            new AppDefinition.Support(true, true, true),
            Map.of()
        );
        SysPurchaseTransactionEntity candidate = new SysPurchaseTransactionEntity();
        candidate.setId(201L);
        candidate.setAppCode("paipai_readingcompanion");
        candidate.setUserId(101L);
        candidate.setProductId("com.paipai.readalong.family.yearly");
        candidate.setTransactionId("tx-201");
        candidate.setOriginalTransactionId("otx-201");
        candidate.setStoreEnvironment("production");
        candidate.setUpdatedAt(OffsetDateTime.parse("2026-04-16T00:00:00Z"));
        SysPurchaseTransactionEntity recentRefresh = new SysPurchaseTransactionEntity();
        recentRefresh.setId(202L);
        recentRefresh.setAppCode("paipai_readingcompanion");
        recentRefresh.setUserId(101L);
        recentRefresh.setSourceType("entitlement_refresh");
        recentRefresh.setOriginalTransactionId("otx-201");
        recentRefresh.setVerificationStatus("verified");
        recentRefresh.setUpdatedAt(OffsetDateTime.now(java.time.ZoneOffset.UTC));

        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition));
        when(sysPurchaseTransactionMapper.selectList(any())).thenReturn(java.util.List.of(candidate));
        when(sysPurchaseTransactionMapper.selectLatestByUserSourceTypeAndOriginalTransactionId("paipai_readingcompanion", 101L, "entitlement_refresh", "otx-201"))
            .thenReturn(recentRefresh);

        EntitlementRefreshResultView result = sysBillingService.refreshEntitlements("paipai_readingcompanion", 101L);

        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.refreshedCount()).isEqualTo(0);
        assertThat(result.insertedTransactionCount()).isEqualTo(0);
        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).lookupStatus()).isEqualTo("skipped_recent_refresh");
        org.mockito.Mockito.verify(appStoreServerApiClient, org.mockito.Mockito.never()).lookup(any(), any());
    }

    @Test
    void refreshEntitlementsShouldDeduplicateByOriginalTransactionId() {
        AppDefinition appDefinition = new AppDefinition(
            "paipai_readingcompanion",
            "拍拍伴读",
            "/api/v1",
            "reading_",
            new AppDefinition.Support(true, true, true),
            Map.of()
        );
        SysPurchaseTransactionEntity latest = new SysPurchaseTransactionEntity();
        latest.setId(101L);
        latest.setAppCode("paipai_readingcompanion");
        latest.setUserId(101L);
        latest.setProductId("com.paipai.readalong.family.yearly");
        latest.setTransactionId("tx-latest");
        latest.setOriginalTransactionId("otx-shared");
        latest.setStoreEnvironment("production");
        latest.setUpdatedAt(OffsetDateTime.parse("2026-04-16T01:00:00Z"));
        SysPurchaseTransactionEntity older = new SysPurchaseTransactionEntity();
        older.setId(100L);
        older.setAppCode("paipai_readingcompanion");
        older.setUserId(101L);
        older.setProductId("com.paipai.readalong.family.yearly");
        older.setTransactionId("tx-older");
        older.setOriginalTransactionId("otx-shared");
        older.setStoreEnvironment("production");
        older.setUpdatedAt(OffsetDateTime.parse("2026-04-16T00:00:00Z"));
        AppStoreJwsVerificationService.TransactionClaims claims = new AppStoreJwsVerificationService.TransactionClaims(
            "com.paipai.readalong.family.yearly",
            "tx-latest",
            "otx-shared",
            "production",
            "com.paipai.readalong",
            null,
            OffsetDateTime.parse("2026-04-16T00:00:00Z"),
            OffsetDateTime.parse("2026-05-16T00:00:00Z"),
            null,
            "Auto-Renewable Subscription"
        );

        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition));
        when(sysPurchaseTransactionMapper.selectList(any())).thenReturn(java.util.List.of(latest, older));
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
        when(sysRemoteConfigService.loadNamespace("paipai_readingcompanion", "billing_refresh_policy"))
            .thenReturn(new RemoteConfigNamespaceView("paipai_readingcompanion", "billing_refresh_policy", Map.of()));
        when(sha256HashService.hash(any())).thenReturn("hash");

        EntitlementRefreshResultView result = sysBillingService.refreshEntitlements("paipai_readingcompanion", 101L);

        assertThat(result.candidateCount()).isEqualTo(1);
        org.mockito.Mockito.verify(appStoreServerApiClient, org.mockito.Mockito.times(1)).lookup(any(), any());
    }

    @Test
    void verifyShouldSkipSnapshotProjectionWhenNoEntitlementMappingExists() {
        AppDefinition appDefinition = new AppDefinition(
            "paipai_readingcompanion",
            "拍拍伴读",
            "/api/v1",
            "reading_",
            new AppDefinition.Support(true, true, true),
            Map.of()
        );
        AppStoreJwsVerificationService.TransactionClaims claims = new AppStoreJwsVerificationService.TransactionClaims(
            "com.paipai.readalong.family.monthly",
            "tx-2",
            "otx-2",
            "production",
            "com.paipai.readalong",
            null,
            OffsetDateTime.parse("2026-04-16T00:00:00Z"),
            OffsetDateTime.parse("2026-05-16T00:00:00Z"),
            null,
            "Auto-Renewable Subscription"
        );

        when(appStoreJwsVerificationService.verifyTransaction(any(), any()))
            .thenReturn(new AppStoreJwsVerificationService.TransactionVerificationResult(
                "pending",
                "pending_server_api_reconciliation",
                "pending",
                claims,
                Map.of()
            ));
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
        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition));
        when(sysRemoteConfigService.loadNamespace("paipai_readingcompanion", "billing_entitlements"))
            .thenReturn(new RemoteConfigNamespaceView("paipai_readingcompanion", "billing_entitlements", Map.of()));
        when(sha256HashService.hash(any())).thenReturn("hash");

        sysBillingService.verify(
            "paipai_readingcompanion",
            101L,
            new PurchaseVerifyRequest(
                "com.paipai.readalong.family.monthly",
                "tx-2",
                "otx-2",
                "production",
                null,
                null,
                "signed-jws",
                null
            )
        );

        org.mockito.Mockito.verify(sysEntitlementSnapshotMapper, org.mockito.Mockito.never()).insert(any(SysEntitlementSnapshotEntity.class));
        ArgumentCaptor<SysPurchaseTransactionEntity> transactionCaptor = ArgumentCaptor.forClass(SysPurchaseTransactionEntity.class);
        verify(sysPurchaseTransactionMapper).insert(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getProductId()).isEqualTo("com.paipai.readalong.family.monthly");
        assertThat(transactionCaptor.getValue().getVerificationStatus()).isEqualTo("verified");
    }
}
