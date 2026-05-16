package com.apphub.backend.sys.compensation.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "补偿码作废请求。")
public record CompensationCodeVoidRequest(
    @Schema(description = "作废原因。", example = "活动结束")
    String reason
) {}
