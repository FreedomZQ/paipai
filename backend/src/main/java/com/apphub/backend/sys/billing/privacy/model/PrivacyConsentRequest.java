package com.apphub.backend.sys.billing.privacy.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "隐私同意更新请求。")
public record PrivacyConsentRequest(
    @Schema(description = "同意类型。", example = "apple_refund_consumption_data") @NotBlank @Size(max = 96) String consentType,
    @Schema(description = "是否同意。撤回时传 false。") Boolean consented,
    @Schema(description = "政策版本。", example = "refund-consumption-v1") @Size(max = 64) String policyVersion,
    @Schema(description = "地区/法域。", example = "CN") @Size(max = 16) String regionCode,
    @Schema(description = "来源场景。", example = "paywall") @Size(max = 64) String sourceType,
    @Schema(description = "来源引用。", example = "com.paipai.readalong.family.multi_child.lifetime") @Size(max = 160) String sourceRef
) {
}
