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
 * 拍拍伴读云端朗读兼容控制器。
 * 设备自带朗读是默认方案；首发阶段云端朗读不接收儿童正文，不调用 provider。
 */
@Tag(name = "拍拍伴读云端朗读", description = "兼容旧客户端的云端 TTS 停用响应；当前版本使用设备端朗读。")
@RestController
@RequestMapping("/api/v1/tts")
public class ReadingTtsCompatController {
    private final ReadingAuthenticatedUserResolver userResolver;
    private final ReadingCompatService readingCompatService;

    public ReadingTtsCompatController(
        ReadingAuthenticatedUserResolver userResolver,
        ReadingCompatService readingCompatService
    ) {
        this.userResolver = userResolver;
        this.readingCompatService = readingCompatService;
    }

    @Operation(summary = "云端文本转语音停用响应", description = "保留旧客户端兼容入口。首发阶段不接收正文代理、不扣减云端次数、不调用 TTS provider。")
    @PostMapping("/speak")
    public ApiResponse<ReadingCompatService.CloudSpeechReceipt> speak(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody ReadingCompatService.CloudSpeechRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        ReadingCloudUsageService.CloudUsageDecision decision = new ReadingCloudUsageService.CloudUsageDecision(
            false,
            ReadingCloudUsageService.CLOUD_TTS,
            0,
            "云端朗读暂未开放",
            "当前版本仅使用设备端朗读。云端朗读未来将改为家长同意后的 capability / reservation 模式，业务后端不会保存儿童正文。",
            java.util.List.of("使用设备端朗读")
        );
        return ApiResponse.success(currentRequestId(), readingCompatService.buildCloudSpeechUnavailable(decision, body));
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
