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

@TableName(value = "sys_privacy_consent", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysPrivacyConsentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String consentType;
    private String consentStatus;
    private String policyVersion;
    private String regionCode;
    private String sourceType;
    private String sourceRef;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String metadataJson;
    private OffsetDateTime consentedAt;
    private OffsetDateTime revokedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
