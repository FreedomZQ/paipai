package com.apphub.backend.sys.auth.service;

import java.util.Map;

/**
 * 认证服务 `AppleTokenRefreshClient`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

public interface AppleTokenRefreshClient {

    RefreshResult refresh(RefreshCommand command, AppleAuthorizationCodeExchangeClient.AppleAuthConfiguration configuration);

    record RefreshCommand(String refreshToken) {
    }

    record RefreshResult(
        String status,
        boolean remoteRefreshAttempted,
        String note,
        String identityToken,
        String refreshToken,
        String accessToken,
        String tokenType,
        Map<String, String> diagnostics
    ) {
        public boolean isSuccessful() {
            return "refreshed".equalsIgnoreCase(status);
        }
    }
}
