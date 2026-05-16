package com.apphub.backend.sys.auth.service;

import java.util.Map;

/**
 * 认证服务 `AppleAuthorizationCodeExchangeClient`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

public interface AppleAuthorizationCodeExchangeClient {

    ExchangeResult exchange(ExchangeCommand command, AppleAuthConfiguration configuration);

    record ExchangeCommand(String authorizationCode, String redirectUri) {
    }

    record AppleAuthConfiguration(
        String clientId,
        String teamId,
        String keyId,
        String privateKey,
        String audience,
        String redirectUri,
        String environment,
        String tokenEndpoint,
        String jwksUrl,
        Boolean remoteExchangeEnabled
    ) {
        public boolean isReadyForExchange() {
            return hasText(clientId) && hasText(teamId) && hasText(keyId) && hasText(privateKey) && hasText(redirectUri);
        }

        public boolean isReadyForIdentityVerification() {
            return hasText(clientId) && hasText(jwksUrl) && hasText(audience);
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    record ExchangeResult(
        String status,
        boolean remoteExchangeAttempted,
        String note,
        String identityToken,
        String refreshToken,
        String accessToken,
        String tokenType,
        Map<String, String> diagnostics
    ) {
        public boolean isSuccessful() {
            return "exchanged".equalsIgnoreCase(status);
        }
    }
}
