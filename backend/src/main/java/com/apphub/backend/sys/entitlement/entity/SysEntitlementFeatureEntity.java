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
 * 统一权益中心功能定义实体。
 * 中文说明：featureCode 是后端权益判断的稳定语义键，各 App 可把它映射到自己的旧字段，避免前端大改。
 */
@TableName(value = "sys_entitlement_feature", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysEntitlementFeatureEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private String featureCode;
    private String featureName;
    private String featureType;
    private String description;
    private Boolean defaultEnabled;
    private Boolean backendEnforced;
    private Boolean frontendVisible;
    private String status;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
