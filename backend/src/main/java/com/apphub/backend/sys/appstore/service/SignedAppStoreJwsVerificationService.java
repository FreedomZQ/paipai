package com.apphub.backend.sys.appstore.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * App Store服务 `SignedAppStoreJwsVerificationService`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Service
@Primary
public class SignedAppStoreJwsVerificationService implements AppStoreJwsVerificationService {

    private final AppStoreSignedJwsVerifier signedJwsVerifier;

    public SignedAppStoreJwsVerificationService(AppStoreSignedJwsVerifier signedJwsVerifier) {
        this.signedJwsVerifier = signedJwsVerifier;
    }

    @Override
    public TransactionVerificationResult verifyTransaction(String signedTransactionInfo, TransactionExpectation expectation) {
        AppStoreSignedJwsVerifier.VerifiedJws verifiedJws;
        try {
            verifiedJws = signedJwsVerifier.verify(signedTransactionInfo, "signedTransactionInfo");
        } catch (AppStoreSignedJwsVerifier.VerificationException ex) {
            return new TransactionVerificationResult("failed", ex.detailStatus(), ex.note(), null, ex.diagnostics());
        }

        TransactionClaims claims = toTransactionClaims(verifiedJws.payload());
        Map<String, String> diagnostics = new LinkedHashMap<>(verifiedJws.diagnostics());
        diagnostics.put("bundleId", claims.bundleId());
        diagnostics.put("environment", claims.environment());
        diagnostics.put("productId", claims.productId());
        diagnostics.put("transactionId", claims.transactionId());
        diagnostics.put("originalTransactionId", claims.originalTransactionId());

        if (!hasText(claims.originalTransactionId()) || !hasText(claims.productId())) {
            return new TransactionVerificationResult("failed", "failed_missing_transaction_claims", "The signed transaction payload is missing productId or originalTransactionId.", claims, diagnostics);
        }
        if (!hasText(claims.bundleId())) {
            return new TransactionVerificationResult("failed", "failed_missing_transaction_bundle_id", "The signed transaction payload is missing bundleId.", claims, diagnostics);
        }
        if (!hasText(claims.environment())) {
            return new TransactionVerificationResult("failed", "failed_missing_transaction_environment", "The signed transaction payload is missing environment.", claims, diagnostics);
        }
        if (hasText(expectation.productId()) && !expectation.productId().equals(claims.productId())) {
            return new TransactionVerificationResult("rejected", "rejected_product_id_mismatch", "The signed transaction payload productId does not match the client-submitted productId.", claims, diagnostics);
        }
        if (hasText(expectation.originalTransactionId()) && !expectation.originalTransactionId().equals(claims.originalTransactionId())) {
            return new TransactionVerificationResult("rejected", "rejected_original_transaction_mismatch", "The signed transaction payload originalTransactionId does not match the client request.", claims, diagnostics);
        }
        if (hasText(expectation.transactionId()) && hasText(claims.transactionId()) && !expectation.transactionId().equals(claims.transactionId())) {
            return new TransactionVerificationResult("rejected", "rejected_transaction_id_mismatch", "The signed transaction payload transactionId does not match the client request.", claims, diagnostics);
        }
        String expectedEnvironment = normalizeEnvironment(expectation.environment());
        if (hasText(expectedEnvironment) && !expectedEnvironment.equals(claims.environment())) {
            return new TransactionVerificationResult("rejected", "rejected_environment_mismatch", "The signed transaction payload environment does not match the submitted environment.", claims, diagnostics);
        }

        return new TransactionVerificationResult(
            "pending",
            "pending_server_api_reconciliation",
            "The transaction JWS signature and x5c certificate chain are valid. App Store Server API reconciliation is still required before projection or entitlement changes.",
            claims,
            diagnostics
        );
    }

