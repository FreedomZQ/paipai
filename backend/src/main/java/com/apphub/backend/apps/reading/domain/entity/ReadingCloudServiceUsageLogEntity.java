package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("reading_cloud_service_usage_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingCloudServiceUsageLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String serviceType;
    private Integer delta;
    private Integer beforeRemaining;
    private Integer afterRemaining;
    private Integer beforeTrialUsed;
    private Integer afterTrialUsed;
    private Integer beforePurchasedCredits;
    private Integer afterPurchasedCredits;
    private Integer beforePurchasedUsed;
    private Integer afterPurchasedUsed;
    private String reason;
    private String operatorType;
    private String operatorId;
    private String idempotencyKey;
    private OffsetDateTime createdAt;
}
