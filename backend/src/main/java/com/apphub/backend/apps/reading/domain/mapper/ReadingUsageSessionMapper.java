package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.domain.entity.ReadingUsageSessionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;

@Mapper
public interface ReadingUsageSessionMapper extends BaseMapper<ReadingUsageSessionEntity> {

    @Select({
        "SELECT *",
        "FROM reading_usage_session",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}",
        "  AND session_uuid = #{sessionUuid}",
        "  AND ended_at IS NULL",
        "ORDER BY created_at DESC, id DESC",
        "LIMIT 1"
    })
    ReadingUsageSessionEntity selectActiveByUserAndSessionUuid(
        @Param("userId") Long userId,
        @Param("sessionUuid") String sessionUuid
    );

    @Select({
        "SELECT COALESCE(MAX(COALESCE(ended_at, updated_at, started_at)), NULL)",
        "FROM reading_usage_session",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}",
        "  AND child_id = #{childId}"
    })
    OffsetDateTime selectLastUsedAtByUserAndChild(
        @Param("userId") Long userId,
        @Param("childId") String childId
    );

    @Select({
        "SELECT COALESCE(MAX(COALESCE(ended_at, updated_at, started_at)), NULL)",
        "FROM reading_usage_session",
        "WHERE app_code = '" + ReadingAppModule.APP_CODE + "'",
        "  AND user_id = #{userId}"
    })
    OffsetDateTime selectLastUsedAtByUser(@Param("userId") Long userId);
}
