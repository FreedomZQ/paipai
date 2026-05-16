package com.apphub.backend.sys.auth.entity;

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

@TableName(value = "sys_user_device_event", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysUserDeviceEventEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private Long sessionId;
    private String eventType;
    private String bundleId;
    private String clientPlatform;
    private String deviceModel;
    private String systemName;
    private String systemVersion;
    private String appVersion;
    private String buildNumber;
    private String locale;
    private String ipCountry;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String payloadJson;
    private OffsetDateTime createdAt;
}
