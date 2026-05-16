package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.domain.entity.ReadingChildProfileEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * reading 孩子档案 Mapper。
 * 所有孩子档案读写都通过后端鉴权后的 userId 过滤，避免跨账号访问。
 */
@Mapper
public interface ReadingChildProfileMapper extends BaseMapper<ReadingChildProfileEntity> {
    @Select("""
        SELECT *
        FROM reading_child_profile
        WHERE user_id = #{userId}
          AND profile_status = 'active'
        ORDER BY created_at ASC
        """)
    List<ReadingChildProfileEntity> selectActiveByUser(@Param("userId") Long userId);

    @Select("""
        SELECT *
        FROM reading_child_profile
        WHERE id = #{childId}
          AND user_id = #{userId}
          AND profile_status = 'active'
        LIMIT 1
        """)
    ReadingChildProfileEntity selectActiveByIdAndUser(@Param("childId") String childId, @Param("userId") Long userId);

    @Select("""
        SELECT COUNT(*)
        FROM reading_child_profile
        WHERE user_id = #{userId}
          AND profile_status = 'active'
        """)
    int countActiveByUser(@Param("userId") Long userId);

    @Update("""
        UPDATE reading_child_profile
        SET profile_status = 'deleted',
            updated_at = #{now}
        WHERE user_id = #{userId}
          AND profile_status = 'active'
        """)
    int deactivateAllByUser(@Param("userId") Long userId, @Param("now") java.time.OffsetDateTime now);
}
