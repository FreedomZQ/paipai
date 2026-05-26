package com.apphub.backend.sys.billing.privacy.entity;

import com.apphub.backend.common.mybatis.JsonbStringTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.JdbcType;

@TableName(value = "sys_refund_decision_log", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysRefundDecisionLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private Long refundCaseId;
    private Long consumptionRequestId;
    private String orderNo;
    private String transactionId;
    private String originalTransactionId;
    private String productId;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String lookupInputJson;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String ticketSnapshotJson;
    private Integer usageCountUsed;
    private Integer usageCountTotal;
    private Integer usageRatioMilli;
    private Integer usageRatioThresholdMilli;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String usageSnapshotJson;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String policySnapshotJson;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String comparisonResultJson;
    private String decisionCode;
    private String displayMessage;
    private String appleRefundPreference;
    private String applePayloadHash;
    private String applePayloadCiphertext;
    private Integer appleHttpStatus;
    private String appleResponseCiphertext;
    private String piiKeyVersion;
    private OffsetDateTime createdAt;
}
