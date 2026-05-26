package com.apphub.backend.sys.billing.privacy.mapper;

import com.apphub.backend.sys.billing.privacy.entity.SysEntitlementLedgerEventEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysEntitlementLedgerEventMapper extends BaseMapper<SysEntitlementLedgerEventEntity> {
    @Select("""
        SELECT COALESCE(SUM(CASE WHEN event_type = 'grant' THEN quantity_delta ELSE 0 END), 0)
        FROM sys_entitlement_ledger_event
        WHERE app_code = #{appCode}
          AND transaction_id = #{transactionId}
          AND refund_status IN ('none', 'refund_reversed')
        """)
    Integer sumGrantedByTransaction(
        @Param("appCode") String appCode,
        @Param("transactionId") String transactionId
    );

    @Select("""
        SELECT COALESCE(SUM(CASE WHEN event_type = 'consume' THEN ABS(quantity_delta) ELSE 0 END), 0)
        FROM sys_entitlement_ledger_event
        WHERE app_code = #{appCode}
          AND transaction_id = #{transactionId}
          AND refund_status IN ('none', 'refund_reversed')
        """)
    Integer sumConsumedByTransaction(
        @Param("appCode") String appCode,
        @Param("transactionId") String transactionId
    );
}
