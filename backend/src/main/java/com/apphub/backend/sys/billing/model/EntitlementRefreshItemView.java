package com.apphub.backend.sys.billing.model;

/**
 * 响应模型 `EntitlementRefreshItemView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record EntitlementRefreshItemView(
    String originalTransactionId,
    String productId,
    String lookupStatus,
    boolean verified,
    String note
) {
}
