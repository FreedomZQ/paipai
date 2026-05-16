package com.apphub.backend.sys.compensation.controller;

import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.compensation.model.CompensationCodeCreateRequest;
import com.apphub.backend.sys.compensation.model.CompensationCodeVoidRequest;
import com.apphub.backend.sys.compensation.model.CompensationCodeView;
import com.apphub.backend.sys.compensation.service.SysCompensationService;
import com.apphub.backend.sys.auth.service.SessionTokenResolver;
import com.apphub.backend.sys.auth.service.SysAuthSessionService;
import com.apphub.backend.sys.auth.model.AuthenticatedSessionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Tag(name = "系统补偿码", description = "补偿码创建、查询、作废接口。")
@RestController
@RequestMapping("/api/v1/system/compensation-codes")
public class SysCompensationController {
    private final SysCompensationService compensationService;
    private final SessionTokenResolver sessionTokenResolver;
    private final SysAuthSessionService sysAuthSessionService;

    public SysCompensationController(
        SysCompensationService compensationService,
        SessionTokenResolver sessionTokenResolver,
        SysAuthSessionService sysAuthSessionService
    ) {
        this.compensationService = compensationService;
        this.sessionTokenResolver = sessionTokenResolver;
        this.sysAuthSessionService = sysAuthSessionService;
    }

    @Operation(summary = "创建补偿码", description = "后端生成或接收指定补偿码，并绑定权益规则。")
    @PostMapping("/apps/{appCode}")
    public ApiResponse<CompensationCodeView> create(
        @PathVariable String appCode,
        @Valid @RequestBody CompensationCodeCreateRequest request,
        @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        AuthenticatedSessionView session = requireOpsSession(httpServletRequest);
        return ApiResponse.success(requestId(), compensationService.createCode(appCode, session.user().userId(), request));
    }

    @Operation(summary = "查询补偿码列表", description = "按 appCode 查询补偿码主表，支持状态和权益类型过滤。")
    @GetMapping("/apps/{appCode}")
    public ApiResponse<List<CompensationCodeView>> list(
        @PathVariable String appCode,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String benefitType,
        @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        requireOpsSession(httpServletRequest);
        return ApiResponse.success(requestId(), compensationService.listCodes(appCode, status, benefitType));
    }

    @Operation(summary = "查询补偿码详情", description = "按补偿码查看详情。")
    @GetMapping("/apps/{appCode}/{compensationCode}")
    public ApiResponse<CompensationCodeView> detail(
        @PathVariable String appCode,
        @PathVariable String compensationCode,
        @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        requireOpsSession(httpServletRequest);
        return ApiResponse.success(requestId(), compensationService.getCode(appCode, compensationCode));
    }

    @Operation(summary = "作废补偿码", description = "将未使用的补偿码作废，已使用码不能回收。")
    @PostMapping("/apps/{appCode}/{compensationCode}/void")
    public ApiResponse<CompensationCodeView> voidCode(
        @PathVariable String appCode,
        @PathVariable String compensationCode,
        @Valid @RequestBody(required = false) CompensationCodeVoidRequest request,
        @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        requireOpsSession(httpServletRequest);
        return ApiResponse.success(requestId(), compensationService.voidCode(appCode, compensationCode, request == null ? null : request.reason()));
    }

    private AuthenticatedSessionView requireOpsSession(HttpServletRequest request) {
        String token = sessionTokenResolver.resolve(request)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token"));
        return sysAuthSessionService.findCurrentSession(token)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bearer token"));
    }

    private String requestId() {
        String value = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
