package com.apphub.backend.sys.appstore.model;

/**
 * 响应模型 `AppStoreNotificationAcceptedView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record AppStoreNotificationAcceptedView(
    String appCode,
    String notificationUuid,
    String notificationType,
    String subtype,
    String verificationStatus,
    String processingStatus,
    boolean duplicate
) {
}
