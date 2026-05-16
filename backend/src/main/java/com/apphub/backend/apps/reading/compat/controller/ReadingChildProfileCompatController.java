package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;


/**
 * 拍拍伴读孩子档案兼容控制器。
 * 孩子数量属于付费权益边界，创建和更新必须由后端校验后才能生效。
 */
@Tag(name = "拍拍伴读孩子档案", description = "拍拍伴读孩子档案查询、创建和更新接口。")
@RestController
@RequestMapping("/api/v1/children")
public class ReadingChildProfileCompatController {
    private final ReadingAuthenticatedUserResolver userResolver;
    private final ReadingCompatService readingCompatService;

    public ReadingChildProfileCompatController(ReadingAuthenticatedUserResolver userResolver, ReadingCompatService readingCompatService) {
        this.userResolver = userResolver;
        this.readingCompatService = readingCompatService;
    }

    @Operation(summary = "查询孩子档案", description = "查询当前账号下的活跃孩子档案列表。")
    @GetMapping
    public ApiResponse<List<ReadingCompatService.ChildView>> children(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.children(user));
    }

    @Operation(summary = "创建资源", description = "创建当前接口对应的业务资源。")
    @PostMapping
    public ApiResponse<ReadingCompatService.CreateChildReceipt> create(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody ReadingCompatService.ChildMutationRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.createChild(user, body));
    }

    @Operation(summary = "更新资源", description = "更新当前接口对应的业务资源。")
    @PatchMapping("/{childId}")
    public ApiResponse<ReadingCompatService.CreateChildReceipt> update(
        @Parameter(description = "孩子档案 ID。示例：child-a", example = "child-a") @PathVariable String childId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody ReadingCompatService.ChildMutationRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.updateChild(user, childId, body));
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
