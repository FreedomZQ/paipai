package com.apphub.backend.sys.powersync.controller;

import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.powersync.model.PowerSyncBootstrapRequest;
import com.apphub.backend.sys.powersync.model.PowerSyncBootstrapView;
import com.apphub.backend.sys.powersync.model.PowerSyncRebuildRequest;
import com.apphub.backend.sys.powersync.model.PowerSyncRebuildView;
import com.apphub.backend.sys.powersync.model.PowerSyncTokenRequest;
import com.apphub.backend.sys.powersync.model.PowerSyncTokenView;
import com.apphub.backend.sys.powersync.service.SysPowerSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/powersync/{appCode}")
@Tag(name = "PowerSync", description = "多 App PowerSync 启动、令牌和重建接口。")
public class SysPowerSyncController {
    private final SysPowerSyncService powerSyncService;

    public SysPowerSyncController(SysPowerSyncService powerSyncService) {
        this.powerSyncService = powerSyncService;
    }

    @Operation(summary = "初始化 PowerSync", description = "按 appCode 初始化当前安装的 PowerSync 同步上下文，返回同步配置和客户端状态。")
    @PostMapping("/bootstrap")
    public ApiResponse<PowerSyncBootstrapView> bootstrap(
        @Parameter(description = "应用编码。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "PowerSync 初始化请求体。示例：installationId=ios-install-001，clientPlatform=ios。", required = true) @Valid @RequestBody PowerSyncBootstrapRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        return ApiResponse.success(currentRequestId(), powerSyncService.bootstrap(appCode, body, request, currentRequestId()));
    }

    @Operation(summary = "签发 PowerSync 令牌", description = "为当前用户和安装签发 PowerSync 客户端同步令牌。")
    @PostMapping("/token")
    public ApiResponse<PowerSyncTokenView> token(
        @Parameter(description = "应用编码。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "令牌请求体。示例：installationId=ios-install-001。", required = true) @Valid @RequestBody PowerSyncTokenRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        return ApiResponse.success(currentRequestId(), powerSyncService.issueToken(appCode, body, request, currentRequestId()));
    }

    @Operation(summary = "请求重建同步状态", description = "客户端发现本地同步状态异常时请求服务端标记重建。")
    @PostMapping("/rebuild")
    public ApiResponse<PowerSyncRebuildView> rebuild(
        @Parameter(description = "应用编码。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "重建请求体。示例：installationId=ios-install-001，reason=client_reinstall。", required = true) @Valid @RequestBody PowerSyncRebuildRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        return ApiResponse.success(currentRequestId(), powerSyncService.requestRebuild(appCode, body, request, currentRequestId()));
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
