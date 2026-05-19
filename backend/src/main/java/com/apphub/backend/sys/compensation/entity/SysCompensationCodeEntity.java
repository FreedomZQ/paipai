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
 * 补偿码主表实体。
 * 中文说明：保存后端生成的补偿码、关联权益和使用状态，支持单次码与多设备各领一次码。
 */
@TableName(value = "sys_compensation_code", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysCompensationCodeEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private String compensationCode;
    private String codeHash;
    private String benefitType;
    private String planCode;
    private String entitlementCode;
    private String serviceType;
    private Integer grantCount;
    private Integer grantValidDays;
    private OffsetDateTime grantValidUntilAt;
    private OffsetDateTime expiresAt;
    private String claimScope;
    private Integer maxUses;
    private Integer usedCount;
    private String status;
    private OffsetDateTime usedAt;
    private String voidReason;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
