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

@TableName(value = "sys_entitlement_ledger_event", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysEntitlementLedgerEventEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = UuidTypeHandler.class)
    private UUID eventId;
    private String eventType;
    private String entitlementCode;
    private String entitlementTokenId;
    private String transactionId;
    private String originalTransactionId;
    private Long refundCaseId;
    private String refundStatus;
    private String refundEffectType;
    private Integer refundedQuantity;
    private OffsetDateTime refundedAt;
    private Integer quantityDelta;
    private Integer balanceBefore;
    private Integer balanceAfter;
    private Long entitlementVersion;
    private String reasonCode;
    private String sourceType;
    private String sourceRef;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String metadataJson;
    private OffsetDateTime createdAt;
}
