package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.auth.service.SysEmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.MDC;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/account/deletion")
@Validated
@Tag(name = "拍拍伴读账号删除验证", description = "账号删除前的邮箱验证码申请与确认接口。")
public class ReadingDeletionVerificationCompatController {
    private final ReadingAuthenticatedUserResolver userResolver;
    private final ReadingCompatService readingCompatService;

    public ReadingDeletionVerificationCompatController(
        ReadingAuthenticatedUserResolver userResolver,
        ReadingCompatService readingCompatService
    ) {
        this.userResolver = userResolver;
        this.readingCompatService = readingCompatService;
    }

    @Operation(summary = "申请账号删除验证码", description = "向用户邮箱发送删除账号验证码；验证码用于后续确认删除。")
    @PostMapping("/request-code")
    public ApiResponse<SysEmailVerificationService.EmailVerificationTicketView> requestCode(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "验证码申请请求体。示例：email=user@example.com。", required = true) @RequestBody DeletionRequestCodeRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), readingCompatService.requestDeletionCode(user, body.email()));
    }

    @Operation(summary = "确认账号删除", description = "使用邮箱验证码确认删除当前账号数据；confirmDataDeletion 必须明确为 true。")
    @PostMapping("/confirm")
    public ApiResponse<ReadingCompatService.DeletionRequestResponse> confirm(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "删除确认请求体。示例：code=123456，email=user@example.com，confirmDataDeletion=true。", required = true) @RequestBody DeletionConfirmRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(
            currentRequestId(),
            readingCompatService.confirmDeletionByCode(user, body.code(), body.email(), body.confirmDataDeletion(), body.idempotencyKey())
        );
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }

    public record DeletionRequestCodeRequest(
        @Schema(description = "接收删除验证码的邮箱地址。", example = "user@example.com") @NotBlank String email
    ) {}
    public record DeletionConfirmRequest(
        @Schema(description = "邮箱验证码。", example = "123456") @NotBlank String code,
        @Schema(description = "接收验证码的邮箱地址。", example = "user@example.com") @NotBlank String email,
        @Schema(description = "是否确认删除账号数据，必须为 true。", example = "true") Boolean confirmDataDeletion,
        @Schema(description = "幂等键，用于避免重复提交。", example = "delete-20260428-001") String idempotencyKey
    ) {}
}
