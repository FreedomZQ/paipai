package com.apphub.backend.sys.billing.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 请求模型 `PurchaseVerifyRequest`。
 * 用于描述接口入参结构，便于统一进行校验、序列化和调用约束说明。
 */
@Schema(description = "App Store 购买交易验证请求体。")
public record PurchaseVerifyRequest(
    @Schema(description = "商品 ID。", example = "com.paipai.readalong.family.monthly") @NotBlank @Size(max = 128) String productId,
    @Schema(description = "交易 ID。", example = "2000000123456789") @Size(max = 128) String transactionId,
    @Schema(description = "原始交易 ID，必填。", example = "2000000123000000") @NotBlank @Size(max = 128) String originalTransactionId,
    @Schema(description = "商店环境，例如 Sandbox 或 Production。", example = "Production") @Size(max = 32) String environment,
    @Schema(description = "店面区域。", example = "CHN") @Size(max = 32) String storefront,
    @Schema(description = "App Account Token。", example = "550e8400-e29b-41d4-a716-446655440000") @Size(max = 128) String appAccountToken,
    @Schema(description = "Apple signedTransactionInfo，必填。", example = "eyJhbGciOiJFUzI1NiIsIng1YyI6W10ifQ...") @NotBlank String signedTransactionInfo,
    @Schema(description = "Apple signedRenewalInfo。", example = "eyJhbGciOiJFUzI1NiIsIng1YyI6W10ifQ...") String signedRenewalInfo
) {
}
