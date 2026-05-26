package com.apphub.backend.apps.reading.billing.controller;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.auth.model.AuthenticatedSessionView;
import com.apphub.backend.sys.auth.service.SessionTokenResolver;
import com.apphub.backend.sys.auth.service.SysAuthSessionService;
import com.apphub.backend.sys.billing.model.EntitlementOverviewView;
import com.apphub.backend.sys.billing.model.EntitlementRefreshResultView;
import com.apphub.backend.sys.billing.model.PurchaseIntakeAcceptedView;
import com.apphub.backend.sys.billing.model.PurchaseRestoreItemRequest;
import com.apphub.backend.sys.billing.model.PurchaseRestoreRequest;
import com.apphub.backend.sys.billing.model.PurchaseRestoreAcceptedView;
import com.apphub.backend.sys.billing.model.PurchasePermissionDecision;
import com.apphub.backend.sys.billing.model.PurchaseVerifyRequest;
import com.apphub.backend.sys.billing.privacy.model.EntitlementEventReportRequest;
import com.apphub.backend.sys.billing.privacy.model.EntitlementEventReportView;
import com.apphub.backend.sys.billing.privacy.model.PrivacyConsentRequest;
import com.apphub.backend.sys.billing.privacy.model.PrivacyConsentView;
import com.apphub.backend.sys.billing.privacy.model.RefundTicketView;
import com.apphub.backend.sys.billing.privacy.service.SysAppStoreRefundService;
import com.apphub.backend.sys.billing.privacy.service.SysEntitlementConsumptionReportService;
import com.apphub.backend.sys.billing.privacy.service.SysPrivacyConsentService;
import com.apphub.backend.sys.billing.service.SysBillingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;


import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 拍拍伴读的计费兼容控制器。
 * 用于保留旧版对外路由，并将请求适配到统一后端内核。
 */

@Tag(name = "拍拍伴读订阅计费", description = "拍拍伴读订阅状态、权益刷新、购买和恢复购买接口。")
@RestController
@RequestMapping("/api/v1")
@Validated
public class ReadingBillingCompatController {

    private static final String APP_CODE = ReadingAppModule.APP_CODE;

    private final AppDefinitionService appDefinitionService;
    private final SessionTokenResolver sessionTokenResolver;
    private final SysAuthSessionService sysAuthSessionService;
    private final SysBillingService sysBillingService;
    private final SysPrivacyConsentService privacyConsentService;
    private final SysEntitlementConsumptionReportService consumptionReportService;
    private final SysAppStoreRefundService appStoreRefundService;
    private final ReadingAuthenticatedUserResolver userResolver;
    private final ReadingCompatService readingCompatService;

    public ReadingBillingCompatController(
        AppDefinitionService appDefinitionService,
        SessionTokenResolver sessionTokenResolver,
        SysAuthSessionService sysAuthSessionService,
        SysBillingService sysBillingService,
        SysPrivacyConsentService privacyConsentService,
        SysEntitlementConsumptionReportService consumptionReportService,
        SysAppStoreRefundService appStoreRefundService,
        ReadingAuthenticatedUserResolver userResolver,
        ReadingCompatService readingCompatService
    ) {
        this.appDefinitionService = appDefinitionService;
        this.sessionTokenResolver = sessionTokenResolver;
        this.sysAuthSessionService = sysAuthSessionService;
        this.sysBillingService = sysBillingService;
        this.privacyConsentService = privacyConsentService;
        this.consumptionReportService = consumptionReportService;
        this.appStoreRefundService = appStoreRefundService;
        this.userResolver = userResolver;
        this.readingCompatService = readingCompatService;
    }

