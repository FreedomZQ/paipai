package com.apphub.backend.sys.billing.privacy.model;

public record PrivacyConsentView(
    String appCode,
    Long userId,
    String consentType,
    String consentStatus,
    String policyVersion,
    String regionCode,
    String sourceType,
    String sourceRef,
    String consentedAt,
    String revokedAt
) {
}
