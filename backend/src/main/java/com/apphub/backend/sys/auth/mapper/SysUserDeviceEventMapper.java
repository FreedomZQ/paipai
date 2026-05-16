package com.apphub.backend.sys.auth.mapper;

import com.apphub.backend.sys.auth.entity.SysUserDeviceEventEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;

@Mapper
public interface SysUserDeviceEventMapper extends BaseMapper<SysUserDeviceEventEntity> {
    @Select("""
        SELECT COUNT(*)
        FROM sys_user_device_event
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND event_type = #{eventType}
          AND created_at >= #{startInclusive}
          AND created_at < #{endExclusive}
        """)
    int countByUserEventBetween(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("eventType") String eventType,
        @Param("startInclusive") OffsetDateTime startInclusive,
        @Param("endExclusive") OffsetDateTime endExclusive
    );

    @Select("""
        SELECT COUNT(*)
        FROM sys_user_device_event
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND event_type = #{eventType}
          AND created_at >= #{startInclusive}
          AND created_at < #{endExclusive}
          AND COALESCE(payload_json ->> 'idempotencyKey', '') = #{idempotencyKey}
        """)
    int countByUserEventAndIdempotencyBetween(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("eventType") String eventType,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("startInclusive") OffsetDateTime startInclusive,
        @Param("endExclusive") OffsetDateTime endExclusive
    );
}
