package com.apphub.backend.apps.saving.user.controller;

import com.apphub.backend.apps.saving.SavingAppModule;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.auth.model.DemoSessionCreateRequest;
import com.apphub.backend.sys.auth.model.DemoSessionCreatedView;
import com.apphub.backend.sys.auth.service.PublicAuthAccessPolicyService;
import com.apphub.backend.sys.auth.service.SysAuthSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;


import io.swagger.v3.oas.annotations.media.Schema;
/**
 * saving（省钱项目） 的 用户兼容控制器。
 * 用于保留旧版对外路由，并将请求适配到统一后端内核。
 */

@Tag(name = "省钱项目用户启动", description = "省钱项目用户启动和初始会话创建接口。")
@RestController
@RequestMapping("/v1/users")
public class SavingUserBootstrapCompatController {

    private static final String APP_CODE = SavingAppModule.APP_CODE;

    private final AppDefinitionService appDefinitionService;
    private final SysAuthSessionService sysAuthSessionService;
    private final PublicAuthAccessPolicyService publicAuthAccessPolicyService;

    public SavingUserBootstrapCompatController(
        AppDefinitionService appDefinitionService,
        SysAuthSessionService sysAuthSessionService,
        PublicAuthAccessPolicyService publicAuthAccessPolicyService
    ) {
        this.appDefinitionService = appDefinitionService;
        this.sysAuthSessionService = sysAuthSessionService;
        this.publicAuthAccessPolicyService = publicAuthAccessPolicyService;
    }

    @Operation(summary = "查询启动配置", description = "返回省钱项目客户端启动所需配置、语言和支持入口。")
    @PostMapping("/bootstrap")
    public ApiResponse<SavingBootstrapResponse> bootstrap(@Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody SavingBootstrapRequest request) {
        AppDefinition appDefinition = appDefinitionService.get(APP_CODE).orElseThrow();
        if (!publicAuthAccessPolicyService.bootstrapSessionsEnabled(appDefinition)) {
            throw new BootstrapDisabledException();
        }
        DemoSessionCreatedView created = sysAuthSessionService.createDemoSession(
            APP_CODE,
            new DemoSessionCreateRequest(request.deviceInstallIdHash(), request.platform(), request.appVersion(), null)
        );
        SavingBootstrapResponse response = new SavingBootstrapResponse(
            String.valueOf(created.user().userId()),
            created.sessionToken(),
            created.expiresAt() != null ? created.expiresAt().toString() : null,
            created.sessionToken(),
            OffsetDateTime.now().toString()
        );
        return ApiResponse.success(currentRequestId(), response);
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    private static class BootstrapDisabledException extends RuntimeException {
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }

    @Schema(description = "省钱项目用户启动请求体。")
    public record SavingBootstrapRequest(
        @Schema(description = "客户端平台，例如 ios。", example = "ios") @NotBlank String platform,
        @Schema(description = "客户端版本号。", example = "1.0.0") @NotBlank String appVersion,
        @Schema(description = "客户端语言。", example = "zh-Hans") @NotBlank String locale,
        @Schema(description = "客户端时区，例如 Asia/Shanghai。", example = "Asia/Shanghai") @NotBlank String timezone,
        @Schema(description = "设备安装标识哈希。", example = "sha256-installation-hash") String deviceInstallIdHash
    ) {
    }

    public record SavingBootstrapResponse(
        String userId,
        String authToken,
        String tokenExpiresAt,
        String appAccountToken,
        String serverTime
    ) {
    }
}
