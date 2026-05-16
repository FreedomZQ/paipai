package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * reading 云端服务次数实体。
 * 仅用于控制需要消耗开发者成本的云端 OCR / 云端朗读，不影响设备自带能力离线运行。
 */
@TableName("reading_cloud_service_usage")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingCloudServiceUsageEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String serviceType;
    private Integer trialLimit;
    private Integer trialUsed;
    private Integer purchasedCredits;
    private Integer purchasedUsed;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
