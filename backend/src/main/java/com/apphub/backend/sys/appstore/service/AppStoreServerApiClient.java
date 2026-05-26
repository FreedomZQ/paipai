package com.apphub.backend.sys.appstore.service;

import java.util.Map;

/**
 * App Store服务 `AppStoreServerApiClient`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

public interface AppStoreServerApiClient {

    LookupResult lookup(LookupCommand command, AppStoreConfiguration configuration);

    ConsumptionSendResult sendConsumptionInformation(
        String transactionId,
        ConsumptionRequestBody body,
        AppStoreConfiguration configuration
    );

    record LookupCommand(
        String transactionId,
        String originalTransactionId,
        String expectedProductId,
        String expectedEnvironment
    ) {
    }

    record AppStoreConfiguration(
        String bundleId,
        String environment,
        Boolean allowSandbox,
        String appAppleId,
        String issuerId,
        String keyId,
        String privateKey
    ) {
        public boolean isReadyForServerApi() {
            return hasText(issuerId) && hasText(keyId) && hasText(privateKey) && hasText(bundleId);
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    record LookupResult(
        String status,
        boolean remoteLookupAttempted,
        String note,
        AppStoreJwsVerificationService.TransactionClaims claims,
        String signedTransactionInfo,
        String signedRenewalInfo,
        Map<String, String> diagnostics
    ) {
        public boolean isVerified() {
            return "verified".equalsIgnoreCase(status);
        }
    }

    record ConsumptionRequestBody(
        boolean customerConsented,
        Integer consumptionPercentage,
        String deliveryStatus,
        Boolean sampleContentProvided,
        String refundPreference
    ) {
    }

    record ConsumptionSendResult(
        String status,
        boolean remoteCallAttempted,
        Integer httpStatus,
        boolean retryable,
        String note,
        Map<String, String> diagnostics
    ) {
        public boolean accepted() {
            return "accepted".equalsIgnoreCase(status);
        }
    }
}
