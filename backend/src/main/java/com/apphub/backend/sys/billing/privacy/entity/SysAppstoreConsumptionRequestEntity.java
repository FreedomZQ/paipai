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

@TableName(value = "sys_appstore_consumption_request", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysAppstoreConsumptionRequestEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String notificationUuid;
    private String transactionId;
    private String originalTransactionId;
    private String productId;
    private String productType;
    private OffsetDateTime appleSignedDate;
    private OffsetDateTime receivedAt;
    private OffsetDateTime deadlineAt;
    private String consentStatus;
    private String replyStatus;
    private String deliveryStatus;
    private Integer consumptionPercentage;
    private Boolean sampleContentProvided;
    private String refundPreference;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String appleRequestPayloadJson;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String replyContextJson;
    private Integer attemptCount;
    private OffsetDateTime nextRetryAt;
    private OffsetDateTime lastAttemptAt;
    private Integer lastHttpStatus;
    private String lastErrorCode;
    private String lastErrorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
