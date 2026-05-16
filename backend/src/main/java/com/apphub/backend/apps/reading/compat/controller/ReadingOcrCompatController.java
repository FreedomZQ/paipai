package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCloudUsageService;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;


/**
 * 拍拍伴读 OCR 兼容控制器。
 * 在线 OCR 已停止作为权益发放入口；客户端应使用设备端识别。
 */
@Tag(name = "拍拍伴读云端识图", description = "兼容旧客户端的云端 OCR 停用响应；当前版本使用设备端识别。")
@RestController
@RequestMapping("/api/v1/ocr")
public class ReadingOcrCompatController {
    private final ReadingAuthenticatedUserResolver userResolver;
    private final ReadingCompatService readingCompatService;

    public ReadingOcrCompatController(
        ReadingAuthenticatedUserResolver userResolver,
        ReadingCompatService readingCompatService
    ) {
        this.userResolver = userResolver;
        this.readingCompatService = readingCompatService;
    }

    @Operation(summary = "云端 OCR 停用响应", description = "保留旧客户端兼容入口，不再调用在线 OCR provider，不扣减或发放 OCR 权益。")
    @PostMapping("/extract")
    public ApiResponse<ReadingCompatService.OcrExtractReceipt> extract(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody ReadingCompatService.OcrExtractRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        ReadingCloudUsageService.CloudUsageDecision decision = new ReadingCloudUsageService.CloudUsageDecision(
            false,
            ReadingCloudUsageService.CLOUD_OCR,
            0,
            "云端识别已停用",
            "当前版本仅使用设备端识别，不再通过在线 OCR API 发放或消耗识别权益。",
            java.util.List.of("使用设备端识别")
        );
        return ApiResponse.success(currentRequestId(), readingCompatService.buildCloudOcrUnavailable(user, body, decision));
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
