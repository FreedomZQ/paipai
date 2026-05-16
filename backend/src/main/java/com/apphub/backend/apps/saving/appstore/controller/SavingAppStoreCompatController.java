package com.apphub.backend.apps.saving.appstore.controller;

import com.apphub.backend.apps.saving.SavingAppModule;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationAcceptedView;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationIngestRequest;
import com.apphub.backend.sys.appstore.service.SysAppStoreNotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;


import io.swagger.v3.oas.annotations.media.Schema;
/**
 * saving（省钱项目） 的 App Store兼容控制器。
 * 用于保留旧版对外路由，并将请求适配到统一后端内核。
 */

@Tag(name = "省钱项目 App Store 回调", description = "省钱项目 App Store Server Notification 兼容接收接口。")
@RestController
@RequestMapping("/v1/appstore")
@Validated
public class SavingAppStoreCompatController {

    private static final String APP_CODE = SavingAppModule.APP_CODE;

    private final AppDefinitionService appDefinitionService;
    private final SysAppStoreNotificationService sysAppStoreNotificationService;

    public SavingAppStoreCompatController(
        AppDefinitionService appDefinitionService,
        SysAppStoreNotificationService sysAppStoreNotificationService
    ) {
        this.appDefinitionService = appDefinitionService;
        this.sysAppStoreNotificationService = sysAppStoreNotificationService;
    }

    @Operation(summary = "接收 App Store 通知", description = "接收 App Store Server Notification，并交由统一通知服务验签和去重处理。")
    @PostMapping("/notifications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<AppStoreNotificationAcceptedView> notifications(@Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody SavingNotificationRequest request) {
        appDefinitionService.get(APP_CODE).orElseThrow(AppNotConfiguredException::new);
        AppStoreNotificationIngestRequest ingestRequest = new AppStoreNotificationIngestRequest(
            request.signedPayload(),
            null,
            null,
            null,
            null
        );
        return ApiResponse.success(currentRequestId(), sysAppStoreNotificationService.ingest(APP_CODE, ingestRequest));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class AppNotConfiguredException extends RuntimeException {
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }

    @Schema(description = "省钱项目 App Store 通知请求体。")
    public record SavingNotificationRequest(@Schema(description = "Apple signedPayload，必填。", example = "eyJhbGciOiJFUzI1NiIsIng1YyI6W10ifQ...") @NotBlank String signedPayload) {
    }
}
