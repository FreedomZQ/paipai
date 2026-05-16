package com.apphub.backend.sys.app.model;

import java.util.List;

/**
 * 响应模型 `SystemReleaseGateView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record SystemReleaseGateView(
    String status,
    String codeStatus,
    String externalStatus,
    String environment,
    int appCount,
    int blockedAppCount,
    int warningAppCount,
    List<ReleaseGateCheckView> checks,
    List<String> blockers,
    List<String> codeBlockers,
    List<String> externalBlockers,
    List<String> warnings,
    List<AppReleaseGateSummaryView> apps
) {
    public record ReleaseGateCheckView(
        String key,
        String status,
        String note,
        String currentValue,
        String expectedValue
    ) {
    }

    public record AppReleaseGateSummaryView(
        String appCode,
        String status,
        int blockerCount,
        int warningCount,
        List<String> blockers,
        List<String> warnings
    ) {
    }
}
