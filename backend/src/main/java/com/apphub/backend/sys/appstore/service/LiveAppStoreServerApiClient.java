package com.apphub.backend.sys.appstore.service;

import com.apphub.backend.shared.apple.AppleJwtTokenFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * App Store服务 `LiveAppStoreServerApiClient`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Service
public class LiveAppStoreServerApiClient implements AppStoreServerApiClient {

    private static final String PRODUCTION_BASE_URL = "https://api.storekit.apple.com";
    private static final String SANDBOX_BASE_URL = "https://api.storekit-sandbox.apple.com";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(8);

    private final RestOperations restOperations;
    private final AppleJwtTokenFactory appleJwtTokenFactory;
    private final AppStoreSignedJwsVerifier appStoreSignedJwsVerifier;
    private final ObjectMapper objectMapper;

    @Autowired
    public LiveAppStoreServerApiClient(
        RestTemplateBuilder restTemplateBuilder,
        AppleJwtTokenFactory appleJwtTokenFactory,
        AppStoreSignedJwsVerifier appStoreSignedJwsVerifier,
        ObjectMapper objectMapper
    ) {
        this(
            restTemplateBuilder
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .build(),
            appleJwtTokenFactory,
            appStoreSignedJwsVerifier,
            objectMapper
        );
    }

