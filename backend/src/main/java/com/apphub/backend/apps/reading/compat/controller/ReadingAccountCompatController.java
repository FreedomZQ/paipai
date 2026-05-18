package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCloudUsageService;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;


/**
 * 拍拍伴读账号兼容控制器。
 * 所有账号状态、首页汇总和删除账号动作都走后端鉴权，满足 App Store 删除账号和权益权威性要求。
 */
@Tag(name = "拍拍伴读账号", description = "拍拍伴读账号状态、首页汇总和删除账号接口。")
@RestController
@RequestMapping("/api/v1/account")
public class ReadingAccountCompatController {
    private final ReadingAuthenticatedUserResolver userResolver;
    private final ReadingCompatService readingCompatService;
    private final ReadingCloudUsageService cloudUsageService;

    public ReadingAccountCompatController(ReadingAuthenticatedUserResolver userResolver, ReadingCompatService readingCompatService, ReadingCloudUsageService cloudUsageService) {
        this.userResolver = userResolver;
        this.readingCompatService = readingCompatService;
        this.cloudUsageService = cloudUsageService;
    }

    @Operation(summary = "查询账号状态", description = "查询当前 App 账号权益、孩子数量和每日额度状态。")
    @GetMapping("/me/state")
    public ApiResponse<ReadingCompatService.AccountStateView> state(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.accountState(user));
    }

    @Operation(summary = "查询当前资源次数", description = "查询当前用户文字识别和语音朗读次数，返回数据库权威记录；本地赠送次数会合并到返回结果中。")
    @GetMapping("/me/cloud-usage")
    public ApiResponse<ReadingCloudUsageService.CloudUsageSnapshot> cloudUsage(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), cloudUsageService.snapshot(user.userId()));
    }

    @Operation(summary = "查询首页汇总", description = "查询当前 App 首页所需的孩子、句卡、成长和额度汇总。")
    @GetMapping("/me/home-summary")
    public ApiResponse<ReadingCompatService.HomeSummaryView> homeSummary(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.homeSummary(user));
    }

    @Operation(summary = "记录权益次数消耗", description = "记录文字识别或朗读等权益次数消耗，并返回更新后的账号额度状态。")
    @PostMapping("/quota/usage")
    public ApiResponse<ReadingCompatService.AccountStateView> recordQuotaUsage(
        @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "权益次数消耗请求体。", required = true) @RequestBody ReadingCompatService.QuotaUsageRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.recordQuotaUsage(user, body));
    }

    @Operation(summary = "删除当前账号（兼容旧链路）", description = "旧链路也必须携带删除验证码，避免绕过邮箱二次确认。")
    @PostMapping("/deletion-requests")
    public ApiResponse<ReadingCompatService.DeletionRequestResponse> delete(
        @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody ReadingCompatService.DeletionRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.deleteAccount(user, body));
    }

    @Operation(summary = "提交家长同意", description = "记录家长授权状态的兼容入口；当前实现以返回快照形式响应，避免 Local Guest 产生后端账号依赖。")
    @PostMapping("/privacy/consent/parent")
    public ApiResponse<PrivacyActionReceipt> parentConsent(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        var state = readingCompatService.accountState(user);
        return ApiResponse.success(currentRequestId(), new PrivacyActionReceipt("parent_consent", "recorded", state.entitlement().planCode(), "家长同意状态已绑定到当前账号上下文。"));
    }

    @Operation(summary = "导出隐私摘要", description = "返回家长可见的权益、删除和购买最小保留说明。")
    @GetMapping("/privacy/export")
    public ApiResponse<PrivacyExportView> privacyExport(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        var state = readingCompatService.accountState(user);
        var subscription = readingCompatService.subscriptionStatus(user);
        return ApiResponse.success(currentRequestId(), new PrivacyExportView(
            "export-ready",
            state.entitlement().planCode(),
            state.entitlement().planName(),
            subscription.hasPendingVerification(),
            "家长可导出同意、权益和购买摘要；不包含 capability token、儿童正文或设备指纹。"
        ));
    }

    @Operation(summary = "查询删除状态", description = "返回当前账号删除流程的兼容状态。")
    @GetMapping("/privacy/deletion-status")
    public ApiResponse<PrivacyActionReceipt> deletionStatus(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        boolean deleted = "deleted".equalsIgnoreCase(user.user().getStatus());
        return ApiResponse.success(currentRequestId(), new PrivacyActionReceipt(
            "deletion_status",
            deleted ? "completed" : "active",
            deleted ? "completed" : "processing",
            deleted ? "账号已删除。" : "账号仍在使用中。"
        ));
    }

    @Operation(summary = "查询购买最小保留", description = "返回删除账号后购买凭证的最小保留说明。")
    @GetMapping("/privacy/purchase-retention")
    public ApiResponse<PrivacyActionReceipt> purchaseRetention(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), new PrivacyActionReceipt(
            "purchase_retention",
            "minimal",
            readingCompatService.accountState(user).entitlement().planCode(),
            "交易哈希与最小账务字段仅用于退款、税务、反欺诈或争议处理。"
        ));
    }

    @Operation(summary = "撤回云处理同意", description = "兼容入口，当前实现返回撤回结果说明。")
    @PostMapping("/privacy/consent/withdraw")
    public ApiResponse<PrivacyActionReceipt> withdrawConsent(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), new PrivacyActionReceipt("consent_withdraw", "recorded", user.user().getAppCode(), "云能力同意已撤回，后续云能力应重新走家长授权。"));
    }

    @Operation(summary = "限制处理", description = "兼容入口，返回处理限制状态说明。")
    @PostMapping("/privacy/restrict-processing")
    public ApiResponse<PrivacyActionReceipt> restrictProcessing(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), new PrivacyActionReceipt("restrict_processing", "recorded", user.user().getAppCode(), "非必要云处理已限制。"));
    }

    public record PrivacyActionReceipt(String action, String status, String scope, String note) {}
    public record PrivacyExportView(String status, String planCode, String planName, Boolean hasPendingVerification, String note) {}

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }

}
