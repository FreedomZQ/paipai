package com.apphub.backend.apps.reading.compat.service;

import com.apphub.backend.apps.common.entitlement.AppEntitlementAccessGuard;
import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.domain.entity.ReadingChildProfileEntity;
import com.apphub.backend.apps.reading.domain.entity.ReadingDailyTaskCompletionEntity;
import com.apphub.backend.apps.reading.domain.entity.ReadingFeedbackTicketEntity;
import com.apphub.backend.apps.reading.domain.entity.ReadingOcrAuditEntity;
import com.apphub.backend.apps.reading.domain.entity.ReadingResourcePackCatalogEntity;
import com.apphub.backend.apps.reading.domain.entity.ReadingReviewCardEntity;
import com.apphub.backend.apps.reading.domain.entity.ReadingReviewEventV2Entity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingChildProfileMapper;
import com.apphub.backend.apps.reading.compat.service.ReadingCloudUsageService.CloudUsageDecision;
import com.apphub.backend.apps.reading.provider.ReadingBailianOcrProvider.OcrProviderResult;
import com.apphub.backend.apps.reading.provider.ReadingBailianTtsProvider.TtsProviderResult;
import com.apphub.backend.apps.reading.domain.mapper.ReadingDailyTaskCompletionMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingFeedbackTicketMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingOcrAuditMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingResourcePackCatalogMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingReviewCardMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingReviewEventV2Mapper;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.app.service.AppAppleReadinessService;
import com.apphub.backend.sys.auth.entity.SysUserDeviceEventEntity;
import com.apphub.backend.sys.auth.mapper.SysAuthProviderTokenMapper;
import com.apphub.backend.sys.auth.mapper.SysAuthSessionMapper;
import com.apphub.backend.sys.auth.mapper.SysUserDeviceEventMapper;
import com.apphub.backend.sys.auth.mapper.SysUserIdentityMapper;
import com.apphub.backend.sys.auth.mapper.SysUserMapper;
import com.apphub.backend.sys.auth.model.AppleRevokeResultView;
import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import com.apphub.backend.sys.billing.mapper.SysPurchaseTransactionMapper;
import com.apphub.backend.sys.auth.service.SysAppleAuthService;
import com.apphub.backend.sys.auth.service.SysEmailVerificationService;
import com.apphub.backend.sys.billing.model.EntitlementOverviewView;
import com.apphub.backend.sys.billing.model.PurchasePermissionDecision;
import com.apphub.backend.sys.billing.privacy.entity.SysEntitlementLedgerEventEntity;
import com.apphub.backend.sys.billing.privacy.mapper.SysEntitlementLedgerEventMapper;
import com.apphub.backend.sys.billing.service.SysBillingService;
import com.apphub.backend.sys.billing.service.SysPurchasePermissionService;
import com.apphub.backend.sys.entitlement.entity.SysUserFeatureOverrideEntity;
import com.apphub.backend.sys.entitlement.mapper.SysUserFeatureOverrideMapper;
import com.apphub.backend.sys.entitlement.model.FeatureAccessView;
import com.apphub.backend.sys.entitlement.model.UserEntitlementDecisionView;
import com.apphub.backend.sys.entitlement.service.SysEntitlementCenterService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * reading 前端兼容服务。
 * 该服务集中实现拍拍 iOS 现有接口所需的最小后端权威能力：账号状态、孩子档案、句卡、每日任务、周报、反馈与 OCR 审计。
 */
@Service
public class ReadingCompatService {
    private static final String APP_CODE = ReadingAppModule.APP_CODE;
    private static final String FREE_PLAN = "free";
    private static final String STANDARD_PLAN = "standard_single_child";
    private static final String FAMILY_PLAN = "family_multi_child_lifetime";
    private static final String FAMILY_ENTITLEMENT = "family_multi_child";
    private static final String PLAN_NAMESPACE = "reading_plan_catalog";
    private static final String LANGUAGE_NAMESPACE = "reading_language_catalog";
    private static final List<String> DEFAULT_SUPPORTED_LOCALES = List.of("zh-Hans", "en", "ja", "ko", "es");
    /**
     * P1 增长配置命名空间。
     *
     * <p>该 namespace 只属于 paipai_readingcompanion 当前 app_code，用来承载不需要发版即可调整的
     * 付费页标题、留存信任点与合规提示。后续其他 App 使用同一套后端时，应创建自己的 app_code + namespace，
     * 不复用拍拍伴读的转化文案，避免多 App 之间互相污染。
     */
    private static final String PAYWALL_NAMESPACE = "reading_paywall_growth";
    private static final String CREDIT_PRODUCT_NAMESPACE = "reading_credit_products";
    private static final String DELETE_ACCOUNT_SCENE = "delete_account";
    private static final String RESOURCE_CHILD_PROFILE = "child_profile";
    private static final String RESOURCE_LOCAL_CARD = "local_card";

    private final ReadingChildProfileMapper childProfileMapper;
    private final ReadingReviewCardMapper reviewCardMapper;
    private final ReadingReviewEventV2Mapper reviewEventMapper;
    private final ReadingDailyTaskCompletionMapper dailyTaskCompletionMapper;
    private final ReadingFeedbackTicketMapper feedbackTicketMapper;
    private final ReadingOcrAuditMapper ocrAuditMapper;
    private final ReadingResourcePackCatalogMapper resourcePackCatalogMapper;
    private final SysUserDeviceEventMapper deviceEventMapper;
    private final SysUserFeatureOverrideMapper userFeatureOverrideMapper;
    private final SysBillingService sysBillingService;
    private final SysPurchasePermissionService purchasePermissionService;
    private final SysPurchaseTransactionMapper purchaseTransactionMapper;
    private final SysAuthSessionMapper sysAuthSessionMapper;
    private final SysUserIdentityMapper sysUserIdentityMapper;
    private final SysAuthProviderTokenMapper sysAuthProviderTokenMapper;
    private final SysUserMapper sysUserMapper;
    private final SysAppleAuthService sysAppleAuthService;
    private final AppDefinitionService appDefinitionService;
    private final AppAppleReadinessService appAppleReadinessService;
    private final SysRemoteConfigService sysRemoteConfigService;
    private final SysEmailVerificationService sysEmailVerificationService;
    private final ReadingUsagePolicyService usagePolicyService;
    private final ReadingWeeklyReportAccessConfigService weeklyReportAccessConfigService;
    private final ReadingWeeklyReportSnapshotService weeklyReportSnapshotService;
    private final SysEntitlementCenterService sysEntitlementCenterService;
    private final ReadingDailyQuotaConfigService dailyQuotaConfigService;
    private final ReadingCloudUsageService cloudUsageService;
    private final SysEntitlementLedgerEventMapper entitlementLedgerEventMapper;
    private final ObjectMapper objectMapper;

    @Value("${backend.apps.paipai_readingcompanion.public.supportEmail:support@paipai.app}")
    private String supportEmail;
    @Value("${backend.apps.paipai_readingcompanion.public.supportUrl:https://www.paipai.app/support}")
    private String supportUrl;
    @Value("${backend.apps.paipai_readingcompanion.public.privacyPolicyUrl:/legal/privacy-policy.html}")
    private String privacyPolicyUrl;
    @Value("${backend.apps.paipai_readingcompanion.public.termsUrl:/legal/terms-of-service.html}")
    private String termsUrl;
    @Value("${backend.apps.paipai_readingcompanion.public.childDataUrl:/legal/child-data.html}")
    private String childDataUrl;
    @Value("${backend.apps.paipai_readingcompanion.public.deleteAccountUrl:https://www.paipai.app/delete-account}")
    private String deleteAccountUrl;
    @Value("${backend.apps.paipai_readingcompanion.plan.familyMultiChildLifetime.productId:com.paipai.readalong.family.multi_child.lifetime}")
    private String familyProductId;
    @Value("${backend.apps.paipai_readingcompanion.plan.familyMultiChildLifetime.displayPrice:¥68}")
    private String familyDisplayPrice;
    @Value("${backend.apps.paipai_readingcompanion.plan.familyMultiChildLifetime.originalPrice:¥98}")
    private String familyOriginalPrice;

    public ReadingCompatService(
        ReadingChildProfileMapper childProfileMapper,
        ReadingReviewCardMapper reviewCardMapper,
        ReadingReviewEventV2Mapper reviewEventMapper,
        ReadingDailyTaskCompletionMapper dailyTaskCompletionMapper,
        ReadingFeedbackTicketMapper feedbackTicketMapper,
        ReadingOcrAuditMapper ocrAuditMapper,
        ReadingResourcePackCatalogMapper resourcePackCatalogMapper,
        SysUserDeviceEventMapper deviceEventMapper,
        SysUserFeatureOverrideMapper userFeatureOverrideMapper,
        SysBillingService sysBillingService,
        SysPurchasePermissionService purchasePermissionService,
        SysPurchaseTransactionMapper purchaseTransactionMapper,
        SysAuthSessionMapper sysAuthSessionMapper,
        SysUserIdentityMapper sysUserIdentityMapper,
        SysAuthProviderTokenMapper sysAuthProviderTokenMapper,
        SysUserMapper sysUserMapper,
        SysAppleAuthService sysAppleAuthService,
        AppDefinitionService appDefinitionService,
        AppAppleReadinessService appAppleReadinessService,
        SysRemoteConfigService sysRemoteConfigService,
        SysEmailVerificationService sysEmailVerificationService,
        ReadingUsagePolicyService usagePolicyService,
        ReadingWeeklyReportAccessConfigService weeklyReportAccessConfigService,
        ReadingWeeklyReportSnapshotService weeklyReportSnapshotService,
        SysEntitlementCenterService sysEntitlementCenterService,
        ReadingDailyQuotaConfigService dailyQuotaConfigService,
        ReadingCloudUsageService cloudUsageService,
        SysEntitlementLedgerEventMapper entitlementLedgerEventMapper,
        ObjectMapper objectMapper
    ) {
        this.childProfileMapper = childProfileMapper;
        this.reviewCardMapper = reviewCardMapper;
        this.reviewEventMapper = reviewEventMapper;
        this.dailyTaskCompletionMapper = dailyTaskCompletionMapper;
        this.feedbackTicketMapper = feedbackTicketMapper;
        this.ocrAuditMapper = ocrAuditMapper;
        this.resourcePackCatalogMapper = resourcePackCatalogMapper;
        this.deviceEventMapper = deviceEventMapper;
        this.userFeatureOverrideMapper = userFeatureOverrideMapper;
        this.sysBillingService = sysBillingService;
        this.purchasePermissionService = purchasePermissionService;
        this.purchaseTransactionMapper = purchaseTransactionMapper;
        this.sysAuthSessionMapper = sysAuthSessionMapper;
        this.sysUserIdentityMapper = sysUserIdentityMapper;
        this.sysAuthProviderTokenMapper = sysAuthProviderTokenMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysAppleAuthService = sysAppleAuthService;
        this.appDefinitionService = appDefinitionService;
        this.appAppleReadinessService = appAppleReadinessService;
        this.sysRemoteConfigService = sysRemoteConfigService;
        this.sysEmailVerificationService = sysEmailVerificationService;
        this.usagePolicyService = usagePolicyService;
        this.weeklyReportAccessConfigService = weeklyReportAccessConfigService;
        this.weeklyReportSnapshotService = weeklyReportSnapshotService;
        this.sysEntitlementCenterService = sysEntitlementCenterService;
        this.dailyQuotaConfigService = dailyQuotaConfigService;
        this.cloudUsageService = cloudUsageService;
        this.entitlementLedgerEventMapper = entitlementLedgerEventMapper;
        this.objectMapper = objectMapper;
    }

    public BootstrapConfigView bootstrap() {
        List<String> supportedLocales = resolveSupportedLocales();
        List<LearningTrackView> learningTracks = resolveLearningTracks();
        return new BootstrapConfigView(
            "拍拍伴读",
            false,
            120,
            supportedLocales.isEmpty() ? "zh-Hans" : supportedLocales.get(0),
            supportedLocales.isEmpty() ? DEFAULT_SUPPORTED_LOCALES : supportedLocales,
            learningTracks,
            resolvePaywallConfig(),
            usagePolicyService.currentPolicy(),
            supportEmail,
            supportUrl,
            deleteAccountUrl
        );
    }

    public List<PlanView> plans() {
        Map<String, Object> items = loadNamespaceItems(PLAN_NAMESPACE);
        LinkedHashMap<String, PlanView> plans = new LinkedHashMap<>();
        plans.put(FREE_PLAN, planViewFromConfig(items.get(FREE_PLAN), defaultFreePlan()));
        plans.put(FAMILY_PLAN, planViewFromConfig(items.get(FAMILY_PLAN), defaultFamilyPlan()));
        items.forEach((key, value) -> {
            PlanView parsed = planViewFromConfig(value, null);
            if (parsed != null && parsed.code() != null && !parsed.code().isBlank()) {
                plans.put(parsed.code(), parsed);
            }
        });
        List<PlanView> result = plans.values().stream()
            .filter(item -> item != null)
            .sorted((left, right) -> Boolean.compare(Boolean.TRUE.equals(right.highlight()), Boolean.TRUE.equals(left.highlight())))
            .toList();
        return result.isEmpty() ? List.of(defaultFreePlan(), defaultFamilyPlan()) : result;
    }

    public BillingHealthView billingHealth() {
        return billingHealth("zh-Hans");
    }

    /**
     * 查询购买页总状态。
     *
     * <p>实现逻辑：先读取多应用通用购买权限配置，再读取当前 App 的资源包配置。
     * 全局禁购或没有任何可购买项时，返回 purchaseAvailable=false，前端据此立即置灰付款按钮。
     *
     * @param locale 前端界面语言，用于从数据库配置中选择对应提示文案。
     */
    public BillingHealthView billingHealth(String locale) {
        if (isLocalOnlyLaunchMode()) {
            // 中文说明：无自有后端首发时，商品白名单和价格展示由 iOS StoreKit 本地实现。
            // 后端购买配置即使存在也不能成为生产包的远程商品/钱包来源。
            return new BillingHealthView(
                "local_only_backend_disabled",
                false,
                localizedJsonText("""
                    {"zh-Hans":"本机积分由 App 内 StoreKit 本地白名单处理，后端购买配置已关闭。","en":"Local credits are handled by the app's StoreKit allowlist; backend purchase configuration is disabled."}
                    """, locale, "本机积分由 App 内 StoreKit 本地白名单处理，后端购买配置已关闭。"),
                purchasePermissionService.checkedAt()
            );
        }
        PurchasePermissionDecision globalPermission = purchasePermissionService.decide(APP_CODE, null, locale);
        List<CreditProductView> products = creditProducts(locale);
        boolean hasEnabledProduct = products.stream().anyMatch(item -> Boolean.TRUE.equals(item.enabled()));
        boolean available = Boolean.TRUE.equals(globalPermission.allowed()) && hasEnabledProduct;
        String unavailableMessage = !Boolean.TRUE.equals(globalPermission.allowed())
            ? firstNonBlank(globalPermission.message(), "服务暂不可用")
            : (hasEnabledProduct ? "" : localizedJsonText("""
                {"zh-Hans":"当前未配置可购买资源包","en":"No purchasable resource packs are configured","ja":"購入可能なリソースパックが設定されていません","ko":"구매 가능한 리소스 팩이 설정되지 않았습니다","es":"No hay paquetes comprables configurados"}
                """, locale, "当前未配置可购买资源包"));
        return new BillingHealthView(
            available ? "available" : "unconfigured",
            available,
            available ? "" : unavailableMessage,
            purchasePermissionService.checkedAt()
        );
    }

    public List<CreditProductView> creditProducts() {
        return creditProducts("zh-Hans");
    }

