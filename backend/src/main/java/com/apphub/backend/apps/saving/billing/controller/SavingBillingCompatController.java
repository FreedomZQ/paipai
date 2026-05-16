package com.apphub.backend.apps.saving.billing.controller;

import com.apphub.backend.apps.common.entitlement.AppEntitlementAccessGuard;
import com.apphub.backend.apps.saving.SavingAppModule;
import com.apphub.backend.apps.saving.api.SavingApiEnvelope;
import com.apphub.backend.apps.saving.service.SavingConfigService;
import com.apphub.backend.apps.saving.service.SavingRequestSupport;
import com.apphub.backend.sys.billing.model.EntitlementItemView;
import com.apphub.backend.sys.billing.model.EntitlementOverviewView;
import com.apphub.backend.sys.billing.model.PurchaseIntakeAcceptedView;
import com.apphub.backend.sys.billing.model.PurchaseRestoreAcceptedView;
import com.apphub.backend.sys.billing.model.PurchaseRestoreItemRequest;
import com.apphub.backend.sys.billing.model.PurchaseRestoreRequest;
import com.apphub.backend.sys.billing.model.PurchaseVerifyRequest;
import com.apphub.backend.sys.billing.service.SysBillingService;
import com.apphub.backend.sys.entitlement.model.UserEntitlementDecisionView;
import com.apphub.backend.sys.entitlement.service.SysEntitlementCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * saving（省钱项目）计费兼容控制器。
 * 响应体保持 SaveMoney iOS 第一版的 code/message/requestId/data 结构，内部复用统一计费内核。
 */
@RestController
@RequestMapping("/v1")
@Validated
@Tag(name = "省钱星球计费", description = "saving 订阅权益、购买校验和恢复购买接口；内部复用统一计费内核。")
public class SavingBillingCompatController {

    private static final String APP_CODE = SavingAppModule.APP_CODE;

    private final SavingRequestSupport requestSupport;
    private final SavingConfigService configService;
    private final SysBillingService sysBillingService;
    private final SysEntitlementCenterService sysEntitlementCenterService;

    public SavingBillingCompatController(SavingRequestSupport requestSupport, SavingConfigService configService, SysBillingService sysBillingService, SysEntitlementCenterService sysEntitlementCenterService) {
        this.requestSupport = requestSupport;
        this.configService = configService;
        this.sysBillingService = sysBillingService;
        this.sysEntitlementCenterService = sysEntitlementCenterService;
    }

    @Operation(summary = "查询当前权益", description = "查询当前 saving 用户的后端验证权益、套餐状态和本地报告高级模块访问证明。")
    @GetMapping("/entitlements")
    public SavingApiEnvelope<Map<String, Object>> entitlements(@Parameter(hidden = true) HttpServletRequest request) {
        Long userId = requestSupport.requireUserId(request);
        return SavingApiEnvelope.ok(requestSupport.requestId(), entitlementResponse(userId, sysBillingService.getEntitlements(APP_CODE, userId)));
    }

    @Operation(summary = "刷新当前权益", description = "主动从统一计费内核刷新当前用户权益，再返回最新 entitlement 状态。")
    @PostMapping("/entitlements/refresh")
    public SavingApiEnvelope<Map<String, Object>> refreshEntitlements(@Parameter(hidden = true) HttpServletRequest request) {
        Long userId = requestSupport.requireUserId(request);
        sysBillingService.refreshEntitlements(APP_CODE, userId);
        return SavingApiEnvelope.ok(requestSupport.requestId(), entitlementResponse(userId, sysBillingService.getEntitlements(APP_CODE, userId)));
    }

