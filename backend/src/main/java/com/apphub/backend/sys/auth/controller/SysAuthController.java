package com.apphub.backend.sys.auth.controller;

import com.apphub.backend.apps.common.AppCompatControllerSupport;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.auth.model.AppleExchangePreviewView;
import com.apphub.backend.sys.auth.model.AppleExchangeRequest;
import com.apphub.backend.sys.auth.model.AppleRevokeResultView;
import com.apphub.backend.sys.auth.model.AppleSessionRefreshView;
import com.apphub.backend.sys.auth.model.AuthenticatedSessionView;
import com.apphub.backend.sys.auth.model.DemoSessionCreateRequest;
import com.apphub.backend.sys.auth.model.DemoSessionCreatedView;
import com.apphub.backend.sys.auth.model.LogoutResultView;
import com.apphub.backend.sys.auth.service.PublicAuthAccessPolicyService;
import com.apphub.backend.sys.auth.service.SysAppleAuthService;
import com.apphub.backend.sys.auth.service.SysAuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 认证控制器 `SysAuthController`。
 * 负责暴露统一后端的 HTTP 接口，并将请求委派给对应服务层处理。
 */

@Tag(name = "系统认证", description = "统一认证会话、Apple 登录、刷新与撤销接口。")
@RestController
@RequestMapping("/api/v1/system/auth")
public class SysAuthController {

    private final SysAuthSessionService sysAuthSessionService;
    private final SysAppleAuthService sysAppleAuthService;
    private final PublicAuthAccessPolicyService publicAuthAccessPolicyService;
    private final AppCompatControllerSupport appCompatControllerSupport;

    @Value("${backend.environment:dev}")
    private String backendEnvironment;

    public SysAuthController(
        SysAuthSessionService sysAuthSessionService,
        SysAppleAuthService sysAppleAuthService,
        PublicAuthAccessPolicyService publicAuthAccessPolicyService,
        AppCompatControllerSupport appCompatControllerSupport
    ) {
        this.sysAuthSessionService = sysAuthSessionService;
        this.sysAppleAuthService = sysAppleAuthService;
        this.publicAuthAccessPolicyService = publicAuthAccessPolicyService;
        this.appCompatControllerSupport = appCompatControllerSupport;
    }

