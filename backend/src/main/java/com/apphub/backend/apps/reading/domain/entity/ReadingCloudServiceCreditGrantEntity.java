package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("reading_cloud_service_credit_grant")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingCloudServiceCreditGrantEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String serviceType;
    private String grantType;
    private Integer totalCount;
    private Integer usedCount;
    private String sourceType;
    private String sourceRef;
    private String productCode;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
