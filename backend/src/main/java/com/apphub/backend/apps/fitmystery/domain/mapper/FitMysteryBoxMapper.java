package com.apphub.backend.apps.fitmystery.domain.mapper;

import com.apphub.backend.apps.fitmystery.domain.entity.FitBlindBoxDrawEntity;
import com.apphub.backend.apps.fitmystery.domain.entity.FitBlindBoxItemEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface FitMysteryBoxMapper extends BaseMapper<FitBlindBoxDrawEntity> {
    @Select("SELECT COALESCE(MAX(balance_after), 0) FROM fit_points_ledger WHERE app_code=#{appCode} AND user_id=#{userId}")
    int currentPointsBalance(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Select("SELECT COALESCE(MAX(balance_after), 0) FROM fit_draw_chance_ledger WHERE app_code=#{appCode} AND user_id=#{userId}")
    int currentChanceBalance(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Select("""
        SELECT id::text AS id, item_code, pool_code, rarity, display_name, description, image_key, weight
        FROM fit_blind_box_item
        WHERE app_code=#{appCode} AND pool_code=#{poolCode} AND status='active' AND weight > 0
        ORDER BY item_code ASC
        """)
    List<FitBlindBoxItemEntity> selectActiveItems(@Param("appCode") String appCode, @Param("poolCode") String poolCode);

    @Select("""
        SELECT id::text AS id, user_id, pool_code, item_code, rarity, consume_type, points_spent, chances_spent, rng_version, odds_version, idempotency_key, created_at
        FROM fit_blind_box_draw
        WHERE app_code=#{appCode} AND user_id=#{userId} AND idempotency_key=#{idempotencyKey}
        """)
    FitBlindBoxDrawEntity selectDrawByIdempotencyKey(@Param("appCode") String appCode, @Param("userId") Long userId, @Param("idempotencyKey") String idempotencyKey);

    @Select("SELECT obtain_count FROM fit_user_collection WHERE app_code=#{appCode} AND user_id=#{userId} AND item_code=#{itemCode}")
    Integer obtainCount(@Param("appCode") String appCode, @Param("userId") Long userId, @Param("itemCode") String itemCode);

    @Insert("""
        INSERT INTO fit_points_ledger (id, app_code, user_id, ledger_type, points_delta, balance_after, related_event_id, related_draw_id, idempotency_key, reason_code, note, created_at)
        VALUES (CAST(#{id} AS uuid), #{appCode}, #{userId}, 'spend', #{pointsDelta}, #{balanceAfter}, NULL, CAST(#{relatedDrawId} AS uuid), #{idempotencyKey}, 'box_open', 'server_authoritative_draw', #{createdAt})
        """)
    void insertSpendPoints(@Param("id") String id, @Param("appCode") String appCode, @Param("userId") Long userId, @Param("pointsDelta") int pointsDelta, @Param("balanceAfter") int balanceAfter, @Param("relatedDrawId") String relatedDrawId, @Param("idempotencyKey") String idempotencyKey, @Param("createdAt") OffsetDateTime createdAt);

    @Insert("""
        INSERT INTO fit_draw_chance_ledger (id, app_code, user_id, ledger_type, chance_delta, balance_after, source_type, source_id, idempotency_key, expires_at, created_at)
        VALUES (CAST(#{id} AS uuid), #{appCode}, #{userId}, 'consume', #{chanceDelta}, #{balanceAfter}, 'box_open', #{sourceId}, #{idempotencyKey}, NULL, #{createdAt})
        """)
    void insertSpendChance(@Param("id") String id, @Param("appCode") String appCode, @Param("userId") Long userId, @Param("chanceDelta") int chanceDelta, @Param("balanceAfter") int balanceAfter, @Param("sourceId") String sourceId, @Param("idempotencyKey") String idempotencyKey, @Param("createdAt") OffsetDateTime createdAt);

    @Insert("""
        INSERT INTO fit_blind_box_draw (id, app_code, user_id, pool_code, item_code, rarity, consume_type, points_spent, chances_spent, rng_version, odds_version, idempotency_key, created_at)
        VALUES (CAST(#{id} AS uuid), #{appCode}, #{userId}, #{poolCode}, #{itemCode}, #{rarity}, #{consumeType}, #{pointsSpent}, #{chancesSpent}, #{rngVersion}, #{oddsVersion}, #{idempotencyKey}, #{createdAt})
        """)
    void insertDraw(@Param("id") String id, @Param("appCode") String appCode, @Param("userId") Long userId, @Param("poolCode") String poolCode, @Param("itemCode") String itemCode, @Param("rarity") String rarity, @Param("consumeType") String consumeType, @Param("pointsSpent") int pointsSpent, @Param("chancesSpent") int chancesSpent, @Param("rngVersion") String rngVersion, @Param("oddsVersion") String oddsVersion, @Param("idempotencyKey") String idempotencyKey, @Param("createdAt") OffsetDateTime createdAt);

    @Insert("""
        INSERT INTO fit_user_collection (id, app_code, user_id, item_code, first_draw_id, first_obtained_at, last_obtained_at, obtain_count, created_at, updated_at)
        VALUES (gen_random_uuid(), #{appCode}, #{userId}, #{itemCode}, CAST(#{drawId} AS uuid), #{now}, #{now}, 1, #{now}, #{now})
        ON CONFLICT (app_code, user_id, item_code) DO UPDATE SET
          last_obtained_at = EXCLUDED.last_obtained_at,
          obtain_count = fit_user_collection.obtain_count + 1,
          updated_at = EXCLUDED.updated_at
        """)
    void upsertCollection(@Param("appCode") String appCode, @Param("userId") Long userId, @Param("itemCode") String itemCode, @Param("drawId") String drawId, @Param("now") OffsetDateTime now);

    @Select("""
        SELECT c.item_code AS "itemCode", i.display_name AS "displayName", i.rarity, i.image_key AS "imageKey", c.obtain_count AS "obtainCount", c.first_obtained_at AS "firstObtainedAt", c.last_obtained_at AS "lastObtainedAt"
        FROM fit_user_collection c
        JOIN fit_blind_box_item i ON i.app_code=c.app_code AND i.item_code=c.item_code
        WHERE c.app_code=#{appCode} AND c.user_id=#{userId}
        ORDER BY c.last_obtained_at DESC
        LIMIT #{limit}
        """)
    List<Map<String, Object>> selectCollection(@Param("appCode") String appCode, @Param("userId") Long userId, @Param("limit") int limit);

    @Select("""
        SELECT d.id::text AS "drawId", d.pool_code AS "poolCode", d.item_code AS "itemCode", i.display_name AS "displayName", d.rarity, d.consume_type AS "consumeType", d.points_spent AS "pointsSpent", d.chances_spent AS "chancesSpent", d.odds_version AS "oddsVersion", d.created_at AS "createdAt"
        FROM fit_blind_box_draw d
        JOIN fit_blind_box_item i ON i.app_code=d.app_code AND i.item_code=d.item_code
        WHERE d.app_code=#{appCode} AND d.user_id=#{userId}
        ORDER BY d.created_at DESC
        LIMIT #{limit}
        """)
    List<Map<String, Object>> selectDrawHistory(@Param("appCode") String appCode, @Param("userId") Long userId, @Param("limit") int limit);
}
