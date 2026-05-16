package com.apphub.backend.apps.reading.common;

import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;

/**
 * 拍拍伴读已认证用户上下文。
 * 控制器只接收 Bearer token，解析后的 session 和 user 统一放在该对象中传递。
 */
public record ReadingAuthenticatedUser(
    SysAuthSessionEntity session,
    SysUserEntity user,
    String rawToken
) {
    public Long userId() {
        return user.getId();
    }
}
