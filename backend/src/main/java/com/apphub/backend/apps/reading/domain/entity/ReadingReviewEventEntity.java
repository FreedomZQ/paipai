package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * reading 复习事件实体。
 * 用于记录用户完成句卡复习的后端事实，周报和每日任务统计都以该表为依据。
 */
@TableName("reading_review_event")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingReviewEventEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String childId;
    private String cardId;
    private String eventType;
    private String resultLevel;
    private OffsetDateTime createdAt;
}
