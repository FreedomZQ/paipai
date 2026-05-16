package com.apphub.backend.apps.common.entitlement;

import com.apphub.backend.sys.billing.model.EntitlementItemView;
import com.apphub.backend.sys.billing.model.EntitlementOverviewView;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 多 App 共用的轻量权益访问判断工具。
 *
 * <p>个人开发者低运维原则：这里不做复杂 Policy Engine，只把统一计费内核的 active entitlement
 * 转成每个业务模块可消费的 backend-verified decision / proof。具体 feature 需要哪些 plan/entitlement，
 * 仍由各 App 自己的 DB 配置 namespace 决定，避免不同 App 的权益口径互相污染。</p>
 */
public final class AppEntitlementAccessGuard {
    private AppEntitlementAccessGuard() {}

    public static Decision decide(
        String appCode,
        Long userId,
        EntitlementOverviewView overview,
        List<String> requiredEntitlements,
        List<String> requiredPlanCodes,
        String effectivePlanCode,
        String effectiveStatus,
        String policyCode
    ) {
        List<String> activeCodes = activeEntitlementCodes(overview);
        boolean entitlementMatched = requiredEntitlements == null || requiredEntitlements.isEmpty()
            || activeCodes.stream().anyMatch(code -> requiredEntitlements.stream().anyMatch(required -> same(code, required)));
        boolean planMatched = requiredPlanCodes == null || requiredPlanCodes.isEmpty()
            || requiredPlanCodes.stream().anyMatch(required -> same(effectivePlanCode, required));
        boolean paidPlan = effectivePlanCode != null && !"free".equalsIgnoreCase(effectivePlanCode);
        boolean allowed = paidPlan && entitlementMatched && planMatched;
        OffsetDateTime serverTime = OffsetDateTime.now(ZoneOffset.UTC);
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("appCode", appCode);
        proof.put("userId", userId == null ? null : String.valueOf(userId));
        proof.put("plan", effectivePlanCode == null || effectivePlanCode.isBlank() ? "free" : effectivePlanCode);
        proof.put("status", effectiveStatus == null || effectiveStatus.isBlank() ? "free" : effectiveStatus);
        proof.put("serverTime", serverTime.toString());
        proof.put("policy", policyCode);
        proof.put("requiredEntitlements", requiredEntitlements == null ? List.of() : requiredEntitlements);
        proof.put("requiredPlanCodes", requiredPlanCodes == null ? List.of() : requiredPlanCodes);
        proof.put("activeEntitlements", activeCodes);
        proof.put("allowed", allowed);
        return new Decision(allowed, true, "backend_sys_billing", proof, activeCodes, serverTime.toString());
    }

    public static List<String> activeEntitlementCodes(EntitlementOverviewView overview) {
        if (overview == null || overview.entitlements() == null) {
            return List.of();
        }
        return overview.entitlements().stream()
            .filter(item -> item != null && "active".equalsIgnoreCase(item.status()))
            .map(EntitlementItemView::entitlementCode)
            .filter(code -> code != null && !code.isBlank())
            .map(code -> code.trim().toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
    }

    private static boolean same(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    public record Decision(
        boolean allowed,
        boolean serverVerified,
        String verificationSource,
        Map<String, Object> accessProof,
        List<String> activeEntitlements,
        String serverTime
    ) {}
}
