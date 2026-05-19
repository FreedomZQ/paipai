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
 * 统一权益中心等级-功能规则实体。
 * 中文说明：后台调高权益会被实时合并给有效期内老用户；调低权益不会覆盖购买时快照中的已付承诺。
 */
@TableName(value = "sys_plan_feature_rule", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysPlanFeatureRuleEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private String planCode;
    private String featureCode;
    private Boolean enabled;
    private String accessLevel;
    private Integer limitValue;
    private String limitUnit;
    private String scopeCode;
    private Integer priority;
    private OffsetDateTime effectiveStartAt;
    private OffsetDateTime effectiveEndAt;
    private String status;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String ruleJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
