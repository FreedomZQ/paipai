package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.domain.entity.ReadingCloudServiceUsageEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * reading 云端服务次数 Mapper。
 * 用于查询和更新云端 OCR / 云端朗读试用和购买次数余量。
 */
@Mapper
public interface ReadingCloudServiceUsageMapper extends BaseMapper<ReadingCloudServiceUsageEntity> {
    @Select("""
        SELECT *
        FROM reading_cloud_service_usage
        WHERE user_id = #{userId}
          AND service_type = #{serviceType}
        LIMIT 1
        """)
    ReadingCloudServiceUsageEntity selectByUserAndServiceType(
        @Param("userId") Long userId,
        @Param("serviceType") String serviceType
    );
}
