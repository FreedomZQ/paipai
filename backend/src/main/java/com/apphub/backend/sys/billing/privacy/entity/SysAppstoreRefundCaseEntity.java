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

@TableName(value = "sys_appstore_refund_case", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysAppstoreRefundCaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String orderNo;
    private String transactionId;
    private String originalTransactionId;
    private String productId;
    private String productType;
    private String notificationUuid;
    private String refundCaseStatus;
    private Long consumptionRequestId;
    private String appleRefundNotificationUuid;
    private OffsetDateTime refundRequestedAt;
    private OffsetDateTime refundResolvedAt;
    private OffsetDateTime revocationAt;
    private String revocationReason;
    private String revocationType;
    private Integer revocationPercentage;
    private Long purchasePriceMilliAmount;
    private Long refundAmountMilliEstimated;
    private String currency;
    private String reasonCode;
    private String riskLabel;
    private String decisionCode;
    private String decisionMessage;
    private Integer usageCountUsed;
    private Integer usageCountTotal;
    private Integer usageRatioMilli;
    private Integer usageRatioThresholdMilli;
    private String appleRefundPreference;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String decisionContextJson;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
