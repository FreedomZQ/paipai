package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;


/**
 * 拍拍伴读周报兼容控制器。
 * 周报属于付费权益内容，家庭范围和历史范围必须由后端检查 entitlement 后返回。
 */
@Tag(name = "拍拍伴读周报", description = "拍拍伴读当前周报和历史周报接口。")
@RestController
@RequestMapping("/api/v1/reports/weekly")
public class ReadingWeeklyReportCompatController {
    private final ReadingAuthenticatedUserResolver userResolver;
    private final ReadingCompatService readingCompatService;

    public ReadingWeeklyReportCompatController(ReadingAuthenticatedUserResolver userResolver, ReadingCompatService readingCompatService) {
        this.userResolver = userResolver;
        this.readingCompatService = readingCompatService;
    }

    @Operation(summary = "查询当前周报", description = "查询当前孩子或家庭范围的本周陪读回顾。")
    @GetMapping("/current")
    public ApiResponse<ReadingCompatService.WeeklyParentReportView> current(
        @Parameter(description = "孩子档案 ID；为空时使用当前孩子或默认范围。示例：child-a", example = "child-a") @RequestParam(required = false) String childId,
        @Parameter(description = "周报范围，child 表示单孩子，family 表示家庭范围。示例：child", example = "child") @RequestParam(defaultValue = "child") String scope,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.weeklyReport(user, childId, scope));
    }

    @Operation(summary = "查询历史周报", description = "查询当前账号可查看的历史周报列表。")
    @GetMapping("/history")
    public ApiResponse<ReadingCompatService.WeeklyReportHistoryView> history(
        @Parameter(description = "孩子档案 ID；为空时使用当前孩子或默认范围。示例：child-a", example = "child-a") @RequestParam(required = false) String childId,
        @Parameter(description = "周报范围，child 表示单孩子，family 表示家庭范围。示例：child", example = "child") @RequestParam(defaultValue = "child") String scope,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.weeklyHistory(user, childId, scope));
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
