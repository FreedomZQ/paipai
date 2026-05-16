package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.domain.entity.ReadingDailyTaskCompletionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * reading 每日任务完成 Mapper。
 * 用于校验用户今天是否已完成后端生成的任务，避免客户端伪造完成态。
 */
@Mapper
public interface ReadingDailyTaskCompletionMapper extends BaseMapper<ReadingDailyTaskCompletionEntity> {
    @Select("""
        SELECT COUNT(*)
        FROM reading_daily_task_completion
        WHERE user_id = #{userId}
          AND task_date = #{taskDate}
        """)
    int countByUserAndDate(@Param("userId") Long userId, @Param("taskDate") LocalDate taskDate);

    @Select("""
        SELECT COUNT(*)
        FROM reading_daily_task_completion
        WHERE user_id = #{userId}
          AND child_id = #{childId}
          AND task_date = #{taskDate}
        """)
    int countByUserChildAndDate(@Param("userId") Long userId, @Param("childId") String childId, @Param("taskDate") LocalDate taskDate);
}
