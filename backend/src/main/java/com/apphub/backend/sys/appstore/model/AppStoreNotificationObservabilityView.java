package com.apphub.backend.sys.appstore.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 响应模型 `AppStoreNotificationObservabilityView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record AppStoreNotificationObservabilityView(
    String appCode,
    int total,
    int verified,
    int failed,
    int accepted,
    int reconciled,
    int rejected,
    List<RecentNotificationView> recentNotifications
) {
    public record RecentNotificationView(
        String notificationUuid,
        String notificationType,
        String subtype,
        String verificationStatus,
        String processingStatus,
        OffsetDateTime receivedAt
    ) {
    }
}
