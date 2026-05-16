package com.apphub.backend.sys.auth.model;

import java.util.Map;

/**
 * 响应模型 `AppleSessionRefreshView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record AppleSessionRefreshView(
    boolean sessionIssued,
    String overallStatus,
    String refreshStatus,
    String identityStatus,
    String note,
    DecodedAppleIdentityTokenView decodedToken,
    Map<String, String> diagnostics,
    AuthSessionIssuedView issuedSession
) {
}
