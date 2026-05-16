package com.apphub.backend.sys.entitlement.service;

import com.apphub.backend.sys.billing.entity.SysEntitlementSnapshotEntity;
import com.apphub.backend.sys.entitlement.entity.SysEntitlementFeatureEntity;
import com.apphub.backend.sys.entitlement.entity.SysMembershipPlanEntity;
import com.apphub.backend.sys.entitlement.entity.SysPlanFeatureRuleEntity;
import com.apphub.backend.sys.entitlement.entity.SysProductEntitlementMappingEntity;
import com.apphub.backend.sys.entitlement.entity.SysUserEntitlementGrantEntity;
import com.apphub.backend.sys.entitlement.entity.SysUserFeatureOverrideEntity;
import com.apphub.backend.sys.entitlement.entity.SysUserPlanSnapshotEntity;
import com.apphub.backend.sys.entitlement.model.FeatureAccessView;
import com.apphub.backend.sys.entitlement.model.ProductEntitlementMappingView;
import com.apphub.backend.sys.entitlement.model.UserEntitlementDecisionView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 统一后台式权益中心（模式三：老用户权益保护版）。
 *
 * <p>中文说明：统一后端同时服务多个 App，权益判断不能再只看当前会员等级配置，否则后台减少额度时会影响
 * 已付款老用户。本服务把“当前等级配置 + 购买时快照 + 后台新增权益 + 用户级赠送/补偿 + 用户级功能覆盖”合并为
 * 最终决策；各 App 原有接口只负责把本决策转换成自己的兼容响应，尽量不要求前端改接口。</p>
 */
@Service
public class SysEntitlementCenterService {
    private static final String ACTIVE = "active";
    private static final String FREE_PLAN = "free";
    private static final List<String> ACCESS_ORDER = List.of("none", "hidden", "preview", "limited", "full");
    private static final List<String> SCOPE_ORDER = List.of("account", "single_child", "child", "per_child", "family");

    private final SysEntitlementDataService dataService;
    private final ObjectMapper objectMapper;

    public SysEntitlementCenterService(SysEntitlementDataService dataService, ObjectMapper objectMapper) {
        this.dataService = dataService;
        this.objectMapper = objectMapper;
    }

