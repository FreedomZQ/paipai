package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * reading 每日任务完成实体。
 * 客户端只能提交完成动作，是否计入今日任务由后端按账号与孩子维度判定。
 */
@TableName("reading_daily_task_completion")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingDailyTaskCompletionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String childId;
    private String taskId;
    private String completionType;
    private LocalDate taskDate;
    private OffsetDateTime completedAt;
}
