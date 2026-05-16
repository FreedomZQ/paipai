package com.apphub.backend.sys.auth.model;

/**
 * 响应模型 `CurrentUserView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record CurrentUserView(
    Long userId,
    String appCode,
    String userType,
    String displayName,
    String status
) {
}