    public UserEntitlementDecisionView resolveUserEntitlement(String appCode, Long userId) {
        OffsetDateTime now = now();
        List<SysEntitlementSnapshotEntity> billingSnapshots = activeBillingSnapshots(appCode, userId, now);
        LinkedHashSet<String> activeEntitlements = new LinkedHashSet<>();
        for (SysEntitlementSnapshotEntity snapshot : billingSnapshots) {
            if (hasText(snapshot.getEntitlementCode())) {
                activeEntitlements.add(snapshot.getEntitlementCode());
            }
        }

        SysMembershipPlanEntity currentPlan = resolveCurrentPlan(appCode, activeEntitlements).orElseGet(() -> freePlan(appCode));
        LinkedHashMap<String, FeatureAccessView> merged = new LinkedHashMap<>(loadPlanFeatures(appCode, currentPlan == null ? FREE_PLAN : currentPlan.getPlanCode(), "current_plan"));

        // 第 2/3 层：有效购买快照与当前配置做增益合并。权益减少时保护老用户，权益增加时自动补齐。
        SysMembershipPlanEntity effectivePlan = currentPlan;
        for (SysUserPlanSnapshotEntity snapshot : activePlanSnapshots(appCode, userId, now)) {
            mergeFeatureMap(merged, snapshotFeatures(snapshot), "purchase_snapshot");
            effectivePlan = higherPlan(effectivePlan, planByCode(appCode, snapshot.getPlanCode()).orElse(null));
            if (hasText(snapshot.getEntitlementCode())) {
                activeEntitlements.add(snapshot.getEntitlementCode());
            }
        }

        for (SysUserEntitlementGrantEntity grant : activeGrants(appCode, userId, now)) {
            if (hasText(grant.getPlanCode())) {
                mergeFeatureMap(merged, loadPlanFeatures(appCode, grant.getPlanCode(), "user_grant"), "user_grant");
                SysMembershipPlanEntity grantPlan = planByCode(appCode, grant.getPlanCode()).orElse(null);
                effectivePlan = higherPlan(effectivePlan, grantPlan);
            }
            if (hasText(grant.getEntitlementCode())) {
                activeEntitlements.add(grant.getEntitlementCode());
            }
        }

        // 第 5 层：用户级覆盖。覆盖是显式后台动作，可以提升也可以关闭；关闭付费功能应配合 reason 和审计。
        for (SysUserFeatureOverrideEntity override : activeOverrides(appCode, userId, now)) {
            FeatureAccessView base = merged.get(override.getFeatureCode());
            merged.put(override.getFeatureCode(), new FeatureAccessView(
                override.getFeatureCode(),
                override.getEnabled() == null ? base == null ? false : base.enabled() : override.getEnabled(),
                firstNonBlank(override.getAccessLevel(), base == null ? "none" : base.accessLevel()),
                override.getLimitValue() == null ? base == null ? null : base.limitValue() : override.getLimitValue(),
                firstNonBlank(override.getLimitUnit(), base == null ? null : base.limitUnit()),
                firstNonBlank(override.getScopeCode(), base == null ? null : base.scopeCode()),
                "user_feature_override"
            ));
        }

        String planCode = effectivePlan == null ? FREE_PLAN : effectivePlan.getPlanCode();
        boolean paid = !FREE_PLAN.equalsIgnoreCase(planCode);
        String expiresAt = billingSnapshots.stream()
            .map(SysEntitlementSnapshotEntity::getExpiresAt)
            .filter(v -> v != null)
            .min(Comparator.naturalOrder())
            .map(OffsetDateTime::toString)
            .orElse(null);
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("appCode", appCode);
        proof.put("userId", userId);
        proof.put("plan", planCode);
        proof.put("activeEntitlements", List.copyOf(activeEntitlements));
        proof.put("policy", "sys_entitlement_center.mode3");
        proof.put("mergeLayers", List.of("current_plan", "purchase_snapshot", "user_grant", "user_feature_override"));
        proof.put("mode3Note", "权益增加自动补齐；有效购买快照保护老用户不被权益减少影响；过期快照不参与合并。人工赠送/覆盖独立于 App Store 投影。");

        return new UserEntitlementDecisionView(
            appCode,
            userId,
            planCode,
            effectivePlan == null ? "免费版" : effectivePlan.getDisplayName(),
            effectivePlan == null ? "free" : effectivePlan.getEntitlementCode(),
            paid ? "active" : "free",
            paid,
            !paid || !activeEntitlements.isEmpty(),
            "sys_entitlement_center",
            expiresAt,
            List.copyOf(activeEntitlements),
            Map.copyOf(merged),
            proof
        );
    }

    public FeatureAccessView decideFeatureAccess(String appCode, Long userId, String featureCode) {
        return resolveUserEntitlement(appCode, userId).features().getOrDefault(
            featureCode,
            new FeatureAccessView(featureCode, false, "none", null, null, null, "missing")
        );
    }

    public ProductEntitlementMappingView resolveProductMapping(String appCode, String productId) {
        if (!hasText(appCode) || !hasText(productId)) {
            return null;
        }
        SysProductEntitlementMappingEntity entity = dataService.activeProductMapping(appCode, "appstore", productId);
        if (entity == null || !hasText(entity.getEntitlementCode())) {
            return null;
        }
        return new ProductEntitlementMappingView(
            entity.getAppCode(),
            entity.getStoreCode(),
            entity.getProductId(),
            entity.getPlanCode(),
            entity.getEntitlementCode(),
            firstNonBlank(entity.getProductType(), "subscription"),
            "sys_product_entitlement_mapping"
        );
    }

    public String resolveEntitlementCodeByProduct(String appCode, String productId) {
        ProductEntitlementMappingView mapping = resolveProductMapping(appCode, productId);
        return mapping == null ? null : mapping.entitlementCode();
    }

