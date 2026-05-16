package com.apphub.backend.sys.entitlement.service;

import com.apphub.backend.sys.billing.entity.SysEntitlementSnapshotEntity;
import com.apphub.backend.sys.entitlement.entity.SysEntitlementFeatureEntity;
import com.apphub.backend.sys.entitlement.entity.SysMembershipPlanEntity;
import com.apphub.backend.sys.entitlement.entity.SysPlanFeatureRuleEntity;
import com.apphub.backend.sys.entitlement.entity.SysProductEntitlementMappingEntity;
import com.apphub.backend.sys.entitlement.entity.SysUserEntitlementGrantEntity;
import com.apphub.backend.sys.entitlement.entity.SysUserFeatureOverrideEntity;
import com.apphub.backend.sys.entitlement.entity.SysUserPlanSnapshotEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 统一权益中心数据访问边界。
 *
 * <p>中文说明：权益编排服务只依赖该接口，不直接依赖 Mapper。当前实现仍使用
 * MyBatis-Plus Mapper/ServiceImpl；未来拆成独立权益微服务时，可把本接口替换成
 * RPC/HTTP client，而无需改动上层权益合并逻辑。</p>
 */
public interface SysEntitlementDataService extends IService<SysMembershipPlanEntity> {
    List<SysEntitlementSnapshotEntity> activeBillingSnapshots(String appCode, Long userId, OffsetDateTime now);
    SysProductEntitlementMappingEntity activeProductMapping(String appCode, String storeCode, String productId);
    int insertPlanSnapshotJsonb(SysUserPlanSnapshotEntity snapshot);
    int updatePlanSnapshotJsonbById(SysUserPlanSnapshotEntity snapshot);
    List<SysMembershipPlanEntity> listPlans(String appCode);
    List<SysEntitlementFeatureEntity> listFeatures(String appCode);
    List<SysPlanFeatureRuleEntity> listRules(String appCode, String planCode);
    List<SysMembershipPlanEntity> activePlansByEntitlements(String appCode, Collection<String> entitlementCodes);
    SysMembershipPlanEntity activePlanByCode(String appCode, String planCode);
    SysMembershipPlanEntity topActivePlanByEntitlement(String appCode, String entitlementCode);
    List<SysPlanFeatureRuleEntity> activePlanRules(String appCode, String planCode, OffsetDateTime now);
    List<SysUserPlanSnapshotEntity> activePlanSnapshots(String appCode, Long userId, OffsetDateTime now);
    List<SysUserEntitlementGrantEntity> activeGrants(String appCode, Long userId, OffsetDateTime now);
    List<SysUserFeatureOverrideEntity> activeOverrides(String appCode, Long userId, OffsetDateTime now);
    SysUserPlanSnapshotEntity existingPurchaseSnapshot(String appCode, Long userId, String entitlementCode, String sourceRef);
}
