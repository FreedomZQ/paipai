package com.apphub.backend.sys.compensation.model;

import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import java.time.OffsetDateTime;

public record CompensationRedeemResultView(
    String compensationCode,
    String status,
    String benefitType,
    String benefitSummary,
    String planCode,
    String entitlementCode,
    String serviceType,
    Integer grantCount,
    String validUntil,
    String redeemedAt,
    String message,
    ReadingCompatService.AccountStateView accountState
) {}
