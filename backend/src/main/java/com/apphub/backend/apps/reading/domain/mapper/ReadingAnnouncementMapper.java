package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.domain.entity.ReadingAnnouncementEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * reading 公告 Mapper。
 * 用于读取与查询窗口相交的公告，既包含近期开过的，也包含即将到达展示时间窗、需要客户端提前缓存的公告。
 */
@Mapper
public interface ReadingAnnouncementMapper extends BaseMapper<ReadingAnnouncementEntity> {
    @Select("""
        SELECT *
        FROM reading_announcement
        WHERE app_code = #{appCode}
          AND status = 'published'
          AND visible_start_at <= #{futureEnd}
          AND (visible_end_at IS NULL OR visible_end_at >= #{historyStart})
        ORDER BY visible_start_at DESC, updated_at DESC, id DESC
        """)
    List<ReadingAnnouncementEntity> selectRecentPublished(
        @Param("appCode") String appCode,
        @Param("futureEnd") OffsetDateTime futureEnd,
        @Param("historyStart") OffsetDateTime historyStart
    );
}
