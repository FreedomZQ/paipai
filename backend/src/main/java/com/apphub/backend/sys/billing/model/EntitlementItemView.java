package com.apphub.backend.sys.billing.model;

import java.time.OffsetDateTime;

/**
 * 响应模型 `EntitlementItemView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record EntitlementItemView(
    String entitlementCode,
    String status,
    String sourceType,
    OffsetDateTime expiresAt
) {
}
