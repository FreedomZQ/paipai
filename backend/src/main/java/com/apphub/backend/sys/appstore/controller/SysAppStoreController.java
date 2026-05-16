package com.apphub.backend.sys.appstore.controller;

import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationAcceptedView;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationIngestRequest;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationObservabilityView;
import com.apphub.backend.sys.appstore.service.SysAppStoreNotificationService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
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
 * App Store控制器 `SysAppStoreController`。
 * 负责暴露统一后端的 HTTP 接口，并将请求委派给对应服务层处理。
 */

@Tag(name = "系统 App Store", description = "统一 App Store 通知接收与观测接口。")
@RestController
@RequestMapping("/api/v1/system/appstore")
public class SysAppStoreController {

    private final AppDefinitionService appDefinitionService;
    private final SysAppStoreNotificationService sysAppStoreNotificationService;

    public SysAppStoreController(
        AppDefinitionService appDefinitionService,
        SysAppStoreNotificationService sysAppStoreNotificationService
    ) {
        this.appDefinitionService = appDefinitionService;
        this.sysAppStoreNotificationService = sysAppStoreNotificationService;
    }

    @Operation(summary = "接收 App Store 通知", description = "接收 App Store Server Notification，并交由统一通知服务验签和去重处理。")
    @PostMapping("/apps/{appCode}/notifications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<AppStoreNotificationAcceptedView> notifications(
        @Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode,
        @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody AppStoreNotificationIngestRequest request
    ) {
        ensureAppExists(appCode);
        return ApiResponse.success(currentRequestId(), sysAppStoreNotificationService.ingest(appCode, request));
    }

    @Operation(summary = "查询 App Store 通知观测", description = "查询指定应用 App Store 通知处理和去重观测信息。")
    @GetMapping("/apps/{appCode}/notifications/observability")
    public ApiResponse<AppStoreNotificationObservabilityView> notificationObservability(@Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode) {
        ensureAppExists(appCode);
        return ApiResponse.success(currentRequestId(), sysAppStoreNotificationService.describeObservability(appCode));
    }

    private void ensureAppExists(String appCode) {
        appDefinitionService.get(appCode).orElseThrow(() -> new AppNotFoundException(appCode));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class AppNotFoundException extends RuntimeException {
        private AppNotFoundException(String appCode) {
            super("App definition not found: " + appCode);
        }
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
