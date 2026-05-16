package com.apphub.backend.sys.auth.mapper;

import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

/**
 * MyBatis Plus Mapper 接口 `SysAuthSessionMapper`。
 * 负责 认证 领域的数据访问，封装对数据库表或视图的读写操作。
 */

@Mapper
public interface SysAuthSessionMapper extends BaseMapper<SysAuthSessionEntity> {

    @Select("""
        SELECT *
        FROM sys_auth_session
        WHERE session_token_hash = #{sessionTokenHash}
          AND status = 'active'
          AND revoked_at IS NULL
          AND (expires_at IS NULL OR expires_at > #{now})
        LIMIT 1
        """)
    SysAuthSessionEntity selectActiveByTokenHash(
        @Param("sessionTokenHash") String sessionTokenHash,
        @Param("now") OffsetDateTime now
    );

    @Update("""
        UPDATE sys_auth_session
        SET last_seen_at = #{now},
            updated_at = #{now}
        WHERE id = #{sessionId}
        """)
    int touchLastSeen(@Param("sessionId") Long sessionId, @Param("now") OffsetDateTime now);

    @Update("""
        UPDATE sys_auth_session
        SET status = 'revoked',
            revoked_at = #{now},
            updated_at = #{now}
        WHERE id = #{sessionId}
          AND status = 'active'
        """)
    int revokeSession(@Param("sessionId") Long sessionId, @Param("now") OffsetDateTime now);

    @Update("""
        UPDATE sys_auth_session
        SET status = 'revoked',
            revoked_at = #{now},
            updated_at = #{now}
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND status = 'active'
        """)
    int revokeAllByUser(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("now") OffsetDateTime now
    );
}
