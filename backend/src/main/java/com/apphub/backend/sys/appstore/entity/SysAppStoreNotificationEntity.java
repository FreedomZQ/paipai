package com.apphub.backend.sys.appstore.entity;

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
 * 持久化实体 `SysAppStoreNotificationEntity`。
 * 该类用于在 MyBatis Plus 中承载字段映射，对应数据库表 `sys_app_store_notification`。
 */

@TableName(value = "sys_app_store_notification", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysAppStoreNotificationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private String notificationUuid;
    private String notificationType;
    private String subtype;
    private String signedPayloadHash;
    private String verificationStatus;
    private String processingStatus;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String rawPayloadJson;
    private OffsetDateTime receivedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
