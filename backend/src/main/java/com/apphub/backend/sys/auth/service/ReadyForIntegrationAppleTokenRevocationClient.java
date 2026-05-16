package com.apphub.backend.sys.auth.service;

import com.apphub.backend.shared.apple.AppleJwtTokenFactory;
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
 * 认证领域的可集成实现 `ReadyForIntegrationAppleTokenRevocationClient`。
 * 用于接入真实外部依赖或正式流程，避免在占位实现与生产实现之间混淆职责。
 */

@Service
public class ReadyForIntegrationAppleTokenRevocationClient implements AppleTokenRevocationClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(8);

    private final RestOperations restOperations;
    private final AppleJwtTokenFactory appleJwtTokenFactory;

    @Autowired
    public ReadyForIntegrationAppleTokenRevocationClient(
        RestTemplateBuilder restTemplateBuilder,
        AppleJwtTokenFactory appleJwtTokenFactory
    ) {
        this(
            restTemplateBuilder
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .build(),
            appleJwtTokenFactory
        );
    }

    ReadyForIntegrationAppleTokenRevocationClient(
        RestOperations restOperations,
        AppleJwtTokenFactory appleJwtTokenFactory
    ) {
        this.restOperations = restOperations;
        this.appleJwtTokenFactory = appleJwtTokenFactory;
    }

    @Override
    public RevocationResult revoke(RevokeCommand command, AppleRevokeConfiguration configuration) {
        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("revokeEndpoint", configuration.revokeEndpoint());

        if (!configuration.isReadyForRevoke()) {
            return new RevocationResult(
                "not_configured",
                false,
                "Apple revoke prerequisites are incomplete. Fill clientId / teamId / keyId / privateKey / revokeEndpoint before upstream revoke can be attempted.",
                diagnostics
            );
        }
        if (command == null || command.refreshToken() == null || command.refreshToken().isBlank()) {
            return new RevocationResult(
                "token_missing",
                false,
                "No Apple refresh token is stored for this user, so upstream Apple revoke cannot be attempted.",
                diagnostics
            );
        }

        String clientSecret = appleJwtTokenFactory.createSignInClientSecret(
            configuration.teamId(),
            configuration.clientId(),
            configuration.keyId(),
            configuration.privateKey(),
            configuration.audience() != null && !configuration.audience().isBlank() ? configuration.audience() : "https://appleid.apple.com"
        );

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", configuration.clientId());
        body.add("client_secret", clientSecret);
        body.add("token", command.refreshToken());
        body.add("token_type_hint", "refresh_token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<String> response = restOperations.postForEntity(
                configuration.revokeEndpoint(),
                new HttpEntity<>(body, headers),
                String.class
            );
            diagnostics.put("httpStatus", String.valueOf(response.getStatusCode().value()));
            return new RevocationResult(
                "succeeded",
                true,
                "Apple revoke endpoint returned HTTP " + response.getStatusCode().value() + ". Apple documents 200 as the response for both successful revocation and tokens that were already invalidated.",
                diagnostics
            );
        } catch (HttpStatusCodeException ex) {
            diagnostics.put("httpStatus", String.valueOf(ex.getStatusCode().value()));
            return new RevocationResult(
                mapErrorStatus(ex.getStatusCode().value()),
                true,
                "Apple revoke endpoint returned HTTP " + ex.getStatusCode().value() + ".",
                diagnostics
            );
        } catch (RuntimeException ex) {
            diagnostics.put("transportException", ex.getClass().getSimpleName());
            return new RevocationResult(
                "failed_transport_error",
                true,
                "Apple revoke endpoint could not be reached.",
                diagnostics
            );
        }
    }

    private String mapErrorStatus(int httpStatus) {
        if (httpStatus == 400) {
            return "failed_bad_request";
        }
        if (httpStatus == 401 || httpStatus == 403) {
            return "failed_invalid_client";
        }
        if (httpStatus == 429) {
            return "failed_rate_limited";
        }
        if (httpStatus >= 500) {
            return "failed_apple_service_unavailable";
        }
        return "failed_remote_revoke";
    }
}
