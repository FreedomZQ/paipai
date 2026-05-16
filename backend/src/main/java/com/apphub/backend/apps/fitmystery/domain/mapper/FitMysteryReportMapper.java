package com.apphub.backend.apps.fitmystery.domain.mapper;

import com.apphub.backend.apps.fitmystery.domain.entity.FitReportGenerationLedgerEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface FitMysteryReportMapper extends BaseMapper<FitReportGenerationLedgerEntity> {
    @Select("SELECT COALESCE(MAX(balance_after), 0) FROM fit_report_generation_ledger WHERE app_code=#{appCode} AND user_id=#{userId}")
    int currentBalance(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM fit_report_generation_ledger WHERE app_code=#{appCode} AND user_id=#{userId} AND source_type='free_initial_grant'")
    int countInitialGrant(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM fit_report_generation_ledger WHERE app_code=#{appCode} AND user_id=#{userId} AND idempotency_key=#{idempotencyKey}")
    int countByIdempotency(@Param("appCode") String appCode, @Param("userId") Long userId, @Param("idempotencyKey") String idempotencyKey);

    @Insert("""
        INSERT INTO fit_report_generation_ledger (id, app_code, user_id, ledger_type, report_type, quota_delta, balance_after, source_type, period_key, local_data_hash, idempotency_key, created_at)
        VALUES (CAST(#{id} AS uuid), #{appCode}, #{userId}, #{ledgerType}, #{reportType}, #{quotaDelta}, #{balanceAfter}, #{sourceType}, #{periodKey}, #{localDataHash}, #{idempotencyKey}, #{createdAt})
        """)
    void insertLedger(@Param("id") String id,
                      @Param("appCode") String appCode,
                      @Param("userId") Long userId,
                      @Param("ledgerType") String ledgerType,
                      @Param("reportType") String reportType,
                      @Param("quotaDelta") int quotaDelta,
                      @Param("balanceAfter") int balanceAfter,
                      @Param("sourceType") String sourceType,
                      @Param("periodKey") String periodKey,
                      @Param("localDataHash") String localDataHash,
                      @Param("idempotencyKey") String idempotencyKey,
                      @Param("createdAt") OffsetDateTime createdAt);

    @Select("""
        SELECT ledger_type AS "ledgerType", report_type AS "reportType", quota_delta AS "quotaDelta", balance_after AS "balanceAfter", source_type AS "sourceType", period_key AS "periodKey", created_at AS "createdAt"
        FROM fit_report_generation_ledger
        WHERE app_code=#{appCode} AND user_id=#{userId}
        ORDER BY created_at DESC
        LIMIT #{limit}
        """)
    List<Map<String, Object>> recent(@Param("appCode") String appCode, @Param("userId") Long userId, @Param("limit") int limit);
}
