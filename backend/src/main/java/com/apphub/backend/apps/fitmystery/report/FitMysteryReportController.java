package com.apphub.backend.apps.fitmystery.report;

import com.apphub.backend.apps.fitmystery.api.FitMysteryApiEnvelope;
import com.apphub.backend.apps.fitmystery.common.FitMysteryRequestSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/fitmystery/reports")
@Tag(name = "FitMystery 报告", description = "FitMystery 本机报告授权、访问状态和历史策略接口。")
public class FitMysteryReportController {
    private final FitMysteryRequestSupport requestSupport;
    private final FitMysteryReportService reportService;

    public FitMysteryReportController(FitMysteryRequestSupport requestSupport, FitMysteryReportService reportService) {
        this.requestSupport = requestSupport;
        this.reportService = reportService;
    }

    @Operation(summary = "查询报告访问状态", description = "查询当前用户是否还有免费报告额度或 Pro 报告权限。")
    @GetMapping("/access")
    public FitMysteryApiEnvelope<Map<String, Object>> access(@Parameter(hidden = true) HttpServletRequest request) {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), reportService.access(requestSupport.requireUserId(request)));
    }

    @Operation(summary = "授权生成本机报告", description = "报告内容由客户端本机生成；后端只校验额度/权益并返回是否允许生成。")
    @PostMapping("/generations/authorize")
    public FitMysteryApiEnvelope<Map<String, Object>> authorize(@Parameter(hidden = true) HttpServletRequest request,
                                                                @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "报告生成授权请求体。示例：reportType=weekly，periodKey=2026-W18。", required = true) @RequestBody FitMysteryReportService.AuthorizeReportGenerationRequest body) {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), reportService.authorize(requestSupport.requireUserId(request), body));
    }

    @Operation(summary = "查询周报告策略", description = "查询指定周的本机周报告展示策略；后端不保存报告内容。")
    @GetMapping("/weekly")
    public FitMysteryApiEnvelope<Map<String, Object>> weekly(@Parameter(hidden = true) HttpServletRequest request,
                                                             @Parameter(description = "周开始日期，ISO-8601 格式。示例：2026-04-27", example = "2026-04-27") @RequestParam(required = false) String weekStart) {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), reportService.localReportPolicy(requestSupport.requireUserId(request), "weekly", weekStart));
    }

    @Operation(summary = "查询月报告策略", description = "查询指定月份的本机月报告展示策略；后端不保存报告内容。")
    @GetMapping("/monthly")
    public FitMysteryApiEnvelope<Map<String, Object>> monthly(@Parameter(hidden = true) HttpServletRequest request,
                                                              @Parameter(description = "月份，格式 yyyy-MM。示例：2026-04", example = "2026-04") @RequestParam(required = false) String month) {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), reportService.localReportPolicy(requestSupport.requireUserId(request), "monthly", month));
    }

    @Operation(summary = "查询报告历史策略", description = "查询报告历史入口策略；报告快照仍由客户端本机保存。")
    @GetMapping("/history")
    public FitMysteryApiEnvelope<Map<String, Object>> history(@Parameter(hidden = true) HttpServletRequest request,
                                                              @Parameter(description = "报告类型。示例：weekly，可选 weekly/monthly。", example = "weekly") @RequestParam(defaultValue = "weekly") String type) {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), Map.of(
            "type", type,
            "generationMode", "local_only",
            "serverStoresReportPayload", false,
            "items", java.util.List.of(),
            "access", reportService.access(requestSupport.requireUserId(request))
        ));
    }
}
