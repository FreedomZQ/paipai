package com.apphub.backend.sys.billing.controller;

import com.apphub.backend.apps.common.AppCompatControllerSupport;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.auth.model.AuthenticatedSessionView;
import com.apphub.backend.sys.billing.model.EntitlementOverviewView;
import com.apphub.backend.sys.billing.model.EntitlementRefreshResultView;
import com.apphub.backend.sys.billing.model.PurchaseIntakeAcceptedView;
import com.apphub.backend.sys.billing.model.PurchaseRestoreAcceptedView;
import com.apphub.backend.sys.billing.model.PurchaseRestoreRequest;
import com.apphub.backend.sys.billing.model.PurchaseVerifyRequest;
import com.apphub.backend.sys.billing.service.SysBillingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 计费控制器 `SysBillingController`。
 * 负责暴露统一后端的 HTTP 接口，并将请求委派给对应服务层处理。
 */

@Tag(name = "系统计费", description = "统一权益查询、刷新、购买验证和恢复购买接口。")
@RestController
@RequestMapping("/api/v1/system/billing")
public class SysBillingController {

    private final AppCompatControllerSupport appCompatControllerSupport;
    private final SysBillingService sysBillingService;

    public SysBillingController(
        AppCompatControllerSupport appCompatControllerSupport,
        SysBillingService sysBillingService
    ) {
        this.appCompatControllerSupport = appCompatControllerSupport;
        this.sysBillingService = sysBillingService;
    }

    @Operation(summary = "查询权益", description = "查询当前用户在指定应用下的权益快照。")
    @GetMapping("/apps/{appCode}/entitlements")
    public ApiResponse<EntitlementOverviewView> entitlements(@Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode, @Parameter(hidden = true) HttpServletRequest request) {
        AuthenticatedSessionView session = requireSession(appCode, request);
        return ApiResponse.success(
            currentRequestId(),
            sysBillingService.getEntitlements(appCode, session.user().userId())
        );
    }

    @Operation(summary = "刷新权益", description = "刷新当前用户在指定应用下的权益投影。")
    @PostMapping("/apps/{appCode}/entitlements/refresh")
    public ApiResponse<EntitlementRefreshResultView> refreshEntitlements(@Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode, @Parameter(hidden = true) HttpServletRequest request) {
        AuthenticatedSessionView session = requireSession(appCode, request);
        return ApiResponse.success(
            currentRequestId(),
            sysBillingService.refreshEntitlements(appCode, session.user().userId())
        );
    }

    @Operation(summary = "验证购买交易", description = "接收交易签名信息并提交统一计费服务验证。")
    @PostMapping("/apps/{appCode}/purchases/verify")
    public ApiResponse<PurchaseIntakeAcceptedView> verify(
        @Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode,
        @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody PurchaseVerifyRequest request,
        @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        AuthenticatedSessionView session = requireSession(appCode, httpServletRequest);
        return ApiResponse.success(
            currentRequestId(),
            sysBillingService.verify(appCode, session.user().userId(), request)
        );
    }

    @Operation(summary = "恢复购买交易", description = "接收恢复购买交易列表并提交统一计费服务处理。")
    @PostMapping("/apps/{appCode}/purchases/restore")
    public ApiResponse<PurchaseRestoreAcceptedView> restore(
        @Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode,
        @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody PurchaseRestoreRequest request,
        @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        AuthenticatedSessionView session = requireSession(appCode, httpServletRequest);
        return ApiResponse.success(
            currentRequestId(),
            sysBillingService.restore(appCode, session.user().userId(), request)
        );
    }

    private AuthenticatedSessionView requireSession(String appCode, @Parameter(hidden = true) HttpServletRequest request) {
        try {
            return appCompatControllerSupport.requireCurrentSessionForApp(appCode, request);
        } catch (org.springframework.web.server.ResponseStatusException exception) {
            throw new UnauthorizedException();
        }
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static class UnauthorizedException extends RuntimeException {
        private UnauthorizedException() {
            super("Unauthorized");
        }
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
