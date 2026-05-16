package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;


/**
 * 拍拍伴读句卡和复习事件兼容控制器。
 * 句卡保存与复习完成都必须经过后端鉴权，避免客户端本地伪造付费云同步和周报源数据。
 */
@Tag(name = "拍拍伴读句卡复习", description = "拍拍伴读句卡查询、保存和复习事件接口。")
@RestController
@RequestMapping("/api/v1")
public class ReadingReviewCompatController {
    private final ReadingAuthenticatedUserResolver userResolver;
    private final ReadingCompatService readingCompatService;

    public ReadingReviewCompatController(ReadingAuthenticatedUserResolver userResolver, ReadingCompatService readingCompatService) {
        this.userResolver = userResolver;
        this.readingCompatService = readingCompatService;
    }

    @Operation(summary = "查询今日复习句卡", description = "查询当前账号今天应复习的句卡列表。")
    @GetMapping("/review-cards/today")
    public ApiResponse<List<ReadingCompatService.ReviewCardView>> today(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.todayReviewCards(user));
    }

    @Operation(summary = "创建资源", description = "创建当前接口对应的业务资源。")
    @PostMapping("/review-cards")
    public ApiResponse<ReadingCompatService.CreateReviewCardReceipt> create(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody ReadingCompatService.CreateReviewCardRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.createReviewCard(user, body));
    }

    @Operation(summary = "记录复习事件", description = "记录一次句卡复习结果，并更新熟练度和下次复习时间。")
    @PostMapping("/review-events")
    public ApiResponse<ReadingCompatService.ReviewEventReceipt> event(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody ReadingCompatService.ReviewEventRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.recordReviewEvent(user, body));
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
