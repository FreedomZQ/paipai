package com.apphub.backend.sys.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 持久化实体 `SysUserIdentityEntity`。
 * 该类用于在 MyBatis Plus 中承载字段映射，对应数据库表 `sys_user_identity`。
 */

@TableName("sys_user_identity")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysUserIdentityEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String providerCode;
    private String providerSubject;
    private String email;
    private Boolean emailVerified;
    private Boolean privateEmail;
    private String status;
    private String payloadJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
