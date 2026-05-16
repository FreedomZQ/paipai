package com.apphub.backend.sys.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 持久化实体 `SysAuthProviderTokenEntity`。
 * 该类用于在 MyBatis Plus 中承载字段映射，对应数据库表 `sys_auth_provider_token`。
 */

@TableName("sys_auth_provider_token")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysAuthProviderTokenEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String providerCode;
    private String providerSubject;
    private String refreshToken;
    private String accessToken;
    private String tokenType;
    private String status;
    private String payloadJson;
    private String refreshTokenKeyId;
    private String refreshTokenEncryptionAlgorithm;
    private String refreshTokenNonceBase64;
    private String refreshTokenCiphertextBase64;
    private OffsetDateTime refreshTokenLastCapturedAt;
    private OffsetDateTime refreshTokenLastUsedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
