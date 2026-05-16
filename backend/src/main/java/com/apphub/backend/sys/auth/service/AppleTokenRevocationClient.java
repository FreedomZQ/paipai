package com.apphub.backend.sys.auth.service;

import java.util.Map;

/**
 * 认证服务 `AppleTokenRevocationClient`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

public interface AppleTokenRevocationClient {

    RevocationResult revoke(RevokeCommand command, AppleRevokeConfiguration configuration);

    record RevokeCommand(String refreshToken) {
    }

    record AppleRevokeConfiguration(
        String clientId,
        String teamId,
        String keyId,
        String privateKey,
        String audience,
        String revokeEndpoint
    ) {
        public boolean isReadyForRevoke() {
            return hasText(clientId) && hasText(teamId) && hasText(keyId) && hasText(privateKey) && hasText(revokeEndpoint);
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    record RevocationResult(
        String status,
        boolean remoteRevokeAttempted,
        String note,
        Map<String, String> diagnostics
    ) {
        public boolean isSuccessful() {
            return "succeeded".equalsIgnoreCase(status);
        }
    }
}
