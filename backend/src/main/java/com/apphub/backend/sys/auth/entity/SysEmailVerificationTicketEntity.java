package com.apphub.backend.sys.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("sys_email_verification_ticket")
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
    private String payloadJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
