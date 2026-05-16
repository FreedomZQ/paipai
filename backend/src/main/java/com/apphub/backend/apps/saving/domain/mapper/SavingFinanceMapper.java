package com.apphub.backend.apps.saving.domain.mapper;

import com.apphub.backend.apps.saving.domain.entity.SavingExpenseRecordEntity;
import com.apphub.backend.apps.saving.domain.entity.SavingSavingRecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SavingFinanceMapper extends BaseMapper<SavingExpenseRecordEntity> {
    @Insert("""
        INSERT INTO saving_expense_record (id, user_id, amount, currency, category_code, category_name, merchant_name, note, source, occurred_at, created_at, updated_at)
        VALUES (CAST(#{id} AS uuid), #{userId}, #{amount}, #{currency}, #{categoryCode}, #{categoryName}, #{merchantName}, #{note}, #{source}, #{occurredAt}, #{createdAt}, #{updatedAt})
        """)
    void insertExpense(SavingExpenseRecordEntity entity);

    @Insert("""
        INSERT INTO saving_saving_record (id, user_id, amount, currency, saving_type, category_code, category_name, scenario, note, source, occurred_at, created_at, updated_at)
        VALUES (CAST(#{id} AS uuid), #{userId}, #{amount}, #{currency}, #{savingType}, #{categoryCode}, #{categoryName}, #{scenario}, #{note}, #{source}, #{occurredAt}, #{createdAt}, #{updatedAt})
        """)
    void insertSaving(SavingSavingRecordEntity entity);

    @Select("""
        <script>
        SELECT id::text AS id, user_id, amount, currency, category_code, category_name, merchant_name, note, source, occurred_at, created_at, updated_at
        FROM saving_expense_record
        WHERE user_id = #{userId}
        <if test="startAt != null">
          AND occurred_at &gt;= #{startAt}
        </if>
        <if test="endAt != null">
          AND occurred_at &lt; #{endAt}
        </if>
        ORDER BY occurred_at DESC, updated_at DESC
        LIMIT #{limit}
        </script>
        """)
    List<SavingExpenseRecordEntity> selectExpenses(@Param("userId") Long userId, @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt, @Param("limit") int limit);

    @Select("""
        <script>
        SELECT id::text AS id, user_id, amount, currency, saving_type, category_code, category_name, scenario, note, source, occurred_at, created_at, updated_at
        FROM saving_saving_record
        WHERE user_id = #{userId}
        <if test="startAt != null">
          AND occurred_at &gt;= #{startAt}
        </if>
        <if test="endAt != null">
          AND occurred_at &lt; #{endAt}
        </if>
        ORDER BY occurred_at DESC, updated_at DESC
        LIMIT #{limit}
        </script>
        """)
    List<SavingSavingRecordEntity> selectSavings(@Param("userId") Long userId, @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt, @Param("limit") int limit);

    @Select("""
        SELECT id::text AS id, user_id, amount, currency, category_code, category_name, merchant_name, note, source, occurred_at, created_at, updated_at
        FROM saving_expense_record WHERE id = CAST(#{id} AS uuid) AND user_id = #{userId}
        """)
    SavingExpenseRecordEntity selectExpenseById(@Param("userId") Long userId, @Param("id") String id);

    @Select("""
        SELECT id::text AS id, user_id, amount, currency, saving_type, category_code, category_name, scenario, note, source, occurred_at, created_at, updated_at
        FROM saving_saving_record WHERE id = CAST(#{id} AS uuid) AND user_id = #{userId}
        """)
    SavingSavingRecordEntity selectSavingById(@Param("userId") Long userId, @Param("id") String id);

    @Update("""
        UPDATE saving_expense_record SET amount=#{amount}, currency=#{currency}, category_code=#{categoryCode}, category_name=#{categoryName}, merchant_name=#{merchantName}, note=#{note}, occurred_at=#{occurredAt}, updated_at=#{updatedAt}
        WHERE id = CAST(#{id} AS uuid) AND user_id = #{userId}
        """)
    int updateExpense(SavingExpenseRecordEntity entity);

    @Update("""
        UPDATE saving_saving_record SET amount=#{amount}, currency=#{currency}, saving_type=#{savingType}, category_code=#{categoryCode}, category_name=#{categoryName}, scenario=#{scenario}, note=#{note}, occurred_at=#{occurredAt}, updated_at=#{updatedAt}
        WHERE id = CAST(#{id} AS uuid) AND user_id = #{userId}
        """)
    int updateSaving(SavingSavingRecordEntity entity);

    @Delete("DELETE FROM saving_expense_record WHERE id = CAST(#{id} AS uuid) AND user_id = #{userId}")
    int deleteExpense(@Param("userId") Long userId, @Param("id") String id);

    @Delete("DELETE FROM saving_saving_record WHERE id = CAST(#{id} AS uuid) AND user_id = #{userId}")
    int deleteSaving(@Param("userId") Long userId, @Param("id") String id);

    @Delete("DELETE FROM saving_expense_record WHERE user_id = #{userId}")
    int deleteAllExpensesByUser(@Param("userId") Long userId);

    @Delete("DELETE FROM saving_saving_record WHERE user_id = #{userId}")
    int deleteAllSavingsByUser(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM saving_expense_record WHERE user_id=#{userId} AND occurred_at >= #{startAt} AND occurred_at < #{endAt}")
    BigDecimal sumExpenses(@Param("userId") Long userId, @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM saving_saving_record WHERE user_id=#{userId} AND saving_type=#{savingType} AND occurred_at >= #{startAt} AND occurred_at < #{endAt}")
    BigDecimal sumSavingsByType(@Param("userId") Long userId, @Param("savingType") String savingType, @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt);

    @Select("SELECT COUNT(*) FROM saving_expense_record WHERE user_id=#{userId} AND occurred_at >= #{startAt} AND occurred_at < #{endAt}")
    int countExpenses(@Param("userId") Long userId, @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt);

    @Select("SELECT COUNT(*) FROM saving_saving_record WHERE user_id=#{userId} AND occurred_at >= #{startAt} AND occurred_at < #{endAt}")
    int countSavings(@Param("userId") Long userId, @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt);

    @Select("""
        SELECT category_code AS "categoryCode",
               COALESCE(MAX(category_name), category_code) AS "categoryName",
               COALESCE(SUM(amount), 0) AS amount,
               COUNT(*)::int AS count
        FROM saving_expense_record
        WHERE user_id=#{userId} AND occurred_at >= #{startAt} AND occurred_at < #{endAt}
        GROUP BY category_code
        ORDER BY COALESCE(SUM(amount), 0) DESC, category_code ASC
        LIMIT #{limit}
        """)
    List<Map<String, Object>> selectExpenseCategoryBreakdown(@Param("userId") Long userId,
                                                             @Param("startAt") OffsetDateTime startAt,
                                                             @Param("endAt") OffsetDateTime endAt,
                                                             @Param("limit") int limit);

    @Select("""
        SELECT category_code AS "categoryCode",
               COALESCE(MAX(category_name), category_code) AS "categoryName",
               COALESCE(SUM(amount), 0) AS amount,
               COUNT(*)::int AS count
        FROM saving_saving_record
        WHERE user_id=#{userId} AND occurred_at >= #{startAt} AND occurred_at < #{endAt}
        GROUP BY category_code
        ORDER BY COALESCE(SUM(amount), 0) DESC, category_code ASC
        LIMIT #{limit}
        """)
    List<Map<String, Object>> selectSavingCategoryBreakdown(@Param("userId") Long userId,
                                                            @Param("startAt") OffsetDateTime startAt,
                                                            @Param("endAt") OffsetDateTime endAt,
                                                            @Param("limit") int limit);

    @Select("""
        SELECT COALESCE(scenario, category_name, category_code) AS title,
               saving_type AS "savingType",
               category_code AS "categoryCode",
               COALESCE(category_name, category_code) AS "categoryName",
               amount,
               occurred_at AS "occurredAt"
        FROM saving_saving_record
        WHERE user_id=#{userId} AND occurred_at >= #{startAt} AND occurred_at < #{endAt}
        ORDER BY amount DESC, occurred_at DESC
        LIMIT #{limit}
        """)
    List<Map<String, Object>> selectTopSavingActions(@Param("userId") Long userId,
                                                     @Param("startAt") OffsetDateTime startAt,
                                                     @Param("endAt") OffsetDateTime endAt,
                                                     @Param("limit") int limit);

    @Select("""
        SELECT EXTRACT(HOUR FROM occurred_at AT TIME ZONE #{timezone})::int AS hour,
               COALESCE(SUM(amount), 0) AS amount,
               COUNT(*)::int AS count
        FROM saving_expense_record
        WHERE user_id=#{userId} AND occurred_at >= #{startAt} AND occurred_at < #{endAt}
        GROUP BY EXTRACT(HOUR FROM occurred_at AT TIME ZONE #{timezone})::int
        ORDER BY COALESCE(SUM(amount), 0) DESC, hour ASC
        LIMIT 1
        """)
    Map<String, Object> selectHighRiskExpenseHour(@Param("userId") Long userId,
                                                  @Param("startAt") OffsetDateTime startAt,
                                                  @Param("endAt") OffsetDateTime endAt,
                                                  @Param("timezone") String timezone);
}
