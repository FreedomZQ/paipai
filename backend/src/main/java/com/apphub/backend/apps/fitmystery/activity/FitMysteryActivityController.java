package com.apphub.backend.apps.fitmystery.activity;

import com.apphub.backend.apps.fitmystery.api.FitMysteryApiEnvelope;
import com.apphub.backend.apps.fitmystery.common.FitMysteryRequestSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/fitmystery")
@Tag(name = "FitMystery 活动", description = "FitMystery 运动事件上报和今日活动摘要接口。")
public class FitMysteryActivityController {
    private final FitMysteryActivityService activityService;
    private final FitMysteryRequestSupport requestSupport;

    public FitMysteryActivityController(FitMysteryActivityService activityService, FitMysteryRequestSupport requestSupport) {
        this.activityService = activityService;
        this.requestSupport = requestSupport;
    }

    @Operation(summary = "批量上报活动事件", description = "客户端批量提交步数、运动、打卡等活动事件；idempotencyKey 用于去重。")
    @PostMapping("/activity/events:batchSubmit")
    public FitMysteryApiEnvelope<Map<String, Object>> batchSubmit(@Parameter(hidden = true) HttpServletRequest request,
                                                                  @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "活动事件批量请求体。示例：events[0].eventType=steps，quantity=1200，unit=count。", required = true) @RequestBody FitMysteryActivityService.ActivityBatchSubmitRequest body) {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), activityService.batchSubmit(requestSupport.requireUserId(request), body));
    }

    @Operation(summary = "查询我的今日活动", description = "查询当前用户某天的活动统计；未传 date 时默认使用服务端当天。")
    @GetMapping("/me/today")
    public FitMysteryApiEnvelope<Map<String, Object>> today(@Parameter(hidden = true) HttpServletRequest request,
                                                            @Parameter(description = "查询日期，ISO-8601 格式。示例：2026-04-28", example = "2026-04-28") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), activityService.today(requestSupport.requireUserId(request), date));
    }
}
