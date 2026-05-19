package com.apphub.backend.sys.billing.entity;

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
 * 持久化实体 `SysEntitlementSnapshotEntity`。
 * 该类用于在 MyBatis Plus 中承载字段映射，对应数据库表 `sys_entitlement_snapshot`。
 */

@TableName(value = "sys_entitlement_snapshot", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysEntitlementSnapshotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String entitlementCode;
    private String status;
    private String sourceType;
    private OffsetDateTime expiresAt;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String payloadJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
