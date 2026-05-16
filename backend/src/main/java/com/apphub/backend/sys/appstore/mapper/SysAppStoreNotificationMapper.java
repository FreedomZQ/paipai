package com.apphub.backend.sys.appstore.mapper;

import com.apphub.backend.sys.appstore.entity.SysAppStoreNotificationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis Plus Mapper 接口 `SysAppStoreNotificationMapper`。
 * 负责 App Store 领域的数据访问，封装对数据库表或视图的读写操作。
 */

@Mapper
public interface SysAppStoreNotificationMapper extends BaseMapper<SysAppStoreNotificationEntity> {

    @Select("""
        SELECT COUNT(*)
        FROM sys_app_store_notification
        WHERE app_code = #{appCode}
        """)
    int countByApp(@Param("appCode") String appCode);

    @Select("""
        SELECT COUNT(*)
        FROM sys_app_store_notification
        WHERE app_code = #{appCode}
          AND verification_status = #{verificationStatus}
        """)
    int countByAppAndVerificationStatus(
        @Param("appCode") String appCode,
        @Param("verificationStatus") String verificationStatus
    );

    @Select("""
        SELECT COUNT(*)
        FROM sys_app_store_notification
        WHERE app_code = #{appCode}
          AND processing_status = #{processingStatus}
        """)
    int countByAppAndProcessingStatus(
        @Param("appCode") String appCode,
        @Param("processingStatus") String processingStatus
    );

    @Select("""
        SELECT *
        FROM sys_app_store_notification
        WHERE app_code = #{appCode}
        ORDER BY received_at DESC, id DESC
        LIMIT #{limit}
        """)
    List<SysAppStoreNotificationEntity> selectRecentByApp(
        @Param("appCode") String appCode,
        @Param("limit") int limit
    );
}
