package com.apphub.backend.sys.auth.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 请求模型 `DemoSessionCreateRequest`。
 * 用于描述接口入参结构，便于统一进行校验、序列化和调用约束说明。
 */
@Schema(description = "创建 Demo 会话的请求体。")
public record DemoSessionCreateRequest(
    @Schema(description = "设备标识，最长 128 字符。", example = "ios-device-001") @Size(max = 128) String deviceId,
    @Schema(description = "客户端平台，例如 ios。", example = "ios") @Size(max = 32) String clientPlatform,
    @Schema(description = "客户端版本号。", example = "1.0.0") @Size(max = 64) String clientVersion,
    @Schema(description = "展示名称。", example = "Demo User") @Size(max = 128) String displayName
) {
    public static DemoSessionCreateRequest empty() {
        return new DemoSessionCreateRequest(null, null, null, null);
    }
}
