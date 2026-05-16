package com.apphub.backend.sys.powersync.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "PowerSync 令牌请求体。")
public record PowerSyncTokenRequest(
    @Schema(description = "客户端安装标识。", example = "ios-install-001") @NotBlank String installationId
) {
}
