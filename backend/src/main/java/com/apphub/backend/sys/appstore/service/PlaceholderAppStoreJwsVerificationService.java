package com.apphub.backend.sys.appstore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * App Store领域的占位实现 `PlaceholderAppStoreJwsVerificationService`。
 * 用于在正式集成尚未完成前提供可运行的替代逻辑，并显式标记该能力仍是过渡态。
 */

public class PlaceholderAppStoreJwsVerificationService implements AppStoreJwsVerificationService {

    private final ObjectMapper objectMapper;

    public PlaceholderAppStoreJwsVerificationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public TransactionVerificationResult verifyTransaction(String signedTransactionInfo, TransactionExpectation expectation) {
        Map<String, String> diagnostics = new LinkedHashMap<>();
        if (signedTransactionInfo == null || signedTransactionInfo.isBlank()) {
            return new TransactionVerificationResult("failed", "failed_missing_signed_transaction", "signedTransactionInfo is required.", null, diagnostics);
        }
        try {
            JsonNode payload = decodePayload(signedTransactionInfo);
            TransactionClaims claims = new TransactionClaims(
                text(payload, "productId"),
                text(payload, "transactionId"),
                text(payload, "originalTransactionId"),
                text(payload, "environment"),
                text(payload, "bundleId"),
                text(payload, "appAccountToken"),
                dateTime(payload, "purchaseDate"),
                dateTime(payload, "expiresDate"),
                dateTime(payload, "revocationDate"),
                text(payload, "type")
            );
            diagnostics.put("productId", claims.productId());
            diagnostics.put("transactionId", claims.transactionId());
            diagnostics.put("originalTransactionId", claims.originalTransactionId());
            diagnostics.put("environment", claims.environment());
            diagnostics.put("bundleId", claims.bundleId());
            if (expectation.originalTransactionId() != null && claims.originalTransactionId() != null
                && !expectation.originalTransactionId().equals(claims.originalTransactionId())) {
                return new TransactionVerificationResult("rejected", "rejected_original_transaction_mismatch", "originalTransactionId does not match the signed payload.", claims, diagnostics);
            }
            if (expectation.productId() != null && claims.productId() != null
                && !expectation.productId().equals(claims.productId())) {
                return new TransactionVerificationResult("rejected", "rejected_product_id_mismatch", "productId does not match the signed payload.", claims, diagnostics);
            }
            return new TransactionVerificationResult("pending", "decoded_unverified", "JWS payload decoded, but cryptographic verification has not been migrated yet.", claims, diagnostics);
        } catch (Exception exception) {
            diagnostics.put("exception", exception.getClass().getSimpleName());
            return new TransactionVerificationResult("failed", "failed_invalid_signed_transaction", "Unable to decode signedTransactionInfo as JWS payload.", null, diagnostics);
        }
    }

    @Override
    public NotificationVerificationResult verifyNotification(String signedPayload) {
        Map<String, String> diagnostics = new LinkedHashMap<>();
        if (signedPayload == null || signedPayload.isBlank()) {
            return new NotificationVerificationResult("failed", "failed_missing_signed_payload", "signedPayload is required.", null, diagnostics);
        }
        try {
            JsonNode payload = decodePayload(signedPayload);
            JsonNode data = payload.path("data");
            NotificationClaims claims = new NotificationClaims(
                text(payload, "notificationUUID"),
                text(payload, "notificationType"),
                text(payload, "subtype"),
                text(payload, "environment"),
                text(data, "originalTransactionId"),
                text(data, "transactionId"),
                text(data, "productId"),
                text(data, "signedTransactionInfo"),
                text(data, "signedRenewalInfo")
            );
            diagnostics.put("notificationUUID", claims.notificationUuid());
            diagnostics.put("notificationType", claims.notificationType());
            diagnostics.put("subtype", claims.subtype());
            diagnostics.put("environment", claims.environment());
            diagnostics.put("originalTransactionId", claims.originalTransactionId());
            return new NotificationVerificationResult("pending", "decoded_unverified", "Notification JWS payload decoded, but cryptographic verification has not been migrated yet.", claims, diagnostics);
        } catch (Exception exception) {
            diagnostics.put("exception", exception.getClass().getSimpleName());
            return new NotificationVerificationResult("failed", "failed_invalid_signed_payload", "Unable to decode signedPayload as JWS payload.", null, diagnostics);
        }
    }

    private JsonNode decodePayload(String jws) throws Exception {
        String[] parts = jws.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWS");
        }
        return objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private OffsetDateTime dateTime(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            long raw = value.asLong();
            if (String.valueOf(Math.abs(raw)).length() > 10) {
                return OffsetDateTime.ofInstant(Instant.ofEpochMilli(raw), ZoneOffset.UTC);
            }
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(raw), ZoneOffset.UTC);
        }
        try {
            return OffsetDateTime.parse(value.asText());
        } catch (Exception ex) {
            return null;
        }
    }
}
