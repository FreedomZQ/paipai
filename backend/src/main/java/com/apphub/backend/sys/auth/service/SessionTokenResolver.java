package com.apphub.backend.sys.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 认证服务 `SessionTokenResolver`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Component
public class SessionTokenResolver {

    public static final String SESSION_TOKEN_HEADER = "X-Session-Token";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    public Optional<String> resolve(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                return Optional.of(token);
            }
        }

        String headerToken = request.getHeader(SESSION_TOKEN_HEADER);
        if (headerToken == null || headerToken.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(headerToken.trim());
    }
}
