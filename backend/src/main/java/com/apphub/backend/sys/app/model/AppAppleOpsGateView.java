package com.apphub.backend.sys.app.model;

import com.apphub.backend.sys.billing.model.EntitlementObservabilityView;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationObservabilityView;

import java.util.List;

/**
 * 响应模型 `AppAppleOpsGateView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record AppAppleOpsGateView(
    String appCode,
    String status,
    AppAppleReadinessView readiness,
    AppAppleTokenStorageView tokenStorage,
    EntitlementObservabilityView entitlementObservability,
    AppStoreNotificationObservabilityView notificationObservability,
    List<OpsGateCheckView> checks,
    List<String> blockers,
    List<String> warnings
) {
    public record OpsGateCheckView(
        String key,
        String status,
        String note
    ) {
    }
}
