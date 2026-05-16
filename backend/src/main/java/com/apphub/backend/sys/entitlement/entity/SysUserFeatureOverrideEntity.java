package com.apphub.backend.sys.entitlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户级功能覆盖实体。
 * 中文说明：用于客服补偿、风控关闭或单用户灰度；这是显式最高优先级覆盖，必须保留 reason/审计线索。
 */
@TableName("sys_user_feature_override")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysUserFeatureOverrideEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String featureCode;
    private Boolean enabled;
    private String accessLevel;
    private Integer limitValue;
    private String limitUnit;
    private String scopeCode;
    private String status;
    private OffsetDateTime startsAt;
    private OffsetDateTime expiresAt;
    private String reason;
    private Long operatorUserId;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
