package com.apphub.backend.sys.auth.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 请求模型 `AppleExchangeRequest`。
 * 用于描述接口入参结构，便于统一进行校验、序列化和调用约束说明。
 */
@Schema(description = "Apple 登录换取后端会话的请求体。")
public record AppleExchangeRequest(
    @Schema(description = "Apple identity token，必填。", example = "eyJraWQiOiJBSURPUEsxIiwidHlwIjoiSldUIn0...") @NotBlank String identityToken,
    @Schema(description = "Apple authorization code，可用于服务端换取 refresh token。", example = "c1234567890abcdef") String authorizationCode,
    @Schema(description = "Apple 返回的名。", example = "Ming") String givenName,
    @Schema(description = "Apple 返回的姓。", example = "Li") String familyName,
    @Schema(description = "客户端收到的 state。", example = "login-state-001") String state,
    @Schema(description = "客户端发起登录时保存的期望 state。", example = "login-state-001") String expectedState,
    @Schema(description = "客户端收到或生成的 nonce。", example = "nonce-001") String nonce,
    @Schema(description = "客户端发起登录时保存的期望 nonce。", example = "nonce-001") String expectedNonce,
    @Schema(description = "Apple 登录回调地址。", example = "https://api.example.com/api/v1/system/auth/apps/saving/apple/exchange") String redirectUri
) {
}
