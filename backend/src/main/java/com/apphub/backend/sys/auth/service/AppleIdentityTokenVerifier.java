package com.apphub.backend.sys.auth.service;

import java.util.Map;

/**
 * 认证服务 `AppleIdentityTokenVerifier`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

public interface AppleIdentityTokenVerifier {

    VerificationResult verify(String identityToken, VerificationCommand command);

    record VerificationCommand(
        String expectedAudience,
        String expectedNonce,
        String expectedIssuer,
        boolean verificationConfigured
    ) {
    }

    record VerificationResult(
        String status,
        String note,
        AppleIdentityTokenDecoder.DecodedAppleIdentityToken decoded,
        Map<String, String> diagnostics
    ) {
        public boolean allowsSessionIssue() {
            return "verified".equalsIgnoreCase(status);
        }
    }
}
