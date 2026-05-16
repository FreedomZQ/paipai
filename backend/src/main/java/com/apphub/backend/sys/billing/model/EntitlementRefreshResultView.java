package com.apphub.backend.sys.billing.model;

import java.util.List;

/**
 * 响应模型 `EntitlementRefreshResultView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record EntitlementRefreshResultView(
    String appCode,
    Long userId,
    int candidateCount,
    int refreshedCount,
    int insertedTransactionCount,
    List<EntitlementRefreshItemView> results
) {
}
