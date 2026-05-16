package com.apphub.backend.sys.auth.model;

import java.time.OffsetDateTime;

/**
 * 响应模型 `AuthSessionIssuedView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record AuthSessionIssuedView(
    String appCode,
    String sessionSource,
    String sessionToken,
    OffsetDateTime expiresAt,
    CurrentUserView user
) {
}
