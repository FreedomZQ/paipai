package com.apphub.backend.sys.entitlement.entity;

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
 * 用户级权益赠送/补偿实体。
 * 中文说明：人工赠送不写 sys_entitlement_snapshot，避免后续 App Store 刷新把后台补偿权益覆盖掉。
 */
@TableName(value = "sys_user_entitlement_grant", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysUserEntitlementGrantEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String grantCode;
    private String planCode;
    private String entitlementCode;
    private String sourceType;
    private String sourceRef;
    private String status;
    private OffsetDateTime startsAt;
    private OffsetDateTime expiresAt;
    private String reason;
    private Long operatorUserId;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