    @Operation(summary = "查询当前权益", description = "查询当前 App 用户的权益摘要。")
    @GetMapping("/billing/entitlement")
    public ApiResponse<ReadingCompatService.AccountEntitlementView> entitlement(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.accountState(user).entitlement());
    }

    @Operation(summary = "查询购买服务状态", description = "购买页进入和提交前都必须检查；不可用时前端展示“暂时无法购买”。")
    @GetMapping("/billing/health")
    public ApiResponse<ReadingCompatService.BillingHealthView> billingHealth(
        @Parameter(description = "界面语言，用于返回本地化禁购提示。", example = "zh-Hans") @RequestParam(required = false) String locale
    ) {
        return ApiResponse.success(currentRequestId(), readingCompatService.billingHealth(locale));
    }

    @Operation(summary = "实时验证购买权限", description = "购买页展示和用户点击付款前共用；返回 allowed=false 时前端必须置灰付款按钮并展示后端文案。")
    @GetMapping("/billing/purchase-permission")
    public ApiResponse<PurchasePermissionDecision> purchasePermission(
        @Parameter(description = "后端配置的商品编码；为空时只验证 App 全局购买状态。", example = "ocr_20") @RequestParam(required = false) String productCode,
        @Parameter(description = "界面语言，用于返回本地化禁购提示。", example = "zh-Hans") @RequestParam(required = false) String locale
    ) {
        return ApiResponse.success(currentRequestId(), readingCompatService.purchasePermission(productCode, locale));
    }

    @Operation(
        summary = "查询可购买资源包",
        description = "历史兼容接口。无自有后端首发模式下返回空列表；iOS 正式包使用 App 内置本机积分白名单和 StoreKit 价格，不从后端拉取可售商品、有效期或云端 API 积分。"
    )
    @GetMapping({"/billing/resource-packs", "/billing/credit-products"})
    public ApiResponse<List<ReadingCompatService.CreditProductView>> creditProducts(
        @Parameter(description = "界面语言，用于返回本地化商品名称和描述。", example = "zh-Hans") @RequestParam(required = false) String locale
    ) {
        return ApiResponse.success(currentRequestId(), readingCompatService.creditProducts(locale));
    }

    @Operation(summary = "提交内部购买", description = "内部票据校验通过后立即发放对应购买权益；同一类权益每日最多购买 5 次。")
    @PostMapping("/billing/internal-purchases")
    public ApiResponse<ReadingCompatService.InternalPurchaseReceipt> internalPurchase(
        @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "内部购买请求体。", required = true) @RequestBody ReadingCompatService.InternalPurchaseRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ensureServerEntitlementPathEnabled();
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.internalPurchase(user, body));
    }

    @Operation(summary = "查询本机积分获取和消耗记录", description = "家长中心积分权益页使用，仅返回 local_ocr/local_tts 的今日免费积分记录；历史次数权益和云端次数包不再出现在响应中。")
    @GetMapping("/billing/entitlement-records")
    public ApiResponse<ReadingCompatService.EntitlementRecordPageView> entitlementRecords(
        @Parameter(description = "积分类别。仅支持 local_ocr/local_tts；cloud_ocr/cloud_tts 等历史次数权益会被拒绝。") @RequestParam(required = false) String serviceType,
        @Parameter(description = "客户端 IANA 时区，用于每日权益按用户本地日切分。", example = "Asia/Shanghai") @RequestParam(required = false) String timezone,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.entitlementRecords(user, serviceType, timezone, page, pageSize));
    }

    @Operation(summary = "刷新当前权益", description = "刷新当前 App 用户的权益投影。")
    @PostMapping({"/billing/entitlement/refresh", "/subscriptions/entitlements/refresh"})
    public ApiResponse<ReadingCompatService.EntitlementRefreshView> refreshEntitlement(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.refreshEntitlement(user));
    }

    @Operation(summary = "查询订阅状态", description = "返回当前 App 用户订阅状态、验证准备度和最近交易记录。")
    @GetMapping("/subscriptions/status")
    public ApiResponse<ReadingCompatService.SubscriptionStatusView> subscriptionStatus(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.subscriptionStatus(user));
    }

    @Operation(summary = "提交购买线索", description = "接收客户端 App Store 购买交易信息并提交后端验证。")
    @PostMapping("/subscriptions/app-store/purchases/intake")
    public ApiResponse<ReadingCompatService.IntakeReceipt> purchaseIntake(
        @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody ReadingTransactionIntakeRequest request,
        @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        ensureServerEntitlementPathEnabled();
        ReadingAuthenticatedUser user = userResolver.require(httpServletRequest);
        PurchaseIntakeAcceptedView accepted = sysBillingService.verify(APP_CODE, user.userId(), request.toVerifyRequest());
        grantReadingResourcePackIfVerified(user.userId(), accepted);
        return ApiResponse.success(currentRequestId(), readingCompatService.intakeReceipt(
            user,
            accepted.intakeId(),
            accepted.sourceType(),
            accepted.processingStatus(),
            accepted.verificationStatus(),
            accepted.productId()
        ));
    }

    @Operation(summary = "提交恢复购买线索", description = "接收客户端恢复购买交易信息并提交后端验证。")
    @PostMapping("/subscriptions/app-store/restores/intake")
    public ApiResponse<ReadingCompatService.IntakeReceipt> restoreIntake(
        @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody ReadingTransactionIntakeRequest request,
        @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        ensureServerEntitlementPathEnabled();
        ReadingAuthenticatedUser user = userResolver.require(httpServletRequest);
        PurchaseRestoreRequest restoreRequest = new PurchaseRestoreRequest(List.of(request.toRestoreItem()));
        PurchaseRestoreAcceptedView accepted = sysBillingService.restore(APP_CODE, user.userId(), restoreRequest);
        PurchaseIntakeAcceptedView first = accepted.transactions().isEmpty() ? null : accepted.transactions().get(0);
        grantReadingResourcePackIfVerified(user.userId(), first);
        return ApiResponse.success(currentRequestId(), readingCompatService.intakeReceipt(
            user,
            first == null ? null : first.intakeId(),
            first == null ? "restore" : first.sourceType(),
            first == null ? "accepted" : first.processingStatus(),
            first == null ? "pending" : first.verificationStatus(),
            first == null ? request.productId() : first.productId()
        ));
    }

    @Operation(summary = "验证购买交易", description = "接收交易签名信息并提交统一计费服务验证。")
    @PostMapping("/subscriptions/transactions/verify")
    public ApiResponse<ReadingCompatService.IntakeReceipt> verify(
        @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody ReadingLegacyVerifyRequest request,
        @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        ensureServerEntitlementPathEnabled();
        ReadingAuthenticatedUser user = userResolver.require(httpServletRequest);
        PurchaseIntakeAcceptedView accepted = sysBillingService.verify(APP_CODE, user.userId(), request.toVerifyRequest());
        grantReadingResourcePackIfVerified(user.userId(), accepted);
        return ApiResponse.success(currentRequestId(), readingCompatService.intakeReceipt(
            user,
            accepted.intakeId(),
            accepted.sourceType(),
            accepted.processingStatus(),
            accepted.verificationStatus(),
            accepted.productId()
        ));
    }

    @Operation(summary = "更新退款数据共享同意", description = "付款客户/监护人可独立同意或撤回向 Apple 分享最小退款消费信息；该同意不包含儿童学习内容。")
    @PostMapping("/billing/privacy/consents")
    public ApiResponse<PrivacyConsentView> updatePrivacyConsent(
        @Valid @RequestBody PrivacyConsentRequest request,
        @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        ensureServerEntitlementPathEnabled();
        ReadingAuthenticatedUser user = userResolver.require(httpServletRequest);
        return ApiResponse.success(currentRequestId(), privacyConsentService.updateConsent(APP_CODE, user.userId(), request));
    }

    @Operation(summary = "查询退款数据共享同意", description = "查询当前家长账号某类隐私同意的最新状态。")
    @GetMapping("/billing/privacy/consents")
    public ApiResponse<PrivacyConsentView> latestPrivacyConsent(
        @RequestParam(defaultValue = SysPrivacyConsentService.CONSENT_REFUND_CONSUMPTION_SHARING) String consentType,
        @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        ensureServerEntitlementPathEnabled();
        ReadingAuthenticatedUser user = userResolver.require(httpServletRequest);
        return ApiResponse.success(currentRequestId(), privacyConsentService.latestView(APP_CODE, user.userId(), consentType));
    }

    @Operation(summary = "报送权益消费事件", description = "客户端 outbox 报送交易维度的聚合消耗次数；禁止上传儿童姓名、图片、音频、OCR 原文或学习正文。")
    @PostMapping("/billing/entitlement-events/report")
    public ApiResponse<EntitlementEventReportView> reportEntitlementEvents(
        @Valid @RequestBody EntitlementEventReportRequest request,
        @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        ensureServerEntitlementPathEnabled();
        ReadingAuthenticatedUser user = userResolver.require(httpServletRequest);
        return ApiResponse.success(currentRequestId(), consumptionReportService.report(APP_CODE, user.userId(), request));
    }

    @Operation(summary = "查询退款票据", description = "返回最小化交易票据和聚合使用统计，供家长核对退款/权益状态。")
    @GetMapping("/billing/refunds/tickets")
    public ApiResponse<RefundTicketView> refundTickets(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        ensureServerEntitlementPathEnabled();
        ReadingAuthenticatedUser user = userResolver.require(httpServletRequest);
        return ApiResponse.success(currentRequestId(), appStoreRefundService.lookupTicketsByUser(APP_CODE, user.userId()));
    }

    private void ensureServerEntitlementPathEnabled() {
        if (!readingCompatService.isLocalOnlyLaunchMode()) {
            return;
        }
        // 当前首发模式只允许 iOS 端 StoreKit + Keychain 本机钱包；后端兼容链路必须失败关闭。
        throw new ResponseStatusException(
            HttpStatus.GONE,
            "Local-only launch mode: server purchase restore, wallet, refund consumption, and compensation paths are disabled."
        );
    }

    private void grantReadingResourcePackIfVerified(Long userId, PurchaseIntakeAcceptedView accepted) {
        if (accepted == null || !"verified".equalsIgnoreCase(accepted.verificationStatus())) {
            return;
        }
        readingCompatService.grantAppStoreProductPurchase(userId, accepted.productId(), accepted.transactionId());
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }

    @Schema(description = "拍拍伴读 App Store 交易提交请求体。")
    public record ReadingTransactionIntakeRequest(
        @Schema(description = "商品 ID。", example = "paipai.family.monthly") @NotBlank String productId,
        @Schema(description = "交易 ID。", example = "2000000123456789") String transactionId,
        @Schema(description = "原始交易 ID。", example = "2000000123000000") @NotBlank String originalTransactionId,
        @Schema(description = "商店环境。", example = "Production") String environment,
        @Schema(description = "店面区域。", example = "CHN") String storefront,
        @Schema(description = "App Account Token。", example = "550e8400-e29b-41d4-a716-446655440000") String appAccountToken,
        @Schema(description = "signedTransactionInfo 原文。", example = "eyJhbGciOiJFUzI1NiIsIng1YyI6W10ifQ...") @NotBlank String signedTransactionInfo,
        @Schema(description = "signedRenewalInfo 原文。", example = "eyJhbGciOiJFUzI1NiIsIng1YyI6W10ifQ...") String signedRenewalInfo,
        @Schema(description = "客户端生成的幂等键。", example = "purchase-2000000123456789") String idempotencyKey,
        @Schema(description = "付款客户/监护人是否同意为 Apple 退款评估共享最小消费信息。") Boolean refundDataSharingConsent,
        @Schema(description = "同意文案版本。", example = "refund-consumption-v1") String consentPolicyVersion,
        @Schema(description = "客户端同意地区/法域。", example = "CN") String consentRegion
    ) {
        PurchaseVerifyRequest toVerifyRequest() {
            return new PurchaseVerifyRequest(
                productId,
                transactionId,
                originalTransactionId,
                environment,
                storefront,
                appAccountToken,
                signedTransactionInfo,
                signedRenewalInfo,
                idempotencyKey,
                refundDataSharingConsent,
                consentPolicyVersion,
                consentRegion
            );
        }

        PurchaseRestoreItemRequest toRestoreItem() {
            return new PurchaseRestoreItemRequest(
                productId,
                transactionId,
                originalTransactionId,
                environment,
                storefront,
                appAccountToken,
                signedTransactionInfo,
                signedRenewalInfo,
                idempotencyKey,
                refundDataSharingConsent,
                consentPolicyVersion,
                consentRegion
            );
        }
    }

    @Schema(description = "兼容旧版 verify 路由的交易验证请求体。")
    public record ReadingLegacyVerifyRequest(
        @Schema(description = "signedTransactionInfo 原文。", example = "eyJhbGciOiJFUzI1NiIsIng1YyI6W10ifQ...") @NotBlank String signedTransactionInfo,
        @Schema(description = "原始交易 ID。", example = "2000000123000000") @NotBlank String originalTransactionId,
        @Schema(description = "商品 ID。", example = "paipai.family.monthly") String productId,
        @Schema(description = "交易 ID。", example = "2000000123456789") String transactionId,
        @Schema(description = "商店环境。", example = "Production") String environment,
        @Schema(description = "店面区域。", example = "CHN") String storefront,
        @Schema(description = "App Account Token。", example = "550e8400-e29b-41d4-a716-446655440000") String appAccountToken,
        @Schema(description = "signedRenewalInfo 原文。", example = "eyJhbGciOiJFUzI1NiIsIng1YyI6W10ifQ...") String signedRenewalInfo,
        @Schema(description = "客户端生成的幂等键。", example = "purchase-2000000123456789") String idempotencyKey,
        @Schema(description = "付款客户/监护人是否同意为 Apple 退款评估共享最小消费信息。") Boolean refundDataSharingConsent,
        @Schema(description = "同意文案版本。", example = "refund-consumption-v1") String consentPolicyVersion,
        @Schema(description = "客户端同意地区/法域。", example = "CN") String consentRegion
    ) {
        PurchaseVerifyRequest toVerifyRequest() {
            return new PurchaseVerifyRequest(
                productId == null || productId.isBlank() ? "unknown" : productId,
                transactionId,
                originalTransactionId,
                environment,
                storefront,
                appAccountToken,
                signedTransactionInfo,
                signedRenewalInfo,
                idempotencyKey,
                refundDataSharingConsent,
                consentPolicyVersion,
                consentRegion
            );
        }
    }
}
