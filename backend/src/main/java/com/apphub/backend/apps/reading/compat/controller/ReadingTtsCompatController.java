package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCloudUsageService;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.apps.reading.provider.ReadingBailianTtsProvider;
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
 * 设备自带朗读应作为默认方案；云端朗读仅在用户主动选择时才经过当前 App 后端次数校验和后续 provider 接入点。
 */
@Tag(name = "拍拍伴读云端朗读", description = "拍拍伴读云端文本转语音接口，受后端会话和云端次数控制。")
@RestController
@RequestMapping("/api/v1/tts")
public class ReadingTtsCompatController {
    private final ReadingAuthenticatedUserResolver userResolver;
    private final ReadingCompatService readingCompatService;
    private final ReadingCloudUsageService cloudUsageService;
    private final ReadingBailianTtsProvider bailianTtsProvider;

    public ReadingTtsCompatController(
        ReadingAuthenticatedUserResolver userResolver,
        ReadingCompatService readingCompatService,
        ReadingCloudUsageService cloudUsageService,
        ReadingBailianTtsProvider bailianTtsProvider
    ) {
        this.userResolver = userResolver;
        this.readingCompatService = readingCompatService;
        this.cloudUsageService = cloudUsageService;
        this.bailianTtsProvider = bailianTtsProvider;
    }

    @Operation(summary = "云端文本转语音", description = "用户主动选择云端服务时，校验次数后调用 TTS provider 合成音频。")
    @PostMapping("/speak")
    public ApiResponse<ReadingCompatService.CloudSpeechReceipt> speak(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody ReadingCompatService.CloudSpeechRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        ReadingCloudUsageService.CloudUsageDecision decision = cloudUsageService.ensureQuota(user.userId(), ReadingCloudUsageService.CLOUD_TTS);
        if (!decision.allowed()) {
            return ApiResponse.success(currentRequestId(), readingCompatService.buildCloudSpeechQuotaBlocked(decision, body));
        }
        var providerResult = bailianTtsProvider.synthesize(body.text(), body.languageCode(), body.rate());
        if (providerResult.success()) {
            decision = cloudUsageService.consume(user.userId(), ReadingCloudUsageService.CLOUD_TTS);
        }
        return ApiResponse.success(currentRequestId(), readingCompatService.buildCloudSpeechResult(providerResult, decision));
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
