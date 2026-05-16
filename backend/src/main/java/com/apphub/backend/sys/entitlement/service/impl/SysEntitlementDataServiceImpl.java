package com.apphub.backend.sys.entitlement.service.impl;

import com.apphub.backend.sys.billing.entity.SysEntitlementSnapshotEntity;
import com.apphub.backend.sys.billing.mapper.SysEntitlementSnapshotMapper;
import com.apphub.backend.sys.entitlement.entity.SysEntitlementFeatureEntity;
import com.apphub.backend.sys.entitlement.entity.SysMembershipPlanEntity;
import com.apphub.backend.sys.entitlement.entity.SysPlanFeatureRuleEntity;
import com.apphub.backend.sys.entitlement.entity.SysProductEntitlementMappingEntity;
import com.apphub.backend.sys.entitlement.entity.SysUserEntitlementGrantEntity;
import com.apphub.backend.sys.entitlement.entity.SysUserFeatureOverrideEntity;
import com.apphub.backend.sys.entitlement.entity.SysUserPlanSnapshotEntity;
import com.apphub.backend.sys.entitlement.mapper.SysEntitlementFeatureMapper;
import com.apphub.backend.sys.entitlement.mapper.SysMembershipPlanMapper;
import com.apphub.backend.sys.entitlement.mapper.SysPlanFeatureRuleMapper;
import com.apphub.backend.sys.entitlement.mapper.SysProductEntitlementMappingMapper;
import com.apphub.backend.sys.entitlement.mapper.SysUserEntitlementGrantMapper;
import com.apphub.backend.sys.entitlement.mapper.SysUserFeatureOverrideMapper;
import com.apphub.backend.sys.entitlement.mapper.SysUserPlanSnapshotMapper;
import com.apphub.backend.sys.entitlement.service.SysEntitlementDataService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

@Service
public class SysEntitlementDataServiceImpl extends ServiceImpl<SysMembershipPlanMapper, SysMembershipPlanEntity> implements SysEntitlementDataService {
    private static final String ACTIVE = "active";

    private final SysEntitlementFeatureMapper entitlementFeatureMapper;
    private final SysPlanFeatureRuleMapper planFeatureRuleMapper;
    private final SysProductEntitlementMappingMapper productEntitlementMappingMapper;
    private final SysUserPlanSnapshotMapper userPlanSnapshotMapper;
    private final SysUserEntitlementGrantMapper userEntitlementGrantMapper;
    private final SysUserFeatureOverrideMapper userFeatureOverrideMapper;
    private final SysEntitlementSnapshotMapper entitlementSnapshotMapper;

    public SysEntitlementDataServiceImpl(
        SysEntitlementFeatureMapper entitlementFeatureMapper,
        SysPlanFeatureRuleMapper planFeatureRuleMapper,
        SysProductEntitlementMappingMapper productEntitlementMappingMapper,
        SysUserPlanSnapshotMapper userPlanSnapshotMapper,
        SysUserEntitlementGrantMapper userEntitlementGrantMapper,
        SysUserFeatureOverrideMapper userFeatureOverrideMapper,
        SysEntitlementSnapshotMapper entitlementSnapshotMapper
    ) {
        this.entitlementFeatureMapper = entitlementFeatureMapper;
        this.planFeatureRuleMapper = planFeatureRuleMapper;
        this.productEntitlementMappingMapper = productEntitlementMappingMapper;
        this.userPlanSnapshotMapper = userPlanSnapshotMapper;
        this.userEntitlementGrantMapper = userEntitlementGrantMapper;
        this.userFeatureOverrideMapper = userFeatureOverrideMapper;
        this.entitlementSnapshotMapper = entitlementSnapshotMapper;
    }

    @Override
    public List<SysEntitlementSnapshotEntity> activeBillingSnapshots(String appCode, Long userId, OffsetDateTime now) {
        return entitlementSnapshotMapper.selectList(new LambdaQueryWrapper<SysEntitlementSnapshotEntity>()
            .eq(SysEntitlementSnapshotEntity::getAppCode, appCode)
            .eq(SysEntitlementSnapshotEntity::getUserId, userId)
            .eq(SysEntitlementSnapshotEntity::getStatus, ACTIVE)
            .and(q -> q.isNull(SysEntitlementSnapshotEntity::getExpiresAt).or().gt(SysEntitlementSnapshotEntity::getExpiresAt, now))
            .orderByDesc(SysEntitlementSnapshotEntity::getUpdatedAt));
    }

