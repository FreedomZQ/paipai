package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.domain.entity.ReadingReviewEventV2Entity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;

@Mapper
public interface ReadingReviewEventV2Mapper extends BaseMapper<ReadingReviewEventV2Entity> {

    @Select("""
        SELECT *
        FROM reading_review_event
        WHERE id = #{id}
          AND user_id = #{userId}
        LIMIT 1
        """)
    ReadingReviewEventV2Entity selectByIdAndUser(@Param("id") String id, @Param("userId") Long userId);

    @Select("""
        SELECT COUNT(*)
        FROM reading_review_event
        WHERE user_id = #{userId}
          AND event_at >= #{startInclusive}
          AND event_at < #{endExclusive}
        """)
    int countByUserBetween(@Param("userId") Long userId, @Param("startInclusive") OffsetDateTime startInclusive, @Param("endExclusive") OffsetDateTime endExclusive);

    @Select("""
        SELECT COUNT(*)
        FROM reading_review_event
        WHERE user_id = #{userId}
          AND child_id = #{childId}
          AND event_at >= #{startInclusive}
          AND event_at < #{endExclusive}
        """)
    int countByChildBetween(@Param("userId") Long userId, @Param("childId") String childId, @Param("startInclusive") OffsetDateTime startInclusive, @Param("endExclusive") OffsetDateTime endExclusive);

    @Select("""
        SELECT COUNT(DISTINCT CAST(event_at AS DATE))
        FROM reading_review_event
        WHERE user_id = #{userId}
          AND event_at >= #{startInclusive}
          AND event_at < #{endExclusive}
        """)
    int countActiveDaysByUserBetween(@Param("userId") Long userId, @Param("startInclusive") OffsetDateTime startInclusive, @Param("endExclusive") OffsetDateTime endExclusive);

    @Select("""
        SELECT COUNT(DISTINCT CAST(event_at AS DATE))
        FROM reading_review_event
        WHERE user_id = #{userId}
          AND child_id = #{childId}
          AND event_at >= #{startInclusive}
          AND event_at < #{endExclusive}
        """)
    int countActiveDaysByChildBetween(@Param("userId") Long userId, @Param("childId") String childId, @Param("startInclusive") OffsetDateTime startInclusive, @Param("endExclusive") OffsetDateTime endExclusive);

    @Delete("DELETE FROM reading_review_event WHERE user_id = #{userId}")
    int deleteByUser(@Param("userId") Long userId);
}
