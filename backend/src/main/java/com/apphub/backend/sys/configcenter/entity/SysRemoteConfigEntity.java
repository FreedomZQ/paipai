package com.apphub.backend.sys.configcenter.entity;

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
 * 持久化实体 `SysRemoteConfigEntity`。
 * 该类用于在 MyBatis Plus 中承载字段映射，对应数据库表 `sys_remote_config`。
 */

@TableName(value = "sys_remote_config", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysRemoteConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private String namespaceCode;
    private String configKey;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String configValueJson;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
