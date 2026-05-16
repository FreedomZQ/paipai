package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.domain.entity.ReadingDailyQuotaConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReadingDailyQuotaConfigMapper extends BaseMapper<ReadingDailyQuotaConfigEntity> {
    @Select("""
        SELECT *
        FROM reading_daily_quota_config
        WHERE app_code = #{appCode}
          AND plan_code = #{planCode}
          AND feature_code = #{featureCode}
          AND status = 'active'
        ORDER BY updated_at DESC, id DESC
        LIMIT 1
        """)
    ReadingDailyQuotaConfigEntity selectActive(
        @Param("appCode") String appCode,
        @Param("planCode") String planCode,
        @Param("featureCode") String featureCode
    );
}
