package com.apphub.backend.sys.auth.mapper;

import com.apphub.backend.sys.auth.entity.SysUserIdentityEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis Plus Mapper 接口 `SysUserIdentityMapper`。
 * 负责 认证 领域的数据访问，封装对数据库表或视图的读写操作。
 */

@Mapper
public interface SysUserIdentityMapper extends BaseMapper<SysUserIdentityEntity> {

    @Select("""
        SELECT *
        FROM sys_user_identity
        WHERE app_code = #{appCode}
          AND provider_code = #{providerCode}
          AND provider_subject = #{providerSubject}
        LIMIT 1
        """)
    SysUserIdentityEntity selectByProviderIdentity(
        @Param("appCode") String appCode,
        @Param("providerCode") String providerCode,
        @Param("providerSubject") String providerSubject
    );

    @Update("""
        UPDATE sys_user_identity
        SET status = 'revoked',
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
