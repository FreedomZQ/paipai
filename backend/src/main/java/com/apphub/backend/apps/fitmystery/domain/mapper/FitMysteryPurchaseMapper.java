package com.apphub.backend.apps.fitmystery.domain.mapper;

import com.apphub.backend.apps.fitmystery.domain.entity.FitDrawChanceLedgerEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.time.OffsetDateTime;

@Mapper
public interface FitMysteryPurchaseMapper extends BaseMapper<FitDrawChanceLedgerEntity> {
    @Select("SELECT COALESCE(MAX(balance_after), 0) FROM fit_draw_chance_ledger WHERE app_code=#{appCode} AND user_id=#{userId}")
    int currentChanceBalance(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM fit_draw_chance_ledger WHERE app_code=#{appCode} AND user_id=#{userId} AND idempotency_key=#{idempotencyKey}")
    int countChanceLedgerByIdempotency(@Param("appCode") String appCode, @Param("userId") Long userId, @Param("idempotencyKey") String idempotencyKey);

    @Insert("""
        INSERT INTO fit_draw_chance_ledger (id, app_code, user_id, ledger_type, chance_delta, balance_after, source_type, source_id, idempotency_key, expires_at, created_at)
        VALUES (CAST(#{id} AS uuid), #{appCode}, #{userId}, 'grant', #{chanceDelta}, #{balanceAfter}, #{sourceType}, #{sourceId}, #{idempotencyKey}, NULL, #{createdAt})
        """)
    void insertGrantChance(@Param("id") String id,
                           @Param("appCode") String appCode,
                           @Param("userId") Long userId,
                           @Param("chanceDelta") int chanceDelta,
                           @Param("balanceAfter") int balanceAfter,
                           @Param("sourceType") String sourceType,
                           @Param("sourceId") String sourceId,
                           @Param("idempotencyKey") String idempotencyKey,
                           @Param("createdAt") OffsetDateTime createdAt);
}