    /**
     * 查询资源包列表。
     *
     * <p>实现逻辑：不仅返回 active 商品，也返回 disabled 商品，并叠加
     * `billing_purchase_control` 中的全局/单商品权限结果，使前端可展示灰色禁用购买项。
     *
     * @param locale 前端界面语言，用于返回本地化商品文案和禁用原因。
     */
    public List<CreditProductView> creditProducts(String locale) {
        if (isLocalOnlyLaunchMode()) {
            return List.of();
        }
        try {
            PurchasePermissionDecision globalPermission = purchasePermissionService.decide(APP_CODE, null, locale);
            return resourcePackCatalogMapper.selectConfigured(APP_CODE).stream()
                .map(item -> resourcePackView(item, locale))
                .map(item -> productWithPermission(item, globalPermission, locale))
                .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public DailyLoginGiftConfigView dailyLoginGiftConfig(String planCode) {
        // 中文说明：每日登录赠送积分只读取 local_device 配置，作为识字和朗读共享的单一日赠总额。
        String safePlanCode = firstNonBlank(planCode, FREE_PLAN);
        int configuredLimit = dailyQuotaConfigService.dailyLimit(
            safePlanCode,
            ReadingDailyQuotaConfigService.FEATURE_LOCAL_DEVICE
        );
        return new DailyLoginGiftConfigView(
            APP_CODE,
            safePlanCode,
            ReadingDailyQuotaConfigService.FEATURE_LOCAL_DEVICE,
            Math.max(configuredLimit, 0),
            "single_daily_login_gift",
            OffsetDateTime.now(ZoneOffset.UTC).toString()
        );
    }

    @Transactional
    public InternalPurchaseReceipt internalPurchase(ReadingAuthenticatedUser user, InternalPurchaseRequest request) {
        // 提交购买前再次实时读取数据库权限配置，避免用户停留在购买页期间后台已禁购但仍继续发放权益。
        if (request == null || request.productCode() == null || request.productCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productCode is required");
        }
        PurchasePermissionDecision permission = purchasePermission(request.productCode(), request.locale());
        if (!Boolean.TRUE.equals(permission.allowed())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, firstNonBlank(permission.message(), "服务暂不可用"));
        }
        if (request.purchaseTicket() == null || request.purchaseTicket().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "purchaseTicket is required");
        }
        String idempotencyKey = request.idempotencyKey() == null ? "" : request.idempotencyKey().trim();
        if (idempotencyKey.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idempotencyKey is required");
        }
        CreditProductView product = creditProducts(request.locale()).stream()
            .filter(item -> item.productCode().equals(request.productCode()))
            .filter(item -> Boolean.TRUE.equals(item.enabled()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        if (!isInternalPurchaseEnabled(product)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Internal purchase is disabled for App Store products");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime dayStart = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        int purchasedToday = 0;
        int remainingOrLimit;
        if (isCreditGrantProduct(product)) {
            purchasedToday = cloudUsageService.countDailyInternalPurchases(user.userId(), product.serviceType(), dayStart, dayStart.plusDays(1));
            if (purchasedToday >= 5) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "每种权益每天最多购买5次");
            }
            ReadingCloudUsageService.CloudUsageDecision decision = cloudUsageService.grantPurchase(
                user.userId(),
                product.serviceType(),
                product.productCode(),
                product.amount() == null ? 0 : product.amount(),
                product.validDays() == null ? 30 : product.validDays(),
                request.purchaseTicket()
            );
            remainingOrLimit = decision.remainingCount();
        } else {
            remainingOrLimit = grantCapacityExtension(user.userId(), product, request.purchaseTicket());
        }
        return new InternalPurchaseReceipt(
            "accepted",
            "internal",
            product,
            remainingOrLimit,
            now.plusDays(product.validDays() == null ? 30 : product.validDays()).toString(),
            isCreditGrantProduct(product) ? Math.min(purchasedToday + 1, 5) : 1,
            5,
            accountState(user)
        );
    }

    @Transactional
    public void grantAppStoreProductPurchase(Long userId, String appStoreProductId, String transactionId) {
        if (userId == null || appStoreProductId == null || appStoreProductId.isBlank() || transactionId == null || transactionId.isBlank()) {
            return;
        }
        CreditProductView product = creditProducts("zh-Hans").stream()
            .filter(item -> appStoreProductId.equals(item.appStoreProductId()))
            .findFirst()
            .orElse(null);
        if (product == null) {
            return;
        }
        if (isSameDeviceLocalWalletProduct(product)) {
            // 首发无后端方案：本机双积分只写入 iOS Keychain 钱包。
            // 即使旧客户端误把交易提交到后端，也不能在服务端创建云端钱包或补发记录。
            return;
        }
        if (isCreditGrantProduct(product)) {
            cloudUsageService.grantAppStorePurchase(
                userId,
                product.serviceType(),
                product.productCode(),
                product.amount() == null ? 0 : product.amount(),
                product.validDays() == null ? 30 : product.validDays(),
                transactionId
            );
        } else {
            grantCapacityExtension(userId, product, transactionId);
        }
        writeAppStoreGrantLedgerEvent(userId, product, transactionId);
    }

    private void writeAppStoreGrantLedgerEvent(Long userId, CreditProductView product, String transactionId) {
        if (entitlementLedgerEventMapper == null || userId == null || product == null || transactionId == null || transactionId.isBlank()) {
            return;
        }
        Long existing = entitlementLedgerEventMapper.selectCount(
            new LambdaQueryWrapper<SysEntitlementLedgerEventEntity>()
                .eq(SysEntitlementLedgerEventEntity::getAppCode, APP_CODE)
                .eq(SysEntitlementLedgerEventEntity::getUserId, userId)
                .eq(SysEntitlementLedgerEventEntity::getTransactionId, transactionId)
                .eq(SysEntitlementLedgerEventEntity::getEventType, "grant")
        );
        if (existing != null && existing > 0) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SysEntitlementLedgerEventEntity event = new SysEntitlementLedgerEventEntity();
        event.setAppCode(APP_CODE);
        event.setUserId(userId);
        event.setEventId(UUID.randomUUID());
        event.setEventType("grant");
        event.setEntitlementCode(firstNonBlank(product.serviceType(), product.packageType(), product.productCode(), "appstore_resource_pack"));
        event.setTransactionId(transactionId);
        event.setRefundStatus("none");
        event.setRefundEffectType("none");
        event.setRefundedQuantity(0);
        event.setQuantityDelta(Math.max(product.amount() == null ? 0 : product.amount(), 0));
        event.setEntitlementVersion(System.currentTimeMillis());
        event.setReasonCode("appstore_resource_pack_grant");
        event.setSourceType("appstore_purchase");
        event.setSourceRef(transactionId);
        event.setMetadataJson(jsonString(Map.of(
            "productCode", firstNonBlank(product.productCode(), ""),
            "appStoreProductId", firstNonBlank(product.appStoreProductId(), ""),
            "packageType", firstNonBlank(product.packageType(), ""),
            "quantityUnit", firstNonBlank(product.quantityUnit(), ""),
            "childrenDataExcluded", true
        )));
        event.setCreatedAt(now);
        entitlementLedgerEventMapper.insert(event);
    }

    private boolean isInternalPurchaseEnabled(CreditProductView product) {
        return product != null && Boolean.TRUE.equals(boolValue(jsonMap(metadataJsonForProduct(product.productCode())), "internalPurchaseEnabled", false));
    }

    private boolean isSameDeviceLocalWalletProduct(CreditProductView product) {
        if (product == null) {
            return false;
        }
        String packageType = firstNonBlank(product.packageType(), "").toLowerCase(Locale.ROOT);
        if ("local_device_credit_pack".equals(packageType)) {
            return true;
        }
        Map<String, Object> metadata = jsonMap(metadataJsonForProduct(product.productCode()));
        return Boolean.TRUE.equals(boolValue(metadata, "sameDeviceKeychainOnly", false))
            || Boolean.TRUE.equals(boolValue(metadata, "serverWalletDisabled", false));
    }

    private boolean isLocalOnlyLaunchMode(AppDefinition definition) {
        Map<String, Object> raw = definition.raw();
        boolean explicitlyLocalOnly = boolValue(raw, "app.launch.localNoBackendFirstRelease", false)
            || boolValue(raw, "app.privacy.noDeveloperBackend", false);
        boolean localGuestEnabled = boolValue(raw, "app.privacy.localGuestEnabled", true);
        boolean cloudProcessingEnabled = boolValue(raw, "app.privacy.cloudContentProcessingEnabled", false);
        boolean remoteAppleExchangeEnabled = boolValue(raw, "app.auth.apple.remoteExchangeEnabled", false);
        boolean demoSessionEnabled = boolValue(raw, "app.auth.demoSessionEnabled", false);
        // 中文说明：`app.launch.localNoBackendFirstRelease` / `app.privacy.noDeveloperBackend`
        // 是无自有后端首发的显式门闩。只要被配置为 true，服务端购买、恢复、
        // 云端钱包、退款消费上报和补偿码路径都必须失败关闭，不能依赖其他开关组合推断。
        return explicitlyLocalOnly || (localGuestEnabled
            && !definition.support().appleSignInRequired()
            && !cloudProcessingEnabled
            && !remoteAppleExchangeEnabled
            && !demoSessionEnabled);
    }

    private String metadataJsonForProduct(String productCode) {
        ReadingResourcePackCatalogEntity entity = resourcePackCatalogMapper.selectByPackageCode(APP_CODE, productCode);
        return entity == null ? null : entity.getMetadataJson();
    }

    /**
     * 实时校验某个购买项是否允许购买。
     *
     * @param productCode 购买项编码；为空时只校验 App 全局购买状态。
     * @param locale 前端界面语言，用于返回数据库中的多语言提示。
     */
    public PurchasePermissionDecision purchasePermission(String productCode, String locale) {
        if (isLocalOnlyLaunchMode()) {
            String message = localizedJsonText("""
                {"zh-Hans":"本机积分购买由 App 内 StoreKit 本地流程处理，后端购买路径已关闭。","en":"Local credit purchases are handled by the app's StoreKit flow; backend purchase paths are disabled."}
                """, locale, "本机积分购买由 App 内 StoreKit 本地流程处理，后端购买路径已关闭。");
            return new PurchasePermissionDecision(
                APP_CODE,
                productCode,
                false,
                "local_only_backend_disabled",
                "local_only_backend_disabled",
                "local_only_backend_disabled",
                Map.of("zh-Hans", "本机积分购买由 App 内 StoreKit 本地流程处理，后端购买路径已关闭。", "en", "Local credit purchases are handled by the app's StoreKit flow; backend purchase paths are disabled."),
                message
            );
        }
        return purchasePermissionService.decide(APP_CODE, productCode, locale);
    }

    /**
     * 判断当前运行配置是否是“本机匿名、无自有后端权益”的首发模式。
     *
     * <p>为降低个人开发者儿童 App 风险，该模式下后端只能作为未来预留和运维配置存在，
     * 不能承接购买恢复、云端钱包、退款消费上报或补偿兑换等会形成服务端权益账本的链路。</p>
     */
    public boolean isLocalOnlyLaunchMode() {
        return appDefinitionService.get(APP_CODE)
            .map(this::isLocalOnlyLaunchMode)
            .orElse(false);
    }

    public EntitlementRecordPageView entitlementRecords(ReadingAuthenticatedUser user, String serviceType, int page, int pageSize) {
        return entitlementRecords(user, serviceType, null, page, pageSize);
    }

    public EntitlementRecordPageView entitlementRecords(ReadingAuthenticatedUser user, String serviceType, String timezone, int page, int pageSize) {
        String normalizedServiceType = normalizeEntitlementRecordServiceType(serviceType);
        ZoneId clientZone = resolveClientZone(timezone);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 50);
        List<ReadingCloudUsageService.ActiveEntitlementView> records = new ArrayList<>();
        if (normalizedServiceType == null || ReadingCloudUsageService.LOCAL_DEVICE.equals(normalizedServiceType)) {
            records.add(localDailyEntitlementRecord(user.userId(), clientZone));
        }
        records = records.stream()
            .filter(item -> item.totalCount() > 0)
            .sorted((left, right) -> {
                int acquiredCompare = right.acquiredAt().compareTo(left.acquiredAt());
                if (acquiredCompare != 0) {
                    return acquiredCompare;
                }
                return right.id().compareTo(left.id());
            })
            .toList();
        int from = Math.min((safePage - 1) * safePageSize, records.size());
        int to = Math.min(from + safePageSize, records.size());
        return new EntitlementRecordPageView(safePage, safePageSize, records.size() > to, records.subList(from, to));
    }

