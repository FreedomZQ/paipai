package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.compat.service.ReadingCloudUsageService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.auth.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Tag(name = "拍拍伴读资源次数管理", description = "管理员调整云端 OCR / TTS 次数、查询当前权威次数和审计日志。")
@RestController
@RequestMapping("/api/v1/admin/reading/cloud-usage")
public class ReadingCloudUsageAdminController {
    private final ReadingCloudUsageService cloudUsageService;
    private final SysUserMapper sysUserMapper;

    @Value("${backend.apps.paipai_readingcompanion.admin.configToken:}")
    private String adminConfigToken;

    public ReadingCloudUsageAdminController(ReadingCloudUsageService cloudUsageService, SysUserMapper sysUserMapper) {
        this.cloudUsageService = cloudUsageService;
        this.sysUserMapper = sysUserMapper;
    }

    @Operation(summary = "查询用户当前云端次数", description = "返回数据库权威记录中的文字识别次数和语音朗读次数。")
    @GetMapping("/users/state")
    public ApiResponse<ReadingCloudUsageService.CloudUsageSnapshot> state(
        @Parameter(description = "管理 token。") @RequestHeader(value = "X-Admin-Config-Token", required = false) String token,
        @Parameter(description = "用户 ID。") @RequestParam Long userId
    ) {
        requireAdminToken(token);
        return ApiResponse.success(currentRequestId(), cloudUsageService.snapshot(userId));
    }

    @Operation(summary = "调整单个用户次数", description = "delta 为正数表示赠送次数，为负数表示扣减可用次数；serviceType 支持 cloud_ocr/cloud_tts/capture/speech。")
    @PostMapping("/users/adjust")
    public ApiResponse<AdminAdjustmentReceipt> adjust(
        @Parameter(description = "管理 token。") @RequestHeader(value = "X-Admin-Config-Token", required = false) String token,
        @Valid @RequestBody AdminAdjustmentRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        requireAdminToken(token);
        var decision = cloudUsageService.adjust(body.userId(), body.serviceType(), body.delta(), body.reason(), operatorId(request), body.idempotencyKey(), body.validDays());
        return ApiResponse.success(currentRequestId(), new AdminAdjustmentReceipt(
            String.valueOf(body.userId()),
            decision.serviceType(),
            body.delta(),
            decision.remainingCount(),
            OffsetDateTime.now(ZoneOffset.UTC).toString()
        ));
    }

    @Operation(summary = "批量赠送次数", description = "为多个指定用户批量增加 OCR、朗读或云端次数，适合按 userId 精准补发。")
    @PostMapping("/users/batch-grant")
    public ApiResponse<BatchGrantReceipt> batchGrant(
        @Parameter(description = "管理 token。") @RequestHeader(value = "X-Admin-Config-Token", required = false) String token,
        @Valid @RequestBody BatchGrantRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        requireAdminToken(token);
        var decisions = cloudUsageService.grantBatch(body.userIds(), body.serviceType(), body.amount(), body.reason(), operatorId(request), body.validDays());
        return ApiResponse.success(currentRequestId(), new BatchGrantReceipt(
            body.userIds().stream().distinct().count(),
            body.serviceType(),
            body.amount(),
            decisions,
            OffsetDateTime.now(ZoneOffset.UTC).toString()
        ));
    }

    @Operation(summary = "向当前应用全部用户赠送次数", description = "按 appCode 范围批量赠送 OCR、朗读或云端次数，默认作用于当前应用全部账号；activeOnly=true 时仅赠送 active 用户。")
    @PostMapping("/users/batch-grant-all")
    public ApiResponse<BatchGrantReceipt> batchGrantAll(
        @Parameter(description = "管理 token。") @RequestHeader(value = "X-Admin-Config-Token", required = false) String token,
        @Valid @RequestBody BatchGrantAllRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        requireAdminToken(token);
        List<Long> userIds = (body.activeOnly()
            ? sysUserMapper.selectActiveByAppCode(ReadingAppModule.APP_CODE)
            : sysUserMapper.selectByAppCode(ReadingAppModule.APP_CODE))
            .stream()
            .map(item -> item.getId())
            .toList();
        var decisions = cloudUsageService.grantBatch(userIds, body.serviceType(), body.amount(), body.reason(), operatorId(request), body.validDays());
        return ApiResponse.success(currentRequestId(), new BatchGrantReceipt(
            userIds.size(),
            body.serviceType(),
            body.amount(),
            decisions,
            OffsetDateTime.now(ZoneOffset.UTC).toString()
        ));
    }

    @Operation(summary = "查询次数变动日志", description = "按用户查询最近次数变动记录，可按 serviceType 过滤。")
    @GetMapping("/logs")
    public ApiResponse<List<ReadingCloudUsageService.CloudUsageLogView>> logs(
        @Parameter(description = "管理 token。") @RequestHeader(value = "X-Admin-Config-Token", required = false) String token,
        @RequestParam Long userId,
        @RequestParam(required = false) String serviceType,
        @RequestParam(defaultValue = "50") int limit
    ) {
        requireAdminToken(token);
        return ApiResponse.success(currentRequestId(), cloudUsageService.recentLogs(userId, serviceType, limit));
    }

    private void requireAdminToken(String token) {
        if (adminConfigToken == null || adminConfigToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_CONFIG_TOKEN_NOT_CONFIGURED");
        }
        if (token == null || !adminConfigToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_CONFIG_TOKEN_INVALID");
        }
    }

    private String operatorId(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return "admin:" + forwardedFor.split(",")[0].trim();
        }
        return "admin:" + request.getRemoteAddr();
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }

    public record AdminAdjustmentRequest(
        @NotNull Long userId,
        @NotBlank String serviceType,
        int delta,
        @NotBlank String reason,
        String idempotencyKey,
        Integer validDays
    ) {}

    public record BatchGrantRequest(
        @NotEmpty List<Long> userIds,
        @NotBlank String serviceType,
        int amount,
        @NotBlank String reason,
        Integer validDays
    ) {}

    public record BatchGrantAllRequest(
        @NotBlank String serviceType,
        int amount,
        @NotBlank String reason,
        Integer validDays,
        boolean activeOnly
    ) {}

    public record AdminAdjustmentReceipt(
        String userId,
        String serviceType,
        int delta,
        int remainingCount,
        String adjustedAt
    ) {}

    public record BatchGrantReceipt(
        long userCount,
        String serviceType,
        int amount,
        List<ReadingCloudUsageService.CloudUsageDecision> results,
        String grantedAt
    ) {}
}