    LiveAppStoreServerApiClient(
        RestOperations restOperations,
        AppleJwtTokenFactory appleJwtTokenFactory,
        AppStoreSignedJwsVerifier appStoreSignedJwsVerifier,
        ObjectMapper objectMapper
    ) {
        this.restOperations = restOperations;
        this.appleJwtTokenFactory = appleJwtTokenFactory;
        this.appStoreSignedJwsVerifier = appStoreSignedJwsVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public LookupResult lookup(LookupCommand command, AppStoreConfiguration configuration) {
        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("configuredEnvironment", normalizeEnvironment(configuration.environment()));
        diagnostics.put("allowSandbox", String.valueOf(Boolean.TRUE.equals(configuration.allowSandbox())));
        diagnostics.put("bundleId", configuration.bundleId());

        if (!configuration.isReadyForServerApi()) {
            return new LookupResult(
                "not_configured",
                false,
                "App Store Server API credentials are incomplete. Keep subscription projection pending until issuerId / keyId / privateKey / bundleId are configured.",
                null,
                null,
                null,
                diagnostics
            );
        }
        if (!hasText(command.transactionId()) && !hasText(command.originalTransactionId())) {
            return new LookupResult(
                "failed_missing_transaction_identifier",
                false,
                "At least one App Store transaction identifier is required for authoritative server-side reconciliation.",
                null,
                null,
                null,
                diagnostics
            );
        }

        List<String> environments = resolveCandidateEnvironments(command.expectedEnvironment(), configuration);
        diagnostics.put("lookupEnvironments", String.join(",", environments));
        if (environments.isEmpty()) {
            return new LookupResult(
                "rejected_environment_not_allowed",
                false,
                "Sandbox App Store reconciliation is disabled for this runtime.",
                null,
                null,
                null,
                diagnostics
            );
        }

        String bearer = appleJwtTokenFactory.createAppStoreServerApiToken(
            configuration.issuerId(),
            configuration.bundleId(),
            configuration.keyId(),
            configuration.privateKey()
        );

        LookupResult lastFailure = null;
        for (String environment : environments) {
            LookupResult byTransaction = lookupTransaction(command, configuration, bearer, environment);
            if (byTransaction != null) {
                if (byTransaction.isVerified()) {
                    return byTransaction;
                }
                lastFailure = byTransaction;
                if (!"failed_transaction_not_found".equalsIgnoreCase(byTransaction.status()) || !hasText(command.originalTransactionId())) {
                    continue;
                }
            }
            if (hasText(command.originalTransactionId())) {
                LookupResult bySubscription = lookupSubscription(command, configuration, bearer, environment);
                if (bySubscription.isVerified()) {
                    return bySubscription;
                }
                lastFailure = bySubscription;
            }
        }
        return lastFailure != null ? lastFailure : new LookupResult(
            "failed_server_api_lookup",
            true,
            "App Store Server API lookup did not yield a verified transaction.",
            null,
            null,
            null,
            diagnostics
        );
    }

    @Override
    public ConsumptionSendResult sendConsumptionInformation(
        String transactionId,
        ConsumptionRequestBody body,
        AppStoreConfiguration configuration
    ) {
        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("configuredEnvironment", normalizeEnvironment(configuration.environment()));
        diagnostics.put("allowSandbox", String.valueOf(Boolean.TRUE.equals(configuration.allowSandbox())));
        diagnostics.put("bundleId", configuration.bundleId());
        diagnostics.put("transactionId", transactionId);

        if (!configuration.isReadyForServerApi()) {
            return new ConsumptionSendResult(
                "not_configured",
                false,
                null,
                true,
                "App Store Server API credentials are incomplete; consumption information was not sent.",
                diagnostics
            );
        }
        if (!hasText(transactionId)) {
            return new ConsumptionSendResult(
                "failed_missing_transaction_identifier",
                false,
                null,
                false,
                "transactionId is required before sending App Store consumption information.",
                diagnostics
            );
        }
        if (body == null) {
            return new ConsumptionSendResult(
                "failed_missing_payload",
                false,
                null,
                false,
                "Consumption request body is required.",
                diagnostics
            );
        }

        List<String> environments = resolveCandidateEnvironments(configuration.environment(), configuration);
        if (environments.isEmpty()) {
            return new ConsumptionSendResult(
                "rejected_environment_not_allowed",
                false,
                null,
                false,
                "Sandbox App Store consumption replies are not allowed in this runtime.",
                diagnostics
            );
        }

        String bearer = appleJwtTokenFactory.createAppStoreServerApiToken(
            configuration.issuerId(),
            configuration.bundleId(),
            configuration.keyId(),
            configuration.privateKey()
        );

        ConsumptionSendResult lastFailure = null;
        for (String environment : environments) {
            String path = baseUrl(environment) + "/inApps/v2/transactions/consumption/" + url(transactionId);
            diagnostics.put("endpoint", path);
            diagnostics.put("sendEnvironment", environment);
            try {
                ResponseEntity<String> response = executePut(path, bearer, body);
                int statusCode = response.getStatusCode().value();
                diagnostics.put("httpStatus", String.valueOf(statusCode));
                if (statusCode == 202) {
                    return new ConsumptionSendResult(
                        "accepted",
                        true,
                        statusCode,
                        false,
                        "Apple accepted the App Store consumption information reply.",
                        diagnostics
                    );
                }
                lastFailure = new ConsumptionSendResult(
                    "failed_unexpected_status",
                    true,
                    statusCode,
                    statusCode == 429 || statusCode >= 500,
                    "App Store consumption information reply returned HTTP " + statusCode + ".",
                    diagnostics
                );
            } catch (HttpStatusCodeException ex) {
                lastFailure = consumptionHttpFailure(ex, diagnostics);
            } catch (Exception ex) {
                diagnostics.put("transportException", ex.getClass().getSimpleName());
                lastFailure = new ConsumptionSendResult(
                    "failed_transport_error",
                    true,
                    null,
                    true,
                    "App Store consumption information reply failed unexpectedly.",
                    diagnostics
                );
            }
            if (lastFailure != null && !lastFailure.retryable()) {
                return lastFailure;
            }
        }
        return lastFailure == null
            ? new ConsumptionSendResult("failed_consumption_reply", true, null, true, "App Store consumption reply did not complete.", diagnostics)
            : lastFailure;
    }

    private LookupResult lookupTransaction(
        LookupCommand command,
        AppStoreConfiguration configuration,
        String bearer,
        String environment
    ) {
        if (!hasText(command.transactionId())) {
            return null;
        }
        Map<String, String> diagnostics = baseDiagnostics(configuration, environment, "transaction");
        String path = baseUrl(environment) + "/inApps/v1/transactions/" + url(command.transactionId());
        diagnostics.put("endpoint", path);
        try {
            JsonNode body = executeGet(path, bearer);
            String signedTransactionInfo = text(body, "signedTransactionInfo");
            diagnostics.put("responseEnvironment", text(body, "environment"));
            return verifiedResult(command, configuration, environment, signedTransactionInfo, null, diagnostics, true);
        } catch (HttpStatusCodeException ex) {
            return httpFailure(ex, diagnostics);
        } catch (Exception ex) {
            diagnostics.put("transportException", ex.getClass().getSimpleName());
            return new LookupResult(
                "failed_transport_error",
                true,
                "App Store transaction lookup failed unexpectedly.",
                null,
                null,
                null,
                diagnostics
            );
        }
    }

    private LookupResult lookupSubscription(
        LookupCommand command,
        AppStoreConfiguration configuration,
        String bearer,
        String environment
    ) {
        Map<String, String> diagnostics = baseDiagnostics(configuration, environment, "subscription_status");
        String path = baseUrl(environment) + "/inApps/v1/subscriptions/" + url(command.originalTransactionId());
        diagnostics.put("endpoint", path);
        try {
            JsonNode body = executeGet(path, bearer);
            SelectedLastTransaction selected = selectLastTransaction(body);
            diagnostics.put("responseEnvironment", text(body, "environment"));
            diagnostics.put("subscriptionGroupCount", String.valueOf(body.path("data").size()));
            if (selected == null) {
                return new LookupResult(
                    "failed_missing_subscription_transaction",
                    true,
                    "App Store subscription status response did not include a usable signedTransactionInfo.",
                    null,
                    null,
                    null,
                    diagnostics
                );
            }
            diagnostics.put("subscriptionStatus", selected.status());
            return verifiedResult(command, configuration, environment, selected.signedTransactionInfo(), selected.signedRenewalInfo(), diagnostics, false);
        } catch (HttpStatusCodeException ex) {
            return httpFailure(ex, diagnostics);
        } catch (Exception ex) {
            diagnostics.put("transportException", ex.getClass().getSimpleName());
            return new LookupResult(
                "failed_transport_error",
                true,
                "App Store subscription-status lookup failed unexpectedly.",
                null,
                null,
                null,
                diagnostics
            );
        }
    }

    private LookupResult verifiedResult(
        LookupCommand command,
        AppStoreConfiguration configuration,
        String environment,
        String signedTransactionInfo,
        String signedRenewalInfo,
        Map<String, String> diagnostics,
        boolean strictTransactionIdMatch
    ) {
        if (!hasText(signedTransactionInfo)) {
            return new LookupResult(
                "failed_missing_signed_transaction_info",
                true,
                "App Store Server API response was missing signedTransactionInfo.",
                null,
                null,
                null,
                diagnostics
            );
        }

        TransactionClaimsWithDiagnostics verifiedClaims;
        try {
            verifiedClaims = verifiedTransactionClaims(signedTransactionInfo);
            diagnostics.putAll(verifiedClaims.diagnostics());
        } catch (AppStoreSignedJwsVerifier.VerificationException ex) {
            diagnostics.putAll(ex.diagnostics());
            return new LookupResult(ex.detailStatus(), true, ex.note(), null, signedTransactionInfo, signedRenewalInfo, diagnostics);
        }
        if (hasText(signedRenewalInfo)) {
            try {
                AppStoreSignedJwsVerifier.VerifiedJws verifiedRenewal = appStoreSignedJwsVerifier.verify(signedRenewalInfo, "signedRenewalInfo");
                diagnostics.putAll(prefixDiagnostics("renewalInfo.", verifiedRenewal.diagnostics()));
            } catch (AppStoreSignedJwsVerifier.VerificationException ex) {
                diagnostics.putAll(prefixDiagnostics("renewalInfo.", ex.diagnostics()));
                return new LookupResult("failed_invalid_renewal_info_signature", true, ex.note(), null, signedTransactionInfo, signedRenewalInfo, diagnostics);
            }
        }

        AppStoreJwsVerificationService.TransactionClaims claims = verifiedClaims.claims();
        diagnostics.put("resolvedProductId", claims.productId());
        diagnostics.put("resolvedTransactionId", claims.transactionId());
        diagnostics.put("resolvedOriginalTransactionId", claims.originalTransactionId());
        diagnostics.put("resolvedEnvironment", claims.environment());
        diagnostics.put("resolvedBundleId", claims.bundleId());
        diagnostics.put("resolvedAppAccountTokenPresent", String.valueOf(hasText(claims.appAccountToken())));

        if (!hasText(claims.originalTransactionId()) || !hasText(claims.productId())) {
            return new LookupResult("failed_missing_transaction_claims", true, "The authoritative App Store response is missing productId or originalTransactionId.", claims, signedTransactionInfo, signedRenewalInfo, diagnostics);
        }
        if (hasText(command.expectedProductId()) && !command.expectedProductId().equals(claims.productId())) {
            return new LookupResult("rejected_product_id_mismatch", true, "The authoritative App Store response productId does not match the client-submitted productId.", claims, signedTransactionInfo, signedRenewalInfo, diagnostics);
        }
        if (hasText(command.originalTransactionId()) && !command.originalTransactionId().equals(claims.originalTransactionId())) {
            return new LookupResult("rejected_original_transaction_mismatch", true, "The authoritative App Store response originalTransactionId does not match the submitted originalTransactionId.", claims, signedTransactionInfo, signedRenewalInfo, diagnostics);
        }
        if (strictTransactionIdMatch && hasText(command.transactionId()) && hasText(claims.transactionId()) && !command.transactionId().equals(claims.transactionId())) {
            return new LookupResult("rejected_transaction_id_mismatch", true, "The authoritative App Store response transactionId does not match the submitted transactionId.", claims, signedTransactionInfo, signedRenewalInfo, diagnostics);
        }
        if (hasText(configuration.bundleId()) && hasText(claims.bundleId()) && !configuration.bundleId().equals(claims.bundleId())) {
            return new LookupResult("rejected_bundle_id_mismatch", true, "The authoritative App Store response bundleId does not match the configured bundleId.", claims, signedTransactionInfo, signedRenewalInfo, diagnostics);
        }
        String resolvedEnvironment = normalizeEnvironment(claims.environment()) != null ? normalizeEnvironment(claims.environment()) : environment;
        if ("sandbox".equals(resolvedEnvironment) && !isSandboxAllowed(configuration)) {
            return new LookupResult("rejected_environment_not_allowed", true, "Sandbox App Store transactions are not allowed in this runtime.", claims, signedTransactionInfo, signedRenewalInfo, diagnostics);
        }
        return new LookupResult(
            "verified",
            true,
            "Authoritative App Store transaction data was fetched from App Store Server API and cryptographically verified against Apple's x5c chain.",
            claims,
            signedTransactionInfo,
            signedRenewalInfo,
            diagnostics
        );
    }

    private TransactionClaimsWithDiagnostics verifiedTransactionClaims(String signedTransactionInfo) {
        AppStoreSignedJwsVerifier.VerifiedJws verifiedJws = appStoreSignedJwsVerifier.verify(signedTransactionInfo, "signedTransactionInfo");
        JsonNode payload = verifiedJws.payload();
        return new TransactionClaimsWithDiagnostics(
            new AppStoreJwsVerificationService.TransactionClaims(
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
            ),
            verifiedJws.diagnostics()
        );
    }

    private JsonNode executeGet(String url, String bearer) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(bearer);
        ResponseEntity<String> response = restOperations.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return response.getBody() == null || response.getBody().isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response.getBody());
    }

    private ResponseEntity<String> executePut(String url, String bearer, ConsumptionRequestBody body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearer);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerConsented", body.customerConsented());
        if (body.consumptionPercentage() != null) {
            payload.put("consumptionPercentage", body.consumptionPercentage());
        }
        if (hasText(body.deliveryStatus())) {
            payload.put("deliveryStatus", body.deliveryStatus());
        }
        if (body.sampleContentProvided() != null) {
            payload.put("sampleContentProvided", body.sampleContentProvided());
        }
        if (hasText(body.refundPreference())) {
            payload.put("refundPreference", body.refundPreference());
        }
        return restOperations.exchange(url, HttpMethod.PUT, new HttpEntity<>(payload, headers), String.class);
    }

    private LookupResult httpFailure(HttpStatusCodeException ex, Map<String, String> diagnostics) {
        diagnostics.put("httpStatus", String.valueOf(ex.getStatusCode().value()));
        diagnostics.put("rawErrorBodyLength", String.valueOf(ex.getResponseBodyAsString() == null ? 0 : ex.getResponseBodyAsString().length()));
        String status;
        if (ex.getStatusCode().value() == 404) {
            status = "failed_transaction_not_found";
        } else if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
            status = "failed_server_api_auth";
        } else if (ex.getStatusCode().value() == 429) {
            status = "failed_rate_limited";
        } else if (ex.getStatusCode().is5xxServerError()) {
            status = "failed_apple_service_unavailable";
        } else {
            status = "failed_server_api_lookup";
        }
        return new LookupResult(status, true, "App Store Server API lookup failed with HTTP " + ex.getStatusCode().value() + ".", null, null, null, diagnostics);
    }

    private ConsumptionSendResult consumptionHttpFailure(HttpStatusCodeException ex, Map<String, String> diagnostics) {
        int statusCode = ex.getStatusCode().value();
        diagnostics.put("httpStatus", String.valueOf(statusCode));
        diagnostics.put("rawErrorBodyLength", String.valueOf(ex.getResponseBodyAsString() == null ? 0 : ex.getResponseBodyAsString().length()));
        String status;
        boolean retryable = false;
        if (statusCode == 400) {
            status = "failed_invalid_consumption_payload";
        } else if (statusCode == 401 || statusCode == 403) {
            status = "failed_server_api_auth";
        } else if (statusCode == 404) {
            status = "failed_transaction_not_found";
        } else if (statusCode == 429) {
            status = "failed_rate_limited";
            retryable = true;
        } else if (ex.getStatusCode().is5xxServerError()) {
            status = "failed_apple_service_unavailable";
            retryable = true;
        } else {
            status = "failed_consumption_reply";
        }
        return new ConsumptionSendResult(
            status,
            true,
            statusCode,
            retryable,
            "App Store consumption information reply failed with HTTP " + statusCode + ".",
            diagnostics
        );
    }

    private SelectedLastTransaction selectLastTransaction(JsonNode body) {
        List<SelectedLastTransaction> candidates = new ArrayList<>();
        for (JsonNode group : body.path("data")) {
            for (JsonNode lastTransaction : group.path("lastTransactions")) {
                String signedTransactionInfo = text(lastTransaction, "signedTransactionInfo");
                if (!hasText(signedTransactionInfo)) {
                    continue;
                }
                try {
                    AppStoreJwsVerificationService.TransactionClaims claims = verifiedTransactionClaims(signedTransactionInfo).claims();
                    candidates.add(new SelectedLastTransaction(
                        text(lastTransaction, "status"),
                        signedTransactionInfo,
                        text(lastTransaction, "signedRenewalInfo"),
                        claims
                    ));
                } catch (Exception ignored) {
                }
            }
        }
        return candidates.stream()
            .max(Comparator.comparing((SelectedLastTransaction item) -> item.claims().expiresDate() == null ? OffsetDateTime.MIN : item.claims().expiresDate())
                .thenComparing(item -> item.claims().purchaseDate() == null ? OffsetDateTime.MIN : item.claims().purchaseDate()))
            .orElse(null);
    }

    private Map<String, String> prefixDiagnostics(String prefix, Map<String, String> diagnostics) {
        Map<String, String> prefixed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : diagnostics.entrySet()) {
            prefixed.put(prefix + entry.getKey(), entry.getValue());
        }
        return prefixed;
    }

    private Map<String, String> baseDiagnostics(AppStoreConfiguration configuration, String environment, String lookupMode) {
        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("lookupMode", lookupMode);
        diagnostics.put("lookupEnvironment", environment);
        diagnostics.put("bundleId", configuration.bundleId());
        diagnostics.put("appAppleId", configuration.appAppleId());
        return diagnostics;
    }

    private List<String> resolveCandidateEnvironments(String requestedEnvironment, AppStoreConfiguration configuration) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String normalizedRequested = normalizeEnvironment(requestedEnvironment);
        String configuredEnvironment = normalizeEnvironment(configuration.environment());
        if (normalizedRequested != null) {
            if (!"sandbox".equals(normalizedRequested) || isSandboxAllowed(configuration) || "sandbox".equals(configuredEnvironment)) {
                values.add(normalizedRequested);
            }
            return List.copyOf(values);
        }
        if (configuredEnvironment != null) {
            values.add(configuredEnvironment);
        }
        if (isSandboxAllowed(configuration)) {
            values.add("sandbox");
        }
        return List.copyOf(values);
    }

    private boolean isSandboxAllowed(AppStoreConfiguration configuration) {
        return Boolean.TRUE.equals(configuration.allowSandbox()) || "sandbox".equalsIgnoreCase(configuration.environment());
    }

    private String baseUrl(String environment) {
        return "sandbox".equalsIgnoreCase(environment) ? SANDBOX_BASE_URL : PRODUCTION_BASE_URL;
    }

    private OffsetDateTime time(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            long raw = node.asLong();
            java.time.Instant instant = raw > 9_999_999_999L ? java.time.Instant.ofEpochMilli(raw) : java.time.Instant.ofEpochSecond(raw);
            return OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
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

    private String normalizeEnvironment(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record SelectedLastTransaction(
        String status,
        String signedTransactionInfo,
        String signedRenewalInfo,
        AppStoreJwsVerificationService.TransactionClaims claims
    ) {
    }

    private record TransactionClaimsWithDiagnostics(
        AppStoreJwsVerificationService.TransactionClaims claims,
        Map<String, String> diagnostics
    ) {
    }
}
