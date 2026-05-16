package com.apphub.backend.sys.compensation.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record CompensationCodeView(
    Long id,
    String appCode,
    String compensationCode,
    String benefitType,
    String planCode,
    String entitlementCode,
    String serviceType,
    Integer grantCount,
    Integer grantValidDays,
    OffsetDateTime expiresAt,
    Integer maxUses,
    Integer usedCount,
    String status,
    Long usedByUserId,
    OffsetDateTime usedAt,
    String voidReason,
    Map<String, Object> metadata,
    Long createdByUserId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
