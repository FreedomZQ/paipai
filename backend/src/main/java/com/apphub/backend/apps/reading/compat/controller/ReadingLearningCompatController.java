package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;


/**
 * 拍拍伴读每日任务兼容控制器。
 * 每日任务由后端按孩子档案、句卡和权益生成，客户端只提交完成动作。
 */
@Tag(name = "拍拍伴读每日任务", description = "拍拍伴读每日任务查询和完成回写接口。")
@RestController
@RequestMapping("/api/v1/learning")
public class ReadingLearningCompatController {
    private final ReadingAuthenticatedUserResolver userResolver;
    private final ReadingCompatService readingCompatService;

    public ReadingLearningCompatController(ReadingAuthenticatedUserResolver userResolver, ReadingCompatService readingCompatService) {
        this.userResolver = userResolver;
        this.readingCompatService = readingCompatService;
    }

    @Operation(summary = "查询每日任务", description = "按当前账号、孩子和权益生成或查询每日任务。")
    @GetMapping("/daily-task")
    public ApiResponse<ReadingCompatService.DailyLearningTaskFeedView> feed(
        @Parameter(description = "孩子档案 ID；为空时使用当前孩子或默认范围。示例：child-a", example = "child-a") @RequestParam(required = false) String childId,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.dailyTaskFeed(user, childId));
    }

    @Operation(summary = "完成每日任务", description = "提交每日任务完成动作，并返回最新任务统计。")
    @PostMapping("/daily-task/{taskId}/complete")
    public ApiResponse<ReadingCompatService.DailyLearningTaskCompletionView> complete(
        @Parameter(description = "每日任务 ID。示例：daily-reading-001", example = "daily-reading-001") @PathVariable String taskId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody ReadingCompatService.DailyTaskCompleteRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.completeDailyTask(user, taskId, body));
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
