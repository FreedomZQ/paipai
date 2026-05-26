package com.apphub.backend.sys.billing.privacy.mapper;

import com.apphub.backend.sys.billing.privacy.entity.SysEntitlementConsumptionReportEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysEntitlementConsumptionReportMapper extends BaseMapper<SysEntitlementConsumptionReportEntity> {
    @Select("""
        SELECT COALESCE(SUM(quantity), 0)
        FROM sys_entitlement_consumption_report
        WHERE app_code = #{appCode}
          AND transaction_id = #{transactionId}
          AND report_status = 'accepted'
          AND refund_status IN ('none', 'refund_reversed')
        """)
    Integer sumAcceptedQuantityByTransaction(
        @Param("appCode") String appCode,
        @Param("transactionId") String transactionId
    );
}
