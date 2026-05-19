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
    OffsetDateTime grantValidUntilAt,
    OffsetDateTime expiresAt,
    String claimScope,
    Integer maxUses,
    Integer usedCount,
    String status,
    OffsetDateTime usedAt,
    String voidReason,
    Map<String, Object> metadata,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
