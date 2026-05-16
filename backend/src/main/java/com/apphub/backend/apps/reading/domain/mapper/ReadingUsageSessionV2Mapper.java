package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.domain.entity.ReadingUsageSessionV2Entity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;

@Mapper
public interface ReadingUsageSessionV2Mapper extends BaseMapper<ReadingUsageSessionV2Entity> {

    @Select({
        "SELECT *",
        "FROM reading_usage_session",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}",
        "  AND id = #{sessionUuid}",
        "  AND ended_at IS NULL",
        "  AND deleted_at IS NULL",
        "ORDER BY created_at DESC",
        "LIMIT 1"
    })
    ReadingUsageSessionV2Entity selectActiveByUserAndSessionUuid(
        @Param("userId") Long userId,
        @Param("sessionUuid") String sessionUuid
    );

    @Select({
        "SELECT COALESCE(MAX(COALESCE(ended_at, updated_at, started_at)), NULL)",
        "FROM reading_usage_session",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}",
        "  AND child_id = #{childId}",
        "  AND deleted_at IS NULL"
    })
    OffsetDateTime selectLastUsedAtByUserAndChild(
        @Param("userId") Long userId,
        @Param("childId") String childId
    );

    @Select({
        "SELECT COALESCE(MAX(COALESCE(ended_at, updated_at, started_at)), NULL)",
        "FROM reading_usage_session",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}",
        "  AND deleted_at IS NULL"
    })
    OffsetDateTime selectLastUsedAtByUser(@Param("userId") Long userId);

    @Select({
        "SELECT *",
        "FROM reading_usage_session",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}",
        "  AND id = #{id}",
        "LIMIT 1"
    })
    ReadingUsageSessionV2Entity selectByIdAndUser(@Param("id") String id, @Param("userId") Long userId);

    @Delete({
        "DELETE FROM reading_usage_session",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}",
        "  AND COALESCE(ended_at, updated_at, started_at) < #{cutoffAt}"
    })
    int deleteByUserBefore(@Param("userId") Long userId, @Param("cutoffAt") OffsetDateTime cutoffAt);
}
