package com.apphub.backend.sys.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 持久化实体 `SysAuthSessionEntity`。
 * 该类用于在 MyBatis Plus 中承载字段映射，对应数据库表 `sys_auth_session`。
 */

@TableName("sys_auth_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysAuthSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String sessionTokenHash;
    private String sessionSource;
    private String deviceId;
    private String clientPlatform;
    private String clientVersion;
    private String status;
    private OffsetDateTime expiresAt;
    private OffsetDateTime revokedAt;
    private OffsetDateTime lastSeenAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
