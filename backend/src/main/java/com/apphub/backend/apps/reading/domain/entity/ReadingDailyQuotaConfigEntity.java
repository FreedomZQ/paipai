package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("reading_daily_quota_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingDailyQuotaConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private String planCode;
    private String featureCode;
    private Integer dailyLimit;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
