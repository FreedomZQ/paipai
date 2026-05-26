package com.apphub.backend.sys.billing.privacy.mapper;

import com.apphub.backend.sys.billing.privacy.entity.SysAppstoreRefundCaseEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysAppstoreRefundCaseMapper extends BaseMapper<SysAppstoreRefundCaseEntity> {
    @Select("""
        SELECT *
        FROM sys_appstore_refund_case
        WHERE app_code = #{appCode}
          AND notification_uuid = #{notificationUuid}
        ORDER BY id DESC
        LIMIT 1
        """)
    SysAppstoreRefundCaseEntity selectByNotificationUuid(
        @Param("appCode") String appCode,
        @Param("notificationUuid") String notificationUuid
    );

    @Select("""
        SELECT *
        FROM sys_appstore_refund_case
        WHERE app_code = #{appCode}
          AND transaction_id = #{transactionId}
        ORDER BY updated_at DESC, id DESC
        LIMIT 1
        """)
    SysAppstoreRefundCaseEntity selectLatestByTransactionId(
        @Param("appCode") String appCode,
        @Param("transactionId") String transactionId
    );
}
