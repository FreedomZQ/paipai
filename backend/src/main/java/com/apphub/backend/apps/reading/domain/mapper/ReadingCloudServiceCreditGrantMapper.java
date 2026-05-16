package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.domain.entity.ReadingCloudServiceCreditGrantEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface ReadingCloudServiceCreditGrantMapper extends BaseMapper<ReadingCloudServiceCreditGrantEntity> {
    @Select("""
        SELECT *
        FROM reading_cloud_service_credit_grant
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND service_type = #{serviceType}
          AND expires_at > #{now}
          AND used_count < total_count
        ORDER BY
          expires_at ASC,
          CASE grant_type WHEN 'gift' THEN 0 WHEN 'paid' THEN 1 ELSE 2 END,
          id ASC
        """)
    List<ReadingCloudServiceCreditGrantEntity> selectActiveUsable(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("serviceType") String serviceType,
        @Param("now") OffsetDateTime now
    );

    @Select("""
        SELECT *
        FROM reading_cloud_service_credit_grant
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND service_type = #{serviceType}
          AND source_type = #{sourceType}
          AND source_ref = #{sourceRef}
        ORDER BY id DESC
        LIMIT 1
        """)
    ReadingCloudServiceCreditGrantEntity selectBySourceRef(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("serviceType") String serviceType,
        @Param("sourceType") String sourceType,
        @Param("sourceRef") String sourceRef
    );

    @Select("""
        <script>
        SELECT *
        FROM reading_cloud_service_credit_grant
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
        <if test="serviceType != null">
          AND service_type = #{serviceType}
        </if>
          AND expires_at > #{now}
        ORDER BY service_type ASC, expires_at ASC, id DESC
        </script>
        """)
    List<ReadingCloudServiceCreditGrantEntity> selectActiveByUser(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("serviceType") String serviceType,
        @Param("now") OffsetDateTime now
    );

    @Select("""
        <script>
        SELECT *
        FROM reading_cloud_service_credit_grant
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND created_at >= #{cutoff}
        <if test="serviceType != null">
          AND service_type = #{serviceType}
        </if>
        ORDER BY created_at DESC, id DESC
        </script>
        """)
    List<ReadingCloudServiceCreditGrantEntity> selectRecentByUser(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("serviceType") String serviceType,
        @Param("cutoff") OffsetDateTime cutoff,
        @Param("now") OffsetDateTime now
    );

    @Select("""
        SELECT COALESCE(SUM(total_count), 0)
        FROM reading_cloud_service_credit_grant
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND service_type = #{serviceType}
          AND grant_type = #{grantType}
          AND expires_at > #{now}
        """)
    int sumActiveTotal(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("serviceType") String serviceType,
        @Param("grantType") String grantType,
        @Param("now") OffsetDateTime now
    );

    @Select("""
        SELECT COALESCE(SUM(used_count), 0)
        FROM reading_cloud_service_credit_grant
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND service_type = #{serviceType}
          AND grant_type = #{grantType}
          AND expires_at > #{now}
        """)
    int sumActiveUsed(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("serviceType") String serviceType,
        @Param("grantType") String grantType,
        @Param("now") OffsetDateTime now
    );

    @Select("""
        SELECT COALESCE(SUM(total_count), 0)
        FROM reading_cloud_service_credit_grant
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND service_type = #{serviceType}
          AND expires_at > #{now}
        """)
    int sumActiveTotalAllTypes(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("serviceType") String serviceType,
        @Param("now") OffsetDateTime now
    );

    @Select("""
        SELECT COALESCE(SUM(used_count), 0)
        FROM reading_cloud_service_credit_grant
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND service_type = #{serviceType}
          AND expires_at > #{now}
        """)
    int sumActiveUsedAllTypes(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("serviceType") String serviceType,
        @Param("now") OffsetDateTime now
    );

    @Select("""
        SELECT COUNT(*)
        FROM reading_cloud_service_credit_grant
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND service_type = #{serviceType}
          AND source_type = 'internal_purchase'
          AND created_at >= #{dayStart}
          AND created_at < #{dayEnd}
        """)
    int countDailyInternalPurchases(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("serviceType") String serviceType,
        @Param("dayStart") OffsetDateTime dayStart,
        @Param("dayEnd") OffsetDateTime dayEnd
    );
}