    @Transactional
    public void createOrRefreshPurchaseSnapshot(
        String appCode,
        Long userId,
        String entitlementCode,
        String planCode,
        String sourceType,
        String sourceRef,
        OffsetDateTime expiresAt
    ) {
        OffsetDateTime now = now();
        if (!hasText(appCode) || userId == null || !hasText(entitlementCode)) {
            return;
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            return;
        }
        String resolvedPlanCode = firstNonBlank(planCode, planCodeForEntitlement(appCode, entitlementCode), FREE_PLAN);
        Map<String, FeatureAccessView> features = loadPlanFeatures(appCode, resolvedPlanCode, "purchase_snapshot");
        if (features.isEmpty()) {
            return;
        }
        SysUserPlanSnapshotEntity snapshot = selectExistingPurchaseSnapshot(appCode, userId, entitlementCode, sourceRef);
        boolean isNew = snapshot == null;
        if (isNew) {
            snapshot = new SysUserPlanSnapshotEntity();
            snapshot.setAppCode(appCode);
            snapshot.setUserId(userId);
            snapshot.setCreatedAt(now);
        }
        snapshot.setPlanCode(resolvedPlanCode);
        snapshot.setEntitlementCode(entitlementCode);
        snapshot.setPolicyVersion(appCode + ":" + resolvedPlanCode + ":mode3_current");
        snapshot.setSourceType(firstNonBlank(sourceType, "appstore"));
        snapshot.setSourceRef(sourceRef);
        snapshot.setStatus(ACTIVE);
        snapshot.setStartsAt(isNew || snapshot.getStartsAt() == null ? now : snapshot.getStartsAt());
        snapshot.setExpiresAt(expiresAt);
        snapshot.setFeatureSnapshotJson(toJson(Map.of(
            "version", snapshot.getPolicyVersion(),
            "planCode", resolvedPlanCode,
            "entitlementCode", entitlementCode,
            "features", features
        )));
        snapshot.setUpdatedAt(now);
        if (isNew) {
            dataService.insertPlanSnapshotJsonb(snapshot);
        } else {
            dataService.updatePlanSnapshotJsonbById(snapshot);
        }
    }

    public List<SysMembershipPlanEntity> listPlans(String appCode) {
        return dataService.listPlans(appCode);
    }

    public List<SysEntitlementFeatureEntity> listFeatures(String appCode) {
        return dataService.listFeatures(appCode);
    }

    public List<SysPlanFeatureRuleEntity> listRules(String appCode, String planCode) {
        return dataService.listRules(appCode, planCode);
    }

    private Optional<SysMembershipPlanEntity> resolveCurrentPlan(String appCode, LinkedHashSet<String> activeEntitlements) {
        if (activeEntitlements == null || activeEntitlements.isEmpty()) {
            return Optional.empty();
        }
        List<SysMembershipPlanEntity> plans = dataService.activePlansByEntitlements(appCode, activeEntitlements);
        return plans.stream().max(Comparator.comparingInt(p -> p.getPlanLevel() == null ? 0 : p.getPlanLevel()));
    }

    private Optional<SysMembershipPlanEntity> planByCode(String appCode, String planCode) {
        if (!hasText(planCode)) {
            return Optional.empty();
        }
        return Optional.ofNullable(dataService.activePlanByCode(appCode, planCode));
    }

    private SysMembershipPlanEntity freePlan(String appCode) {
        return planByCode(appCode, FREE_PLAN).orElse(null);
    }

    private SysMembershipPlanEntity higherPlan(SysMembershipPlanEntity left, SysMembershipPlanEntity right) {
        if (left == null) return right;
        if (right == null) return left;
        int l = left.getPlanLevel() == null ? 0 : left.getPlanLevel();
        int r = right.getPlanLevel() == null ? 0 : right.getPlanLevel();
        return r > l ? right : left;
    }

    private String planCodeForEntitlement(String appCode, String entitlementCode) {
        SysMembershipPlanEntity plan = dataService.topActivePlanByEntitlement(appCode, entitlementCode);
        return plan == null ? null : plan.getPlanCode();
    }

