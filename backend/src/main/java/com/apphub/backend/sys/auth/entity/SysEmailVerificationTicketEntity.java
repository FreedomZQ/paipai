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

@TableName(value = "sys_email_verification_ticket", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysEmailVerificationTicketEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private String email;
    private String sceneCode;
    private String codeHash;
    private String status;
    private Integer attemptCount;
    private Integer maxAttemptCount;
    private OffsetDateTime expiresAt;
    private OffsetDateTime verifiedAt;
    private OffsetDateTime consumedAt;
    private String requestIp;
    @TableField(jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String payloadJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
