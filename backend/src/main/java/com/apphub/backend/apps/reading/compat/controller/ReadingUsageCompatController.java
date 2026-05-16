package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingUsageService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usage")
@Tag(name = "拍拍伴读使用统计", description = "学习使用时长、孩子/家庭摘要和数据留存清理接口。")
public class ReadingUsageCompatController {
    private final ReadingAuthenticatedUserResolver userResolver;
    private final ReadingUsageService usageService;

    public ReadingUsageCompatController(ReadingAuthenticatedUserResolver userResolver, ReadingUsageService usageService) {
        this.userResolver = userResolver;
        this.usageService = usageService;
    }

    @Operation(summary = "开始使用会话", description = "记录孩子一次学习/复习会话开始时间；sessionUuid 用于客户端幂等。")
    @PostMapping("/session/start")
    public ApiResponse<ReadingUsageService.UsageSessionStartReceipt> start(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "会话开始请求体。示例：childId=child-a，sessionUuid=550e8400-e29b-41d4-a716-446655440000。", required = true) @RequestBody ReadingUsageService.UsageSessionStartRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), usageService.startSession(user, body));
    }

    @Operation(summary = "结束使用会话", description = "记录学习/复习会话结束时间并计算时长。")
    @PostMapping("/session/end")
    public ApiResponse<ReadingUsageService.UsageSessionEndReceipt> end(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "会话结束请求体。示例：sessionUuid=550e8400-e29b-41d4-a716-446655440000，endedAt=2026-04-28T09:30:00Z。", required = true) @RequestBody ReadingUsageService.UsageSessionEndRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), usageService.endSession(user, body));
    }

    @Operation(summary = "查询孩子使用摘要", description = "查询指定孩子今天、最近和累计使用时长摘要。")
    @GetMapping("/children/{childId}/summary")
    public ApiResponse<ReadingUsageService.ChildUsageSummaryView> childSummary(
        @Parameter(description = "孩子档案 ID。示例：child-a", example = "child-a") @PathVariable String childId,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), usageService.childSummary(user, childId));
    }

    @Operation(summary = "查询家庭使用摘要", description = "聚合当前账号下所有孩子的学习使用时长摘要。")
    @GetMapping("/family/summary")
    public ApiResponse<ReadingUsageService.FamilyUsageSummaryView> familySummary(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), usageService.familySummary(user));
    }

    @Operation(summary = "清理过期使用数据", description = "按当前数据留存策略清理该用户过期使用统计数据。")
    @PostMapping("/retention/cleanup")
    public ApiResponse<ReadingUsageService.UsageRetentionCleanupReceipt> cleanupRetention(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), usageService.cleanupRetentionForUser(user));
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
