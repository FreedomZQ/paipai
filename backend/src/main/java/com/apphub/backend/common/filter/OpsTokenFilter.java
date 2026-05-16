package com.apphub.backend.common.filter;

import com.apphub.backend.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * `OpsTokenFilter` 过滤器。
 * 用于在请求进入业务逻辑前统一处理链路追踪、访问控制或其他横切逻辑。
 */

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class OpsTokenFilter extends OncePerRequestFilter {

    public static final String OPS_TOKEN_HEADER = "X-Ops-Token";

    private final ObjectMapper objectMapper;
    private final String configuredOpsToken;
    private final String environment;

    public OpsTokenFilter(
        ObjectMapper objectMapper,
        @Value("${backend.ops.token:${BACKEND_OPS_TOKEN:}}") String configuredOpsToken,
        @Value("${backend.environment:${BACKEND_ENV:dev}}") String environment
    ) {
        this.objectMapper = objectMapper;
        this.configuredOpsToken = configuredOpsToken;
        this.environment = environment;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/system/")) {
            return true;
        }
        // App-facing authentication endpoints live under /api/v1/system/auth for the
        // multi-app namespace, but they are guarded by their own auth/session policy.
        // Requiring the ops token here would make normal iOS sign-in and /me checks
        // impossible whenever BACKEND_OPS_TOKEN is configured.
        if (path.startsWith("/api/v1/system/auth/")) {
            return true;
        }
        return path.startsWith("/api/v1/system/healthz");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (configuredOpsToken == null || configuredOpsToken.isBlank()) {
            if ("prod".equalsIgnoreCase(environment)) {
                writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Ops token is not configured");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        String presentedToken = request.getHeader(OPS_TOKEN_HEADER);
        if (presentedToken != null && MessageDigest.isEqual(
            configuredOpsToken.trim().getBytes(StandardCharsets.UTF_8),
            presentedToken.trim().getBytes(StandardCharsets.UTF_8)
        )) {
            filterChain.doFilter(request, response);
            return;
        }

        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Ops token required");
    }

    private void writeError(HttpServletResponse response, int httpStatus, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        response.getWriter().write(objectMapper.writeValueAsString(new ApiResponse<>(
            false,
            requestId == null ? "unknown" : requestId,
            null,
            message
        )));
    }
}
