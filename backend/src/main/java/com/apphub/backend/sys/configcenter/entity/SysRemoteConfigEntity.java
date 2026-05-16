package com.apphub.backend.sys.configcenter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 持久化实体 `SysRemoteConfigEntity`。
 * 该类用于在 MyBatis Plus 中承载字段映射，对应数据库表 `sys_remote_config`。
 */

@TableName("sys_remote_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysRemoteConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private String namespaceCode;
    private String configKey;
    private String configValueJson;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
