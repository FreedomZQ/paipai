package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("reading_child_usage_daily")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingChildUsageDailyEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String childId;
    private LocalDate usageDate;
    private Integer durationSeconds;
    private Integer sessionCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
