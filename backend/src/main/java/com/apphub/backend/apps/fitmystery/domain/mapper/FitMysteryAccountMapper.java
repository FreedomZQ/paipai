package com.apphub.backend.apps.fitmystery.domain.mapper;

import com.apphub.backend.apps.fitmystery.domain.entity.FitAccountDeletionRequestEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.time.OffsetDateTime;

@Mapper
public interface FitMysteryAccountMapper extends BaseMapper<FitAccountDeletionRequestEntity> {
    @Delete("DELETE FROM fit_user_collection WHERE app_code=#{appCode} AND user_id=#{userId}")
    int deleteCollection(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Delete("DELETE FROM fit_blind_box_draw WHERE app_code=#{appCode} AND user_id=#{userId}")
    int deleteDraws(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Delete("DELETE FROM fit_draw_chance_ledger WHERE app_code=#{appCode} AND user_id=#{userId}")
    int deleteChanceLedger(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Delete("DELETE FROM fit_points_ledger WHERE app_code=#{appCode} AND user_id=#{userId}")
    int deletePointsLedger(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Delete("DELETE FROM fit_daily_score_snapshot WHERE app_code=#{appCode} AND user_id=#{userId}")
    int deleteDailySnapshots(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Delete("DELETE FROM fit_activity_event WHERE app_code=#{appCode} AND user_id=#{userId}")
    int deleteActivityEvents(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Delete("DELETE FROM fit_report_generation_ledger WHERE app_code=#{appCode} AND user_id=#{userId}")
    int deleteReportGenerationLedger(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Delete("DELETE FROM sys_entitlement_snapshot WHERE app_code=#{appCode} AND user_id=#{userId}")
    int deleteEntitlementSnapshots(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM sys_purchase_transaction WHERE app_code=#{appCode} AND user_id=#{userId}")
    int countRetainedPurchaseTransactions(@Param("appCode") String appCode, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM sys_app_store_notification WHERE app_code=#{appCode}")
    int countRetainedAppStoreNotifications(@Param("appCode") String appCode);

    @Insert("""
        INSERT INTO fit_account_deletion_request (id, app_code, user_id, request_status, deletion_scope, requested_at, completed_at, note)
        VALUES (gen_random_uuid(), #{appCode}, #{userId}, 'completed', 'fitmystery_app_data', #{now}, #{now}, #{note})
        """)
    void insertDeletionRequest(@Param("appCode") String appCode, @Param("userId") Long userId, @Param("now") OffsetDateTime now, @Param("note") String note);
}
