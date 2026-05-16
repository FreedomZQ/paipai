package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.domain.entity.ReadingReviewCardEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * reading 句卡 Mapper。
 * 句卡查询始终限定 userId，保证付费内容和孩子数据不会跨账号泄漏。
 */
@Mapper
public interface ReadingReviewCardMapper extends BaseMapper<ReadingReviewCardEntity> {
    @Select("""
        SELECT *
        FROM reading_review_card
        WHERE user_id = #{userId}
          AND card_status = 'active'
        ORDER BY created_at DESC
        LIMIT #{limit}
        """)
    List<ReadingReviewCardEntity> selectRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("""
        SELECT *
        FROM reading_review_card
        WHERE user_id = #{userId}
          AND child_id = #{childId}
          AND card_status = 'active'
        ORDER BY created_at DESC
        LIMIT #{limit}
        """)
    List<ReadingReviewCardEntity> selectRecentByChild(@Param("userId") Long userId, @Param("childId") String childId, @Param("limit") int limit);

    @Select("""
        SELECT *
        FROM reading_review_card
        WHERE user_id = #{userId}
          AND card_status = 'active'
          AND (next_review_at IS NULL OR next_review_at <= #{now})
        ORDER BY next_review_at ASC NULLS FIRST, created_at DESC
        LIMIT #{limit}
        """)
    List<ReadingReviewCardEntity> selectDueByUser(@Param("userId") Long userId, @Param("now") OffsetDateTime now, @Param("limit") int limit);

    @Select("""
        SELECT COUNT(*)
        FROM reading_review_card
        WHERE user_id = #{userId}
          AND card_status = 'active'
        """)
    int countActiveByUser(@Param("userId") Long userId);

    @Select("""
        SELECT COUNT(*)
        FROM reading_review_card
        WHERE user_id = #{userId}
          AND card_status = 'active'
          AND created_at >= #{startInclusive}
          AND created_at < #{endExclusive}
        """)
    int countCreatedBetween(@Param("userId") Long userId, @Param("startInclusive") OffsetDateTime startInclusive, @Param("endExclusive") OffsetDateTime endExclusive);

    @Select("""
        SELECT COUNT(*)
        FROM reading_review_card
        WHERE user_id = #{userId}
          AND child_id = #{childId}
          AND card_status = 'active'
          AND created_at >= #{startInclusive}
          AND created_at < #{endExclusive}
        """)
    int countCreatedByChildBetween(@Param("userId") Long userId, @Param("childId") String childId, @Param("startInclusive") OffsetDateTime startInclusive, @Param("endExclusive") OffsetDateTime endExclusive);

    @Select("""
        SELECT COUNT(*)
        FROM reading_review_card
        WHERE user_id = #{userId}
          AND child_id = #{childId}
          AND card_status = 'active'
        """)
    int countActiveByChild(@Param("userId") Long userId, @Param("childId") String childId);

    @Select("""
        SELECT COUNT(*)
        FROM reading_review_card
        WHERE user_id = #{userId}
          AND card_status = 'active'
          AND (next_review_at IS NULL OR next_review_at <= #{now})
        """)
    int countDueByUser(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    @Select("""
        SELECT COUNT(*)
        FROM reading_review_card
        WHERE user_id = #{userId}
          AND child_id = #{childId}
          AND card_status = 'active'
          AND (next_review_at IS NULL OR next_review_at <= #{now})
        """)
    int countDueByChild(@Param("userId") Long userId, @Param("childId") String childId, @Param("now") OffsetDateTime now);

    @Select("""
        SELECT *
        FROM reading_review_card
        WHERE id = #{cardId}
          AND user_id = #{userId}
          AND card_status = 'active'
        LIMIT 1
        """)
    ReadingReviewCardEntity selectActiveByIdAndUser(@Param("cardId") String cardId, @Param("userId") Long userId);

    @Update("""
        UPDATE reading_review_card
        SET card_status = 'deleted',
            updated_at = #{now}
        WHERE user_id = #{userId}
          AND card_status = 'active'
        """)
    int deactivateAllByUser(@Param("userId") Long userId, @Param("now") java.time.OffsetDateTime now);
}
