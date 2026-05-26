package com.apphub.backend.sys.billing.privacy.entity;

import com.apphub.backend.common.mybatis.JsonbStringTypeHandler;
import com.apphub.backend.common.mybatis.UuidTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.JdbcType;

@TableName(value = "sys_entitlement_consumption_report", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysEntitlementConsumptionReportEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = UuidTypeHandler.class)
    private UUID eventId;
    private String idempotencyKey;
    private String eventType;
    private String entitlementCode;
    private String entitlementTokenId;
    private String transactionId;
    private String originalTransactionId;
    private Integer quantity;
    private Long clientEntitlementVersion;
    private String deviceIdHash;
    private String appInstanceIdHash;
    private OffsetDateTime localCreatedAt;
    private String reportStatus;
    private String rejectReason;
    private String refundStatus;
    private Long refundCaseId;
    private String refundEffectType;
    private OffsetDateTime refundedAt;
    private Boolean countedInRefundDecision;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
