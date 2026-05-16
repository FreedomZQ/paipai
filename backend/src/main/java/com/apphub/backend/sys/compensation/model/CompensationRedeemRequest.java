package com.apphub.backend.sys.compensation.model;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "补偿码兑换请求。")
public record CompensationRedeemRequest(
    @Schema(description = "补偿码。", example = "PP-ABCDE-FGHIJ-KLMNO")
    @NotBlank String compensationCode
) {}
