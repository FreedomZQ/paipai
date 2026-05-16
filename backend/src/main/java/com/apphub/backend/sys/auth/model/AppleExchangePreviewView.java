package com.apphub.backend.sys.auth.model;

import java.util.Map;

/**
 * 响应模型 `AppleExchangePreviewView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record AppleExchangePreviewView(
    boolean sessionIssued,
    String overallStatus,
    String exchangeStatus,
    String identityStatus,
    String note,
    DecodedAppleIdentityTokenView decodedToken,
    Map<String, String> diagnostics,
    AuthSessionIssuedView issuedSession
) {
}
