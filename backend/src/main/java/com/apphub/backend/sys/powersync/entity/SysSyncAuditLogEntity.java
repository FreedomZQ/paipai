package com.apphub.backend.sys.powersync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("sys_sync_audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysSyncAuditLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String installationId;
    private String actionType;
    private String entityType;
    private String entityId;
    private String requestId;
    private String resultStatus;
    private String detailJson;
    private OffsetDateTime createdAt;
}
