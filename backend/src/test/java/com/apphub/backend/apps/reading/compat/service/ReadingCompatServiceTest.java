package com.apphub.backend.apps.reading.compat.service;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.domain.entity.ReadingChildProfileEntity;
import com.apphub.backend.sys.auth.entity.SysUserDeviceEventEntity;
import com.apphub.backend.apps.reading.domain.entity.ReadingReviewCardEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingChildProfileMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingDailyTaskCompletionMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingFeedbackTicketMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingOcrAuditMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingResourcePackCatalogMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingReviewCardMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingReviewEventV2Mapper;
import com.apphub.backend.sys.app.model.AppAppleReadinessView;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.app.service.AppAppleReadinessService;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import com.apphub.backend.sys.auth.mapper.SysAuthProviderTokenMapper;
import com.apphub.backend.sys.auth.mapper.SysAuthSessionMapper;
import com.apphub.backend.sys.auth.mapper.SysUserDeviceEventMapper;
import com.apphub.backend.sys.auth.mapper.SysUserIdentityMapper;
import com.apphub.backend.sys.auth.mapper.SysUserMapper;
import com.apphub.backend.sys.auth.service.SysAppleAuthService;
import com.apphub.backend.sys.auth.service.SysEmailVerificationService;
import com.apphub.backend.sys.billing.mapper.SysPurchaseTransactionMapper;
import com.apphub.backend.sys.billing.model.EntitlementItemView;
import com.apphub.backend.sys.billing.model.EntitlementObservabilityView;
import com.apphub.backend.sys.billing.model.EntitlementOverviewView;
import com.apphub.backend.sys.billing.service.SysBillingService;
import com.apphub.backend.sys.billing.service.SysPurchasePermissionService;
import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import com.apphub.backend.sys.entitlement.mapper.SysUserFeatureOverrideMapper;
import com.apphub.backend.sys.entitlement.service.SysEntitlementCenterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReadingCompatServiceTest {
    @Mock private ReadingChildProfileMapper childProfileMapper;
    @Mock private ReadingReviewCardMapper reviewCardMapper;
    @Mock private ReadingReviewEventV2Mapper reviewEventMapper;
    @Mock private ReadingDailyTaskCompletionMapper dailyTaskCompletionMapper;
    @Mock private ReadingFeedbackTicketMapper feedbackTicketMapper;
    @Mock private ReadingOcrAuditMapper ocrAuditMapper;
    @Mock private ReadingResourcePackCatalogMapper resourcePackCatalogMapper;
    @Mock private SysUserDeviceEventMapper deviceEventMapper;
    @Mock private SysUserFeatureOverrideMapper userFeatureOverrideMapper;
    @Mock private SysBillingService sysBillingService;
    @Mock private SysPurchaseTransactionMapper purchaseTransactionMapper;
    @Mock private SysAuthSessionMapper sysAuthSessionMapper;
    @Mock private SysUserIdentityMapper sysUserIdentityMapper;
    @Mock private SysAuthProviderTokenMapper sysAuthProviderTokenMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysAppleAuthService sysAppleAuthService;
    @Mock private AppDefinitionService appDefinitionService;
    @Mock private AppAppleReadinessService appAppleReadinessService;
    @Mock private SysRemoteConfigService sysRemoteConfigService;
    @Mock private SysEmailVerificationService sysEmailVerificationService;
    @Mock private ReadingUsagePolicyService usagePolicyService;
    @Mock private ReadingDailyQuotaConfigService dailyQuotaConfigService;
    @Mock private ReadingCloudUsageService cloudUsageService;

    @Mock private ReadingWeeklyReportSnapshotService weeklyReportSnapshotService;
    @Mock private SysEntitlementCenterService sysEntitlementCenterService;

    private ReadingCompatService service;
    private ReadingWeeklyReportAccessConfigService weeklyReportAccessConfigService;
    private SysPurchasePermissionService purchasePermissionService;

    @BeforeEach
    void setUp() {
        weeklyReportAccessConfigService = new ReadingWeeklyReportAccessConfigService(sysRemoteConfigService);
        // 测试中使用真实购买权限服务，复用 sysRemoteConfigService mock 来覆盖全局/单商品禁购配置。
        purchasePermissionService = new SysPurchasePermissionService(sysRemoteConfigService);
        service = new ReadingCompatService(
            childProfileMapper,
            reviewCardMapper,
            reviewEventMapper,
            dailyTaskCompletionMapper,
            feedbackTicketMapper,
            ocrAuditMapper,
            resourcePackCatalogMapper,
            deviceEventMapper,
            userFeatureOverrideMapper,
            sysBillingService,
            purchasePermissionService,
            purchaseTransactionMapper,
            sysAuthSessionMapper,
            sysUserIdentityMapper,
            sysAuthProviderTokenMapper,
            sysUserMapper,
            sysAppleAuthService,
            appDefinitionService,
            appAppleReadinessService,
            sysRemoteConfigService,
            sysEmailVerificationService,
            usagePolicyService,
            weeklyReportAccessConfigService,
            weeklyReportSnapshotService,
            sysEntitlementCenterService,
            dailyQuotaConfigService,
            cloudUsageService,
            new ObjectMapper()
        );
        org.mockito.Mockito.lenient().when(usagePolicyService.currentPolicy())
            .thenReturn(new ReadingUsagePolicyService.UsagePolicyView(30, 7, "client_local", 24));
        org.mockito.Mockito.lenient().when(dailyQuotaConfigService.dailyLimit(eq("premium_lite_monthly"), eq(ReadingDailyQuotaConfigService.FEATURE_CAPTURE)))
            .thenReturn(12);
        org.mockito.Mockito.lenient().when(dailyQuotaConfigService.dailyLimit(eq("premium_lite_monthly"), eq(ReadingDailyQuotaConfigService.FEATURE_SPEECH)))
            .thenReturn(24);
        org.mockito.Mockito.lenient().when(dailyQuotaConfigService.dailyLimit(eq("family_multi_child_lifetime"), eq(ReadingDailyQuotaConfigService.FEATURE_CAPTURE)))
            .thenReturn(50);
        org.mockito.Mockito.lenient().when(dailyQuotaConfigService.dailyLimit(eq("family_multi_child_lifetime"), eq(ReadingDailyQuotaConfigService.FEATURE_SPEECH)))
            .thenReturn(100);
        org.mockito.Mockito.lenient().when(dailyQuotaConfigService.dailyLimit(eq("free"), eq(ReadingDailyQuotaConfigService.FEATURE_CAPTURE)))
            .thenReturn(5);
        org.mockito.Mockito.lenient().when(dailyQuotaConfigService.dailyLimit(eq("free"), eq(ReadingDailyQuotaConfigService.FEATURE_SPEECH)))
            .thenReturn(10);
        org.mockito.Mockito.lenient().when(weeklyReportSnapshotService.load(any(), any(), any(), any(), any()))
            .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(sysRemoteConfigService.loadNamespace("paipai_readingcompanion", "reading_plan_catalog"))
            .thenReturn(new RemoteConfigNamespaceView("paipai_readingcompanion", "reading_plan_catalog", dynamicPlanCatalog()));
        org.mockito.Mockito.lenient().when(childProfileMapper.countActiveByUser(42L)).thenReturn(2);
        org.mockito.Mockito.lenient().when(deviceEventMapper.countByUserEventBetween(eq("paipai_readingcompanion"), eq(42L), eq("capture_ocr"), any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(4);
        org.mockito.Mockito.lenient().when(deviceEventMapper.countByUserEventBetween(eq("paipai_readingcompanion"), eq(42L), eq("speech_play"), any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(0);
        org.mockito.Mockito.lenient().when(cloudUsageService.activeCreditBalance(eq(42L), eq(ReadingCloudUsageService.LOCAL_CAPTURE)))
            .thenReturn(new ReadingCloudUsageService.CreditGrantBalance(ReadingCloudUsageService.LOCAL_CAPTURE, 0, 0, 0));
        org.mockito.Mockito.lenient().when(cloudUsageService.activeCreditBalance(eq(42L), eq(ReadingCloudUsageService.LOCAL_SPEECH)))
            .thenReturn(new ReadingCloudUsageService.CreditGrantBalance(ReadingCloudUsageService.LOCAL_SPEECH, 0, 0, 0));
    }

    @Test
    void accountStateShouldSelectActivePlanByConfiguredEntitlementCode() {
        given(sysBillingService.getEntitlements("paipai_readingcompanion", 42L))
            .willReturn(new EntitlementOverviewView(
                "paipai_readingcompanion",
                42L,
                0,
                List.of(new EntitlementItemView("premium_lite_access", "active", "app_store", null))
            ));

        ReadingCompatService.AccountStateView state = service.accountState(42L, "apple");

        assertThat(state.entitlement().planCode()).isEqualTo("premium_lite_monthly");
        assertThat(state.entitlement().entitlementCode()).isEqualTo("premium_lite_access");
        assertThat(state.entitlement().planName()).isEqualTo("轻量月付版");
        assertThat(state.entitlement().childLimit()).isEqualTo(3);
        assertThat(state.entitlement().remainingChildSlots()).isEqualTo(1);
        assertThat(state.entitlement().dailyCaptureLimit()).isEqualTo(12);
        assertThat(state.entitlement().localCardLimit()).isEqualTo(120);
        assertThat(state.entitlement().premiumActive()).isTrue();
        assertThat(state.quota().captureLimit()).isEqualTo(12);
        assertThat(state.quota().captureUsed()).isEqualTo(4);
        assertThat(state.quota().captureRemaining()).isEqualTo(8);
    }

    @Test
    void accountStateShouldIncludeActiveGiftCreditsInDailyQuotaTotals() {
        given(sysBillingService.getEntitlements("paipai_readingcompanion", 42L))
            .willReturn(new EntitlementOverviewView(
                "paipai_readingcompanion",
                42L,
                0,
                List.of()
            ));
        given(cloudUsageService.activeCreditBalance(42L, ReadingCloudUsageService.LOCAL_CAPTURE))
            .willReturn(new ReadingCloudUsageService.CreditGrantBalance(ReadingCloudUsageService.LOCAL_CAPTURE, 10, 2, 8));
        given(cloudUsageService.activeCreditBalance(42L, ReadingCloudUsageService.LOCAL_SPEECH))
            .willReturn(new ReadingCloudUsageService.CreditGrantBalance(ReadingCloudUsageService.LOCAL_SPEECH, 6, 1, 5));

        ReadingCompatService.AccountStateView state = service.accountState(42L, "apple");

        assertThat(state.quota().captureLimit()).isEqualTo(15);
        assertThat(state.quota().captureUsed()).isEqualTo(6);
        assertThat(state.quota().captureRemaining()).isEqualTo(9);
        assertThat(state.quota().speechLimit()).isEqualTo(16);
        assertThat(state.quota().speechUsed()).isEqualTo(1);
        assertThat(state.quota().speechRemaining()).isEqualTo(15);
    }

    @Test
    void accountStateShouldCountPurchasedAndGiftCreditsTogether() {
        given(sysBillingService.getEntitlements("paipai_readingcompanion", 42L))
            .willReturn(new EntitlementOverviewView(
                "paipai_readingcompanion",
                42L,
                0,
                List.of()
            ));
        given(cloudUsageService.activeCreditBalance(42L, ReadingCloudUsageService.LOCAL_CAPTURE))
            .willReturn(new ReadingCloudUsageService.CreditGrantBalance(ReadingCloudUsageService.LOCAL_CAPTURE, 3, 1, 2));
        given(cloudUsageService.activeCreditBalance(42L, ReadingCloudUsageService.LOCAL_SPEECH))
            .willReturn(new ReadingCloudUsageService.CreditGrantBalance(ReadingCloudUsageService.LOCAL_SPEECH, 4, 2, 2));

        ReadingCompatService.AccountStateView state = service.accountState(42L, "apple");

        assertThat(state.quota().captureLimit()).isEqualTo(8);
        assertThat(state.quota().captureUsed()).isEqualTo(5);
        assertThat(state.quota().captureRemaining()).isEqualTo(3);
        assertThat(state.quota().speechLimit()).isEqualTo(14);
        assertThat(state.quota().speechUsed()).isEqualTo(2);
        assertThat(state.quota().speechRemaining()).isEqualTo(12);
    }

    @Test
    void entitlementRecordsShouldKeepDailyGiftSeparateFromAdminGiftCredits() {
        given(sysBillingService.getEntitlements("paipai_readingcompanion", 42L))
            .willReturn(new EntitlementOverviewView(
                "paipai_readingcompanion",
                42L,
                0,
                List.of()
            ));
        given(cloudUsageService.recentCreditEntitlements(42L, ReadingCloudUsageService.LOCAL_SPEECH, 60))
            .willReturn(List.of(new ReadingCloudUsageService.ActiveEntitlementView(
                "gift-1",
                ReadingCloudUsageService.LOCAL_SPEECH,
                "gift",
                "后台赠送",
                6,
                1,
                5,
                "2026-05-04T00:00:00Z",
                "2026-06-03T00:00:00Z",
                null
            )));

        ReadingCompatService.EntitlementRecordPageView page = service.entitlementRecords(user(), "speech", 1, 20);

        assertThat(page.records()).hasSize(2);
        assertThat(page.records())
            .filteredOn(record -> "daily_gift".equals(record.grantType()))
            .singleElement()
            .satisfies(record -> {
                assertThat(record.acquireMethod()).isEqualTo("每日赠送");
                assertThat(record.totalCount()).isEqualTo(10);
                assertThat(record.usedCount()).isEqualTo(0);
                assertThat(record.remainingCount()).isEqualTo(10);
                assertThat(record.expiresAt()).contains("T23:59:59Z");
            });
        assertThat(page.records())
            .filteredOn(record -> "gift".equals(record.grantType()))
            .singleElement()
            .satisfies(record -> {
                assertThat(record.acquireMethod()).isEqualTo("后台赠送");
                assertThat(record.totalCount()).isEqualTo(6);
                assertThat(record.usedCount()).isEqualTo(1);
                assertThat(record.remainingCount()).isEqualTo(5);
            });
    }

    @Test
    void entitlementRecordsShouldUseClientTimezoneForDailyGiftWindow() {
        given(sysBillingService.getEntitlements("paipai_readingcompanion", 42L))
            .willReturn(new EntitlementOverviewView(
                "paipai_readingcompanion",
                42L,
                0,
                List.of()
            ));

        ReadingCompatService.EntitlementRecordPageView page = service.entitlementRecords(user(), "speech", "Asia/Shanghai", 1, 20);

        ReadingCloudUsageService.ActiveEntitlementView record = page.records().get(0);
        OffsetDateTime acquiredAt = OffsetDateTime.parse(record.acquiredAt());
        OffsetDateTime expiresAt = OffsetDateTime.parse(record.expiresAt());
        assertThat(record.id()).isEqualTo("daily-speech-" + java.time.LocalDate.now(ZoneId.of("Asia/Shanghai")));
        assertThat(acquiredAt.getOffset().toString()).isEqualTo("+08:00");
        assertThat(acquiredAt.toLocalTime().toString()).isEqualTo("00:00");
        assertThat(expiresAt.getOffset().toString()).isEqualTo("+08:00");
        assertThat(expiresAt.toLocalTime().toString()).isEqualTo("23:59:59");
    }

    @Test
    void intakeReceiptShouldMapProductIdToPlanFromCatalog() {
        given(sysBillingService.getEntitlements("paipai_readingcompanion", 42L))
            .willReturn(new EntitlementOverviewView(
                "paipai_readingcompanion",
                42L,
                0,
                List.of(new EntitlementItemView("premium_lite_access", "active", "app_store", null))
            ));

        ReadingCompatService.IntakeReceipt receipt = service.intakeReceipt(
            user(),
            99L,
            "app_store_transaction",
            "accepted",
            "pending_server_verification",
            "com.paipai.readalong.premium.lite.monthly"
        );

        assertThat(receipt.planCode()).isEqualTo("premium_lite_monthly");
        assertThat(receipt.authoritativePlanCode()).isEqualTo("premium_lite_monthly");
    }

    @Test
    void accountStateShouldMatchConfiguredEntitlementAliasesWithoutGuessing() {
        given(sysBillingService.getEntitlements("paipai_readingcompanion", 42L))
            .willReturn(new EntitlementOverviewView(
                "paipai_readingcompanion",
                42L,
                0,
                List.of(new EntitlementItemView("family_access", "active", "app_store", null))
            ));

        ReadingCompatService.AccountStateView state = service.accountState(42L, "apple");

        assertThat(state.entitlement().planCode()).isEqualTo("family_multi_child_lifetime");
        assertThat(state.entitlement().entitlementCode()).isEqualTo("family_multi_child");
        assertThat(state.entitlement().multiChildEnabled()).isTrue();
    }

    @Test
    void intakeReceiptShouldLeaveUnknownProductUnmappedInsteadOfFallingBack() {
        given(sysBillingService.getEntitlements("paipai_readingcompanion", 42L))
            .willReturn(new EntitlementOverviewView(
                "paipai_readingcompanion",
                42L,
                0,
                List.of(new EntitlementItemView("premium_lite_access", "active", "app_store", null))
            ));

        ReadingCompatService.IntakeReceipt receipt = service.intakeReceipt(
            user(),
            100L,
            "app_store_transaction",
            "accepted",
            "pending_server_verification",
            "com.paipai.readalong.family.mystery"
        );

        assertThat(receipt.planCode()).isNull();
        assertThat(receipt.authoritativePlanCode()).isEqualTo("premium_lite_monthly");
    }

    @Test
    void subscriptionStatusShouldReportReadinessBlockedWhenExplicitMappingsAreMissing() {
        given(sysBillingService.getEntitlements("paipai_readingcompanion", 42L))
            .willReturn(new EntitlementOverviewView(
                "paipai_readingcompanion",
                42L,
                0,
                List.of(new EntitlementItemView("premium_lite_access", "active", "app_store", null))
            ));
        given(purchaseTransactionMapper.selectRecentByUser("paipai_readingcompanion", 42L, 10)).willReturn(List.of());
        given(appDefinitionService.get("paipai_readingcompanion")).willReturn(Optional.of(appDefinition()));
        given(appAppleReadinessService.inspect(appDefinition())).willReturn(readyAppleReadiness());
        given(sysBillingService.describeEntitlementObservability("paipai_readingcompanion"))
            .willReturn(new EntitlementObservabilityView(
                "paipai_readingcompanion",
                0,
                0,
                0,
                List.of(),
                new EntitlementObservabilityView.EntitlementRefreshPolicyView(20, "default", 5, "default"),
                new EntitlementObservabilityView.EntitlementRefreshStatsView(0, 0, 0, 0, 0),
                List.of()
            ));

        ReadingCompatService.SubscriptionStatusView status = service.subscriptionStatus(user());

        assertThat(status.verificationReadiness().cryptographicVerificationLive()).isFalse();
        assertThat(status.verificationReadiness().note()).contains("Explicit productId to entitlement mapping is missing");
    }

    @Test
    void weeklyReportShouldUseChildScopedUsageForSingleChildView() {
        given(sysBillingService.getEntitlements("paipai_readingcompanion", 42L))
            .willReturn(new EntitlementOverviewView(
                "paipai_readingcompanion",
                42L,
                0,
                List.of(new EntitlementItemView("premium_lite_access", "active", "app_store", null))
            ));
        ReadingChildProfileEntity alpha = child("child-a", "小宝");
        ReadingChildProfileEntity beta = child("child-b", "大宝");
        given(childProfileMapper.selectActiveByUser(42L)).willReturn(List.of(alpha, beta));
        given(childProfileMapper.selectActiveByIdAndUser("child-a", 42L)).willReturn(alpha);
        given(reviewEventMapper.countByChildBetween(eq(42L), eq("child-a"), any(OffsetDateTime.class), any(OffsetDateTime.class))).willReturn(2);
        given(reviewEventMapper.countActiveDaysByChildBetween(eq(42L), eq("child-a"), any(OffsetDateTime.class), any(OffsetDateTime.class))).willReturn(1);
        given(reviewCardMapper.countActiveByChild(42L, "child-a")).willReturn(3);

        ReadingCompatService.WeeklyParentReportView report = service.weeklyReport(user(), "child-a", "child");

        assertThat(report.scope()).isEqualTo("child");
        assertThat(report.childId()).isEqualTo("child-a");
        assertThat(report.childName()).isEqualTo("小宝");
        assertThat(report.stats().weeklyReviewCount()).isEqualTo(2);
        assertThat(report.stats().weeklyActiveDays()).isEqualTo(1);
        assertThat(report.stats().savedCardCount()).isEqualTo(3);
        assertThat(report.stats().childCount()).isEqualTo(1);
    }

    @Test
    void createReviewCardShouldNotPersistPlainSourceTextForClientEncryptedEnvelope() {
        given(sysBillingService.getEntitlements("paipai_readingcompanion", 42L))
            .willReturn(new EntitlementOverviewView("paipai_readingcompanion", 42L, 0, List.of()));
        ReadingChildProfileEntity alpha = child("child-a", "小宝");
        given(childProfileMapper.selectActiveByIdAndUser("child-a", 42L)).willReturn(alpha);
        given(childProfileMapper.countActiveByUser(42L)).willReturn(1);
        given(reviewCardMapper.countActiveByUser(42L)).willReturn(0);

        service.createReviewCard(
            user(),
            new ReadingCompatService.CreateReviewCardRequest(
                "child-a",
                "zh_to_en",
                "enc:v1:aesgcm:keychain:abc123",
                "辅助提示",
                "en",
                "ja",
                true
            )
        );

        ArgumentCaptor<ReadingReviewCardEntity> captor = ArgumentCaptor.forClass(ReadingReviewCardEntity.class);
        verify(reviewCardMapper).insert(captor.capture());
        ReadingReviewCardEntity saved = captor.getValue();
        assertThat(saved.getEncryptedText()).startsWith("enc:v1:aesgcm:keychain:");
        assertThat(saved.getTextPreview()).isEqualTo("已保存句卡");
        assertThat(saved.getSourceText()).isNull();
        assertThat(saved.getTranslatedText()).isNull();
        assertThat(saved.getSourceLanguageCode()).isEqualTo("en");
        assertThat(saved.getTargetLanguageCode()).isEqualTo("ja");
        assertThat(saved.getContentEncryptionVersion()).isEqualTo("aesgcm_keychain_v1");
        assertThat(saved.getContentKeyId()).isEqualTo("local_device_key_v1");
    }

    @Test
    void todayReviewCardsShouldExposeLanguageCodes() {
        ReadingReviewCardEntity card = new ReadingReviewCardEntity();
        card.setId("card-a");
        card.setTextPreview("Good night.");
        card.setSupportHint("晚安。");
        card.setProficiency(1);
        card.setSyncEnabled(true);
        card.setStorageMode("server_synced");
        card.setSourceLanguageCode("en");
        card.setTargetLanguageCode("zh-Hans");
        given(reviewCardMapper.selectDueByUser(eq(42L), any(OffsetDateTime.class), eq(20)))
            .willReturn(List.of(card));

        List<ReadingCompatService.ReviewCardView> cards = service.todayReviewCards(user());

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).sourceLanguageCode()).isEqualTo("en");
        assertThat(cards.get(0).targetLanguageCode()).isEqualTo("zh-Hans");
    }

    @Test
    void homeSummaryShouldUseRealAggregatedUsageCounts() {
        given(sysBillingService.getEntitlements("paipai_readingcompanion", 42L))
            .willReturn(new EntitlementOverviewView(
                "paipai_readingcompanion",
                42L,
                0,
                List.of(new EntitlementItemView("premium_lite_access", "active", "app_store", null))
            ));
        ReadingChildProfileEntity alpha = child("child-a", "小宝");
        ReadingChildProfileEntity beta = child("child-b", "大宝");
        given(childProfileMapper.selectActiveByUser(42L)).willReturn(List.of(alpha, beta));
        given(reviewCardMapper.selectRecentByUser(42L, 5)).willReturn(List.of());
        given(dailyTaskCompletionMapper.countByUserAndDate(eq(42L), any())).willReturn(2);
        given(reviewCardMapper.countDueByUser(eq(42L), any(OffsetDateTime.class))).willReturn(5);
        given(reviewEventMapper.countByUserBetween(eq(42L), any(OffsetDateTime.class), any(OffsetDateTime.class))).willReturn(4);
        given(reviewEventMapper.countActiveDaysByUserBetween(eq(42L), any(OffsetDateTime.class), any(OffsetDateTime.class))).willReturn(3);
        given(reviewCardMapper.countDueByChild(eq(42L), eq("child-a"), any(OffsetDateTime.class))).willReturn(4);
        given(reviewCardMapper.countDueByChild(eq(42L), eq("child-b"), any(OffsetDateTime.class))).willReturn(1);
        given(reviewCardMapper.countActiveByChild(42L, "child-a")).willReturn(7);
        given(reviewCardMapper.countActiveByChild(42L, "child-b")).willReturn(2);
        given(dailyTaskCompletionMapper.countByUserChildAndDate(42L, "child-a", java.time.LocalDate.now(java.time.ZoneOffset.UTC))).willReturn(2);
        given(dailyTaskCompletionMapper.countByUserChildAndDate(42L, "child-b", java.time.LocalDate.now(java.time.ZoneOffset.UTC))).willReturn(0);

        ReadingCompatService.HomeSummaryView summary = service.homeSummary(user());

        assertThat(summary.currentChild().childId()).isEqualTo("child-a");
        assertThat(summary.todayCompletedCount()).isEqualTo(2);
        assertThat(summary.reviewDueCount()).isEqualTo(5);
        assertThat(summary.growth().weeklyReviewCount()).isEqualTo(4);
        assertThat(summary.growth().weeklyActiveDays()).isEqualTo(3);
        assertThat(summary.childSummaries()).hasSize(2);
        assertThat(summary.childSummaries().get(0).reviewDueCount()).isEqualTo(4);
        assertThat(summary.childSummaries().get(0).savedCardCount()).isEqualTo(7);
        assertThat(summary.childSummaries().get(0).todayCompletedCount()).isEqualTo(2);
        assertThat(summary.childSummaries().get(1).reviewDueCount()).isEqualTo(1);
        assertThat(summary.childSummaries().get(1).savedCardCount()).isEqualTo(2);
        assertThat(summary.childSummaries().get(1).todayCompletedCount()).isEqualTo(0);
    }

    @Test
    void recordQuotaUsageShouldBeIdempotentForCaptureRequests() {
        List<SysUserDeviceEventEntity> events = new ArrayList<>();
        String idempotencyKey = "capture-ocr-001";

        given(sysBillingService.getEntitlements("paipai_readingcompanion", 42L))
            .willReturn(new EntitlementOverviewView("paipai_readingcompanion", 42L, 0, List.of()));

        org.mockito.Mockito.when(deviceEventMapper.countByUserEventBetween(eq("paipai_readingcompanion"), eq(42L), eq("capture_ocr"), any(OffsetDateTime.class), any(OffsetDateTime.class)))
            .thenAnswer(invocation -> (int) events.stream().filter(event -> "capture_ocr".equals(event.getEventType())).count());
        org.mockito.Mockito.when(deviceEventMapper.countByUserEventBetween(eq("paipai_readingcompanion"), eq(42L), eq("speech_play"), any(OffsetDateTime.class), any(OffsetDateTime.class)))
            .thenAnswer(invocation -> (int) events.stream().filter(event -> "speech_play".equals(event.getEventType())).count());
        org.mockito.Mockito.when(deviceEventMapper.countByUserEventAndIdempotencyBetween(
            eq("paipai_readingcompanion"),
            eq(42L),
            eq("capture_ocr"),
            eq(idempotencyKey),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)
        )).thenAnswer(invocation -> (int) events.stream()
            .filter(event -> "capture_ocr".equals(event.getEventType()))
            .filter(event -> event.getPayloadJson() != null && event.getPayloadJson().contains(idempotencyKey))
            .count());
        org.mockito.Mockito.doAnswer(invocation -> {
            events.add(invocation.getArgument(0));
            return 1;
        }).when(deviceEventMapper).insert(org.mockito.ArgumentMatchers.any(SysUserDeviceEventEntity.class));

        ReadingCompatService.QuotaUsageRequest request = new ReadingCompatService.QuotaUsageRequest(
            "ocr",
            "device_ocr",
            "zh-Hans",
            1,
            idempotencyKey,
            "2026-05-02T10:15:30Z"
        );

        ReadingCompatService.AccountStateView first = service.recordQuotaUsage(user(), request);
        ReadingCompatService.AccountStateView second = service.recordQuotaUsage(user(), request);

        assertThat(first.quota().captureUsed()).isEqualTo(1);
        assertThat(first.quota().captureRemaining()).isEqualTo(first.quota().captureLimit() - 1);
        assertThat(second.quota().captureUsed()).isEqualTo(1);
        assertThat(second.quota().captureRemaining()).isEqualTo(second.quota().captureLimit() - 1);
        verify(deviceEventMapper, org.mockito.Mockito.times(1)).insert(org.mockito.ArgumentMatchers.any(SysUserDeviceEventEntity.class));
        verify(deviceEventMapper, org.mockito.Mockito.times(2)).countByUserEventAndIdempotencyBetween(
            eq("paipai_readingcompanion"),
            eq(42L),
            eq("capture_ocr"),
            eq(idempotencyKey),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)
        );
    }

    private ReadingAuthenticatedUser user() {
        SysAuthSessionEntity session = new SysAuthSessionEntity();
        session.setSessionSource("apple");
        SysUserEntity user = new SysUserEntity();
        user.setId(42L);
        return new ReadingAuthenticatedUser(session, user, "token");
    }

    private ReadingChildProfileEntity child(String id, String nickname) {
        ReadingChildProfileEntity entity = new ReadingChildProfileEntity();
        entity.setId(id);
        entity.setNickname(nickname);
        entity.setAgeBand("age_5_7");
        entity.setLearningTrackCode("zh_to_en");
        entity.setAvatarEmoji("🧸");
        return entity;
    }

    private Map<String, Object> dynamicPlanCatalog() {
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("free", Map.of(
            "code", "free",
            "displayName", "免费版",
            "entitlementCode", "free",
            "childLimit", 1,
            "dailyCaptureLimit", 3,
            "localCardLimit", 20,
            "premiumActive", false
        ));
        items.put("premium_lite_monthly", Map.ofEntries(
            Map.entry("code", "premium_lite_monthly"),
            Map.entry("displayName", "轻量月付版"),
            Map.entry("entitlementCode", "premium_lite_access"),
            Map.entry("childLimit", 3),
            Map.entry("dailyCaptureLimit", 12),
            Map.entry("localCardLimit", 120),
            Map.entry("cloudSyncEnabled", true),
            Map.entry("advancedVoiceEnabled", true),
            Map.entry("premiumActive", true),
            Map.entry("appStoreProductId", "com.paipai.readalong.premium.lite.monthly"),
            Map.entry("multiChildEnabled", true),
            Map.entry("dailyPlanScope", "per_child"),
            Map.entry("weeklyReportScope", "family"),
            Map.entry("weeklyReportHistoryWeeks", 4),
            Map.entry("historyEnabled", true)
        ));
        items.put("family_multi_child_lifetime", Map.ofEntries(
            Map.entry("code", "family_multi_child_lifetime"),
            Map.entry("displayName", "家庭多孩子终身版"),
            Map.entry("entitlementCode", "family_multi_child"),
            Map.entry("matchedEntitlementCodes", List.of("family_multi_child", "family_access")),
            Map.entry("matchedProductIds", List.of(
                "com.paipai.readalong.family.yearly",
                "com.paipai.readalong.family.monthly",
                "com.paipai.readalong.family.multi_child.lifetime"
            )),
            Map.entry("childLimit", 5),
            Map.entry("dailyCaptureLimit", 50),
            Map.entry("localCardLimit", 800),
            Map.entry("cloudSyncEnabled", true),
            Map.entry("advancedVoiceEnabled", true),
            Map.entry("premiumActive", true),
            Map.entry("appStoreProductId", "com.paipai.readalong.family.multi_child.lifetime"),
            Map.entry("multiChildEnabled", true),
            Map.entry("dailyPlanScope", "per_child"),
            Map.entry("weeklyReportScope", "family"),
            Map.entry("weeklyReportHistoryWeeks", 12),
            Map.entry("historyEnabled", true)
        ));
        return items;
    }

    private AppDefinition appDefinition() {
        return new AppDefinition(
            "paipai_readingcompanion",
            "拍拍伴读",
            "/api/v1",
            "reading_",
            new AppDefinition.Support(true, true, true),
            Map.of(
                "app.billing.appstore.bundleId", "com.paipai.readalong",
                "app.billing.appstore.environment", "production"
            )
        );
    }

    private AppAppleReadinessView readyAppleReadiness() {
        return new AppAppleReadinessView(
            "paipai_readingcompanion",
            "ready",
            new AppAppleReadinessView.AppleAuthReadiness(
                "ready",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true
            ),
            new AppAppleReadinessView.AppStoreReadiness(
                "ready",
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true
            ),
            List.of(),
            List.of()
        );
    }
}
