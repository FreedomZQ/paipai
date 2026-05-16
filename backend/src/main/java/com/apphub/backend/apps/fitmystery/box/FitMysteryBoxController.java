package com.apphub.backend.apps.fitmystery.box;

import com.apphub.backend.apps.fitmystery.api.FitMysteryApiEnvelope;
import com.apphub.backend.apps.fitmystery.common.FitMysteryRequestSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/fitmystery")
@Tag(name = "FitMystery 盲盒", description = "FitMystery 盲盒状态、开盒记录与收藏图鉴接口。")
public class FitMysteryBoxController {
    private final FitMysteryBoxService boxService;
    private final FitMysteryRequestSupport requestSupport;

    public FitMysteryBoxController(FitMysteryBoxService boxService, FitMysteryRequestSupport requestSupport) {
        this.boxService = boxService;
        this.requestSupport = requestSupport;
    }

    @Operation(summary = "查询盲盒状态", description = "查询当前用户可开盒次数、候选奖池与盲盒状态。")
    @GetMapping("/box/state")
    public FitMysteryApiEnvelope<Map<String, Object>> state(@Parameter(hidden = true) HttpServletRequest request) {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), boxService.state(requestSupport.requireUserId(request)));
    }

    @Operation(summary = "打开盲盒", description = "消耗一次可用次数或购买权益打开指定奖池盲盒；请求体中的 idempotencyKey 用于防止重复开盒。")
    @PostMapping("/box/open")
    public FitMysteryApiEnvelope<Map<String, Object>> open(@Parameter(hidden = true) HttpServletRequest request, @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "开盒请求体。示例：poolCode=starter_pool，consumeType=free_quota。", required = true) @RequestBody FitMysteryBoxService.OpenBoxRequest body) {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), boxService.open(requestSupport.requireUserId(request), body));
    }

    @Operation(summary = "查询开盒记录", description = "分页查询当前用户最近的盲盒抽取记录。")
    @GetMapping("/box/draws")
    public FitMysteryApiEnvelope<Map<String, Object>> draws(@Parameter(hidden = true) HttpServletRequest request, @Parameter(description = "返回条数，建议 1-100。示例：50", example = "50") @RequestParam(defaultValue = "50") int pageSize) {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), boxService.history(requestSupport.requireUserId(request), pageSize));
    }

    @Operation(summary = "查询收藏图鉴", description = "分页查询当前用户已经收集到的物品图鉴。")
    @GetMapping("/collection/items")
    public FitMysteryApiEnvelope<Map<String, Object>> collection(@Parameter(hidden = true) HttpServletRequest request, @Parameter(description = "返回条数，建议 1-200。示例：100", example = "100") @RequestParam(defaultValue = "100") int pageSize) {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), boxService.collection(requestSupport.requireUserId(request), pageSize));
    }
}
