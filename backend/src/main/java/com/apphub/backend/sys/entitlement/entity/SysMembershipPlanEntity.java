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
 * 统一权益中心会员等级实体。
 * 中文说明：同一个后端同时服务多个 App，所有会员等级必须通过 appCode 隔离，planCode 只在单个 App 内有意义。
 */
@TableName(value = "sys_membership_plan", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysMembershipPlanEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private String planCode;
    private String displayName;
    private String description;
    private Integer planLevel;
    private String entitlementCode;
    private String status;
    private Integer sortOrder;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
