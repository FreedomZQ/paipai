package com.apphub.backend.sys.configcenter.controller;

import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;


/**
 * 配置中心控制器 `SysConfigCenterController`。
 * 负责暴露统一后端的 HTTP 接口，并将请求委派给对应服务层处理。
 */

@Tag(name = "系统配置中心", description = "统一远程配置查询接口。")
@RestController
@RequestMapping("/api/v1/system/config")
public class SysConfigCenterController {

    private final AppDefinitionService appDefinitionService;
    private final SysRemoteConfigService sysRemoteConfigService;

    public SysConfigCenterController(AppDefinitionService appDefinitionService, SysRemoteConfigService sysRemoteConfigService) {
        this.appDefinitionService = appDefinitionService;
        this.sysRemoteConfigService = sysRemoteConfigService;
    }

    @Operation(summary = "查询远程配置命名空间", description = "按应用编码和命名空间读取 active 远程配置。")
    @GetMapping("/{appCode}/{namespaceCode}")
    public ApiResponse<RemoteConfigNamespaceView> namespace(
        @Parameter(description = "应用编码，例如 paipai_readingcompanion 或 saving。示例：saving", example = "saving") @PathVariable String appCode,
        @Parameter(description = "远程配置命名空间编码。示例：release_ios", example = "release_ios") @PathVariable String namespaceCode
    ) {
        appDefinitionService.get(appCode).orElseThrow(() -> new AppNotFoundException(appCode));
        return ApiResponse.success(currentRequestId(), sysRemoteConfigService.loadNamespace(appCode, namespaceCode));
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
