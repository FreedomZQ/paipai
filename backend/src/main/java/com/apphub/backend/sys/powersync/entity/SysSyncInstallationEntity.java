package com.apphub.backend.sys.powersync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("sys_sync_installation")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysSyncInstallationEntity {
    @TableId(type = IdType.INPUT)
    private String installationId;
    private String appCode;
    private Long userId;
    private String deviceId;
    private String clientPlatform;
    private String deviceModel;
    private String appVersion;
    private String powersyncClientId;
    private Boolean cloudSyncEnabled;
    private Boolean initialSyncCompleted;
    private OffsetDateTime lastSyncAt;
    private OffsetDateTime lastPullAt;
    private OffsetDateTime lastPushAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
