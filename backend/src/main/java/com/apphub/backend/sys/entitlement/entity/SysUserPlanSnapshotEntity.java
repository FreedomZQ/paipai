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
 * 用户购买时权益快照。
 * 中文说明：这是模式三的核心表，只保存有效购买当时承诺的权益包；App Store 当前投影仍写 sys_entitlement_snapshot。
 */
@TableName(value = "sys_user_plan_snapshot", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysUserPlanSnapshotEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String planCode;
    private String entitlementCode;
    private String policyVersion;
    private String sourceType;
    private String sourceRef;
    private String status;
    private OffsetDateTime startsAt;
    private OffsetDateTime expiresAt;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String featureSnapshotJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
