package com.apphub.backend.sys.auth.service;

import com.apphub.backend.shared.apple.AppleJwtTokenFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证领域的可集成实现 `ReadyForIntegrationAppleAuthorizationCodeExchangeClient`。
 * 用于接入真实外部依赖或正式流程，避免在占位实现与生产实现之间混淆职责。
 */

@Service
public class ReadyForIntegrationAppleAuthorizationCodeExchangeClient implements AppleAuthorizationCodeExchangeClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(8);

    private final RestOperations restOperations;
    private final AppleJwtTokenFactory appleJwtTokenFactory;
    private final ObjectMapper objectMapper;

    @Autowired
    public ReadyForIntegrationAppleAuthorizationCodeExchangeClient(
        RestTemplateBuilder restTemplateBuilder,
        AppleJwtTokenFactory appleJwtTokenFactory,
        ObjectMapper objectMapper
    ) {
        this(
            restTemplateBuilder
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .build(),
            appleJwtTokenFactory,
            objectMapper
        );
    }

    ReadyForIntegrationAppleAuthorizationCodeExchangeClient(
        RestOperations restOperations,
        AppleJwtTokenFactory appleJwtTokenFactory,
        ObjectMapper objectMapper
    ) {
        this.restOperations = restOperations;
        this.appleJwtTokenFactory = appleJwtTokenFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExchangeResult exchange(ExchangeCommand command, AppleAuthConfiguration configuration) {
        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("tokenEndpoint", configuration.tokenEndpoint());
        diagnostics.put("environment", configuration.environment());
        diagnostics.put("remoteExchangeEnabled", String.valueOf(Boolean.TRUE.equals(configuration.remoteExchangeEnabled())));

        String redirectUri = normalize(command.redirectUri()) != null ? normalize(command.redirectUri()) : normalize(configuration.redirectUri());
        diagnostics.put("redirectUri", redirectUri);

        if (!configuration.isReadyForExchange()) {
            return new ExchangeResult(
                "not_configured",
                false,
                "Apple authorization-code exchange credentials are incomplete. Fill clientId / teamId / keyId / privateKey / redirectUri before formal Sign in with Apple can be enabled.",
                null,
                null,
                null,
                null,
                diagnostics
            );
        }
        if (!Boolean.TRUE.equals(configuration.remoteExchangeEnabled())) {
            return new ExchangeResult(
                "remote_exchange_disabled",
                false,
                "Apple authorization-code exchange is configured, but remote /auth/token exchange is explicitly disabled for this runtime.",
                null,
                null,
                null,
                null,
                diagnostics
            );
        }
        if (redirectUri == null) {
            return new ExchangeResult(
                "rejected_missing_redirect_uri",
                false,
                "The Apple exchange request is missing redirectUri and the server configuration does not provide one.",
                null,
                null,
                null,
                null,
                diagnostics
            );
        }
        if (normalize(command.redirectUri()) != null && !redirectUri.equals(normalize(configuration.redirectUri()))) {
            return new ExchangeResult(
                "rejected_redirect_uri_mismatch",
                false,
                "The request redirectUri does not match the configured Apple redirectUri.",
                null,
                null,
                null,
                null,
                diagnostics
            );
        }

        String clientSecret = appleJwtTokenFactory.createSignInClientSecret(
            configuration.teamId(),
            configuration.clientId(),
            configuration.keyId(),
            configuration.privateKey(),
            normalize(configuration.audience()) != null ? configuration.audience() : "https://appleid.apple.com"
        );
        diagnostics.put("clientId", configuration.clientId());
        diagnostics.put("teamId", configuration.teamId());
        diagnostics.put("keyId", configuration.keyId());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", command.authorizationCode());
        body.add("client_id", configuration.clientId());
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<String> response = restOperations.postForEntity(
                configuration.tokenEndpoint(),
                new HttpEntity<>(body, headers),
                String.class
            );
            JsonNode json = response.getBody() == null || response.getBody().isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(response.getBody());
            String exchangedIdentityToken = text(json, "id_token");
            diagnostics.put("httpStatus", String.valueOf(response.getStatusCode().value()));
            diagnostics.put("scope", text(json, "scope"));
            diagnostics.put("expiresIn", text(json, "expires_in"));
            if (exchangedIdentityToken == null || exchangedIdentityToken.isBlank()) {
                return new ExchangeResult(
                    "failed_missing_id_token",
                    true,
                    "Apple /auth/token responded without id_token, so the server cannot establish a formal session.",
                    null,
                    text(json, "refresh_token"),
                    text(json, "access_token"),
                    text(json, "token_type"),
                    diagnostics
                );
            }
            return new ExchangeResult(
                "exchanged",
                true,
                "Apple authorization code exchanged successfully.",
                exchangedIdentityToken,
                text(json, "refresh_token"),
                text(json, "access_token"),
                text(json, "token_type"),
                diagnostics
            );
        } catch (HttpStatusCodeException ex) {
            return errorResult(ex, diagnostics);
        } catch (Exception ex) {
            diagnostics.put("transportException", ex.getClass().getSimpleName());
            return new ExchangeResult(
                "failed_transport_error",
                true,
                "Apple /auth/token exchange failed before a valid response could be parsed.",
                null,
                null,
                null,
                null,
                diagnostics
            );
        }
    }

    private ExchangeResult errorResult(HttpStatusCodeException ex, Map<String, String> diagnostics) {
        diagnostics.put("httpStatus", String.valueOf(ex.getStatusCode().value()));
        try {
            JsonNode errorBody = ex.getResponseBodyAsString() == null || ex.getResponseBodyAsString().isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(ex.getResponseBodyAsString());
            String error = text(errorBody, "error");
            String description = text(errorBody, "error_description");
            diagnostics.put("appleError", error);
            diagnostics.put("appleErrorDescription", description);
            return new ExchangeResult(
                mapErrorStatus(error, description, ex.getStatusCode().value()),
                true,
                description != null && !description.isBlank() ? description : "Apple /auth/token rejected the authorization-code exchange.",
                null,
                null,
                null,
                null,
                diagnostics
            );
        } catch (Exception parseException) {
            diagnostics.put("rawErrorBody", ex.getResponseBodyAsString());
            return new ExchangeResult(
                "failed_unparseable_error_response",
                true,
                "Apple /auth/token returned an error response that could not be parsed.",
                null,
                null,
                null,
                null,
                diagnostics
            );
        }
    }

    private String mapErrorStatus(String error, String description, int httpStatus) {
        if ("invalid_grant".equalsIgnoreCase(error)) {
            return "rejected_invalid_grant";
        }
        if ("invalid_client".equalsIgnoreCase(error)) {
            return "failed_invalid_client";
        }
        String detail = description == null ? "" : description.toLowerCase();
        if (detail.contains("redirect") && detail.contains("uri")) {
            return "rejected_redirect_uri_mismatch";
        }
        if (httpStatus == 429) {
            return "failed_rate_limited";
        }
        if (httpStatus >= 500) {
            return "failed_apple_service_unavailable";
        }
        return "failed_remote_exchange";
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
