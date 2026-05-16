package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.domain.entity.ReadingReviewEventEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;

/**
 * reading 复习事件 Mapper。
 * 复习行为是周报和成长统计依据，所有统计都以后端事件记录为准。
 */
@Mapper
public interface ReadingReviewEventMapper extends BaseMapper<ReadingReviewEventEntity> {
    @Select("""
        SELECT COUNT(*)
        FROM reading_review_event
        WHERE user_id = #{userId}
          AND created_at >= #{startInclusive}
          AND created_at < #{endExclusive}
        """)
    int countByUserBetween(@Param("userId") Long userId, @Param("startInclusive") OffsetDateTime startInclusive, @Param("endExclusive") OffsetDateTime endExclusive);

    @Select("""
        SELECT COUNT(*)
        FROM reading_review_event
        WHERE user_id = #{userId}
          AND child_id = #{childId}
          AND created_at >= #{startInclusive}
          AND created_at < #{endExclusive}
        """)
    int countByChildBetween(@Param("userId") Long userId, @Param("childId") String childId, @Param("startInclusive") OffsetDateTime startInclusive, @Param("endExclusive") OffsetDateTime endExclusive);

    @Select("""
        SELECT COUNT(DISTINCT CAST(created_at AS DATE))
        FROM reading_review_event
        WHERE user_id = #{userId}
          AND created_at >= #{startInclusive}
          AND created_at < #{endExclusive}
        """)
    int countActiveDaysByUserBetween(@Param("userId") Long userId, @Param("startInclusive") OffsetDateTime startInclusive, @Param("endExclusive") OffsetDateTime endExclusive);

    @Delete("DELETE FROM reading_review_event WHERE user_id = #{userId}")
    int deleteByUser(@Param("userId") Long userId);
}
