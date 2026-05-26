package com.apphub.backend.sys.billing.privacy.mapper;

import com.apphub.backend.sys.billing.privacy.entity.SysAppstoreConsumptionRequestEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface SysAppstoreConsumptionRequestMapper extends BaseMapper<SysAppstoreConsumptionRequestEntity> {
    @Select("""
        SELECT *
        FROM sys_appstore_consumption_request
        WHERE app_code = #{appCode}
          AND notification_uuid = #{notificationUuid}
        ORDER BY id DESC
        LIMIT 1
        """)
    SysAppstoreConsumptionRequestEntity selectByNotificationUuid(
        @Param("appCode") String appCode,
        @Param("notificationUuid") String notificationUuid
    );

    @Select("""
        SELECT *
        FROM sys_appstore_consumption_request
        WHERE reply_status IN ('pending', 'retry')
          AND next_retry_at <= #{now}
          AND deadline_at > #{now}
        ORDER BY deadline_at ASC, id ASC
        LIMIT #{limit}
        """)
    List<SysAppstoreConsumptionRequestEntity> selectDue(
        @Param("now") OffsetDateTime now,
        @Param("limit") int limit
    );
}
