package com.apphub.backend.apps.fitmystery.domain.mapper;

import com.apphub.backend.apps.fitmystery.domain.entity.FitActivityEventEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

@Mapper
public interface FitMysteryActivityMapper extends BaseMapper<FitActivityEventEntity> {
    @Select("SELECT COUNT(*) FROM fit_activity_event WHERE app_code=#{appCode} AND user_id=#{userId} AND idempotency_key=#{idempotencyKey}")
    int countEventByIdempotencyKey(@Param("appCode") String appCode, @Param("userId") Long userId, @Param("idempotencyKey") String idempotencyKey);

    @Insert("""
        INSERT INTO fit_activity_event (id, app_code, user_id, idempotency_key, event_type, source, quantity, unit, event_date, occurred_at, client_recorded_at, trust_level, raw_payload_json, status, reject_reason, created_at, updated_at)
        VALUES (CAST(#{id} AS uuid), #{appCode}, #{userId}, #{idempotencyKey}, #{eventType}, #{source}, #{quantity}, #{unit}, #{eventDate}, #{occurredAt}, #{clientRecordedAt}, #{trustLevel}, #{rawPayloadJson}, #{status}, #{rejectReason}, #{createdAt}, #{updatedAt})
        """)
    void insertEvent(FitActivityEventEntity entity);

    @Select("SELECT COALESCE(MAX(balance_after), 0) FROM fit_points_ledger WHERE app_code=#{appCode} AND user_id=#{userId}")
    int currentPointsBalance(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Select("SELECT COALESCE(MAX(balance_after), 0) FROM fit_draw_chance_ledger WHERE app_code=#{appCode} AND user_id=#{userId}")
    int currentChanceBalance(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Insert("""
        INSERT INTO fit_points_ledger (id, app_code, user_id, ledger_type, points_delta, balance_after, related_event_id, related_draw_id, idempotency_key, reason_code, note, created_at)
        VALUES (CAST(#{id} AS uuid), #{appCode}, #{userId}, #{ledgerType}, #{pointsDelta}, #{balanceAfter}, CAST(#{relatedEventId} AS uuid), NULL, #{idempotencyKey}, #{reasonCode}, #{note}, #{createdAt})
        """)
    void insertPointsLedger(@Param("id") String id,
                            @Param("appCode") String appCode,
                            @Param("userId") Long userId,
                            @Param("ledgerType") String ledgerType,
                            @Param("pointsDelta") int pointsDelta,
                            @Param("balanceAfter") int balanceAfter,
                            @Param("relatedEventId") String relatedEventId,
                            @Param("idempotencyKey") String idempotencyKey,
                            @Param("reasonCode") String reasonCode,
                            @Param("note") String note,
                            @Param("createdAt") OffsetDateTime createdAt);

    @Insert("""
        INSERT INTO fit_daily_score_snapshot (id, app_code, user_id, score_date, water_ml, steps, exercise_minutes, points_earned, points_spent, points_balance, box_chance_balance, calculated_at, created_at, updated_at)
        VALUES (gen_random_uuid(), #{appCode}, #{userId}, #{scoreDate}, #{waterMl}, #{steps}, #{exerciseMinutes}, #{pointsEarned}, 0, #{pointsBalance}, #{chanceBalance}, #{now}, #{now}, #{now})
        ON CONFLICT (app_code, user_id, score_date) DO UPDATE SET
          water_ml = fit_daily_score_snapshot.water_ml + EXCLUDED.water_ml,
          steps = GREATEST(fit_daily_score_snapshot.steps, EXCLUDED.steps),
          exercise_minutes = fit_daily_score_snapshot.exercise_minutes + EXCLUDED.exercise_minutes,
          points_earned = fit_daily_score_snapshot.points_earned + EXCLUDED.points_earned,
          points_balance = EXCLUDED.points_balance,
          box_chance_balance = EXCLUDED.box_chance_balance,
          calculated_at = EXCLUDED.calculated_at,
          updated_at = EXCLUDED.updated_at
        """)
    void upsertDailySnapshot(@Param("appCode") String appCode,
                             @Param("userId") Long userId,
                             @Param("scoreDate") LocalDate scoreDate,
                             @Param("waterMl") int waterMl,
                             @Param("steps") int steps,
                             @Param("exerciseMinutes") int exerciseMinutes,
                             @Param("pointsEarned") int pointsEarned,
                             @Param("pointsBalance") int pointsBalance,
                             @Param("chanceBalance") int chanceBalance,
                             @Param("now") OffsetDateTime now);

    @Select("""
        SELECT score_date AS "scoreDate", water_ml AS "waterMl", steps, exercise_minutes AS "exerciseMinutes",
               points_earned AS "pointsEarned", points_spent AS "pointsSpent", points_balance AS "pointsBalance", box_chance_balance AS "boxChanceBalance"
        FROM fit_daily_score_snapshot
        WHERE app_code=#{appCode} AND user_id=#{userId} AND score_date=#{scoreDate}
        """)
    Map<String, Object> selectToday(@Param("appCode") String appCode, @Param("userId") Long userId, @Param("scoreDate") LocalDate scoreDate);
}
