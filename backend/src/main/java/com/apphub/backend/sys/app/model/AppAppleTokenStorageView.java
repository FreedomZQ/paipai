package com.apphub.backend.sys.app.model;

/**
 * 响应模型 `AppAppleTokenStorageView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record AppAppleTokenStorageView(
    String appCode,
    int totalAppleProviderTokens,
    int encryptedRefreshTokenCount,
    int plaintextRefreshTokenFallbackCount,
    boolean plaintextFallbackPresent
) {
}