    @Override
    public SysProductEntitlementMappingEntity activeProductMapping(String appCode, String storeCode, String productId) {
        return productEntitlementMappingMapper.selectOne(new LambdaQueryWrapper<SysProductEntitlementMappingEntity>()
            .eq(SysProductEntitlementMappingEntity::getAppCode, appCode)
            .eq(SysProductEntitlementMappingEntity::getStoreCode, storeCode)
            .eq(SysProductEntitlementMappingEntity::getProductId, productId)
            .eq(SysProductEntitlementMappingEntity::getStatus, ACTIVE)
            .last("LIMIT 1"));
    }

    @Override
    public int insertPlanSnapshotJsonb(SysUserPlanSnapshotEntity snapshot) {
        return userPlanSnapshotMapper.insertJsonb(snapshot);
    }

    @Override
    public int updatePlanSnapshotJsonbById(SysUserPlanSnapshotEntity snapshot) {
        return userPlanSnapshotMapper.updateJsonbById(snapshot);
    }

    @Override
    public List<SysMembershipPlanEntity> listPlans(String appCode) {
        return baseMapper.selectList(new LambdaQueryWrapper<SysMembershipPlanEntity>()
            .eq(SysMembershipPlanEntity::getAppCode, appCode)
            .orderByAsc(SysMembershipPlanEntity::getSortOrder)
            .orderByAsc(SysMembershipPlanEntity::getPlanLevel));
    }

    @Override
    public List<SysEntitlementFeatureEntity> listFeatures(String appCode) {
        return entitlementFeatureMapper.selectList(new LambdaQueryWrapper<SysEntitlementFeatureEntity>()
            .eq(SysEntitlementFeatureEntity::getAppCode, appCode)
            .orderByAsc(SysEntitlementFeatureEntity::getFeatureCode));
    }

    @Override
    public List<SysPlanFeatureRuleEntity> listRules(String appCode, String planCode) {
        return planFeatureRuleMapper.selectList(new LambdaQueryWrapper<SysPlanFeatureRuleEntity>()
            .eq(SysPlanFeatureRuleEntity::getAppCode, appCode)
            .eq(SysPlanFeatureRuleEntity::getPlanCode, planCode)
            .orderByAsc(SysPlanFeatureRuleEntity::getPriority)
            .orderByAsc(SysPlanFeatureRuleEntity::getFeatureCode));
    }

    @Override
    public List<SysMembershipPlanEntity> activePlansByEntitlements(String appCode, Collection<String> entitlementCodes) {
        return baseMapper.selectList(new LambdaQueryWrapper<SysMembershipPlanEntity>()
            .eq(SysMembershipPlanEntity::getAppCode, appCode)
            .eq(SysMembershipPlanEntity::getStatus, ACTIVE)
            .in(SysMembershipPlanEntity::getEntitlementCode, entitlementCodes));
    }

