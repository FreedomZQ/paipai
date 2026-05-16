package com.apphub.backend.sys.compensation.entity;

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

/**
 * 用户补偿领取记录实体。
 */
@TableName(value = "sys_user_compensation_record", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysCompensationRedemptionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private Long compensationCodeId;
    private String compensationCode;
    private String benefitType;
    private String benefitSummary;
    private String planCode;
    private String entitlementCode;
    private String serviceType;
    private Integer grantCount;
    private OffsetDateTime redeemAt;
    private OffsetDateTime validUntilAt;
    private String status;
    private String resultMessage;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String beforeJson;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String afterJson;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