    private String normalizeEntitlementRecordServiceType(String serviceType) {
        if (serviceType == null || serviceType.isBlank() || "all".equalsIgnoreCase(serviceType.trim())) {
            return null;
        }
        String normalized = serviceType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "local_device", "local_credits", "local_feature", "daily_login_gift" -> ReadingCloudUsageService.LOCAL_DEVICE;
            case "local_ocr", "ocr", "text_recognition", "image_ocr", "picture_ocr", "photo_ocr", "device_ocr" -> ReadingCloudUsageService.LOCAL_OCR;
            case "local_tts", "tts", "voice_reading", "text_to_speech", "speech_synthesis", "device_tts" -> ReadingCloudUsageService.LOCAL_TTS;
            case "cloud_ocr", "cloud_tts" -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Historical count entitlements are no longer exposed");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported serviceType");
        };
    }

    private ZoneId resolveClientZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException ex) {
            return ZoneOffset.UTC;
        }
    }

    private ReadingCloudUsageService.ActiveEntitlementView localDailyEntitlementRecord(Long userId, ZoneId clientZone) {
        AccountEntitlementView entitlement = resolveEntitlement(userId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate clientDate = now.atZoneSameInstant(clientZone).toLocalDate();
        OffsetDateTime dayStart = clientDate.atStartOfDay(clientZone).toOffsetDateTime();
        OffsetDateTime dayEnd = clientDate.plusDays(1).atStartOfDay(clientZone).toOffsetDateTime();
        OffsetDateTime expiresAt = dayEnd.minusSeconds(1);
        int total = dailyQuotaLimit(entitlement, ReadingDailyQuotaConfigService.FEATURE_LOCAL_DEVICE);
        int used = Math.min(localDeviceDailyUsed(userId, dayStart, dayEnd), total);
        // 中文说明：后端查询权益记录时同步落库/更新当天单条赠送记录，保证数据库和接口呈现一致。
        return cloudUsageService.ensureDailyLoginGiftGrant(userId, total, used, dayStart, expiresAt, clientDate.toString());
    }

    public List<LegalDocView> legalDocs() {
        return List.of(
            new LegalDocView("privacy", "zh-Hans", privacyPolicyUrl),
            new LegalDocView("terms", "zh-Hans", termsUrl),
            new LegalDocView("child_data", "zh-Hans", childDataUrl)
        );
    }

    public AccountStateView accountState(ReadingAuthenticatedUser user) {
        return accountState(user.userId(), providerFor(user));
    }

    public HomeSummaryView homeSummary(ReadingAuthenticatedUser user) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AccountStateView accountState = accountState(user);
        List<ReadingChildProfileEntity> children = childProfileMapper.selectActiveByUser(user.userId());
        ReadingChildProfileEntity currentChild = children.isEmpty() ? null : children.get(0);
        List<ReadingReviewCardEntity> recent = reviewCardMapper.selectRecentByUser(user.userId(), 5);
        int todayCompleted = dailyTaskCompletionMapper.countByUserAndDate(user.userId(), LocalDate.now(ZoneOffset.UTC));
        int dueCount = reviewCardMapper.countDueByUser(user.userId(), now);
        OffsetDateTime weekStart = startOfWeek(now);
        OffsetDateTime weekEnd = weekStart.plusDays(7);
        int weeklyReview = reviewEventMapper.countByUserBetween(user.userId(), weekStart, weekEnd);
        int weeklyActiveDays = reviewEventMapper.countActiveDaysByUserBetween(user.userId(), weekStart, weekEnd);
        List<ChildProgressView> childSummaries = children.stream()
            .map(child -> new ChildProgressView(
                child.getId(),
                child.getNickname(),
                child.getAgeBand(),
                defaultAvatar(child.getAvatarEmoji()),
                reviewCardMapper.countDueByChild(user.userId(), child.getId(), now),
                reviewCardMapper.countActiveByChild(user.userId(), child.getId()),
                dailyTaskCompletionMapper.countByUserChildAndDate(user.userId(), child.getId(), LocalDate.now(ZoneOffset.UTC))
            ))
            .toList();
        return new HomeSummaryView(
            currentChild == null ? null : toHomeChild(currentChild),
            todayCompleted,
            dueCount,
            recent.stream().map(this::toRecentCard).toList(),
            accountState.quota(),
            accountState.entitlement(),
            new LearningGrowthView(
                weeklyActiveDays > 0 ? 1 : 0,
                weeklyActiveDays,
                weeklyReview,
                weeklyReview > 0 ? "这周已经有复习记录了，继续保持短句高频回看。" : "先从今天的一句开始，连续几天回来复习，就会慢慢看到陪读节奏稳定下来。"
            ),
            childSummaries
        );
    }

    @Transactional
    public CreateChildReceipt createChild(ReadingAuthenticatedUser user, ChildMutationRequest request) {
        AccountStateView state = accountState(user);
        int childCount = childProfileMapper.countActiveByUser(user.userId());
        if (childCount >= state.entitlement().childLimit()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CHILD_LIMIT_REACHED");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ReadingChildProfileEntity entity = new ReadingChildProfileEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setAppCode(APP_CODE);
        entity.setUserId(user.userId());
        entity.setNickname(trimRequired(request.nickname(), "nickname"));
        entity.setAgeBand(normalizeAgeBand(request.ageBand()));
        entity.setLearningTrackCode(normalizeLearningTrack(request.learningTrackCode()));
        entity.setAvatarEmoji("🧸");
        entity.setProfileStatus("active");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        childProfileMapper.insert(entity);
        return new CreateChildReceipt(toChildView(entity), accountState(user));
    }

    @Transactional
    public CreateChildReceipt updateChild(ReadingAuthenticatedUser user, String childId, ChildMutationRequest request) {
        ReadingChildProfileEntity entity = requireChild(user.userId(), childId);
        entity.setNickname(trimRequired(request.nickname(), "nickname"));
        entity.setAgeBand(normalizeAgeBand(request.ageBand()));
        entity.setLearningTrackCode(normalizeLearningTrack(request.learningTrackCode()));
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        childProfileMapper.updateById(entity);
        return new CreateChildReceipt(toChildView(entity), accountState(user));
    }

    public List<ChildView> children(ReadingAuthenticatedUser user) {
        return childProfileMapper.selectActiveByUser(user.userId()).stream().map(this::toChildView).toList();
    }

    public DailyLearningTaskFeedView dailyTaskFeed(ReadingAuthenticatedUser user, String childId) {
        AccountEntitlementView entitlement = accountState(user).entitlement();
        List<ReadingChildProfileEntity> children = childProfileMapper.selectActiveByUser(user.userId());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String scope = entitlement.multiChildEnabled() ? "per_child" : "single_child";
        List<DailyLearningTaskView> tasks = children.stream()
            .filter(child -> childId == null || childId.isBlank() || child.getId().equals(childId))
            .map(child -> new DailyLearningTaskView(
                "daily-" + today + "-" + child.getId(),
                child.getId(),
                reviewCardMapper.countDueByUser(user.userId(), OffsetDateTime.now(ZoneOffset.UTC)) > 0 ? "review_one" : "local_ocr_one",
                reviewCardMapper.countDueByUser(user.userId(), OffsetDateTime.now(ZoneOffset.UTC)) > 0 ? "今天复习 1 张句卡" : "今天拍读 1 句",
                "后端会根据账号权益、孩子档案和句卡状态生成任务。",
                3,
                dailyTaskCompletionMapper.countByUserAndDate(user.userId(), today) > 0 ? "completed" : "generated",
                reviewCardMapper.countDueByUser(user.userId(), OffsetDateTime.now(ZoneOffset.UTC)) > 0 ? "go_review" : "go_local_ocr",
                "今天完成了，继续保持就好。"
            ))
            .toList();
        return new DailyLearningTaskFeedView(today.toString(), scope, !FREE_PLAN.equals(entitlement.planCode()), childId, tasks);
    }

    @Transactional
    public DailyLearningTaskCompletionView completeDailyTask(ReadingAuthenticatedUser user, String taskId, DailyTaskCompleteRequest request) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ReadingDailyTaskCompletionEntity completion = new ReadingDailyTaskCompletionEntity();
        completion.setAppCode(APP_CODE);
        completion.setUserId(user.userId());
        completion.setChildId(request.childId());
        completion.setTaskId(taskId);
        completion.setCompletionType(request.completionType() == null ? "completed" : request.completionType());
        completion.setTaskDate(today);
        completion.setCompletedAt(now);
        try {
            dailyTaskCompletionMapper.insert(completion);
        } catch (RuntimeException ignored) {
            // 同一 taskId 当天重复提交时保持幂等，避免客户端重试造成重复计数。
        }
        OffsetDateTime weekStart = startOfWeek(now);
        OffsetDateTime weekEnd = weekStart.plusDays(7);
        return new DailyLearningTaskCompletionView(
            taskId,
            "completed",
            now.toString(),
            reviewEventMapper.countActiveDaysByUserBetween(user.userId(), weekStart, weekEnd),
            reviewEventMapper.countActiveDaysByUserBetween(user.userId(), weekStart, weekEnd),
            reviewEventMapper.countByUserBetween(user.userId(), weekStart, weekEnd),
            dailyTaskCompletionMapper.countByUserAndDate(user.userId(), today),
            "今天完成了，继续保持就好。"
        );
    }

    public List<ReviewCardView> todayReviewCards(ReadingAuthenticatedUser user) {
        return reviewCardMapper.selectDueByUser(user.userId(), OffsetDateTime.now(ZoneOffset.UTC), 20).stream()
            .map(this::toReviewCardView)
            .toList();
    }

    @Transactional
    public CreateReviewCardReceipt createReviewCard(ReadingAuthenticatedUser user, CreateReviewCardRequest request) {
        ReadingChildProfileEntity child = requireChild(user.userId(), request.childId());
        ensureLocalCardLimitNotExceeded(user.userId(), accountState(user).entitlement());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ReadingReviewCardEntity card = new ReadingReviewCardEntity();
        card.setId(UUID.randomUUID().toString());
        card.setAppCode(APP_CODE);
        card.setUserId(user.userId());
        card.setChildId(child.getId());
        card.setLearningTrackCode(normalizeLearningTrack(request.learningTrackCode()));
        String encryptedText = trimRequired(request.encryptedText(), "encryptedText");
        if (!isClientEncryptedEnvelope(encryptedText) && !encryptedText.startsWith("hash:v1:sha256:")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ENCRYPTED_REVIEW_CARD_REQUIRED");
        }
        String decodedPreview = decodePreview(encryptedText);
        card.setEncryptedText(encryptedText);
        card.setTextPreview(decodedPreview);
        card.setSupportHint(blankToNull(request.supportHint()) == null ? "" : request.supportHint().trim());
        // COPPA/GDPR-K 收口：生产写入只接受客户端加密 envelope 或内容哈希。
        // 后端不再 base64 解码儿童原文，也不回填 source_text / translated_text。
        card.setSourceText(null);
        card.setTranslatedText(null);
        card.setContentEncryptionVersion(isClientEncryptedEnvelope(encryptedText) ? "aesgcm_keychain_v1" : "hash_reference_v1");
        card.setContentKeyId(isClientEncryptedEnvelope(encryptedText) ? "local_device_key_v1" : null);
        card.setSourceLanguageCode(defaultIfBlank(request.sourceLanguageCode(), sourceLanguageCode(card.getLearningTrackCode())));
        card.setTargetLanguageCode(defaultIfBlank(request.targetLanguageCode(), targetLanguageCode(card.getLearningTrackCode())));
        card.setSourceType("manual");
        card.setDeletedAt(null);
        card.setRecordVersion(1);
        card.setProficiency(0);
        card.setNextReviewAt(now.plusDays(1));
        card.setCardStatus("active");
        card.setCreatedAt(now);
        card.setUpdatedAt(now);
        reviewCardMapper.insert(card);
        return new CreateReviewCardReceipt(card.getId(), child.getId(), now.toString(), accountState(user));
    }

    @Transactional
    public ReviewEventReceipt recordReviewEvent(ReadingAuthenticatedUser user, ReviewEventRequest request) {
        ReadingReviewCardEntity card = reviewCardMapper.selectActiveByIdAndUser(trimRequired(request.cardId(), "cardId"), user.userId());
        if (card == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int nextProficiency = Math.min(Math.max((card.getProficiency() == null ? 0 : card.getProficiency()) + proficiencyDelta(request.resultLevel()), 0), 5);
        card.setProficiency(nextProficiency);
        card.setNextReviewAt(nextProficiency >= 3 ? now.plusDays(7) : now.plusDays(2));
        card.setLastReviewedAt(now);
        card.setRecordVersion(card.getRecordVersion() == null ? 1 : card.getRecordVersion() + 1);
        card.setUpdatedAt(now);
        reviewCardMapper.updateById(card);

        ReadingReviewEventV2Entity event = new ReadingReviewEventV2Entity();
        event.setId(UUID.randomUUID().toString());
        event.setAppCode(APP_CODE);
        event.setUserId(user.userId());
        event.setChildId(card.getChildId());
        event.setCardId(card.getId());
        event.setEventType(request.eventType() == null ? "completed" : request.eventType());
        event.setResultLevel(request.resultLevel() == null ? "remembered" : request.resultLevel());
        event.setEventAt(now);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        reviewEventMapper.insert(event);
        return new ReviewEventReceipt(card.getId(), event.getEventType(), nextProficiency, card.getNextReviewAt().toString(), now.toString());
    }

    public WeeklyParentReportView weeklyReport(ReadingAuthenticatedUser user, String childId, String scope) {
        AccountEntitlementView entitlement = accountState(user).entitlement();
        var access = weeklyReportAccessConfigService.current();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return weeklyReportForPeriod(user, childId, scope, startOfWeek(now), entitlement, access, true);
    }

    public WeeklyReportHistoryView weeklyHistory(ReadingAuthenticatedUser user, String childId, String scope) {
        AccountEntitlementView entitlement = accountState(user).entitlement();
        var access = weeklyReportAccessConfigService.current();
        int configuredWeeks = access.historyWeeksFor(entitlement.planCode());
        int entitlementWeeks = entitlement.weeklyReportHistoryWeeks() == null ? 0 : entitlement.weeklyReportHistoryWeeks();
        int historyWeeks = Math.min(configuredWeeks, entitlementWeeks);
        if (!Boolean.TRUE.equals(entitlement.historyEnabled()) || historyWeeks <= 0 || !access.moduleEnabled(entitlement.planCode(), "history_reports")) {
            return new WeeklyReportHistoryView(scope == null ? "child" : scope, childId, false, 0, 0, List.of(), Boolean.TRUE.equals(access.offlineHistoryPreviewEnabled()));
        }
        String effectiveScope = "family".equalsIgnoreCase(scope) ? "family" : "child";
        String snapshotChildId = snapshotChildId(user.userId(), childId, effectiveScope);
        OffsetDateTime currentWeekStart = startOfWeek(OffsetDateTime.now(ZoneOffset.UTC));
        List<WeeklyParentReportView> reports = new ArrayList<>();
        for (int weekOffset = 1; weekOffset <= historyWeeks; weekOffset++) {
            OffsetDateTime reportWeekStart = currentWeekStart.minusWeeks(weekOffset);
            WeeklyParentReportView report = weeklyReportSnapshotService
                .load(user.userId(), snapshotChildId, effectiveScope, reportWeekStart.toLocalDate(), entitlement.planCode())
                .orElseGet(() -> {
                    WeeklyParentReportView generated = weeklyReportForPeriod(user, snapshotChildId, effectiveScope, reportWeekStart, entitlement, access, false);
                    // P1 升级：历史周报首次访问时写入快照。无需额外定时任务，适合个人开发者低运维；
                    // 快照仅包含聚合统计和建议，不包含儿童原始句卡正文，降低合规和审核风险。
                    weeklyReportSnapshotService.save(
                        user.userId(),
                        generated.childId(),
                        generated.scope(),
                        reportWeekStart.toLocalDate(),
                        generated.planCode(),
                        generated.tier(),
                        generated
                    );
                    return generated;
                });
            reports.add(report);
        }
        return new WeeklyReportHistoryView(
            effectiveScope,
            snapshotChildId,
            !FREE_PLAN.equals(entitlement.planCode()),
            historyWeeks,
            historyWeeks,
            reports,
            Boolean.TRUE.equals(access.offlineHistoryPreviewEnabled())
        );
    }

    @Transactional
    public FeedbackSubmissionReceipt submitFeedback(ReadingAuthenticatedUser userOrNull, FeedbackSubmitRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String ticketNo = "fb-" + UUID.randomUUID().toString().substring(0, 8);
        ReadingFeedbackTicketEntity ticket = new ReadingFeedbackTicketEntity();
        ticket.setAppCode(APP_CODE);
        ticket.setUserId(userOrNull == null ? null : userOrNull.userId());
        ticket.setTicketNo(ticketNo);
        ticket.setCategory(trimRequired(request.category(), "category"));
        ticket.setContent(trimRequired(request.content(), "content"));
        ticket.setContactEmail(blankToNull(request.contactEmail()));
        ticket.setAuthMode(blankToNull(request.authMode()));
        ticket.setTraceId(blankToNull(request.traceId()));
        ticket.setStatus("open");
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        feedbackTicketMapper.insert(ticket);
        return new FeedbackSubmissionReceipt(ticketNo, ticket.getCategory(), now.toString(), supportEmail);
    }

    @Transactional
    public OcrExtractReceipt buildCloudOcrQuotaBlocked(CloudUsageDecision decision) {
        return new OcrExtractReceipt(
            null,
            "cloud_service",
            "quota_blocked",
            "",
            "manual_review",
            0,
            0,
            false,
            "trial_exhausted",
            decision.remainingCount(),
            decision.upgradeTitle(),
            decision.upgradeMessage(),
            decision.unlockOptions()
        );
    }

    @Transactional
    public OcrExtractReceipt buildCloudOcrUnavailable(ReadingAuthenticatedUser user, OcrExtractRequest request, CloudUsageDecision decision) {
        String traceId = "ocr-" + UUID.randomUUID();
        ReadingOcrAuditEntity audit = new ReadingOcrAuditEntity();
        audit.setAppCode(APP_CODE);
        audit.setUserId(user.userId());
        audit.setTraceId(traceId);
        audit.setProvider("cloud_service");
        audit.setModel("not_configured");
        audit.setStatus("discontinued");
        audit.setNote("Cloud OCR is discontinued. Device OCR should be used.");
        audit.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        ocrAuditMapper.insert(audit);
        return new OcrExtractReceipt(
            traceId,
            "cloud_service",
            "discontinued",
            "",
            request.promptOverride() == null || request.promptOverride().isBlank() ? "manual_review" : request.promptOverride(),
            0,
            0,
            false,
            "discontinued",
            decision.remainingCount(),
            "云端识别已停用",
            "当前版本仅使用设备端识别，不再通过在线 OCR API 发放或消耗识别权益。",
            decision.unlockOptions()
        );
    }

    public CloudSpeechReceipt buildCloudSpeechQuotaBlocked(CloudUsageDecision decision, CloudSpeechRequest request) {
        return new CloudSpeechReceipt(
            false,
            "trial_exhausted",
            decision.remainingCount(),
            "cloud_service",
            "quota_blocked",
            null,
            null,
            request.text(),
            request.languageCode(),
            request.rate(),
            decision.upgradeTitle(),
            decision.upgradeMessage(),
            decision.unlockOptions()
        );
    }

    public CloudSpeechReceipt buildCloudSpeechUnavailable(CloudUsageDecision decision, CloudSpeechRequest request) {
        return new CloudSpeechReceipt(
            false,
            "not_configured",
            decision.remainingCount(),
            "cloud_service",
            "not_configured",
            null,
            null,
            null,
            request.languageCode(),
            request.rate(),
            "云端朗读暂未启用",
            "当前版本仅使用设备端朗读；云端朗读未来会改为家长同意后的 capability / reservation 模式，业务后端不会保存儿童正文。",
            decision.unlockOptions()
        );
    }


    public OcrExtractReceipt buildCloudOcrResult(OcrProviderResult providerResult, CloudUsageDecision decision) {
        return new OcrExtractReceipt(
            providerResult.traceId(),
            providerResult.provider() == null ? "alibaba_bailian" : providerResult.provider(),
            providerResult.model() == null ? "unknown" : providerResult.model(),
            providerResult.text() == null ? "" : providerResult.text(),
            providerResult.prompt() == null ? "manual_review" : providerResult.prompt(),
            providerResult.minPixels() == null ? 0 : providerResult.minPixels(),
            providerResult.maxPixels() == null ? 0 : providerResult.maxPixels(),
            providerResult.success(),
            providerResult.success() ? "succeeded" : "provider_failed",
            decision.remainingCount(),
            providerResult.success() ? null : "云端识别当前不可用",
            providerResult.errorMessage(),
            decision.unlockOptions()
        );
    }

    public CloudSpeechReceipt buildCloudSpeechResult(TtsProviderResult providerResult, CloudUsageDecision decision) {
        return new CloudSpeechReceipt(
            providerResult.success(),
            providerResult.success() ? "succeeded" : "provider_failed",
            decision.remainingCount(),
            providerResult.provider() == null ? "alibaba_bailian" : providerResult.provider(),
            providerResult.model() == null ? "unknown" : providerResult.model(),
            providerResult.audioBase64(),
            providerResult.mimeType(),
            providerResult.text(),
            providerResult.languageCode(),
            providerResult.rate(),
            providerResult.success() ? null : "云端朗读当前不可用",
            providerResult.errorMessage(),
            decision.unlockOptions()
        );
    }

    public SysEmailVerificationService.EmailVerificationTicketView requestDeletionCode(ReadingAuthenticatedUser user, String emailOverride) {
        String email = resolveDeletionEmail(emailOverride);
        return sysEmailVerificationService.requestCode(
            APP_CODE,
            email,
            DELETE_ACCOUNT_SCENE,
            null,
            Map.of(
                "userId", String.valueOf(user.userId()),
                "provider", providerFor(user),
                "sessionSource", user.session().getSessionSource() == null ? "" : user.session().getSessionSource()
            )
        );
    }

    @Transactional
    public DeletionRequestResponse confirmDeletionByCode(
        ReadingAuthenticatedUser user,
        String code,
        String email,
        Boolean confirmDataDeletion,
        String idempotencyKey
    ) {
        if (!Boolean.TRUE.equals(confirmDataDeletion)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CONFIRM_DATA_DELETION_REQUIRED");
        }
        String resolvedEmail = resolveDeletionEmail(email);
        sysEmailVerificationService.consumeCode(APP_CODE, resolvedEmail, DELETE_ACCOUNT_SCENE, code);
        return executeDeletion(user, providerFor(user));
    }

    @Transactional
    public DeletionRequestResponse deleteAccount(ReadingAuthenticatedUser user, DeletionRequest request) {
        if (!Boolean.TRUE.equals(request.confirmDataDeletion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CONFIRM_DATA_DELETION_REQUIRED");
        }
        if (request.verificationCode() == null || request.verificationCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DELETION_CODE_REQUIRED");
        }
        String resolvedEmail = resolveDeletionEmail(request.email());
        sysEmailVerificationService.consumeCode(APP_CODE, resolvedEmail, DELETE_ACCOUNT_SCENE, request.verificationCode());
        return executeDeletion(user, request.provider());
    }

    private DeletionRequestResponse executeDeletion(ReadingAuthenticatedUser user, String providerHint) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String provider = providerHint == null ? providerFor(user) : providerHint;
        AppleRevokeResultView revokeResult = null;
        if ("apple".equalsIgnoreCase(provider)) {
            revokeResult = appDefinitionService.get(APP_CODE)
                .flatMap(app -> sysAppleAuthService.revoke(app, user.rawToken()))
                .orElse(null);
        }
        int children = childProfileMapper.deactivateAllByUser(user.userId(), now);
        int reviewCards = reviewCardMapper.deactivateAllByUser(user.userId(), now);
        int reviewEvents = reviewEventMapper.deleteByUser(user.userId());
        int sessions = sysAuthSessionMapper.revokeAllByUser(APP_CODE, user.userId(), now);
        int identities = sysUserIdentityMapper.revokeAllByUser(APP_CODE, user.userId(), now);
        sysAuthProviderTokenMapper.revokeAllByUser(APP_CODE, user.userId(), now);
        var sysUser = user.user();
        sysUser.setStatus("deleted");
        sysUser.setDisplayName("Deleted user");
        sysUser.setUpdatedAt(now);
        sysUserMapper.updateById(sysUser);
        return new DeletionRequestResponse(
            UUID.randomUUID().toString(),
            "completed",
            "completed",
            now.toString(),
            now.toString(),
            now.toString(),
            null,
            provider,
            true,
            false,
            revokeResult == null ? "not_applicable" : revokeResult.remoteRevokeStatus(),
            revokeResult == null ? "当前账号不是 Apple formal session，或未找到可撤销的 Apple provider token。" : revokeResult.note(),
            sessions,
            identities,
            children,
            reviewCards,
            reviewEvents,
            null,
            null,
            "已完成账号删除请求：登录态已撤销，账号已匿名化，孩子档案和句卡不再对该账号开放。"
        );
    }

    public SubscriptionStatusView subscriptionStatus(ReadingAuthenticatedUser user) {
        AccountEntitlementView entitlement = accountState(user).entitlement();
        EntitlementOverviewView overview = sysBillingService.getEntitlements(APP_CODE, user.userId());
        var recent = purchaseTransactionMapper.selectRecentByUser(APP_CODE, user.userId(), 10).stream()
            .map(item -> new IntakeItemView(String.valueOf(item.getId()), item.getSourceType(), item.getProcessingStatus(), item.getVerificationStatus(), item.getProductId(), planCodeForProduct(item.getProductId()), item.getCreatedAt() == null ? null : item.getCreatedAt().toString(), null))
            .toList();
        var projections = overview.entitlements().stream()
            .map(item -> new ProjectionView(item.entitlementCode(), item.status(), "verified", null, planCodeForEntitlement(item.entitlementCode()), item.expiresAt() == null ? null : item.expiresAt().toString(), item.sourceType(), null))
            .toList();
        VerificationReadinessView verificationReadiness = subscriptionVerificationReadiness();
        return new SubscriptionStatusView(
            entitlement.planCode(),
            entitlement.planName(),
            entitlement.authoritative(),
            overview.pendingTransactionCount() > 0 || purchaseTransactionMapper.countPendingByUser(APP_CODE, user.userId()) > 0,
            verificationReadiness,
            projections,
            recent
        );
    }

    public EntitlementRefreshView refreshEntitlement(ReadingAuthenticatedUser user) {
        sysBillingService.refreshEntitlements(APP_CODE, user.userId());
        EntitlementOverviewView overview = sysBillingService.getEntitlements(APP_CODE, user.userId());
        AccountEntitlementView entitlement = accountState(user).entitlement();
        int activeProjectionCount = overview == null || overview.entitlements() == null
            ? 0
            : (int) overview.entitlements().stream()
                .filter(item -> item != null && "active".equalsIgnoreCase(item.status()))
                .count();
        return new EntitlementRefreshView(OffsetDateTime.now(ZoneOffset.UTC).toString(), entitlement.planCode(), entitlement.planName(), activeProjectionCount, "backend_refresh");
    }

    public IntakeReceipt intakeReceipt(ReadingAuthenticatedUser user, Long intakeId, String sourceType, String status, String verificationStatus, String productId) {
        AccountStateView accountState = accountState(user);
        String planCode = planCodeForProduct(productId);
        return new IntakeReceipt(String.valueOf(intakeId), sourceType, status, verificationStatus, productId, planCode, accountState.entitlement().planCode(), true, "已提交后端验证，权益以后端权威投影为准。", accountState);
    }

    public AccountStateView accountState(Long userId, String provider) {
        AccountEntitlementView entitlement = resolveEntitlement(userId);
        int childCount = childProfileMapper.countActiveByUser(userId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime dayStart = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        int localOcrUsed = deviceEventMapper.countByUserEventBetween(APP_CODE, userId, "local_ocr", dayStart, dayStart.plusDays(1));
        int localTtsUsed = deviceEventMapper.countByUserEventBetween(APP_CODE, userId, "local_tts", dayStart, dayStart.plusDays(1));
        int dailyLoginGiftLimit = dailyQuotaLimit(entitlement, ReadingDailyQuotaConfigService.FEATURE_LOCAL_DEVICE);
        int dailyLoginGiftUsed = Math.min(localOcrUsed + localTtsUsed, dailyLoginGiftLimit);
        int dailyLoginGiftRemaining = Math.max(dailyLoginGiftLimit - dailyLoginGiftUsed, 0);
        // 中文说明：打开 App 查询账号状态时按后端配置发放当天唯一一条本机功能积分赠送记录。
        cloudUsageService.ensureDailyLoginGiftGrant(userId, dailyLoginGiftLimit, dailyLoginGiftUsed, dayStart, dayStart.plusDays(1).minusSeconds(1), now.toLocalDate().toString());
        int baseLocalOcrLimit = dailyLoginGiftLimit;
        int baseLocalTtsLimit = dailyLoginGiftLimit;
        ReadingCloudUsageService.CreditGrantBalance localOcrCredits = activeCreditBalance(userId, ReadingCloudUsageService.LOCAL_OCR);
        ReadingCloudUsageService.CreditGrantBalance localTtsCredits = activeCreditBalance(userId, ReadingCloudUsageService.LOCAL_TTS);
        int localOcrPlanUsed = Math.min(localOcrUsed, baseLocalOcrLimit);
        int localTtsPlanUsed = Math.min(localTtsUsed, baseLocalTtsLimit);
        int localOcrCreditTotal = Math.max(localOcrCredits.totalCount(), 0);
        int localTtsCreditTotal = Math.max(localTtsCredits.totalCount(), 0);
        int localOcrCreditUsed = Math.min(Math.max(localOcrCredits.usedCount(), 0), localOcrCreditTotal);
        int localTtsCreditUsed = Math.min(Math.max(localTtsCredits.usedCount(), 0), localTtsCreditTotal);
        int localOcrLimit = baseLocalOcrLimit + localOcrCreditTotal;
        int localTtsLimit = baseLocalTtsLimit + localTtsCreditTotal;
        localOcrUsed = Math.min(localOcrPlanUsed + localOcrCreditUsed, localOcrLimit);
        localTtsUsed = Math.min(localTtsPlanUsed + localTtsCreditUsed, localTtsLimit);
        int remaining = Math.max(baseLocalOcrLimit - localOcrPlanUsed, 0) + Math.max(localOcrCredits.remainingCount(), 0);
        int localTtsRemaining = Math.max(baseLocalTtsLimit - localTtsPlanUsed, 0) + Math.max(localTtsCredits.remainingCount(), 0);
        return accountStateView(userId, provider, entitlement, childCount, now.toLocalDate(), localOcrLimit, localOcrUsed, remaining, localTtsLimit, localTtsUsed, localTtsRemaining, dailyLoginGiftLimit, dailyLoginGiftUsed, dailyLoginGiftRemaining);
    }

    private ReadingCloudUsageService.CreditGrantBalance activeCreditBalance(Long userId, String serviceType) {
        ReadingCloudUsageService.CreditGrantBalance balance = cloudUsageService.activeCreditBalance(userId, serviceType);
        return balance == null ? new ReadingCloudUsageService.CreditGrantBalance(serviceType, 0, 0, 0) : balance;
    }

    @Transactional
    public AccountStateView recordQuotaUsage(ReadingAuthenticatedUser user, QuotaUsageRequest request) {
        String kind = request == null || request.kind() == null ? "" : request.kind().trim().toLowerCase(Locale.ROOT);
        boolean localOcrQuota = "ocr".equals(kind)
            || "local_ocr".equals(kind)
            || "text_recognition".equals(kind)
            || "image_ocr".equals(kind)
            || "picture_ocr".equals(kind)
            || "photo_ocr".equals(kind);
        boolean localTtsQuota = "local_tts".equals(kind)
            || "tts".equals(kind)
            || "voice_reading".equals(kind)
            || "text_to_speech".equals(kind)
            || "speech_synthesis".equals(kind);
        if (!localTtsQuota && !localOcrQuota) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported quota usage kind");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        String idempotencyKey = request.idempotencyKey() == null ? "" : request.idempotencyKey().trim();
        if (idempotencyKey.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idempotencyKey is required");
        }
        AccountEntitlementView entitlement = resolveEntitlement(user.userId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime dayStart = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        String eventType = localTtsQuota ? "local_tts" : "local_ocr";
        int duplicateCount = deviceEventMapper.countByUserEventAndIdempotencyBetween(
            APP_CODE,
            user.userId(),
            eventType,
            idempotencyKey,
            dayStart,
            dayStart.plusDays(1)
        );
        if (duplicateCount > 0) {
            return accountState(user);
        }
        int baseLimit = dailyQuotaLimit(entitlement, ReadingDailyQuotaConfigService.FEATURE_LOCAL_DEVICE);
        int used = localDeviceDailyUsed(user.userId(), dayStart, dayStart.plusDays(1));
        if (used >= baseLimit) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, localTtsQuota ? "Read-aloud quota exhausted" : "OCR quota exhausted");
        }
        if (duplicateCount <= 0) {
            SysUserDeviceEventEntity event = new SysUserDeviceEventEntity();
            event.setAppCode(APP_CODE);
            event.setUserId(user.userId());
            event.setSessionId(user.session().getId());
            event.setEventType(eventType);
            event.setClientPlatform("ios");
            event.setPayloadJson("{\"idempotencyKey\":\"" + idempotencyKey.replace("\"", "") + "\"}");
            event.setCreatedAt(now);
            deviceEventMapper.insert(event);
            // 中文说明：日赠积分是识字/朗读共享总池，消耗后立即回写当天唯一赠送记录的 used_count。
            cloudUsageService.ensureDailyLoginGiftGrant(user.userId(), baseLimit, used + 1, dayStart, dayStart.plusDays(1).minusSeconds(1), now.toLocalDate().toString());
        }
        return accountState(user);
    }

    private int localDeviceDailyUsed(Long userId, OffsetDateTime dayStart, OffsetDateTime dayEnd) {
        int localOcrUsed = deviceEventMapper.countByUserEventBetween(APP_CODE, userId, "local_ocr", dayStart, dayEnd);
        int localTtsUsed = deviceEventMapper.countByUserEventBetween(APP_CODE, userId, "local_tts", dayStart, dayEnd);
        return Math.max(localOcrUsed, 0) + Math.max(localTtsUsed, 0);
    }

    private AccountStateView accountStateView(
        Long userId,
        String provider,
        AccountEntitlementView entitlement,
        int childCount,
        LocalDate quotaDate,
        int localOcrLimit,
        int used,
        int remaining,
        int localTtsLimit,
        int localTtsUsed,
        int localTtsRemaining,
        int dailyLoginGiftLimit,
        int dailyLoginGiftUsed,
        int dailyLoginGiftRemaining
    ) {
        return new AccountStateView(
            String.valueOf(userId),
            provider,
            new AccountEntitlementView(
                entitlement.planCode(),
                entitlement.planName(),
                entitlement.entitlementCode(),
                entitlement.dailyLocalOcrLimit(),
                dailyQuotaLimit(entitlement, ReadingDailyQuotaConfigService.FEATURE_LOCAL_TTS),
                entitlement.childLimit(),
                entitlement.localCardLimit(),
                childCount,
                Math.max(entitlement.childLimit() - childCount, 0),
                entitlement.advancedVoiceEnabled(),
                entitlement.premiumActive() && Boolean.TRUE.equals(entitlement.serverVerified()),
                entitlement.validUntil(),
                true,
                entitlement.multiChildEnabled(),
                entitlement.dailyPlanScope(),
                entitlement.weeklyReportScope(),
                entitlement.weeklyReportHistoryWeeks(),
                entitlement.historyEnabled(),
                false,
                entitlement.serverVerified(),
                entitlement.verificationSource(),
                entitlement.accessProof()
            ),
            new DailyQuotaView(quotaDate.toString(), localOcrLimit, used, remaining, localTtsLimit, localTtsUsed, localTtsRemaining, dailyLoginGiftLimit, dailyLoginGiftUsed, dailyLoginGiftRemaining)
        );
    }

    private VerificationReadinessView subscriptionVerificationReadiness() {
        var definition = appDefinitionService.get(APP_CODE).orElse(null);
        if (definition == null) {
            return new VerificationReadinessView(
                false,
                false,
                false,
                null,
                null,
                "App definition is missing; subscription verification readiness is blocked."
            );
        }
        var readiness = appAppleReadinessService.inspect(definition);
        String bundleId = rawDefinitionValue(definition, "app.billing.appstore.bundleId");
        String environment = rawDefinitionValue(definition, "app.billing.appstore.environment");
        boolean bundleContextConfigured = readiness.appStore().bundleIdConfigured();
        boolean serverApiCredentialsConfigured = readiness.appStore().issuerIdConfigured()
            && readiness.appStore().keyIdConfigured()
            && readiness.appStore().privateKeyConfigured();
        boolean explicitMappingsConfigured = true;
        if (definition.support().billingRequired()) {
            try {
                var observability = sysBillingService.describeEntitlementObservability(APP_CODE);
                explicitMappingsConfigured = observability != null && observability.effectiveMappingCount() > 0;
            } catch (Exception ignored) {
                explicitMappingsConfigured = false;
            }
        }
        boolean cryptographicVerificationLive = "ready".equalsIgnoreCase(readiness.overallStatus())
            && "ready".equalsIgnoreCase(readiness.appStore().status())
            && (!readiness.auth().required() || "ready".equalsIgnoreCase(readiness.auth().status()))
            && explicitMappingsConfigured;
        String note = cryptographicVerificationLive
            ? "Backend Apple readiness is ready. Subscription verification can rely on configured Server API and signing checks."
            : readiness.blockers().isEmpty()
                ? "Backend Apple readiness is not fully ready yet: " + String.join("; ", readiness.warnings())
                : "Backend Apple readiness is blocked: " + String.join("; ", readiness.blockers());
        if (!explicitMappingsConfigured) {
            note = appendNote(note, "Explicit productId to entitlement mapping is missing, so verified transactions still cannot be projected safely.");
        }
        return new VerificationReadinessView(
            bundleContextConfigured,
            serverApiCredentialsConfigured,
            cryptographicVerificationLive,
            bundleId,
            environment,
            note
        );
    }

    private String rawDefinitionValue(com.apphub.backend.sys.app.model.AppDefinition definition, String key) {
        if (definition == null || definition.raw() == null) {
            return null;
        }
        Object value = definition.raw().get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private AccountEntitlementView resolveEntitlement(Long userId) {
        // 中文说明：优先使用统一权益中心模式三决策；若新表未初始化或异常，则回退旧 reading_plan_catalog，保证现有 App 不受影响。
        AccountEntitlementView centerEntitlement = resolveEntitlementFromCenter(userId);
        if (centerEntitlement != null) {
            return centerEntitlement;
        }
        EntitlementOverviewView overview = sysBillingService.getEntitlements(APP_CODE, userId);
        LinkedHashSet<String> activeEntitlementCodes = new LinkedHashSet<>();
        if (overview != null && overview.entitlements() != null) {
            overview.entitlements().stream()
                .filter(item -> item != null && "active".equalsIgnoreCase(item.status()))
                .map(item -> item.entitlementCode())
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toLowerCase(Locale.ROOT))
                .forEach(activeEntitlementCodes::add);
        }
        PlanCatalogEntry matched = resolvePlanCatalogEntries().stream()
            .filter(entry -> activeEntitlementCodes.stream().anyMatch(code -> matchesPlanCatalogEntryForEntitlementCode(entry, code)))
            .findFirst()
            .orElse(null);
        if (matched != null) {
            AccountEntitlementView entitlement = configuredEntitlement(matched);
            AppEntitlementAccessGuard.Decision decision = AppEntitlementAccessGuard.decide(
                APP_CODE,
                userId,
                overview,
                requiredEntitlementCodesForEntry(matched),
                matched.planCode() == null || matched.planCode().isBlank() ? List.of("__missing_plan__") : List.of(matched.planCode()),
                entitlement.planCode(),
                "active",
                "reading_weekly_report_access.access_matrix_v1"
            );
            return withVerifiedAccessProof(entitlement, decision);
        }
        AccountEntitlementView free = configuredEntitlement(FREE_PLAN, defaultFreeEntitlement());
        AppEntitlementAccessGuard.Decision decision = AppEntitlementAccessGuard.decide(
            APP_CODE,
            userId,
            overview,
            List.of("free"),
            List.of(FREE_PLAN),
            FREE_PLAN,
            "free",
            "reading_weekly_report_access.access_matrix_v1"
        );
        return withVerifiedAccessProof(free, decision);
    }

    private AccountEntitlementView resolveEntitlementFromCenter(Long userId) {
        try {
            UserEntitlementDecisionView decision = sysEntitlementCenterService.resolveUserEntitlement(APP_CODE, userId);
            if (decision == null || decision.features() == null || decision.features().isEmpty()) {
                return null;
            }
            FeatureAccessView childProfile = decision.features().get("child_profile");
            FeatureAccessView dailyLocalOcr = decision.features().get("daily_local_ocr");
            FeatureAccessView localCard = decision.features().get("local_card");
            FeatureAccessView advancedVoice = decision.features().get("advanced_voice");
            FeatureAccessView multiChild = decision.features().get("multi_child");
            FeatureAccessView weeklyReport = decision.features().get("weekly_report");
            FeatureAccessView weeklyHistory = decision.features().get("weekly_report_history");
            FeatureAccessView customReminder = decision.features().get("custom_reminder");
            boolean paidActive = decision.paid() && decision.serverVerified();
            return new AccountEntitlementView(
                decision.planCode(),
                decision.planName(),
                decision.entitlementCode(),
                limitOr(dailyLocalOcr, 3),
                limitOr(decision.features().get("daily_local_tts"), 10),
                limitOr(childProfile, 1),
                limitOr(localCard, 20),
                0,
                limitOr(childProfile, 1),
                paidActive && enabled(advancedVoice),
                paidActive,
                decision.expiresAt(),
                true,
                paidActive && enabled(multiChild),
                firstNonBlank(scope(dailyLocalOcr), "single_child"),
                firstNonBlank(scope(weeklyReport), "child"),
                paidActive && enabled(weeklyHistory) ? limitOr(weeklyHistory, 0) : 0,
                paidActive && enabled(weeklyHistory),
                paidActive && enabled(customReminder),
                decision.serverVerified(),
                "backend_sys_billing",
                readingCompatAccessProof(userId, decision, paidActive)
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> readingCompatAccessProof(Long userId, UserEntitlementDecisionView decision, boolean paidActive) {
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("appCode", APP_CODE);
        proof.put("userId", String.valueOf(userId));
        proof.put("plan", decision.planCode());
        proof.put("status", paidActive ? "active" : "free");
        proof.put("policy", "sys_entitlement_center.mode3.compat_reading");
        proof.put("allowed", paidActive || FREE_PLAN.equalsIgnoreCase(decision.planCode()));
        proof.put("source", decision.verificationSource());
        proof.put("activeEntitlements", decision.activeEntitlements() == null ? List.of() : decision.activeEntitlements());
        proof.put("mode3Note", "中文说明：当前接口保持拍拍伴读旧字段兼容；真实权益由统一权益中心模式三合并，老用户有效购买快照不会因后台降配被减少。");
        return proof;
    }

    private boolean enabled(FeatureAccessView feature) {
        return feature != null && Boolean.TRUE.equals(feature.enabled());
    }

    private int limitOr(FeatureAccessView feature, int fallback) {
        return feature == null || feature.limitValue() == null ? fallback : feature.limitValue();
    }

    private int dailyQuotaLimit(AccountEntitlementView entitlement, String featureCode) {
        String planCode = entitlement == null ? FREE_PLAN : entitlement.planCode();
        return dailyQuotaConfigService.dailyLimit(planCode, featureCode);
    }

    private String scope(FeatureAccessView feature) {
        return feature == null ? null : feature.scopeCode();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private AccountEntitlementView withVerifiedAccessProof(AccountEntitlementView source, AppEntitlementAccessGuard.Decision decision) {
        boolean paidFeatureActive = !FREE_PLAN.equals(source.planCode()) && decision.allowed();
        return new AccountEntitlementView(
            source.planCode(),
            source.planName(),
            source.entitlementCode(),
            source.dailyLocalOcrLimit(),
            source.dailyLocalTtsLimit(),
            source.childLimit(),
            source.localCardLimit(),
            source.childCount(),
            source.remainingChildSlots(),
            paidFeatureActive && Boolean.TRUE.equals(source.advancedVoiceEnabled()),
            paidFeatureActive,
            source.validUntil(),
            source.authoritative(),
            paidFeatureActive && Boolean.TRUE.equals(source.multiChildEnabled()),
            source.dailyPlanScope(),
            source.weeklyReportScope(),
            paidFeatureActive ? source.weeklyReportHistoryWeeks() : 0,
            paidFeatureActive && Boolean.TRUE.equals(source.historyEnabled()),
            paidFeatureActive && Boolean.TRUE.equals(source.customReminderEnabled()),
            decision.serverVerified(),
            decision.verificationSource(),
            decision.accessProof()
        );
    }

    private AccountEntitlementView configuredEntitlement(String planCode, AccountEntitlementView fallback) {
        Map<String, Object> items = loadNamespaceItems(PLAN_NAMESPACE);
        return entitlementFromConfig(items.get(planCode), fallback);
    }

    private AccountEntitlementView configuredEntitlement(PlanCatalogEntry entry) {
        if (entry == null) {
            return configuredEntitlement(FREE_PLAN, defaultFreeEntitlement());
        }
        return entitlementFromConfig(entry.rawConfig(), entitlementFallbackForPlan(entry));
    }

    private PaywallView resolvePaywallConfig() {
        Map<String, Object> items = loadNamespaceItems(PAYWALL_NAMESPACE);
        Map<String, Object> config = asMap(items.get("default"));
        PaywallView fallback = defaultPaywallConfig();
        if (config.isEmpty()) {
            return fallback;
        }
        return new PaywallView(
            stringValue(config, "defaultHighlight", fallback.defaultHighlight()),
            boolValue(config, "trialEnabled", fallback.trialEnabled()),
            stringValue(config, "headline", fallback.headline()),
            stringValue(config, "subtitle", fallback.subtitle()),
            stringListOrFallback(config.get("trustBullets"), fallback.trustBullets()),
            stringValue(config, "legalNotice", fallback.legalNotice())
        );
    }

    private PaywallView defaultPaywallConfig() {
        return new PaywallView(
            "local_ocr_100",
            false,
            "本机积分",
            "用于当前设备的本地识字和朗读。购买由 Apple 确认，余额只保存在本机 Keychain。",
            List.of(
                "购买或赠送的积分不按日期过期，使用后按页面显示的消耗值扣减。",
                "学习内容和本机积分默认只保存在当前设备，不上传到开发者服务器。",
                "消耗型本机积分不支持跨设备自动恢复。"
            ),
            "价格与扣款以 Apple 确认弹窗为准；换机、抹掉设备或重置本机钱包后余额可能无法恢复。"
        );
    }

    private PlanView defaultFreePlan() {
        return new PlanView(
            FREE_PLAN,
            "免费版",
            1,
            3,
            20,
            false,
            null,
            false,
            "¥0",
            null,
            null,
            false,
            resolveSupportedLocales(),
            resolveLearningTracks().stream().map(LearningTrackView::code).toList()
        );
    }

    private PlanView defaultFamilyPlan() {
        return new PlanView(
            FAMILY_PLAN,
            "家庭多孩子终身版",
            5,
            50,
            800,
            true,
            familyProductId,
            true,
            familyDisplayPrice,
            familyOriginalPrice,
            "一次开通",
            true,
            resolveSupportedLocales(),
            resolveLearningTracks().stream().map(LearningTrackView::code).toList()
        );
    }

    private CreditProductView resourcePackView(ReadingResourcePackCatalogEntity entity, String locale) {
        String productCode = firstNonBlank(entity.getPackageCode(), "unknown");
        String packageType = firstNonBlank(entity.getPackageType(), "resource_pack");
        String serviceType = firstNonBlank(entity.getServiceType(), ReadingCloudUsageService.LOCAL_TTS);
        Integer amount = entity.getIncludedQuantity() == null ? 0 : entity.getIncludedQuantity();
        return new CreditProductView(
            productCode,
            packageType,
            serviceType,
            localizedJsonText(entity.getDisplayNameJson(), locale, productCode),
            localizedJsonText(entity.getDisplayDescriptionJson(), locale, ""),
            amount,
            firstNonBlank(entity.getQuantityUnit(), "count"),
            formatPrice(entity.getPriceAmountCents(), entity.getCurrencyCode()),
            firstNonBlank(entity.getCurrencyCode(), "CNY"),
            entity.getPriceAmountCents() == null ? 0 : entity.getPriceAmountCents(),
            entity.getValidDays() == null ? 30 : entity.getValidDays(),
            firstNonBlank(entity.getAppStoreProductId(), metadataString(entity.getMetadataJson(), "appStoreProductId"), productCode),
            "active".equalsIgnoreCase(firstNonBlank(entity.getStatus(), "")),
            firstNonBlank(entity.getStatus(), "inactive"),
            entity.getSortOrder() == null ? 100 : entity.getSortOrder(),
            null,
            null
        );
    }

    /**
     * 将商品基础信息与购买权限合并。
     *
     * <p>关键参数：globalPermission 是应用级购买开关；productPermission 是单商品购买开关。
     * 任一层禁用都会把 enabled 置为 false，并返回 disabledMessage/messageKey 给前端展示。
     */
    private CreditProductView productWithPermission(CreditProductView product, PurchasePermissionDecision globalPermission, String locale) {
        PurchasePermissionDecision productPermission = purchasePermissionService.decide(APP_CODE, product.productCode(), locale);
        boolean allowed = Boolean.TRUE.equals(globalPermission.allowed()) && Boolean.TRUE.equals(productPermission.allowed());
        String disabledMessage = !Boolean.TRUE.equals(globalPermission.allowed())
            ? firstNonBlank(globalPermission.message(), "服务暂不可用")
            : (!Boolean.TRUE.equals(productPermission.allowed()) ? firstNonBlank(productPermission.message(), "服务暂不可用") : null);
        return new CreditProductView(
            product.productCode(),
            product.packageType(),
            product.serviceType(),
            product.displayName(),
            product.displayDescription(),
            product.amount(),
            product.quantityUnit(),
            product.displayPrice(),
            product.currency(),
            product.priceAmountCents(),
            product.validDays(),
            product.appStoreProductId(),
            Boolean.TRUE.equals(product.enabled()) && allowed,
            Boolean.TRUE.equals(product.enabled()) && allowed ? "active" : "disabled",
            product.sortOrder(),
            disabledMessage,
            !Boolean.TRUE.equals(globalPermission.allowed()) ? globalPermission.messageKey() : productPermission.messageKey()
        );
    }

    private boolean isCreditGrantProduct(CreditProductView product) {
        String serviceType = product == null ? "" : firstNonBlank(product.serviceType(), "").toLowerCase(Locale.ROOT);
        return ReadingCloudUsageService.LOCAL_OCR.equals(serviceType)
            || ReadingCloudUsageService.LOCAL_TTS.equals(serviceType)
            || ReadingCloudUsageService.CLOUD_OCR.equals(serviceType)
            || ReadingCloudUsageService.CLOUD_TTS.equals(serviceType);
    }

    private int grantCapacityExtension(Long userId, CreditProductView product, String sourceRef) {
        AccountEntitlementView entitlement = resolveEntitlement(userId);
        String serviceType = firstNonBlank(product.serviceType(), "").toLowerCase(Locale.ROOT);
        int amount = product.amount() == null ? 0 : product.amount();
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be greater than zero");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusDays(product.validDays() == null ? 3650 : product.validDays());
        if (RESOURCE_CHILD_PROFILE.equals(serviceType)) {
            int nextLimit = Math.max(entitlement.childLimit() == null ? 1 : entitlement.childLimit(), 0) + amount;
            replaceFeatureOverride(userId, RESOURCE_CHILD_PROFILE, true, "limited", nextLimit, "count", "account", expiresAt, product, sourceRef);
            return nextLimit;
        }
        if (RESOURCE_LOCAL_CARD.equals(serviceType)) {
            int nextLimit = Math.max(entitlement.localCardLimit() == null ? 20 : entitlement.localCardLimit(), 0) + amount;
            replaceFeatureOverride(userId, RESOURCE_LOCAL_CARD, true, "limited", nextLimit, "count", "account", expiresAt, product, sourceRef);
            return nextLimit;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported resource package type");
    }

    private void replaceFeatureOverride(
        Long userId,
        String featureCode,
        boolean enabled,
        String accessLevel,
        int limitValue,
        String limitUnit,
        String scopeCode,
        OffsetDateTime expiresAt,
        CreditProductView product,
        String sourceRef
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        userFeatureOverrideMapper.update(null, new LambdaUpdateWrapper<SysUserFeatureOverrideEntity>()
            .eq(SysUserFeatureOverrideEntity::getAppCode, APP_CODE)
            .eq(SysUserFeatureOverrideEntity::getUserId, userId)
            .eq(SysUserFeatureOverrideEntity::getFeatureCode, featureCode)
            .eq(SysUserFeatureOverrideEntity::getStatus, "active")
            .set(SysUserFeatureOverrideEntity::getStatus, "superseded")
            .set(SysUserFeatureOverrideEntity::getUpdatedAt, now));
        SysUserFeatureOverrideEntity override = new SysUserFeatureOverrideEntity();
        override.setAppCode(APP_CODE);
        override.setUserId(userId);
        override.setFeatureCode(featureCode);
        override.setEnabled(enabled);
        override.setAccessLevel(accessLevel);
        override.setLimitValue(limitValue);
        override.setLimitUnit(limitUnit);
        override.setScopeCode(scopeCode);
        override.setStatus("active");
        override.setStartsAt(now);
        override.setExpiresAt(expiresAt);
        override.setReason("internal_resource_pack_purchase:" + product.productCode());
        override.setOperatorUserId(null);
        override.setMetadataJson(null);
        override.setCreatedAt(now);
        override.setUpdatedAt(now);
        userFeatureOverrideMapper.insert(override);
    }

    private String localizedJsonText(String rawJson, String locale, String fallback) {
        Map<String, Object> values = jsonMap(rawJson);
        if (values.isEmpty()) {
            return fallback;
        }
        for (String key : localeKeys(locale)) {
            String value = stringValue(values, key, null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return firstNonBlank(
            stringValue(values, "zh-Hans", null),
            stringValue(values, "en", null),
            stringValue(values, "default", null),
            fallback
        );
    }

    private String metadataString(String rawJson, String key) {
        Map<String, Object> values = jsonMap(rawJson);
        return stringValue(values, key, null);
    }

    private List<String> localeKeys(String locale) {
        String normalized = locale == null ? "" : locale.trim().replace("_", "-").toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return List.of("zh-Hans", "zh", "en");
        }
        if (normalized.startsWith("zh")) {
            return List.of("zh-Hans", "zh", "en");
        }
        if (normalized.startsWith("ja")) {
            return List.of("ja", "en", "zh-Hans");
        }
        if (normalized.startsWith("ko")) {
            return List.of("ko", "en", "zh-Hans");
        }
        if (normalized.startsWith("es")) {
            return List.of("es", "en", "zh-Hans");
        }
        return List.of("en", "zh-Hans");
    }

    private Map<String, Object> jsonMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String formatPrice(Integer priceAmountCents, String currencyCode) {
        int cents = priceAmountCents == null ? 0 : Math.max(priceAmountCents, 0);
        String currency = firstNonBlank(currencyCode, "CNY").toUpperCase(Locale.ROOT);
        BigDecimal amount = BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).stripTrailingZeros();
        if ("CNY".equals(currency)) {
            return "¥" + amount.toPlainString();
        }
        return currency + " " + amount.toPlainString();
    }


    private AccountEntitlementView defaultFamilyEntitlement() {
        return new AccountEntitlementView(FAMILY_PLAN, "家庭多孩子终身版", FAMILY_ENTITLEMENT, 50, 100, 5, 800, 0, 5, true, true, null, true, true, "per_child", "family", 12, true, false, false, null, Map.of());
    }

    private AccountEntitlementView defaultFreeEntitlement() {
        return new AccountEntitlementView(FREE_PLAN, "免费版", "free", 3, 10, 1, 20, 0, 1, false, false, null, true, false, "single_child", "child", 0, false, false, true, "backend_sys_billing", Map.of("appCode", APP_CODE, "policy", "reading_weekly_report_access.access_matrix_v1", "plan", FREE_PLAN));
    }

    private Map<String, Object> loadNamespaceItems(String namespaceCode) {
        try {
            RemoteConfigNamespaceView view = sysRemoteConfigService.loadNamespace(APP_CODE, namespaceCode);
            return view == null || view.items() == null ? Map.of() : view.items();
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private PlanView planViewFromConfig(Object raw, PlanView fallback) {
        Map<String, Object> config = asMap(raw);
        if (config.isEmpty()) {
            return fallback;
        }
        if (fallback == null) {
            String code = stringValue(config, "code", null);
            if (code == null || code.isBlank()) {
                return null;
            }
            fallback = new PlanView(code, code, 1, 3, 20, false, null, false, null, null, null, false, resolveSupportedLocales(), resolveLearningTracks().stream().map(LearningTrackView::code).toList());
        }
        return new PlanView(
            stringValue(config, "code", fallback.code()),
            stringValue(config, "displayName", fallback.displayName()),
            intValue(config, "childLimit", fallback.childLimit()),
            intValue(config, "dailyLocalOcrLimit", fallback.dailyLocalOcrLimit()),
            intValue(config, "localCardLimit", fallback.localCardLimit()),
            boolValue(config, "advancedVoiceEnabled", fallback.advancedVoiceEnabled()),
            stringValue(config, "appStoreProductId", fallback.appStoreProductId()),
            boolValue(config, "highlight", fallback.highlight()),
            stringValue(config, "displayPrice", fallback.displayPrice()),
            stringValue(config, "originalPrice", fallback.originalPrice()),
            stringValue(config, "badgeText", fallback.badgeText()),
            boolValue(config, "historyEnabled", fallback.historyEnabled()),
            stringListOrFallback(config.get("supportedLocales"), fallback.supportedLocales()),
            stringListOrFallback(config.get("supportedLearningTrackCodes"), fallback.supportedLearningTrackCodes())
        );
    }

    private AccountEntitlementView entitlementFromConfig(Object raw, AccountEntitlementView fallback) {
        Map<String, Object> config = asMap(raw);
        if (config.isEmpty()) {
            return fallback;
        }
        int childLimit = intValue(config, "childLimit", fallback.childLimit());
        return new AccountEntitlementView(
            stringValue(config, "code", fallback.planCode()),
            stringValue(config, "displayName", fallback.planName()),
            stringValue(config, "entitlementCode", fallback.entitlementCode()),
            intValue(config, "dailyLocalOcrLimit", fallback.dailyLocalOcrLimit()),
            intValue(config, "dailyLocalTtsLimit", fallback.dailyLocalTtsLimit()),
            childLimit,
            intValue(config, "localCardLimit", fallback.localCardLimit()),
            fallback.childCount(),
            Math.max(childLimit - fallback.childCount(), 0),
            boolValue(config, "advancedVoiceEnabled", fallback.advancedVoiceEnabled()),
            boolValue(config, "premiumActive", fallback.premiumActive()),
            fallback.validUntil(),
            fallback.authoritative(),
            boolValue(config, "multiChildEnabled", fallback.multiChildEnabled()),
            stringValue(config, "dailyPlanScope", fallback.dailyPlanScope()),
            stringValue(config, "weeklyReportScope", fallback.weeklyReportScope()),
            intValue(config, "weeklyReportHistoryWeeks", fallback.weeklyReportHistoryWeeks()),
            boolValue(config, "historyEnabled", fallback.historyEnabled()),
            boolValue(config, "customReminderEnabled", fallback.customReminderEnabled()),
            fallback.serverVerified(),
            fallback.verificationSource(),
            fallback.accessProof()
        );
    }

    private void ensureLocalCardLimitNotExceeded(Long userId, AccountEntitlementView entitlement) {
        int localCardLimit = entitlement == null || entitlement.localCardLimit() == null ? 0 : entitlement.localCardLimit();
        int activeCardCount = reviewCardMapper.countActiveByUser(userId);
        if (activeCardCount >= localCardLimit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "LOCAL_CARD_LIMIT_REACHED");
        }
    }

    private List<String> resolveSupportedLocales() {
        Map<String, Object> items = loadNamespaceItems(LANGUAGE_NAMESPACE);
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.addAll(stringList(items.get("supported_locales")));
        ordered.addAll(stringList(items.get("supported_source_languages")));
        ordered.addAll(stringList(items.get("supported_target_languages")));
        if (ordered.isEmpty()) {
            ordered.addAll(DEFAULT_SUPPORTED_LOCALES);
        }
        return List.copyOf(ordered);
    }

    private List<LearningTrackView> resolveLearningTracks() {
        Map<String, Object> items = loadNamespaceItems(LANGUAGE_NAMESPACE);
        Object raw = items.get("learning_tracks");
        if (raw instanceof List<?> list) {
            List<LearningTrackView> tracks = list.stream()
                .map(this::learningTrackViewFromConfig)
                .filter(item -> item != null)
                .toList();
            if (!tracks.isEmpty()) {
                return tracks;
            }
        }
        return List.of(
            new LearningTrackView("zh_to_en", "中文家庭学英语"),
            new LearningTrackView("en_to_zh", "English families learn Chinese")
        );
    }

    private LearningTrackView learningTrackViewFromConfig(Object raw) {
        Map<String, Object> config = asMap(raw);
        if (config.isEmpty()) {
            return null;
        }
        String code = stringValue(config, "code", null);
        String label = stringValue(config, "label", code);
        if (code == null || code.isBlank() || label == null || label.isBlank()) {
            return null;
        }
        return new LearningTrackView(code, label);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .filter(item -> item != null && !item.toString().isBlank())
            .map(item -> item.toString().trim())
            .toList();
    }

    private List<String> stringListOrFallback(Object raw, List<String> fallback) {
        List<String> parsed = stringList(raw);
        return parsed.isEmpty() ? fallback : parsed;
    }

    private String stringValue(Map<String, Object> config, String key, String fallback) {
        Object raw = config.get(key);
        if (raw == null || raw.toString().isBlank()) {
            return fallback;
        }
        return raw.toString().trim();
    }

    private int intValue(Map<String, Object> config, String key, Integer fallback) {
        Object raw = config.get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return raw == null ? (fallback == null ? 0 : fallback) : Integer.parseInt(raw.toString().trim());
        } catch (Exception ignored) {
            return fallback == null ? 0 : fallback;
        }
    }

    private boolean boolValue(Map<String, Object> config, String key, Boolean fallback) {
        Object raw = config.get(key);
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw == null) {
            return fallback != null && fallback;
        }
        return switch (raw.toString().trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> fallback != null && fallback;
        };
    }

    private List<PlanCatalogEntry> resolvePlanCatalogEntries() {
        Map<String, Object> items = loadNamespaceItems(PLAN_NAMESPACE);
        LinkedHashMap<String, PlanCatalogEntry> entries = new LinkedHashMap<>();
        entries.put(FREE_PLAN, new PlanCatalogEntry(FREE_PLAN, defaultFreeEntitlement().entitlementCode(), items.get(FREE_PLAN), false, defaultFreeEntitlement().childLimit(), defaultFreeEntitlement().localCardLimit()));
        entries.put(FAMILY_PLAN, new PlanCatalogEntry(FAMILY_PLAN, defaultFamilyEntitlement().entitlementCode(), items.get(FAMILY_PLAN), true, defaultFamilyEntitlement().childLimit(), defaultFamilyEntitlement().localCardLimit()));
        items.forEach((key, raw) -> {
            Map<String, Object> config = asMap(raw);
            if (config.isEmpty()) {
                return;
            }
            String code = stringValue(config, "code", key);
            if (code == null || code.isBlank()) {
                return;
            }
            String entitlementCode = stringValue(config, "entitlementCode", code);
            boolean premium = boolValue(config, "premiumActive", !FREE_PLAN.equals(code));
            int childLimit = intValue(config, "childLimit", FREE_PLAN.equals(code) ? 1 : 2);
            int localCardLimit = intValue(config, "localCardLimit", FREE_PLAN.equals(code) ? 20 : 50);
            entries.put(code, new PlanCatalogEntry(code, entitlementCode, raw, premium, childLimit, localCardLimit));
        });
        return entries.values().stream()
            .sorted((left, right) -> {
                if (left.premium() != right.premium()) {
                    return Boolean.compare(right.premium(), left.premium());
                }
                if (left.childLimit() != right.childLimit()) {
                    return Integer.compare(right.childLimit(), left.childLimit());
                }
                if (left.localCardLimit() != right.localCardLimit()) {
                    return Integer.compare(right.localCardLimit(), left.localCardLimit());
                }
                return left.planCode().compareTo(right.planCode());
            })
            .toList();
    }

    /**
     * 这里把“产品 ID / entitlementCode 命中哪个套餐”统一收口为显式配置解析。
     *
     * 允许两类显式别名：
     * 1. plan catalog 内声明的 `matchedProductIds` / `matchedEntitlementCodes`
     * 2. billing_entitlements namespace 或 app-definition 里的 productId -> entitlementCode 映射
     *
     * 不再保留基于字符串 contains("family") 的静默猜测，避免把未知商品错误映射成可用套餐。
     */
    private String planCodeForProduct(String productId) {
        PlanCatalogEntry directMatch = planCatalogEntryForProduct(productId);
        if (directMatch != null) {
            return directMatch.planCode();
        }
        String entitlementCode = explicitEntitlementCodeForProduct(productId);
        PlanCatalogEntry entitlementMatch = planCatalogEntryForEntitlement(entitlementCode);
        return entitlementMatch == null ? null : entitlementMatch.planCode();
    }

    private String planCodeForEntitlement(String entitlementCode) {
        PlanCatalogEntry entry = planCatalogEntryForEntitlement(entitlementCode);
        return entry == null ? null : entry.planCode();
    }

    private PlanCatalogEntry planCatalogEntryForProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            return null;
        }
        return resolvePlanCatalogEntries().stream()
            .filter(entry -> matchesPlanCatalogEntryForProductId(entry, productId))
            .findFirst()
            .orElse(null);
    }

    private PlanCatalogEntry planCatalogEntryForEntitlement(String entitlementCode) {
        if (entitlementCode == null || entitlementCode.isBlank()) {
            return null;
        }
        return resolvePlanCatalogEntries().stream()
            .filter(entry -> matchesPlanCatalogEntryForEntitlementCode(entry, entitlementCode))
            .findFirst()
            .orElse(null);
    }

    private boolean matchesPlanCatalogEntryForProductId(PlanCatalogEntry entry, String productId) {
        if (entry == null || productId == null || productId.isBlank()) {
            return false;
        }
        Map<String, Object> config = asMap(entry.rawConfig());
        String primaryProductId = stringValue(config, "appStoreProductId", null);
        if (primaryProductId != null && primaryProductId.equals(productId)) {
            return true;
        }
        return configuredAliasList(config, "matchedProductIds", "productIds").stream()
            .anyMatch(productId::equals);
    }

    private boolean matchesPlanCatalogEntryForEntitlementCode(PlanCatalogEntry entry, String entitlementCode) {
        if (entry == null || entitlementCode == null || entitlementCode.isBlank()) {
            return false;
        }
        String normalized = entitlementCode.trim().toLowerCase(Locale.ROOT);
        return requiredEntitlementCodesForEntry(entry).stream()
            .map(item -> item.trim().toLowerCase(Locale.ROOT))
            .anyMatch(normalized::equals);
    }

    private List<String> requiredEntitlementCodesForEntry(PlanCatalogEntry entry) {
        if (entry == null) {
            return List.of("__missing_entitlement__");
        }
        Map<String, Object> config = asMap(entry.rawConfig());
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        String primaryEntitlementCode = stringValue(config, "entitlementCode", entry.entitlementCode());
        if (primaryEntitlementCode != null && !primaryEntitlementCode.isBlank()) {
            codes.add(primaryEntitlementCode.trim());
        }
        configuredAliasList(config, "matchedEntitlementCodes", "entitlementAliases").stream()
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .forEach(codes::add);
        return codes.isEmpty() ? List.of("__missing_entitlement__") : List.copyOf(codes);
    }

    private List<String> configuredAliasList(Map<String, Object> config, String... keys) {
        if (config == null || config.isEmpty() || keys == null) {
            return List.of();
        }
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (String key : keys) {
            aliases.addAll(stringList(config.get(key)));
        }
        return List.copyOf(aliases);
    }

    private String explicitEntitlementCodeForProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            return null;
        }
        Map<String, Object> items = loadNamespaceItems("billing_entitlements");
        String namespaced = stringValue(items, "productMappings." + productId, null);
        if (namespaced != null) {
            return namespaced;
        }
        String direct = stringValue(items, productId, null);
        if (direct != null) {
            return direct;
        }
        var definition = appDefinitionService.get(APP_CODE).orElse(null);
        if (definition == null || definition.raw() == null) {
            return null;
        }
        Object configured = definition.raw().get("app.billing.entitlements.productMappings." + productId);
        if (configured != null && !String.valueOf(configured).isBlank()) {
            return String.valueOf(configured).trim();
        }
        Object relaxed = definition.raw().get("app.billing.entitlements.product-mappings." + productId);
        if (relaxed != null && !String.valueOf(relaxed).isBlank()) {
            return String.valueOf(relaxed).trim();
        }
        return null;
    }

    private AccountEntitlementView entitlementFallbackForPlan(PlanCatalogEntry entry) {
        if (entry == null) {
            return defaultFreeEntitlement();
        }
        if (FREE_PLAN.equals(entry.planCode())) {
            return defaultFreeEntitlement();
        }
        if (FAMILY_PLAN.equals(entry.planCode())) {
            return defaultFamilyEntitlement();
        }
        Map<String, Object> config = asMap(entry.rawConfig());
        int childLimit = intValue(config, "childLimit", 1);
        int localCardLimit = intValue(config, "localCardLimit", 20);
        boolean premiumActive = boolValue(config, "premiumActive", !FREE_PLAN.equals(entry.planCode()));
        boolean multiChildEnabled = boolValue(config, "multiChildEnabled", childLimit > 1);
        int weeklyReportHistoryWeeks = intValue(config, "weeklyReportHistoryWeeks", 0);
        return new AccountEntitlementView(
            entry.planCode(),
            stringValue(config, "displayName", entry.planCode()),
            stringValue(config, "entitlementCode", entry.entitlementCode()),
            intValue(config, "dailyLocalOcrLimit", 3),
            intValue(config, "dailyLocalTtsLimit", 10),
            childLimit,
            localCardLimit,
            0,
            childLimit,
            boolValue(config, "advancedVoiceEnabled", false),
            premiumActive,
            null,
            true,
            multiChildEnabled,
            stringValue(config, "dailyPlanScope", multiChildEnabled ? "per_child" : "single_child"),
            stringValue(config, "weeklyReportScope", multiChildEnabled ? "family" : "child"),
            weeklyReportHistoryWeeks,
            boolValue(config, "historyEnabled", weeklyReportHistoryWeeks > 0),
            boolValue(config, "customReminderEnabled", false),
            false,
            null,
            Map.of()
        );
    }

    private ReadingChildProfileEntity requireChild(Long userId, String childId) {
        ReadingChildProfileEntity child = childProfileMapper.selectActiveByIdAndUser(trimRequired(childId, "childId"), userId);
        if (child == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CHILD_NOT_FOUND");
        }
        return child;
    }

    private WeeklyParentReportView weeklyReportForPeriod(
        ReadingAuthenticatedUser user,
        String childId,
        String scope,
        OffsetDateTime weekStart,
        AccountEntitlementView entitlement,
        ReadingWeeklyReportAccessConfigService.WeeklyReportAccessConfig access,
        boolean currentWeek
    ) {
        boolean familyScope = "family".equalsIgnoreCase(scope);
        if (familyScope && (!Boolean.TRUE.equals(entitlement.multiChildEnabled()) || !access.moduleEnabled(entitlement.planCode(), "family_overview"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "REPORT_PREMIUM_SCOPE_REQUIRED");
        }
        OffsetDateTime weekEnd = weekStart.plusDays(7);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<ReadingChildProfileEntity> children = childProfileMapper.selectActiveByUser(user.userId());
        ReadingChildProfileEntity child = resolveReportChild(user.userId(), children, childId, familyScope);
        WeeklyStatsView stats = stats(user.userId(), child == null ? null : child.getId(), weekStart, weekEnd);
        String planCode = entitlement.planCode();
        String tier = access.tierFor(planCode);
        List<String> suggestions = cappedSuggestions(planCode, access);
        List<WeeklyChildSummaryView> childSummaries = familyScope && access.moduleEnabled(planCode, "child_summaries")
            ? children.stream().map(item -> new WeeklyChildSummaryView(
                item.getId(),
                item.getNickname(),
                stats(user.userId(), item.getId(), weekStart, weekEnd),
                "本周重点看陪读节奏，不做孩子之间的排名比较。",
                "下周继续每天一句，优先复习到期和低熟练度句卡。"
            )).toList()
            : List.of();
        String summary = stats.hasActivity()
            ? (currentWeek ? "这周已经产生了陪读和复习记录，可以继续保持短句高频节奏。" : "这一周已经留下陪读和复习记录，可作为家庭回顾参考。")
            : "这一周记录较少，建议从每天 1 句的轻节奏开始。";
        String highlight = stats.weeklyReviewCount() > 0 ? "已经开始复习，继续保持。" : "先保存 1 张句卡，再安排复习。";
        String nextWeekSuggestion = "下周建议保持每天 1 句，优先处理到期复习卡。";
        List<WeeklyReportModuleView> modules = buildWeeklyReportModules(planCode, access, stats, summary, highlight, nextWeekSuggestion, suggestions, childSummaries);
        return new WeeklyParentReportView(
            (familyScope ? "family" : "child") + "|" + weekStart.toLocalDate(),
            familyScope ? "family" : "child",
            weekStart.toLocalDate().toString(),
            weekEnd.minusDays(1).toLocalDate().toString(),
            !FREE_PLAN.equals(planCode),
            child == null ? null : child.getId(),
            child == null ? null : child.getNickname(),
            stats,
            summary,
            highlight,
            nextWeekSuggestion,
            suggestions,
            access.disclaimer(),
            childSummaries,
            now.toString(),
            planCode,
            tier,
            Boolean.TRUE.equals(access.pageShareEnabled()),
            Boolean.TRUE.equals(access.exportReportEnabled()) && access.moduleEnabled(planCode, "export_report"),
            Boolean.TRUE.equals(access.offlineHistoryPreviewEnabled()) && access.moduleEnabled(planCode, "offline_history_preview"),
            modules
        );
    }

    private List<String> cappedSuggestions(String planCode, ReadingWeeklyReportAccessConfigService.WeeklyReportAccessConfig access) {
        List<String> all = List.of(
            "每天只拍一句，降低孩子压力。",
            "复习时先读熟悉句，再处理新句。",
            "把陪读控制在 3-5 分钟，更容易长期坚持。",
            "优先处理到期复习卡，不需要一次追求很多新内容。",
            "多孩子家庭可以分开看节奏，不做孩子之间的排名。"
        );
        int maxItems = Math.min(access.maxItems(planCode, "basic_suggestions", 2), all.size());
        return all.subList(0, Math.max(maxItems, 0));
    }

    private List<WeeklyReportModuleView> buildWeeklyReportModules(
        String planCode,
        ReadingWeeklyReportAccessConfigService.WeeklyReportAccessConfig access,
        WeeklyStatsView stats,
        String summary,
        String highlight,
        String nextWeekSuggestion,
        List<String> suggestions,
        List<WeeklyChildSummaryView> childSummaries
    ) {
        List<WeeklyReportModuleView> modules = new ArrayList<>();
        if (access.moduleEnabled(planCode, "basic_stats")) {
            modules.add(new WeeklyReportModuleView("basic_stats", "本周基础回顾", "full", Map.of(
                "weeklyActiveDays", stats.weeklyActiveDays(),
                "weeklyReviewCount", stats.weeklyReviewCount(),
                "weeklySavedCardCount", stats.weeklySavedCardCount(),
                "savedCardCount", stats.savedCardCount(),
                "childCount", stats.childCount()
            )));
        }
        if (access.moduleEnabled(planCode, "safe_summary")) {
            modules.add(new WeeklyReportModuleView("safe_summary", "本周小结", "full", Map.of("text", summary)));
        }
        if (access.moduleEnabled(planCode, "basic_suggestions")) {
            modules.add(new WeeklyReportModuleView("basic_suggestions", "下周建议", "full", Map.of("items", suggestions)));
        }
        if (access.moduleEnabled(planCode, "single_child_detail")) {
            modules.add(new WeeklyReportModuleView("single_child_detail", "单孩子详细回顾", "full", Map.of(
                "highlight", highlight,
                "nextWeekSuggestion", nextWeekSuggestion,
                "reviewDueCount", stats.reviewDueCount() == null ? 0 : stats.reviewDueCount()
            )));
        }
        if (access.moduleEnabled(planCode, "review_focus")) {
            modules.add(new WeeklyReportModuleView("review_focus", "复习重点", "full", Map.of(
                "weeklyReviewCount", stats.weeklyReviewCount(),
                "reviewDueCount", stats.reviewDueCount() == null ? 0 : stats.reviewDueCount(),
                "message", stats.weeklyReviewCount() > 0 ? "继续保持短句高频复习。" : "先从 1 张已保存句卡开始复习。"
            )));
        }
        if (access.moduleEnabled(planCode, "family_overview")) {
            modules.add(new WeeklyReportModuleView("family_overview", "家庭总览", "full", Map.of(
                "childCount", stats.childCount(),
                "weeklyActiveDays", stats.weeklyActiveDays(),
                "weeklyReviewCount", stats.weeklyReviewCount()
            )));
        }
        if (access.moduleEnabled(planCode, "child_summaries")) {
            modules.add(new WeeklyReportModuleView("child_summaries", "孩子小结", "full", Map.of("items", childSummaries)));
        }
        if (access.moduleEnabled(planCode, "history_reports")) {
            modules.add(new WeeklyReportModuleView("history_reports", "历史回顾", "full", Map.of("weeks", access.historyWeeksFor(planCode))));
        }
        if (access.moduleEnabled(planCode, "offline_history_preview")) {
            modules.add(new WeeklyReportModuleView("offline_history_preview", "离线历史预览", "full", Map.of("enabled", true)));
        }
        if (access.moduleEnabled(planCode, "page_share")) {
            modules.add(new WeeklyReportModuleView("page_share", "页面分享", "full", Map.of("enabled", true)));
        }
        return modules;
    }

    private String snapshotChildId(Long userId, String childId, String scope) {
        if ("family".equalsIgnoreCase(scope)) {
            return null;
        }
        List<ReadingChildProfileEntity> children = childProfileMapper.selectActiveByUser(userId);
        ReadingChildProfileEntity child = resolveReportChild(userId, children, childId, false);
        return child == null ? null : child.getId();
    }

    private ReadingChildProfileEntity resolveReportChild(Long userId, List<ReadingChildProfileEntity> children, String childId, boolean familyScope) {
        if (familyScope) {
            return null;
        }
        if (childId != null && !childId.isBlank()) {
            return requireChild(userId, childId);
        }
        return children.isEmpty() ? null : children.get(0);
    }

    private WeeklyStatsView stats(Long userId, String childId, OffsetDateTime start, OffsetDateTime end) {
        int weeklyReview = childId == null
            ? reviewEventMapper.countByUserBetween(userId, start, end)
            : reviewEventMapper.countByChildBetween(userId, childId, start, end);
        int saved = childId == null
            ? reviewCardMapper.countActiveByUser(userId)
            : reviewCardMapper.countActiveByChild(userId, childId);
        int newSaved = childId == null
            ? reviewCardMapper.countCreatedBetween(userId, start, end)
            : reviewCardMapper.countCreatedByChildBetween(userId, childId, start, end);
        int activeDays = childId == null
            ? reviewEventMapper.countActiveDaysByUserBetween(userId, start, end)
            : reviewEventMapper.countActiveDaysByChildBetween(userId, childId, start, end);
        int due = childId == null
            ? reviewCardMapper.countDueByUser(userId, OffsetDateTime.now(ZoneOffset.UTC))
            : reviewCardMapper.countDueByChild(userId, childId, OffsetDateTime.now(ZoneOffset.UTC));
        return new WeeklyStatsView(
            activeDays,
            weeklyReview,
            newSaved,
            saved,
            activeDays > 0 ? 1 : 0,
            due,
            null,
            0,
            newSaved,
            childId == null ? childProfileMapper.countActiveByUser(userId) : 1
        );
    }

    private String providerFor(ReadingAuthenticatedUser user) {
        String source = user.session().getSessionSource();
        if (source == null || source.isBlank()) {
            return "session";
        }
        return source.toLowerCase(Locale.ROOT).contains("apple") ? "apple" : source;
    }

    private String resolveDeletionEmail(String emailOverride) {
        if (emailOverride == null || emailOverride.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DELETION_EMAIL_REQUIRED");
        }
        return emailOverride.trim().toLowerCase(Locale.ROOT);
    }

    private String appendNote(String note, String extra) {
        if (extra == null || extra.isBlank()) {
            return note;
        }
        if (note == null || note.isBlank()) {
            return extra;
        }
        return note + " " + extra;
    }

    private int proficiencyDelta(String resultLevel) {
        if (resultLevel == null) {
            return 1;
        }
        return switch (resultLevel) {
            case "easy", "remembered", "mastered" -> 1;
            case "forgot", "hard" -> -1;
            default -> 1;
        };
    }

    private OffsetDateTime startOfWeek(OffsetDateTime now) {
        return now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            .toLocalDate()
            .atStartOfDay()
            .atOffset(ZoneOffset.UTC);
    }

    private String trimRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String jsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String normalizeAgeBand(String value) {
        String normalized = value == null || value.isBlank() ? "age_5_7" : value.trim();
        return normalized.startsWith("age_") ? normalized : "age_" + normalized;
    }

    private String normalizeLearningTrack(String value) {
        return value == null || value.isBlank() ? "zh_to_en" : value.trim();
    }

    private String sourceLanguageCode(String learningTrackCode) {
        String[] pair = languagePair(learningTrackCode);
        if (pair != null) {
            return pair[0];
        }
        return "bilingual".equalsIgnoreCase(learningTrackCode) ? "en" : "zh-Hans";
    }

    private String targetLanguageCode(String learningTrackCode) {
        String[] pair = languagePair(learningTrackCode);
        if (pair != null) {
            return pair[1];
        }
        return "bilingual".equalsIgnoreCase(learningTrackCode) ? "zh-Hans" : "en";
    }

    private String[] languagePair(String learningTrackCode) {
        if (learningTrackCode == null) {
            return null;
        }
        String[] parts = learningTrackCode.trim().toLowerCase(Locale.ROOT).replace('-', '_').split("_to_", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return null;
        }
        return new String[] { normalizedLanguageCode(parts[0]), normalizedLanguageCode(parts[1]) };
    }

    private String normalizedLanguageCode(String languageCode) {
        if (languageCode == null) {
            return "";
        }
        String normalized = languageCode.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        if (normalized.equals("zh") || normalized.startsWith("zh-hans") || normalized.startsWith("zh-cn")) {
            return "zh-Hans";
        }
        if (normalized.startsWith("zh-hant") || normalized.startsWith("zh-tw") || normalized.startsWith("zh-hk")) {
            return "zh-Hant";
        }
        int separator = normalized.indexOf('-');
        return separator > 0 ? normalized.substring(0, separator) : normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String defaultAvatar(String value) {
        return value == null || value.isBlank() ? "🧸" : value;
    }

    private String decodePreview(String encryptedText) {
        return "已保存句卡";
    }

    private boolean isClientEncryptedEnvelope(String encryptedText) {
        return encryptedText != null && encryptedText.startsWith("enc:v1:aesgcm:keychain:");
    }

    private ChildView toChildView(ReadingChildProfileEntity entity) {
        return new ChildView(entity.getId(), entity.getNickname(), entity.getAgeBand(), entity.getLearningTrackCode(), defaultAvatar(entity.getAvatarEmoji()));
    }

    private HomeChildView toHomeChild(ReadingChildProfileEntity entity) {
        return new HomeChildView(entity.getId(), entity.getNickname(), entity.getAgeBand(), defaultAvatar(entity.getAvatarEmoji()));
    }

    private RecentReviewCardView toRecentCard(ReadingReviewCardEntity entity) {
        return new RecentReviewCardView(
            entity.getId(),
            entity.getTextPreview() == null || entity.getTextPreview().isBlank() ? "已保存句卡" : entity.getTextPreview(),
            entity.getSupportHint() == null ? "" : entity.getSupportHint(),
            entity.getProficiency() == null ? 0 : entity.getProficiency(),
            entity.getNextReviewAt() == null ? null : entity.getNextReviewAt().toString(),
            entity.getSourceLanguageCode(),
            entity.getTargetLanguageCode(),
            entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString()
        );
    }

    private ReviewCardView toReviewCardView(ReadingReviewCardEntity entity) {
        return new ReviewCardView(
            entity.getId(),
            entity.getTextPreview() == null || entity.getTextPreview().isBlank() ? "已保存句卡" : entity.getTextPreview(),
            entity.getSupportHint() == null ? "" : entity.getSupportHint(),
            entity.getProficiency() == null ? 0 : entity.getProficiency(),
            entity.getNextReviewAt() == null ? null : entity.getNextReviewAt().toString(),
            entity.getSourceLanguageCode(),
            entity.getTargetLanguageCode()
        );
    }

    public record BootstrapConfigView(
        String appName,
        Boolean kidsCategoryEnabled,
        Integer captureCharLimit,
        String defaultLocale,
        List<String> supportedLocales,
        List<LearningTrackView> learningTracks,
        PaywallView paywall,
        ReadingUsagePolicyService.UsagePolicyView usagePolicy,
        String supportEmail,
        String supportUrl,
        String deleteAccountUrl
    ) {}

    public record LearningTrackView(String code, String label) {}
    public record PaywallView(
        String defaultHighlight,
        Boolean trialEnabled,
        String headline,
        String subtitle,
        List<String> trustBullets,
        String legalNotice
    ) {}
    public record LegalDocView(String type, String locale, String url) {}

    public record PlanView(
        String code,
        String displayName,
        Integer childLimit,
        Integer dailyLocalOcrLimit,
        Integer localCardLimit,
        Boolean advancedVoiceEnabled,
        String appStoreProductId,
        Boolean highlight,
        String displayPrice,
        String originalPrice,
        String badgeText,
        Boolean historyEnabled,
        List<String> supportedLocales,
        List<String> supportedLearningTrackCodes
    ) {}

    public record BillingHealthView(
        String status,
        Boolean purchaseAvailable,
        String unavailableMessage,
        String checkedAt
    ) {}

    public record CreditProductView(
        String productCode,
        String packageType,
        String serviceType,
        String displayName,
        String displayDescription,
        Integer amount,
        String quantityUnit,
        String displayPrice,
        String currency,
        Integer priceAmountCents,
        Integer validDays,
        String appStoreProductId,
        Boolean enabled,
        String status,
        Integer sortOrder,
        String disabledMessage,
        String messageKey
    ) {}

    public record DailyLoginGiftConfigView(
        String appCode,
        String planCode,
        String featureCode,
        Integer dailyGiftCredits,
        String recordMode,
        String fetchedAt
    ) {}

    @Schema(description = "内部购买受理请求体。")
    public record InternalPurchaseRequest(
        @Schema(description = "后端配置的商品编码。", example = "ocr_50") String productCode,
        @Schema(description = "内部票据或业务幂等键。", example = "ticket-20260504-001") String purchaseTicket,
        @Schema(description = "幂等键，防止重复发放。", example = "purchase-20260504-001") String idempotencyKey,
        @Schema(description = "前端界面语言，用于返回实时禁购提示。", example = "zh-Hans") String locale
    ) {}

    public record InternalPurchaseReceipt(
        String status,
        String paymentMode,
        CreditProductView product,
        Integer remainingCount,
        String expiresAt,
        Integer purchasedToday,
        Integer dailyPurchaseLimit,
        AccountStateView accountState
    ) {}

    public record EntitlementRecordPageView(
        Integer page,
        Integer pageSize,
        Boolean hasMore,
        List<ReadingCloudUsageService.ActiveEntitlementView> records
    ) {}

    public record AccountStateView(String accountId, String signInProvider, AccountEntitlementView entitlement, DailyQuotaView quota) {}

    public record AccountEntitlementView(
        String planCode,
        String planName,
        String entitlementCode,
        Integer dailyLocalOcrLimit,
        Integer dailyLocalTtsLimit,
        Integer childLimit,
        Integer localCardLimit,
        Integer childCount,
        Integer remainingChildSlots,
        Boolean advancedVoiceEnabled,
        Boolean premiumActive,
        String validUntil,
        Boolean authoritative,
        Boolean multiChildEnabled,
        String dailyPlanScope,
        String weeklyReportScope,
        Integer weeklyReportHistoryWeeks,
        Boolean historyEnabled,
        Boolean customReminderEnabled,
        Boolean serverVerified,
        String verificationSource,
        Map<String, Object> accessProof
    ) {}

    public record DailyQuotaView(
        String quotaDate,
        Integer localOcrLimit,
        Integer localOcrUsed,
        Integer localOcrRemaining,
        Integer localTtsLimit,
        Integer localTtsUsed,
        Integer localTtsRemaining,
        Integer dailyLoginGiftLimit,
        Integer dailyLoginGiftUsed,
        Integer dailyLoginGiftRemaining
    ) {
        public DailyQuotaView(
            String quotaDate,
            Integer localOcrLimit,
            Integer localOcrUsed,
            Integer localOcrRemaining,
            Integer localTtsLimit,
            Integer localTtsUsed,
            Integer localTtsRemaining
        ) {
            // 中文说明：兼容旧测试和旧接口构造路径；没有统一日赠字段时，用旧 OCR/TTS 中较大的额度做降级展示。
            this(
                quotaDate,
                localOcrLimit,
                localOcrUsed,
                localOcrRemaining,
                localTtsLimit,
                localTtsUsed,
                localTtsRemaining,
                Math.max(localOcrLimit == null ? 0 : localOcrLimit, localTtsLimit == null ? 0 : localTtsLimit),
                Math.max(localOcrUsed == null ? 0 : localOcrUsed, localTtsUsed == null ? 0 : localTtsUsed),
                Math.max(localOcrRemaining == null ? 0 : localOcrRemaining, localTtsRemaining == null ? 0 : localTtsRemaining)
            );
        }
    }
    public record QuotaUsageRequest(
        @Schema(description = "权益类型：local_tts/tts/voice_reading/text_to_speech/speech_synthesis 或 ocr/local_ocr/text_recognition。", example = "local_tts") String kind,
        @Schema(description = "触发来源。", example = "device_tts") String source,
        @Schema(description = "语言编码。", example = "en-US") String languageCode,
        @Schema(description = "消耗次数。", example = "1") Integer amount,
        @Schema(description = "幂等键，防止重试重复扣减。", example = "localTts-20260502-001") String idempotencyKey,
        @Schema(description = "客户端发生时间。", example = "2026-05-02T10:15:30Z") String occurredAt
    ) {}
    public record ChildView(String id, String nickname, String ageBand, String learningTrackCode, String avatarEmoji) {}
    public record CreateChildReceipt(ChildView child, AccountStateView accountState) {}
    @Schema(description = "孩子档案创建或更新请求体。")
    public record ChildMutationRequest(
        @Schema(description = "孩子昵称。", example = "小明") String nickname,
        @Schema(description = "年龄段编码。", example = "6_8") String ageBand,
        @Schema(description = "学习方向编码。", example = "zh_to_en") String learningTrackCode,
        @Schema(description = "家长是否已确认保存。", example = "true") Boolean parentConfirmed
    ) {}

    public record HomeSummaryView(
        HomeChildView currentChild,
        Integer todayCompletedCount,
        Integer reviewDueCount,
        List<RecentReviewCardView> recentCards,
        DailyQuotaView quota,
        AccountEntitlementView entitlement,
        LearningGrowthView growth,
        List<ChildProgressView> childSummaries
    ) {}

    public record HomeChildView(String childId, String nickname, String ageBand, String avatarEmoji) {}
    public record RecentReviewCardView(String cardId, String text, String supportHint, Integer proficiency, String nextReviewAt, String sourceLanguageCode, String targetLanguageCode, String createdAt) {}
    public record LearningGrowthView(Integer currentStreakDays, Integer weeklyActiveDays, Integer weeklyReviewCount, String encouragement) {}
    public record ChildProgressView(String childId, String nickname, String ageBand, String avatarEmoji, Integer reviewDueCount, Integer savedCardCount, Integer todayCompletedCount) {}

    public record DailyLearningTaskFeedView(String taskDate, String scope, Boolean isPremiumPlan, String currentChildId, List<DailyLearningTaskView> tasks) {}
    public record DailyLearningTaskView(String taskId, String childId, String taskType, String title, String reason, Integer estimatedMinutes, String status, String ctaType, String completionMessage) {}
    @Schema(description = "每日任务完成回写请求体。")
    public record DailyTaskCompleteRequest(
        @Schema(description = "完成类型，例如 local_ocr_saved。", example = "local_ocr_saved") String completionType,
        @Schema(description = "孩子档案 ID。", example = "child-a") String childId
    ) {}
    public record DailyLearningTaskCompletionView(String taskId, String status, String completedAt, Integer streakDays, Integer weeklyActiveDays, Integer weeklyReviewCount, Integer todayCompletedCount, String message) {}

    public record ReviewCardView(String id, String text, String supportHint, Integer proficiency, String nextReviewAt, String sourceLanguageCode, String targetLanguageCode) {}
    @Schema(description = "创建句卡请求体。")
    public record CreateReviewCardRequest(
        @Schema(description = "孩子档案 ID。", example = "child-a") String childId,
        @Schema(description = "学习方向编码。", example = "zh_to_en") String learningTrackCode,
        @Schema(description = "句卡正文编码值。", example = "encrypted-card-text") String encryptedText,
        @Schema(description = "可选的译文或辅助提示。", example = "The quick brown fox jumps over the lazy dog.") String supportHint,
        @Schema(description = "原文语种编码。未传时由学习方向推导。", example = "en") String sourceLanguageCode,
        @Schema(description = "翻译语种编码。未传时由学习方向推导。", example = "ja") String targetLanguageCode
    ) {}
    public record CreateReviewCardReceipt(String cardId, String childId, String savedAt, AccountStateView accountState) {}
    @Schema(description = "复习事件记录请求体。")
    public record ReviewEventRequest(
        @Schema(description = "句卡 ID。", example = "card-001") String cardId,
        @Schema(description = "事件类型，例如 completed。", example = "completed") String eventType,
        @Schema(description = "复习结果等级，例如 remembered、easy、hard。", example = "remembered") String resultLevel
    ) {}
    public record ReviewEventReceipt(String cardId, String eventType, Integer proficiency, String nextReviewAt, String recordedAt) {}

    public record WeeklyParentReportView(
        String id,
        String scope,
        String weekStart,
        String weekEnd,
        Boolean isPremiumPlan,
        String childId,
        String childName,
        WeeklyStatsView stats,
        String summary,
        String highlight,
        String nextWeekSuggestion,
        List<String> suggestions,
        String disclaimer,
        List<WeeklyChildSummaryView> childSummaries,
        String generatedAt,
        String planCode,
        String tier,
        Boolean pageShareEnabled,
        Boolean exportReportEnabled,
        Boolean offlineHistoryPreviewEnabled,
        List<WeeklyReportModuleView> modules
    ) {}

    public record WeeklyStatsView(
        Integer weeklyActiveDays,
        Integer weeklyReviewCount,
        Integer weeklySavedCardCount,
        Integer savedCardCount,
        Integer currentStreakDays,
        Integer reviewDueCount,
        Integer todayCompletedCount,
        Integer completedDailyTaskCount,
        Integer newSavedCardCount,
        Integer childCount
    ) {
        public boolean hasActivity() {
            return (weeklyActiveDays != null && weeklyActiveDays > 0)
                || (weeklyReviewCount != null && weeklyReviewCount > 0)
                || (savedCardCount != null && savedCardCount > 0)
                || (newSavedCardCount != null && newSavedCardCount > 0);
        }
    }

    public record WeeklyChildSummaryView(String childId, String childName, WeeklyStatsView stats, String highlight, String nextWeekSuggestion) {}
    public record WeeklyReportModuleView(String code, String title, String access, Map<String, Object> payload) {}
    public record WeeklyReportHistoryView(String scope, String childId, Boolean isPremiumPlan, Integer historyWeeks, Integer availableHistoryWeeks, List<WeeklyParentReportView> reports, Boolean offlineHistoryPreviewEnabled) {}

    @Schema(description = "反馈提交请求体。")
    public record FeedbackSubmitRequest(
        @Schema(description = "反馈分类。", example = "bug") String category,
        @Schema(description = "反馈正文。", example = "拍照识别结果不准确") String content,
        @Schema(description = "联系邮箱。", example = "user@example.com") String contactEmail,
        @Schema(description = "客户端版本号。", example = "1.0.0") String appVersion,
        @Schema(description = "客户端平台。", example = "ios") String clientPlatform,
        @Schema(description = "账号模式。", example = "apple") String authMode,
        @Schema(description = "可选的诊断编号。", example = "trace-20260428-001") String traceId
    ) {}
    public record FeedbackSubmissionReceipt(String ticketNo, String category, String submittedAt, String supportEmail) {}

    @Schema(description = "云端 OCR 请求体。")
    public record OcrExtractRequest(
        @Schema(description = "图片 base64 内容。", example = "/9j/4AAQSkZJRgABAQ...") String imageBase64,
        @Schema(description = "图片 MIME 类型，例如 image/jpeg。", example = "image/jpeg") String mimeType,
        @Schema(description = "覆盖默认 OCR prompt 的自定义提示词。", example = "请识别图片中的英文句子") String promptOverride
    ) {}
    public record OcrExtractReceipt(
        String traceId,
        String provider,
        String model,
        String text,
        String prompt,
        Integer minPixels,
        Integer maxPixels,
        Boolean allowed,
        String serviceStatus,
        Integer remainingTrialCount,
        String upgradeTitle,
        String upgradeMessage,
        List<String> unlockOptions
    ) {}

    @Schema(description = "云端文本转语音请求体。")
    public record CloudSpeechRequest(
        @Schema(description = "待朗读文本。", example = "Hello, world!") String text,
        @Schema(description = "语言编码，例如 en-US。", example = "en-US") String languageCode,
        @Schema(description = "语速倍率。", example = "1.0") Float rate
    ) {}
    public record CloudSpeechReceipt(
        Boolean allowed,
        String serviceStatus,
        Integer remainingTrialCount,
        String provider,
        String model,
        String audioBase64,
        String mimeType,
        String text,
        String languageCode,
        Float rate,
        String upgradeTitle,
        String upgradeMessage,
        List<String> unlockOptions
    ) {}

    @Schema(description = "删除账号请求体。")
    public record DeletionRequest(
        @Schema(description = "账号 provider，例如 apple。", example = "apple") String provider,
        @Schema(description = "是否确认删除数据。", example = "true") Boolean confirmDataDeletion,
        @Schema(description = "幂等键。", example = "delete-20260428-001") String idempotencyKey,
        @Schema(description = "删除验证码。", example = "123456") String verificationCode,
        @Schema(description = "验证码对应邮箱，可为空，默认取当前账号邮箱。", example = "user@example.com") String email
    ) {}
    public record DeletionRequestResponse(
        String requestId,
        String status,
        String executionStatus,
        String requestedAt,
        String startedAt,
        String completedAt,
        String failedAt,
        String provider,
        Boolean executeSynchronously,
        Boolean idempotentReplay,
        String appleRevokeStatus,
        String appleRevokeNote,
        Integer sessionsRevoked,
        Integer identityLinksRevoked,
        Integer childrenScrubbed,
        Integer reviewCardsDeleted,
        Integer reviewEventsDeleted,
        String lastErrorCode,
        String lastErrorMessage,
        String note
    ) {}

    public record SubscriptionStatusView(
        String currentPlanCode,
        String currentPlanName,
        Boolean authoritative,
        Boolean hasPendingVerification,
        VerificationReadinessView verificationReadiness,
        List<ProjectionView> projections,
        List<IntakeItemView> recentIntakes
    ) {}

    public record VerificationReadinessView(Boolean bundleContextConfigured, Boolean serverApiCredentialsConfigured, Boolean cryptographicVerificationLive, String bundleId, String environment, String note) {}
    private record PlanCatalogEntry(String planCode, String entitlementCode, Object rawConfig, boolean premium, int childLimit, int localCardLimit) {}
    public record ProjectionView(String originalTransactionId, String status, String verificationStatus, String productId, String planCode, String validUntil, String authoritativeSource, String lastVerifiedAt) {}
    public record IntakeItemView(String intakeId, String sourceType, String status, String verificationStatus, String productId, String planCode, String receivedAt, String failureReason) {}
    public record EntitlementRefreshView(String refreshedAt, String effectivePlanCode, String effectivePlanName, Integer activeProjectionCount, String source) {}
    public record IntakeReceipt(String intakeId, String sourceType, String status, String verificationStatus, String productId, String planCode, String authoritativePlanCode, Boolean requiresServerVerification, String note, AccountStateView accountState) {}
}
