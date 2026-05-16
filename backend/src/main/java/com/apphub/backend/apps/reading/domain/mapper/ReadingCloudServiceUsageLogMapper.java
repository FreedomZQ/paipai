package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.domain.entity.ReadingCloudServiceUsageLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReadingCloudServiceUsageLogMapper extends BaseMapper<ReadingCloudServiceUsageLogEntity> {
    @Select("""
        <script>
        SELECT *
        FROM reading_cloud_service_usage_log
        WHERE user_id = #{userId}
        <if test="serviceType != null">
          AND service_type = #{serviceType}
        </if>
        ORDER BY created_at DESC, id DESC
        LIMIT #{limit}
        </script>
        """)
    List<ReadingCloudServiceUsageLogEntity> selectRecentByUser(
        @Param("userId") Long userId,
        @Param("serviceType") String serviceType,
        @Param("limit") int limit
    );
}
