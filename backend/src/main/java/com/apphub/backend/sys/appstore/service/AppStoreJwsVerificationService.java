package com.apphub.backend.sys.appstore.service;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * App Store服务 `AppStoreJwsVerificationService`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

public interface AppStoreJwsVerificationService {

    TransactionVerificationResult verifyTransaction(String signedTransactionInfo, TransactionExpectation expectation);

    NotificationVerificationResult verifyNotification(String signedPayload);

    record TransactionExpectation(
        String productId,
        String transactionId,
        String originalTransactionId,
        String environment,
        Long userId
    ) {
    }

    record TransactionClaims(
        String productId,
        String transactionId,
        String originalTransactionId,
        String environment,
        String bundleId,
        String appAccountToken,
        OffsetDateTime purchaseDate,
        OffsetDateTime expiresDate,
        OffsetDateTime revocationDate,
        String type
    ) {
    }

    record TransactionVerificationResult(
        String verificationStatus,
        String detailStatus,
        String note,
        TransactionClaims claims,
        Map<String, String> diagnostics
    ) {
    }

    record NotificationClaims(
        String notificationUuid,
        String notificationType,
        String subtype,
        String environment,
        String originalTransactionId,
        String transactionId,
        String productId,
        String signedTransactionInfo,
        String signedRenewalInfo
    ) {
    }

    record NotificationVerificationResult(
        String verificationStatus,
        String detailStatus,
        String note,
        NotificationClaims claims,
        Map<String, String> diagnostics
    ) {
    }
}
