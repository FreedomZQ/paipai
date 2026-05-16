package com.apphub.backend.sys.entitlement.mapper;

import com.apphub.backend.sys.entitlement.entity.SysUserPlanSnapshotEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/** 中文说明：统一权益中心 SysUserPlanSnapshot 数据访问接口，所有查询都必须带 appCode 边界。 */
@Mapper
public interface SysUserPlanSnapshotMapper extends BaseMapper<SysUserPlanSnapshotEntity> {
    @Insert("""
        INSERT INTO sys_user_plan_snapshot
        (app_code, user_id, plan_code, entitlement_code, policy_version, source_type, source_ref, status, starts_at, expires_at, feature_snapshot_json, created_at, updated_at)
        VALUES
        (#{appCode}, #{userId}, #{planCode}, #{entitlementCode}, #{policyVersion}, #{sourceType}, #{sourceRef}, #{status}, #{startsAt}, #{expiresAt}, CAST(#{featureSnapshotJson} AS jsonb), #{createdAt}, #{updatedAt})
        """)
    int insertJsonb(SysUserPlanSnapshotEntity entity);

    @Update("""
        UPDATE sys_user_plan_snapshot
        SET plan_code = #{planCode},
            entitlement_code = #{entitlementCode},
            policy_version = #{policyVersion},
            source_type = #{sourceType},
            source_ref = #{sourceRef},
            status = #{status},
            starts_at = #{startsAt},
            expires_at = #{expiresAt},
            feature_snapshot_json = CAST(#{featureSnapshotJson} AS jsonb),
            updated_at = #{updatedAt}
        WHERE id = #{id}
        """)
    int updateJsonbById(SysUserPlanSnapshotEntity entity);
}