    @Operation(summary = "校验购买", description = "提交 App Store 交易信息，由后端校验并发放 saving Pro 权益。")
    @PostMapping("/purchases/verify")
    public SavingApiEnvelope<Map<String, Object>> verify(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "购买校验请求体。示例：platform=ios，productId=com.savingsplanet.app.pro.monthly。", required = true) @Valid @RequestBody SavingPurchaseVerifyRequest request, @Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        Long userId = requestSupport.requireUserId(httpServletRequest);
        PurchaseIntakeAcceptedView accepted = sysBillingService.verify(APP_CODE, userId, request.toVerifyRequest());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("verified", "verified".equalsIgnoreCase(accepted.verificationStatus()));
        data.put("entitlement", entitlementResponse(userId, sysBillingService.getEntitlements(APP_CODE, userId)));
        return SavingApiEnvelope.ok(requestSupport.requestId(), data);
    }

    @Operation(summary = "恢复购买", description = "提交客户端发现的 App Store 历史交易列表，由后端重新绑定并刷新权益。")
    @PostMapping("/purchases/restore")
    public SavingApiEnvelope<Map<String, Object>> restore(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "恢复购买请求体。示例：transactions[0].productId=com.savingsplanet.app.pro.monthly。", required = true) @Valid @RequestBody SavingPurchaseRestoreRequest request, @Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        Long userId = requestSupport.requireUserId(httpServletRequest);
        PurchaseRestoreRequest restoreRequest = new PurchaseRestoreRequest(
            request.transactions().stream().map(SavingRestoreTransactionItem::toRestoreItem).collect(Collectors.toList())
        );
        PurchaseRestoreAcceptedView accepted = sysBillingService.restore(APP_CODE, userId, restoreRequest);
        List<PurchaseIntakeAcceptedView> transactions = accepted.transactions() == null ? List.of() : accepted.transactions();
        long verifiedCount = transactions.stream().filter(item -> "verified".equalsIgnoreCase(item.verificationStatus())).count();
        long pendingCount = transactions.stream().filter(item -> isPendingVerificationStatus(item.verificationStatus())).count();
        long rejectedCount = transactions.stream().filter(item -> isRejectedVerificationStatus(item.verificationStatus())).count();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("restored", accepted.acceptedCount() > 0);
        data.put("rebindOccurred", false);
        data.put("acceptedCount", accepted.acceptedCount());
        data.put("verifiedCount", verifiedCount);
        data.put("pendingCount", pendingCount);
        data.put("rejectedCount", rejectedCount);
        data.put("entitlement", entitlementResponse(userId, sysBillingService.getEntitlements(APP_CODE, userId)));
        return SavingApiEnvelope.ok(requestSupport.requestId(), data);
    }

    private boolean isPendingVerificationStatus(String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        String normalized = status.toLowerCase();
        return normalized.startsWith("pending") || normalized.startsWith("skipped") || normalized.startsWith("not_configured");
    }

    private boolean isRejectedVerificationStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String normalized = status.toLowerCase();
        return normalized.startsWith("rejected") || normalized.startsWith("failed");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> entitlementResponse(Long userId, EntitlementOverviewView overview) {
        List<EntitlementItemView> activeItems = overview.entitlements() == null ? List.of() : overview.entitlements().stream()
            .filter(item -> item != null && "active".equalsIgnoreCase(item.status()))
            .toList();
        UserEntitlementDecisionView centerDecision = resolveSavingDecision(userId);
        boolean paid = centerDecision == null
            ? activeItems.stream().anyMatch(item -> item.entitlementCode() != null && !"free".equalsIgnoreCase(item.entitlementCode()))
            : centerDecision.paid();
        String planCode = centerDecision == null ? (paid ? "pro_monthly" : "free") : centerDecision.planCode();
        Object limitsRaw = configService.namespace("saving_entitlement_limits").get(planCode);
        Map<String, Object> limits = limitsRaw instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
        if (centerDecision != null && centerDecision.features() != null && !centerDecision.features().isEmpty()) {
            // 中文说明：saving 前端仍消费旧 limits/accessProof 结构；后端内部优先使用统一权益中心决策，避免前端发版。
            limits.put("featureAccess", centerDecision.features());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", String.valueOf(userId));
        data.put("plan", planCode);
        data.put("status", paid ? "active" : "free");
        data.put("entitlements", centerDecision != null && centerDecision.activeEntitlements() != null && !centerDecision.activeEntitlements().isEmpty()
            ? centerDecision.activeEntitlements()
            : paid ? activeItems.stream().map(EntitlementItemView::entitlementCode).distinct().toList() : List.of("free"));
        data.put("limits", limits);
        data.put("expiresAt", centerDecision != null ? centerDecision.expiresAt() : activeItems.stream().map(EntitlementItemView::expiresAt).filter(v -> v != null).map(OffsetDateTime::toString).findFirst().orElse(null));
        data.put("graceUntil", null);
        Map<String, Object> policy = entitlementAccessPolicy();
        AppEntitlementAccessGuard.Decision advancedReportDecision = AppEntitlementAccessGuard.decide(
            APP_CODE,
            userId,
            overview,
            stringList(policy.get("requiredEntitlements"), List.of("pro_access")),
            stringList(policy.get("requiredPlanCodes"), List.of("pro_monthly")),
            planCode,
            paid ? "active" : "free",
            "saving_entitlement_access_policy.ios_v1"
        );
        data.put("serverTime", advancedReportDecision.serverTime());
        // 中文说明：Pro 报告模块的官方解锁依据必须来自后端计费内核，而不是客户端本地 StoreKit 状态或可被篡改的 feature flag。
        // 首发不上传记账明细，后端只返回 entitlement 判断结果；后端离线时 App 仍可生成基础报告，但高级模块应保持锁定。
        data.put("serverVerified", centerDecision == null ? advancedReportDecision.serverVerified() : centerDecision.serverVerified());
        data.put("verificationSource", "backend_sys_billing");
        data.put("accessProof", centerDecision == null ? advancedReportDecision.accessProof() : savingCompatAccessProof(userId, centerDecision, paid));
        return data;
    }

    private Map<String, Object> savingCompatAccessProof(Long userId, UserEntitlementDecisionView decision, boolean paid) {
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("appCode", APP_CODE);
        proof.put("userId", String.valueOf(userId));
        proof.put("plan", decision.planCode());
        proof.put("status", paid ? "active" : "free");
        proof.put("allowed", paid && "pro_monthly".equalsIgnoreCase(decision.planCode()));
        proof.put("requiredPlanCodes", List.of("pro_monthly"));
        proof.put("requiredEntitlements", List.of("pro_access"));
        proof.put("activeEntitlements", decision.activeEntitlements() == null ? List.of() : decision.activeEntitlements());
        proof.put("policy", "sys_entitlement_center.mode3.compat_saving");
        proof.put("source", decision.verificationSource());
        proof.put("mode3Note", "中文说明：saving 前端继续消费旧 entitlement 字段；高级报告解锁仍以 pro_access 为兼容计费权益，具体功能由统一权益中心模式三合并。 ");
        return proof;
    }

    private UserEntitlementDecisionView resolveSavingDecision(Long userId) {
        try {
            UserEntitlementDecisionView decision = sysEntitlementCenterService.resolveUserEntitlement(APP_CODE, userId);
            return decision == null || decision.features() == null || decision.features().isEmpty() ? null : decision;
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> entitlementAccessPolicy() {
        Object raw = configService.namespace("saving_entitlement_access_policy").get("ios_v1");
        if (!(raw instanceof Map<?, ?> root)) {
            return Map.of();
        }
        Object reportGeneration = root.get("reportGeneration");
        return reportGeneration instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : Map.of();
    }

    private List<String> stringList(Object raw, List<String> fallback) {
        if (!(raw instanceof List<?> list)) {
            return fallback;
        }
        List<String> values = list.stream()
            .filter(v -> v != null && !v.toString().isBlank())
            .map(v -> v.toString().trim())
            .toList();
        return values.isEmpty() ? fallback : values;
    }

    public record SavingPurchaseVerifyRequest(
        @Schema(description = "客户端平台。", example = "ios") @NotBlank String platform,
        @Schema(description = "App Store 商品 ID。", example = "com.savingsplanet.app.pro.monthly") @NotBlank String productId,
        @Schema(description = "当前交易 ID。", example = "2000000123456789") @NotBlank String transactionId,
        @Schema(description = "原始交易 ID，用于订阅归因。", example = "2000000123000000") @NotBlank String originalTransactionId,
        @Schema(description = "App Store signedTransactionInfo JWS。", example = "eyJhbGciOiJFUzI1NiIsIng1YyI6W10ifQ...") @NotBlank String signedTransactionInfo,
        @Schema(description = "App Account Token，可为空。", example = "550e8400-e29b-41d4-a716-446655440000") String appAccountToken
    ) {
        PurchaseVerifyRequest toVerifyRequest() {
            return new PurchaseVerifyRequest(productId, transactionId, originalTransactionId, platform, null, appAccountToken, signedTransactionInfo, null);
        }
    }

    public record SavingPurchaseRestoreRequest(
        @Schema(description = "客户端平台。", example = "ios") @NotBlank String platform,
        @Schema(description = "待恢复的交易列表，至少一条。", example = "[{\"productId\":\"com.savingsplanet.app.pro.monthly\",\"transactionId\":\"2000000123456789\",\"originalTransactionId\":\"2000000123000000\"}]") @Valid @NotEmpty List<SavingRestoreTransactionItem> transactions
    ) {}

    public record SavingRestoreTransactionItem(
        @Schema(description = "App Store 商品 ID。", example = "com.savingsplanet.app.pro.monthly") @NotBlank String productId,
        @Schema(description = "当前交易 ID。", example = "2000000123456789") @NotBlank String transactionId,
        @Schema(description = "原始交易 ID。", example = "2000000123000000") @NotBlank String originalTransactionId,
        @Schema(description = "App Store signedTransactionInfo JWS。", example = "eyJhbGciOiJFUzI1NiIsIng1YyI6W10ifQ...") @NotBlank String signedTransactionInfo
    ) {
        PurchaseRestoreItemRequest toRestoreItem() {
            return new PurchaseRestoreItemRequest(productId, transactionId, originalTransactionId, null, null, null, signedTransactionInfo, null);
        }
    }
}
