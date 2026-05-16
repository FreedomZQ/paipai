package com.apphub.backend.sys.billing.model;

/**
 * 响应模型 `PurchaseIntakeAcceptedView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record PurchaseIntakeAcceptedView(
    Long intakeId,
    String sourceType,
    String productId,
    String transactionId,
    String originalTransactionId,
    String verificationStatus,
    String processingStatus
) {
}
