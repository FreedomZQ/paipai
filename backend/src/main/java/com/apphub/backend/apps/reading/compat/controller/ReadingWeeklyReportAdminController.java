package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.compat.service.ReadingWeeklyReportAccessConfigService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 拍拍伴读周报配置管理接口。
 *
 * <p>该接口只刷新后端内存里的“周报模块权益配置缓存”，不刷新单个用户的购买权益。
 * 单用户购买权益仍使用 `/api/v1/subscriptions/entitlements/refresh`。
 *
 * <p>使用简单管理 token 是为了满足个人开发者低运维：不额外搭建后台系统；同时默认 token 为空时拒绝访问，
 * 避免 App Store 上线环境把管理接口误暴露给普通客户端。
 */
@Tag(name = "拍拍伴读周报管理", description = "周报模块权益配置缓存刷新接口。")
@RestController
@RequestMapping("/api/v1/admin/reading/weekly-report")
public class ReadingWeeklyReportAdminController {
    private final ReadingWeeklyReportAccessConfigService accessConfigService;

    @Value("${backend.apps.paipai_readingcompanion.admin.configToken:}")
    private String adminConfigToken;

    public ReadingWeeklyReportAdminController(ReadingWeeklyReportAccessConfigService accessConfigService) {
        this.accessConfigService = accessConfigService;
    }

    @Operation(summary = "刷新周报权益配置缓存", description = "重新读取 reading_weekly_report_access 配置；用于低运维场景下数据库改配置后立即生效。")
    @PostMapping("/access-cache/refresh")
    public ApiResponse<WeeklyReportAccessRefreshReceipt> refreshAccessCache(
        @Parameter(description = "管理配置刷新 token，通过环境变量配置。示例：ops-refresh-token", example = "ops-refresh-token") @RequestHeader(value = "X-Admin-Config-Token", required = false) String token,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        requireAdminToken(token);
        var config = accessConfigService.refresh();
        return ApiResponse.success(currentRequestId(), new WeeklyReportAccessRefreshReceipt(
            config.version(),
            OffsetDateTime.now(ZoneOffset.UTC).toString(),
            config.planCodes(),
            config.historyEnabled(),
            config.exportReportEnabled(),
            config.pageShareEnabled(),
            config.offlineHistoryPreviewEnabled()
        ));
    }

    private void requireAdminToken(String token) {
        if (adminConfigToken == null || adminConfigToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_CONFIG_TOKEN_NOT_CONFIGURED");
        }
        if (token == null || !adminConfigToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_CONFIG_TOKEN_INVALID");
        }
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }

    public record WeeklyReportAccessRefreshReceipt(
        Integer version,
        String refreshedAt,
        List<String> planCodes,
        Boolean historyEnabled,
        Boolean exportReportEnabled,
        Boolean pageShareEnabled,
        Boolean offlineHistoryPreviewEnabled
    ) {}
}
