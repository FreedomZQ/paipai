package com.apphub.backend.sys.compensation.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "补偿码创建请求。")
public record CompensationCodeCreateRequest(
    @Schema(description = "补偿码，留空则由后端生成。", example = "PP-ABCDE-FGHIJ-KLMNO")
    @Pattern(regexp = "^(|PP(?:-?[A-Z2-9]{5}){3})$", message = "补偿码格式不正确")
    String compensationCode,
    @Schema(description = "权益类型：plan / usage_credit。", example = "plan")
    @NotBlank String benefitType,
    @Schema(description = "权益方案编码。类型为 plan 时必填。", example = "standard_single_child")
    String planCode,
    @Schema(description = "对应权益编码。类型为 plan 时可选，后端可自动推导。", example = "family_access")
    String entitlementCode,
    @Schema(description = "次数或额度。类型为 usage_credit 时必填。", example = "5")
    @Min(1) Integer grantCount,
    @Schema(description = "兑换后权益有效天数。", example = "30")
    @Min(1) Integer grantValidDays,
    @Schema(description = "权益最终截止时间。为空时按创建时间 + grantValidDays 计算。", example = "2026-12-31T23:59:59Z")
    OffsetDateTime grantValidUntilAt,
    @Schema(description = "补偿码到期时间，未传则使用 grantValidDays 计算。", example = "2026-12-31T23:59:59Z")
    OffsetDateTime expiresAt,
    @Schema(description = "领取范围：single_use / multi_device_once。", example = "single_use")
    String claimScope,
    @Schema(description = "最大领取次数。single_use 固定为 1；multi_device_once 表示最多可被多少设备各领取一次。", example = "1")
    @Min(1) Integer maxUses,
    @Schema(description = "额度类型：local_ocr / local_tts / cloud_ocr / cloud_tts。", example = "local_tts")
    String serviceType,
    @Schema(description = "备注说明。", example = "活动补偿码")
    String note
) {}
