package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.domain.entity.ReadingWeeklyReportSnapshotEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * reading 周报快照 Mapper。
 * 查询条件必须包含 appCode + userId，保证统一后端多 App、多账号之间不会串数据。
 */
@Mapper
public interface ReadingWeeklyReportSnapshotMapper extends BaseMapper<ReadingWeeklyReportSnapshotEntity> {
    @Select("""
        SELECT *
        FROM reading_weekly_report_snapshot
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND scope = #{scope}
          AND child_id IS NOT DISTINCT FROM #{childId}
          AND week_start = #{weekStart}
          AND plan_code = #{planCode}
          AND report_status = 'active'
        ORDER BY updated_at DESC
        LIMIT 1
        """)
    ReadingWeeklyReportSnapshotEntity selectActiveSnapshot(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("childId") String childId,
        @Param("scope") String scope,
        @Param("weekStart") LocalDate weekStart,
        @Param("planCode") String planCode
    );
}