    private Map<String, FeatureAccessView> loadPlanFeatures(String appCode, String planCode, String source) {
        if (!hasText(planCode)) {
            return Map.of();
        }
        OffsetDateTime now = now();
        List<SysPlanFeatureRuleEntity> rules = dataService.activePlanRules(appCode, planCode, now);
        LinkedHashMap<String, FeatureAccessView> result = new LinkedHashMap<>();
        for (SysPlanFeatureRuleEntity rule : rules) {
            result.put(rule.getFeatureCode(), new FeatureAccessView(
                rule.getFeatureCode(),
                Boolean.TRUE.equals(rule.getEnabled()),
                firstNonBlank(rule.getAccessLevel(), "none"),
                rule.getLimitValue(),
                rule.getLimitUnit(),
                rule.getScopeCode(),
                source
            ));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, FeatureAccessView> snapshotFeatures(SysUserPlanSnapshotEntity snapshot) {
        if (snapshot == null || !hasText(snapshot.getFeatureSnapshotJson())) {
            return Map.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(snapshot.getFeatureSnapshotJson(), new TypeReference<>() {});
            Object rawFeatures = root.get("features");
            if (!(rawFeatures instanceof Map<?, ?> rawMap)) {
                return Map.of();
            }
            LinkedHashMap<String, FeatureAccessView> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (!(entry.getKey() instanceof String featureCode) || !(entry.getValue() instanceof Map<?, ?> value)) {
                    continue;
                }
                Map<String, Object> feature = (Map<String, Object>) value;
                result.put(featureCode, new FeatureAccessView(
                    featureCode,
                    bool(feature.get("enabled")),
                    string(feature.get("accessLevel"), "none"),
                    integer(feature.get("limitValue")),
                    string(feature.get("limitUnit"), null),
                    string(feature.get("scopeCode"), null),
                    "purchase_snapshot"
                ));
            }
            return result;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private void mergeFeatureMap(Map<String, FeatureAccessView> target, Map<String, FeatureAccessView> incoming, String source) {
        for (Map.Entry<String, FeatureAccessView> entry : incoming.entrySet()) {
            FeatureAccessView existing = target.get(entry.getKey());
            FeatureAccessView next = entry.getValue();
            target.put(entry.getKey(), existing == null ? next : mergeFeature(existing, next, source));
        }
    }

    private FeatureAccessView mergeFeature(FeatureAccessView current, FeatureAccessView incoming, String source) {
        return new FeatureAccessView(
            current.featureCode(),
            Boolean.TRUE.equals(current.enabled()) || Boolean.TRUE.equals(incoming.enabled()),
            higherAccess(current.accessLevel(), incoming.accessLevel()),
            maxInteger(current.limitValue(), incoming.limitValue()),
            firstNonBlank(incoming.limitUnit(), current.limitUnit()),
            higherScope(current.scopeCode(), incoming.scopeCode()),
            source
        );
    }

    private List<SysEntitlementSnapshotEntity> activeBillingSnapshots(String appCode, Long userId, OffsetDateTime now) {
        return dataService.activeBillingSnapshots(appCode, userId, now);
    }

    private List<SysUserPlanSnapshotEntity> activePlanSnapshots(String appCode, Long userId, OffsetDateTime now) {
        return dataService.activePlanSnapshots(appCode, userId, now);
    }

    private List<SysUserEntitlementGrantEntity> activeGrants(String appCode, Long userId, OffsetDateTime now) {
        return dataService.activeGrants(appCode, userId, now);
    }

    private List<SysUserFeatureOverrideEntity> activeOverrides(String appCode, Long userId, OffsetDateTime now) {
        return dataService.activeOverrides(appCode, userId, now);
    }

    private SysUserPlanSnapshotEntity selectExistingPurchaseSnapshot(String appCode, Long userId, String entitlementCode, String sourceRef) {
        return dataService.existingPurchaseSnapshot(appCode, userId, entitlementCode, sourceRef);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String higherAccess(String left, String right) {
        return rank(ACCESS_ORDER, right) > rank(ACCESS_ORDER, left) ? firstNonBlank(right, "none") : firstNonBlank(left, "none");
    }

    private String higherScope(String left, String right) {
        return rank(SCOPE_ORDER, right) > rank(SCOPE_ORDER, left) ? right : left;
    }

    private int rank(List<String> order, String value) {
        int index = value == null ? -1 : order.indexOf(value);
        return Math.max(index, 0);
    }

    private Integer maxInteger(Integer left, Integer right) {
        if (left == null) return right;
        if (right == null) return left;
        return Math.max(left, right);
    }

    private Boolean bool(Object value) {
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String string(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }
}
