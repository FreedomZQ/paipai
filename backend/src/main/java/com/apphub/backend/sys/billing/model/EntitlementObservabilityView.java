package com.apphub.backend.sys.billing.model;

import java.util.List;

/**
 * 响应模型 `EntitlementObservabilityView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record EntitlementObservabilityView(
    String appCode,
    int definitionMappingCount,
    int remoteConfigMappingCount,
    int effectiveMappingCount,
    List<EntitlementMappingItemView> effectiveMappings,
    EntitlementRefreshPolicyView refreshPolicy,
    EntitlementRefreshStatsView refreshStats,
    List<EntitlementRefreshRecentItemView> recentRefreshes
) {
    public record EntitlementMappingItemView(
        String productId,
        String entitlementCode,
        String source
    ) {
    }

    public record EntitlementRefreshPolicyView(
        int candidateLimit,
        String candidateLimitSource,
        long cooldownMinutes,
        String cooldownMinutesSource
    ) {
    }

    public record EntitlementRefreshRecentItemView(
        Long transactionId,
        String originalTransactionId,
        String productId,
        String verificationStatus,
        String processingStatus,
        java.time.OffsetDateTime updatedAt
    ) {
    }

    public record EntitlementRefreshStatsView(
        int total,
        int verified,
        int pending,
        int failed,
        int rejected
    ) {
    }
}
