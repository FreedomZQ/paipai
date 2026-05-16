package com.apphub.backend.apps.fitmystery.config;

import com.apphub.backend.apps.fitmystery.api.FitMysteryApiEnvelope;
import com.apphub.backend.apps.fitmystery.common.FitMysteryRequestSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/fitmystery/config")
@Tag(name = "FitMystery 配置", description = "FitMystery 启动配置、App Store 文案摘要和概率披露接口。")
public class FitMysteryConfigController {
    private final FitMysteryConfigService configService;
    private final FitMysteryRequestSupport requestSupport;

    public FitMysteryConfigController(FitMysteryConfigService configService, FitMysteryRequestSupport requestSupport) {
        this.configService = configService;
        this.requestSupport = requestSupport;
    }

    @Operation(summary = "查询启动配置", description = "按语言查询 FitMystery 首屏、功能开关和基础文案配置。")
    @GetMapping("/bootstrap")
    public FitMysteryApiEnvelope<Map<String, Object>> bootstrap(@Parameter(description = "本地化语言。示例：zh-Hans", example = "zh-Hans") @RequestParam(defaultValue = "zh-Hans") String locale) {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), configService.bootstrap(locale));
    }

    @Operation(summary = "查询 App Store 摘要", description = "查询提交审核和展示使用的 FitMystery App Store 配置摘要。")
    @GetMapping("/app-store-summary")
    public FitMysteryApiEnvelope<Map<String, Object>> appStoreSummary() {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), configService.appStoreSummary());
    }

    @Operation(summary = "查询盲盒概率披露", description = "查询 starter_pool 等奖池的稀有度概率、无现金价值提示和审核披露文案。")
    @GetMapping("/odds")
    public FitMysteryApiEnvelope<Map<String, Object>> odds() {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), configService.oddsDisclosure());
    }
}
