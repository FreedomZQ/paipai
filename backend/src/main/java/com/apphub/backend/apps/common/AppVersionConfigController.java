package com.apphub.backend.apps.common;

import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 中文说明：多 App 通用版本策略入口。
 *
 * <p>各客户端可以按 appCode 查询自己的发布版本策略；已有兼容客户端也可以在自己的 /v1/config 下做代理入口。
 * 该接口只返回 App Store 更新提示配置，不处理安装包分发，不绕过 Apple 审核与上架流程。</p>
 */
@RestController
@RequestMapping("/api/v1/apps/{appCode}/release")
@Tag(name = "多 App 发布配置", description = "按 appCode 查询各应用的 App Store 版本策略与升级提示配置。")
public class AppVersionConfigController {
    private final AppVersionPolicyService appVersionPolicyService;

    public AppVersionConfigController(AppVersionPolicyService appVersionPolicyService) {
        this.appVersionPolicyService = appVersionPolicyService;
    }

    @Operation(summary = "查询应用版本策略", description = "客户端启动或进入设置页时按应用编码查询升级提示策略；只返回 App Store 更新提示配置，不做安装包分发。")
    @GetMapping("/app-version")
    public ApiResponse<Map<String, Object>> appVersion(
                                                       @Parameter(description = "应用编码。示例：saving", example = "saving") @PathVariable String appCode,
                                                       @Parameter(description = "客户端平台。示例：ios", example = "ios") @RequestParam(defaultValue = "ios") String platform,
                                                       @Parameter(description = "客户端展示版本号。示例：1.0.0", example = "1.0.0") @RequestParam(defaultValue = "unknown") String appVersion,
                                                       @Parameter(description = "客户端构建号。示例：100", example = "100") @RequestParam(defaultValue = "unknown") String buildNumber) {
        return ApiResponse.success(currentRequestId(), appVersionPolicyService.policy(appCode, platform, appVersion, buildNumber));
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
