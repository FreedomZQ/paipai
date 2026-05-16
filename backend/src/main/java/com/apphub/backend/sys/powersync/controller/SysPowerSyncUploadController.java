package com.apphub.backend.sys.powersync.controller;

import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.powersync.model.PowerSyncUploadEnvelope;
import com.apphub.backend.sys.powersync.model.PowerSyncUploadResult;
import com.apphub.backend.sys.powersync.service.SysPowerSyncUploadService;
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
@Tag(name = "PowerSync 上传", description = "多 App PowerSync 客户端变更上传接口。")
public class SysPowerSyncUploadController {
    private final SysPowerSyncUploadService uploadService;

    public SysPowerSyncUploadController(SysPowerSyncUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Operation(summary = "上传 PowerSync 变更", description = "客户端上传本地变更批次，后端按 appCode 路由到对应 App 同步适配器。")
    @PostMapping("/upload")
    public ApiResponse<PowerSyncUploadResult> upload(
        @Parameter(description = "应用编码。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "上传变更信封。示例：changes[0].table=reading_child_profile。", required = true) @Valid @RequestBody PowerSyncUploadEnvelope body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        return ApiResponse.success(currentRequestId(), uploadService.upload(appCode, body, request, currentRequestId()));
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
