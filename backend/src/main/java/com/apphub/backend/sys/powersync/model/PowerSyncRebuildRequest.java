package com.apphub.backend.sys.powersync.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "PowerSync 重建请求体。")
public record PowerSyncRebuildRequest(
    @Schema(description = "客户端安装标识。", example = "ios-install-001") @NotBlank String installationId,
    @Schema(description = "请求重建原因。", example = "client_reinstall") String reason
) {
}