    @Override
    public SysMembershipPlanEntity activePlanByCode(String appCode, String planCode) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SysMembershipPlanEntity>()
            .eq(SysMembershipPlanEntity::getAppCode, appCode)
            .eq(SysMembershipPlanEntity::getPlanCode, planCode)
            .eq(SysMembershipPlanEntity::getStatus, ACTIVE)
            .last("LIMIT 1"));
    }

    @Override
    public SysMembershipPlanEntity topActivePlanByEntitlement(String appCode, String entitlementCode) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SysMembershipPlanEntity>()
            .eq(SysMembershipPlanEntity::getAppCode, appCode)
            .eq(SysMembershipPlanEntity::getEntitlementCode, entitlementCode)
            .eq(SysMembershipPlanEntity::getStatus, ACTIVE)
            .orderByDesc(SysMembershipPlanEntity::getPlanLevel)
            .last("LIMIT 1"));
    }

    @Override
    public List<SysPlanFeatureRuleEntity> activePlanRules(String appCode, String planCode, OffsetDateTime now) {
        return planFeatureRuleMapper.selectList(new LambdaQueryWrapper<SysPlanFeatureRuleEntity>()
            .eq(SysPlanFeatureRuleEntity::getAppCode, appCode)
            .eq(SysPlanFeatureRuleEntity::getPlanCode, planCode)
            .eq(SysPlanFeatureRuleEntity::getStatus, ACTIVE)
            .and(q -> q.isNull(SysPlanFeatureRuleEntity::getEffectiveStartAt).or().le(SysPlanFeatureRuleEntity::getEffectiveStartAt, now))
            .and(q -> q.isNull(SysPlanFeatureRuleEntity::getEffectiveEndAt).or().gt(SysPlanFeatureRuleEntity::getEffectiveEndAt, now))
            .orderByAsc(SysPlanFeatureRuleEntity::getPriority));
    }

    @Override
    public List<SysUserPlanSnapshotEntity> activePlanSnapshots(String appCode, Long userId, OffsetDateTime now) {
        return userPlanSnapshotMapper.selectList(new LambdaQueryWrapper<SysUserPlanSnapshotEntity>()
            .eq(SysUserPlanSnapshotEntity::getAppCode, appCode)
            .eq(SysUserPlanSnapshotEntity::getUserId, userId)
            .eq(SysUserPlanSnapshotEntity::getStatus, ACTIVE)
            .and(q -> q.isNull(SysUserPlanSnapshotEntity::getStartsAt).or().le(SysUserPlanSnapshotEntity::getStartsAt, now))
            .and(q -> q.isNull(SysUserPlanSnapshotEntity::getExpiresAt).or().gt(SysUserPlanSnapshotEntity::getExpiresAt, now))
            .orderByDesc(SysUserPlanSnapshotEntity::getUpdatedAt));
    }

    @Override
    public List<SysUserEntitlementGrantEntity> activeGrants(String appCode, Long userId, OffsetDateTime now) {
        return userEntitlementGrantMapper.selectList(new LambdaQueryWrapper<SysUserEntitlementGrantEntity>()
            .eq(SysUserEntitlementGrantEntity::getAppCode, appCode)
            .eq(SysUserEntitlementGrantEntity::getUserId, userId)
            .eq(SysUserEntitlementGrantEntity::getStatus, ACTIVE)
            .and(q -> q.isNull(SysUserEntitlementGrantEntity::getStartsAt).or().le(SysUserEntitlementGrantEntity::getStartsAt, now))
            .and(q -> q.isNull(SysUserEntitlementGrantEntity::getExpiresAt).or().gt(SysUserEntitlementGrantEntity::getExpiresAt, now))
            .orderByDesc(SysUserEntitlementGrantEntity::getUpdatedAt));
    }

    @Override
    public List<SysUserFeatureOverrideEntity> activeOverrides(String appCode, Long userId, OffsetDateTime now) {
        return userFeatureOverrideMapper.selectList(new LambdaQueryWrapper<SysUserFeatureOverrideEntity>()
            .eq(SysUserFeatureOverrideEntity::getAppCode, appCode)
            .eq(SysUserFeatureOverrideEntity::getUserId, userId)
            .eq(SysUserFeatureOverrideEntity::getStatus, ACTIVE)
            .and(q -> q.isNull(SysUserFeatureOverrideEntity::getStartsAt).or().le(SysUserFeatureOverrideEntity::getStartsAt, now))
            .and(q -> q.isNull(SysUserFeatureOverrideEntity::getExpiresAt).or().gt(SysUserFeatureOverrideEntity::getExpiresAt, now))
            .orderByDesc(SysUserFeatureOverrideEntity::getUpdatedAt));
    }

    @Override
    public SysUserPlanSnapshotEntity existingPurchaseSnapshot(String appCode, Long userId, String entitlementCode, String sourceRef) {
        LambdaQueryWrapper<SysUserPlanSnapshotEntity> wrapper = new LambdaQueryWrapper<SysUserPlanSnapshotEntity>()
            .eq(SysUserPlanSnapshotEntity::getAppCode, appCode)
            .eq(SysUserPlanSnapshotEntity::getUserId, userId)
            .eq(SysUserPlanSnapshotEntity::getEntitlementCode, entitlementCode);
        if (sourceRef != null && !sourceRef.isBlank()) {
            wrapper.eq(SysUserPlanSnapshotEntity::getSourceRef, sourceRef);
        }
        wrapper.orderByDesc(SysUserPlanSnapshotEntity::getUpdatedAt).last("LIMIT 1");
        return userPlanSnapshotMapper.selectOne(wrapper);
    }
}
