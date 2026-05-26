package com.apphub.backend.sys.billing.privacy.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "权益消费事件报送请求。")
public record EntitlementEventReportRequest(
    @Schema(description = "设备安装 ID，会在后端 hash 后保存。") @Size(max = 160) String deviceId,
    @Schema(description = "App 安装实例 ID，会在后端 hash 后保存。") @Size(max = 160) String appInstanceId,
    @Schema(description = "事件列表。") @Valid @NotEmpty List<EntitlementEventItem> events
) {
    public record EntitlementEventItem(
        @Schema(description = "事件 UUID。") @Size(max = 64) String eventId,
        @Schema(description = "幂等键。") @Size(max = 160) String idempotencyKey,
        @Schema(description = "事件类型，目前支持 consume。") @Size(max = 32) String eventType,
        @Schema(description = "权益编码。") @Size(max = 96) String entitlementCode,
        @Schema(description = "权益 token ID。") @Size(max = 128) String entitlementTokenId,
        @Schema(description = "Apple transactionId。") @Size(max = 128) String transactionId,
        @Schema(description = "Apple originalTransactionId。") @Size(max = 128) String originalTransactionId,
        @Schema(description = "消耗数量。") Integer quantity,
        @Schema(description = "客户端权益版本。") Long clientEntitlementVersion,
        @Schema(description = "客户端本地创建时间。") String localCreatedAt
    ) {
    }
}
