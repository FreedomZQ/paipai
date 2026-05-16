package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.domain.entity.ReadingChildUsageDailyEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface ReadingChildUsageDailyMapper extends BaseMapper<ReadingChildUsageDailyEntity> {

    @Select({
        "SELECT *",
        "FROM reading_child_usage_daily",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}",
        "  AND child_id = #{childId}",
        "  AND usage_date = #{usageDate}",
        "LIMIT 1"
    })
    ReadingChildUsageDailyEntity selectByUserChildDate(
        @Param("userId") Long userId,
        @Param("childId") String childId,
        @Param("usageDate") LocalDate usageDate
    );

    @Insert({
        "INSERT INTO reading_child_usage_daily(",
        "    app_code, user_id, child_id, usage_date, duration_seconds, session_count, created_at, updated_at",
        ") VALUES (",
        "    '" + ReadingAppModule.APP_CODE + "', #{userId}, #{childId}, #{usageDate}, #{durationSeconds}, #{sessionCount}, #{now}, #{now}",
        ")",
        "ON CONFLICT (user_id, child_id, usage_date)",
        "DO UPDATE SET",
        "    duration_seconds = reading_child_usage_daily.duration_seconds + EXCLUDED.duration_seconds,",
        "    session_count = reading_child_usage_daily.session_count + EXCLUDED.session_count,",
        "    updated_at = EXCLUDED.updated_at"
    })
    int upsertDuration(
        @Param("userId") Long userId,
        @Param("childId") String childId,
        @Param("usageDate") LocalDate usageDate,
        @Param("durationSeconds") Integer durationSeconds,
        @Param("sessionCount") Integer sessionCount,
        @Param("now") java.time.OffsetDateTime now
    );

    @Select({
        "SELECT COALESCE(SUM(duration_seconds), 0)",
        "FROM reading_child_usage_daily",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}",
        "  AND child_id = #{childId}"
    })
    Integer sumDurationByUserChild(
        @Param("userId") Long userId,
        @Param("childId") String childId
    );

    @Select({
        "SELECT COALESCE(SUM(duration_seconds), 0)",
        "FROM reading_child_usage_daily",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}",
        "  AND child_id = #{childId}",
        "  AND usage_date >= #{startDate}",
        "  AND usage_date < #{endDateExclusive}"
    })
    Integer sumDurationByUserChildRange(
        @Param("userId") Long userId,
        @Param("childId") String childId,
        @Param("startDate") LocalDate startDate,
        @Param("endDateExclusive") LocalDate endDateExclusive
    );

    @Select({
        "SELECT COALESCE(SUM(duration_seconds), 0)",
        "FROM reading_child_usage_daily",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}"
    })
    Integer sumDurationByUser(@Param("userId") Long userId);

    @Select({
        "SELECT COALESCE(SUM(duration_seconds), 0)",
        "FROM reading_child_usage_daily",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}",
        "  AND usage_date >= #{startDate}",
        "  AND usage_date < #{endDateExclusive}"
    })
    Integer sumDurationByUserRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDateExclusive") LocalDate endDateExclusive
    );

    @Select({
        "SELECT COALESCE(SUM(session_count), 0)",
        "FROM reading_child_usage_daily",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}",
        "  AND child_id = #{childId}",
        "  AND usage_date = #{usageDate}"
    })
    Integer sumSessionCountByUserChildDate(
        @Param("userId") Long userId,
        @Param("childId") String childId,
        @Param("usageDate") LocalDate usageDate
    );

    @Select({
        "SELECT COALESCE(SUM(session_count), 0)",
        "FROM reading_child_usage_daily",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}",
        "  AND usage_date = #{usageDate}"
    })
    Integer sumSessionCountByUserDate(
        @Param("userId") Long userId,
        @Param("usageDate") LocalDate usageDate
    );

    @Delete({
        "DELETE FROM reading_child_usage_daily",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}",
        "  AND usage_date < #{cutoffDate}"
    })
    int deleteByUserBeforeDate(@Param("userId") Long userId, @Param("cutoffDate") LocalDate cutoffDate);
}
