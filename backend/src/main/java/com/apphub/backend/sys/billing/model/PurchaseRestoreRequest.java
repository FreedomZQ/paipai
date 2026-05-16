package com.apphub.backend.sys.billing.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 请求模型 `PurchaseRestoreRequest`。
 * 用于描述接口入参结构，便于统一进行校验、序列化和调用约束说明。
 */
@Schema(description = "恢复购买交易列表请求体。")
public record PurchaseRestoreRequest(
    @Schema(description = "需要恢复的交易列表，至少一条。", example = "[{\"transactionId\":\"2000000123456789\",\"originalTransactionId\":\"2000000123000000\"}]") @Valid @NotEmpty List<PurchaseRestoreItemRequest> transactions
) {
}
