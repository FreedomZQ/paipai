package com.apphub.backend.sys.auth.model;

import java.time.OffsetDateTime;

/**
 * 响应模型 `AppleRevokeResultView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record AppleRevokeResultView(
    String appCode,
    String sessionStatus,
    String providerTokenStatus,
    String remoteRevokeStatus,
    boolean remoteRevokeAttempted,
    OffsetDateTime revokedAt,
    String note
) {
}
