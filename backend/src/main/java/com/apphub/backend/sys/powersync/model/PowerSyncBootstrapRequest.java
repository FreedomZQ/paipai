package com.apphub.backend.sys.powersync.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "PowerSync 初始化请求体。")
public record PowerSyncBootstrapRequest(
    @Schema(description = "客户端安装标识，用于区分同一用户的不同设备安装。", example = "ios-install-001") @NotBlank String installationId,
    @Schema(description = "设备 ID，可为空。", example = "device-001") String deviceId,
    @Schema(description = "客户端平台。", example = "ios") @NotBlank String clientPlatform,
    @Schema(description = "设备型号。", example = "iPhone16,2") String deviceModel,
    @Schema(description = "客户端版本号。", example = "1.0.0") String appVersion,
    @Schema(description = "客户端是否请求开启云同步。", example = "true") boolean cloudSyncEnabled,
    @Schema(description = "PowerSync 客户端 ID，可为空。", example = "ps-client-001") String powersyncClientId
) {
}
