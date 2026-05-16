package com.apphub.backend.sys.auth.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 响应模型 `DecodedAppleIdentityTokenView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record DecodedAppleIdentityTokenView(
    String subject,
    String email,
    Boolean emailVerified,
    String issuer,
    List<String> audience,
    String nonce,
    Boolean nonceSupported,
    Boolean privateEmail,
    OffsetDateTime expiresAt,
    OffsetDateTime issuedAt,
    String algorithm,
    String keyId
) {
}