    @Override
    public NotificationVerificationResult verifyNotification(String signedPayload) {
        AppStoreSignedJwsVerifier.VerifiedJws verifiedNotification;
        try {
            verifiedNotification = signedJwsVerifier.verify(signedPayload, "signedPayload");
        } catch (AppStoreSignedJwsVerifier.VerificationException ex) {
            return new NotificationVerificationResult("failed", ex.detailStatus(), ex.note(), null, ex.diagnostics());
        }

        JsonNode payload = verifiedNotification.payload();
        JsonNode data = payload.path("data");
        JsonNode dataSummary = data.path("dataSummary");
        String signedTransactionInfo = text(data, "signedTransactionInfo");
        String signedRenewalInfo = text(data, "signedRenewalInfo");
        Map<String, String> diagnostics = new LinkedHashMap<>(verifiedNotification.diagnostics());

        TransactionClaims nestedTransactionClaims = null;
        if (hasText(signedTransactionInfo)) {
            try {
                AppStoreSignedJwsVerifier.VerifiedJws verifiedTransaction = signedJwsVerifier.verify(signedTransactionInfo, "signedTransactionInfo");
                nestedTransactionClaims = toTransactionClaims(verifiedTransaction.payload());
                diagnostics.put("nestedTransactionSignatureVerified", "true");
            } catch (AppStoreSignedJwsVerifier.VerificationException ex) {
                diagnostics.putAll(prefixDiagnostics("nestedTransaction.", ex.diagnostics()));
                return new NotificationVerificationResult("failed", "failed_nested_transaction_signature_verification", ex.note(), null, diagnostics);
            }
        }
        if (hasText(signedRenewalInfo)) {
            try {
                signedJwsVerifier.verify(signedRenewalInfo, "signedRenewalInfo");
                diagnostics.put("renewalInfoSignatureVerified", "true");
            } catch (AppStoreSignedJwsVerifier.VerificationException ex) {
                diagnostics.putAll(prefixDiagnostics("renewalInfo.", ex.diagnostics()));
                return new NotificationVerificationResult("failed", "failed_nested_renewal_signature_verification", ex.note(), null, diagnostics);
            }
        }

        String notificationUuid = text(payload, "notificationUUID");
        String notificationType = text(payload, "notificationType");
        String subtype = text(payload, "subtype");
        String environment = normalizeEnvironment(firstNonBlank(
            nestedTransactionClaims != null ? nestedTransactionClaims.environment() : null,
            text(payload, "environment"),
            text(data, "environment"),
            text(dataSummary, "environment")
        ));
        String originalTransactionId = firstNonBlank(
            nestedTransactionClaims != null ? nestedTransactionClaims.originalTransactionId() : null,
            text(dataSummary, "originalTransactionId")
        );
        String transactionId = firstNonBlank(
            nestedTransactionClaims != null ? nestedTransactionClaims.transactionId() : null,
            text(dataSummary, "transactionId")
        );
        String productId = firstNonBlank(
            nestedTransactionClaims != null ? nestedTransactionClaims.productId() : null,
            text(dataSummary, "productId")
        );

        NotificationClaims claims = new NotificationClaims(
            notificationUuid,
            notificationType,
            subtype,
            environment,
            originalTransactionId,
            transactionId,
            productId,
            signedTransactionInfo,
            signedRenewalInfo
        );

        diagnostics.put("notificationUUID", notificationUuid);
        diagnostics.put("notificationType", notificationType);
        diagnostics.put("subtype", subtype);
        diagnostics.put("environment", environment);
        diagnostics.put("originalTransactionId", originalTransactionId);
        diagnostics.put("transactionId", transactionId);
        diagnostics.put("productId", productId);

        if (!hasText(notificationUuid)) {
            return new NotificationVerificationResult("failed", "failed_missing_notification_uuid", "The App Store notification payload did not expose notificationUUID.", claims, diagnostics);
        }
        if (!hasText(environment)) {
            return new NotificationVerificationResult("failed", "failed_missing_notification_environment", "The App Store notification payload did not expose environment.", claims, diagnostics);
        }
        if (!hasText(originalTransactionId)) {
            return new NotificationVerificationResult("failed", "failed_missing_notification_transaction", "The App Store notification payload did not expose an originalTransactionId.", claims, diagnostics);
        }

        return new NotificationVerificationResult(
            "verified",
            "verified_signed_notification",
            "The App Store notification JWS and nested signed data verified successfully. It is a trusted trigger; entitlement changes still require Server API reconciliation.",
            claims,
            diagnostics
        );
    }

    private TransactionClaims toTransactionClaims(JsonNode payload) {
        return new TransactionClaims(
            text(payload, "productId"),
            text(payload, "transactionId"),
            text(payload, "originalTransactionId"),
            normalizeEnvironment(text(payload, "environment")),
            text(payload, "bundleId"),
            text(payload, "appAccountToken"),
            time(payload.get("purchaseDate")),
            time(payload.get("expiresDate")),
            time(payload.get("revocationDate")),
            text(payload, "revocationReason"),
            integer(payload, "revocationPercentage"),
            longValue(payload, "price"),
            text(payload, "currency"),
            text(payload, "storefront"),
            text(payload, "webOrderLineItemId"),
            text(payload, "transactionReason"),
            text(payload, "inAppOwnershipType"),
            integer(payload, "quantity"),
            text(payload, "type")
        );
    }

    private Map<String, String> prefixDiagnostics(String prefix, Map<String, String> diagnostics) {
        Map<String, String> prefixed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : diagnostics.entrySet()) {
            prefixed.put(prefix + entry.getKey(), entry.getValue());
        }
        return prefixed;
    }

    private OffsetDateTime time(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            long raw = node.asLong();
            Instant instant = raw > 9_999_999_999L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        String text = node.asText();
        if (!hasText(text)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asInt();
        }
        try {
            return Integer.parseInt(value.asText());
        } catch (Exception ex) {
            return null;
        }
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asLong();
        }
        try {
            return Long.parseLong(value.asText());
        } catch (Exception ex) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String normalizeEnvironment(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
