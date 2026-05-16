package com.apphub.backend.sys.appstore.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 请求模型 `AppStoreNotificationIngestRequest`。
 * 用于描述接口入参结构，便于统一进行校验、序列化和调用约束说明。
 */
@Schema(description = "App Store Server Notification 接收请求体。")
public record AppStoreNotificationIngestRequest(
    @Schema(description = "Apple signedPayload，必填。", example = "eyJhbGciOiJFUzI1NiIsIng1YyI6W10ifQ...") @NotBlank String signedPayload,
    @Schema(description = "通知 UUID，用于去重。", example = "notification-uuid-001") @Size(max = 128) String notificationUuid,
    @Schema(description = "通知类型。", example = "DID_RENEW") @Size(max = 128) String notificationType,
    @Schema(description = "通知子类型。", example = "AUTO_RENEW_ENABLED") @Size(max = 128) String subtype,
    @Schema(description = "原始通知 payload。", example = "{\"notificationType\":\"DID_RENEW\"}") Map<String, Object> payload
) {
}
