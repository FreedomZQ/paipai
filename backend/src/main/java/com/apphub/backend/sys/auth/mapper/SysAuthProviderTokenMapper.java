package com.apphub.backend.sys.auth.mapper;

import com.apphub.backend.sys.auth.entity.SysAuthProviderTokenEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis Plus Mapper 接口 `SysAuthProviderTokenMapper`。
 * 负责 认证 领域的数据访问，封装对数据库表或视图的读写操作。
 */

@Mapper
public interface SysAuthProviderTokenMapper extends BaseMapper<SysAuthProviderTokenEntity> {

    @Select("""
        SELECT *
        FROM sys_auth_provider_token
        WHERE app_code = #{appCode}
          AND provider_code = #{providerCode}
          AND provider_subject = #{providerSubject}
        LIMIT 1
        """)
    SysAuthProviderTokenEntity selectByProviderIdentity(
        @Param("appCode") String appCode,
        @Param("providerCode") String providerCode,
        @Param("providerSubject") String providerSubject
    );

    @Select("""
        SELECT *
        FROM sys_auth_provider_token
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND provider_code = #{providerCode}
        ORDER BY updated_at DESC
        LIMIT 1
        """)
    SysAuthProviderTokenEntity selectByUserAndProvider(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("providerCode") String providerCode
    );

    @Select("""
        SELECT COUNT(*)
        FROM sys_auth_provider_token
        WHERE app_code = #{appCode}
          AND provider_code = #{providerCode}
        """)
    int countByAppAndProvider(
        @Param("appCode") String appCode,
        @Param("providerCode") String providerCode
    );

    @Select("""
        SELECT COUNT(*)
        FROM sys_auth_provider_token
        WHERE app_code = #{appCode}
          AND provider_code = #{providerCode}
          AND refresh_token_ciphertext_base64 IS NOT NULL
          AND refresh_token_ciphertext_base64 <> ''
        """)
    int countEncryptedRefreshTokens(
        @Param("appCode") String appCode,
        @Param("providerCode") String providerCode
    );

    @Select("""
        SELECT COUNT(*)
        FROM sys_auth_provider_token
        WHERE app_code = #{appCode}
          AND provider_code = #{providerCode}
          AND refresh_token IS NOT NULL
          AND refresh_token <> ''
          AND (refresh_token_ciphertext_base64 IS NULL OR refresh_token_ciphertext_base64 = '')
        """)
    int countPlaintextRefreshTokenFallbacks(
        @Param("appCode") String appCode,
        @Param("providerCode") String providerCode
    );

    @Update("""
        UPDATE sys_auth_provider_token
        SET status = 'revoked',
            refresh_token = NULL,
            access_token = NULL,
            token_type = NULL,
            refresh_token_nonce_base64 = NULL,
            refresh_token_ciphertext_base64 = NULL,
            updated_at = #{now}
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND status <> 'revoked'
        """)
    int revokeAllByUser(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("now") java.time.OffsetDateTime now
    );

}
