package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
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
 * 拍拍伴读反馈兼容控制器。
 * 反馈可以匿名提交，但如果带 Bearer token 会自动关联账号，且不会接收图片、音频等高风险原始数据。
 */
@Tag(name = "拍拍伴读反馈", description = "拍拍伴读低敏问题反馈接口。")
@RestController
@RequestMapping("/api/v1/feedback")
public class ReadingFeedbackCompatController {
    private final ReadingAuthenticatedUserResolver userResolver;
    private final ReadingCompatService readingCompatService;

    public ReadingFeedbackCompatController(ReadingAuthenticatedUserResolver userResolver, ReadingCompatService readingCompatService) {
        this.userResolver = userResolver;
        this.readingCompatService = readingCompatService;
    }

    @Operation(summary = "提交反馈", description = "提交低敏问题反馈，可匿名或关联当前账号。")
    @PostMapping
    public ApiResponse<ReadingCompatService.FeedbackSubmissionReceipt> submit(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody ReadingCompatService.FeedbackSubmitRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = null;
        try {
            user = userResolver.require(request);
        } catch (RuntimeException ignored) {
            // 反馈允许匿名提交，避免用户因为登录态过期而无法联系支持。
        }
        return ApiResponse.success(currentRequestId(), readingCompatService.submitFeedback(user, body));
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
