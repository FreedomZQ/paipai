package com.apphub.backend.sys.auth.mapper;

import com.apphub.backend.sys.auth.entity.SysEmailVerificationTicketEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

@Mapper
public interface SysEmailVerificationTicketMapper extends BaseMapper<SysEmailVerificationTicketEntity> {

    @Select("""
        SELECT *
        FROM sys_email_verification_ticket
        WHERE app_code = #{appCode}
          AND email = #{email}
          AND scene_code = #{sceneCode}
        ORDER BY created_at DESC, id DESC
        LIMIT 1
        """)
    SysEmailVerificationTicketEntity selectLatest(
        @Param("appCode") String appCode,
        @Param("email") String email,
        @Param("sceneCode") String sceneCode
    );

    @Update("""
        UPDATE sys_email_verification_ticket
        SET status = 'expired',
            updated_at = #{now}
        WHERE app_code = #{appCode}
          AND email = #{email}
          AND scene_code = #{sceneCode}
          AND status = 'pending'
        """)
    int expirePending(
        @Param("appCode") String appCode,
        @Param("email") String email,
        @Param("sceneCode") String sceneCode,
        @Param("now") OffsetDateTime now
    );
}