    @Operation(summary = "创建 Demo 会话", description = "为客户端创建后端 demo 会话，返回 bearer token 和账号上下文。")
    @PostMapping("/apps/{appCode}/sessions/demo")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DemoSessionCreatedView> createDemoSession(
        @Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode,
        @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = false) @RequestBody(required = false) DemoSessionCreateRequest request
    ) {
        var appDefinition = appCompatControllerSupport.requireAppDefinition(appCode);
        if (!publicAuthAccessPolicyService.demoSessionsEnabled(appDefinition)) {
            throw new DemoSessionDisabledException();
        }
        DemoSessionCreatedView response = sysAuthSessionService.createDemoSession(
            appCode,
            request == null ? DemoSessionCreateRequest.empty() : request
        );
        return ApiResponse.success(currentRequestId(), response);
    }

    @Operation(summary = "创建本地开发会话", description = "仅用于本机模拟器联调；生产环境禁用。")
    @PostMapping("/apps/{appCode}/sessions/dev")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DemoSessionCreatedView> createDevSession(
        @Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode,
        @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "本地开发会话请求体。", required = false) @RequestBody(required = false) DemoSessionCreateRequest request
    ) {
        if (!localDevelopmentAuthEnabled()) {
            throw new DemoSessionDisabledException();
        }
        appCompatControllerSupport.requireAppDefinition(appCode);
        DemoSessionCreatedView response = sysAuthSessionService.createDemoSession(
            appCode,
            request == null ? new DemoSessionCreateRequest(null, "ios-simulator", null, "模拟器开发账号") : request
        );
        return ApiResponse.success(currentRequestId(), response);
    }

    @Operation(summary = "Apple 登录换取会话", description = "校验 Apple identity token / authorization code，并在通过后签发正式会话。")
    @PostMapping("/apps/{appCode}/apple/exchange")
    public ApiResponse<AppleExchangePreviewView> exchangeApple(
        @Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode,
        @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody AppleExchangeRequest request
    ) {
        var appDefinition = appCompatControllerSupport.requireAppDefinition(appCode);
        AppleExchangePreviewView response = sysAppleAuthService.exchange(appDefinition, request);
        return ApiResponse.success(currentRequestId(), response);
    }

    @Operation(summary = "撤销 Apple 授权", description = "撤销当前会话关联的 Apple provider token，并同步撤销本地会话。")
    @PostMapping("/apps/{appCode}/apple/revoke")
    public ApiResponse<AppleRevokeResultView> revokeApple(
        @Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        var appDefinition = appCompatControllerSupport.requireAppDefinition(appCode);
        String sessionToken = appCompatControllerSupport.requireSessionToken(request);
        AuthenticatedSessionView currentSession = appCompatControllerSupport.requireCurrentSession(sessionToken);
        appCompatControllerSupport.ensureSessionBelongsToApp(appCode, currentSession.appCode());
        AppleRevokeResultView response = sysAppleAuthService.revoke(appDefinition, sessionToken)
            .orElseThrow(UnauthorizedException::new);
        return ApiResponse.success(currentRequestId(), response);
    }

    @Operation(summary = "刷新 Apple 会话", description = "使用已保存的 Apple provider token 刷新并签发新会话。")
    @PostMapping("/apps/{appCode}/apple/refresh")
    public ApiResponse<AppleSessionRefreshView> refreshApple(
        @Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        var appDefinition = appCompatControllerSupport.requireAppDefinition(appCode);
        String sessionToken = appCompatControllerSupport.requireSessionToken(request);
        AuthenticatedSessionView currentSession = appCompatControllerSupport.requireCurrentSession(sessionToken);
        appCompatControllerSupport.ensureSessionBelongsToApp(appCode, currentSession.appCode());
        AppleSessionRefreshView response = sysAppleAuthService.refresh(appDefinition, sessionToken)
            .orElseThrow(UnauthorizedException::new);
        return ApiResponse.success(currentRequestId(), response);
    }

    @Operation(summary = "查询当前会话", description = "根据 bearer token 查询当前账号、会话和权益上下文。")
    @GetMapping("/me")
    public ApiResponse<AuthenticatedSessionView> me(@Parameter(hidden = true) HttpServletRequest request) {
        String sessionToken = appCompatControllerSupport.requireSessionToken(request);
        AuthenticatedSessionView response = appCompatControllerSupport.requireCurrentSession(sessionToken);
        return ApiResponse.success(currentRequestId(), response);
    }

    @Operation(summary = "查询指定应用的当前会话", description = "根据 bearer token 查询当前账号、会话和权益上下文，并校验会话确实属于 path 中的 appCode。")
    @GetMapping("/apps/{appCode}/me")
    public ApiResponse<AuthenticatedSessionView> meForApp(
        @Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        AuthenticatedSessionView response = appCompatControllerSupport.requireCurrentSessionForApp(appCode, request);
        return ApiResponse.success(currentRequestId(), response);
    }

    @Operation(summary = "退出登录", description = "撤销当前 bearer 会话。")
    @PostMapping("/logout")
    public ApiResponse<LogoutResultView> logout(@Parameter(hidden = true) HttpServletRequest request) {
        String sessionToken = appCompatControllerSupport.requireSessionToken(request);
        LogoutResultView response = sysAuthSessionService.logout(sessionToken)
            .orElseThrow(UnauthorizedException::new);
        return ApiResponse.success(currentRequestId(), response);
    }

    @Operation(summary = "退出指定应用登录", description = "仅当当前 bearer 会话属于 path 中的 appCode 时才撤销会话，防止客户端误把别的应用会话打到当前应用路由。")
    @PostMapping("/apps/{appCode}/logout")
    public ApiResponse<LogoutResultView> logoutForApp(
        @Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        AuthenticatedSessionView currentSession = appCompatControllerSupport.requireCurrentSessionForApp(appCode, request);
        String sessionToken = appCompatControllerSupport.requireSessionToken(request);
        appCompatControllerSupport.ensureSessionBelongsToApp(appCode, currentSession.appCode());
        LogoutResultView response = sysAuthSessionService.logout(sessionToken)
            .orElseThrow(UnauthorizedException::new);
        return ApiResponse.success(currentRequestId(), response);
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static class UnauthorizedException extends RuntimeException {
        private UnauthorizedException() {
            super("Unauthorized");
        }
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    private static class DemoSessionDisabledException extends RuntimeException {
        private DemoSessionDisabledException() {
            super("Demo sessions are disabled for this app");
        }
    }

    private boolean localDevelopmentAuthEnabled() {
        return backendEnvironment == null
            || backendEnvironment.isBlank()
            || "dev".equalsIgnoreCase(backendEnvironment)
            || "local".equalsIgnoreCase(backendEnvironment)
            || "test".equalsIgnoreCase(backendEnvironment);
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
