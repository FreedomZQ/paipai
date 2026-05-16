package com.apphub.backend.apps.saving.service;

import com.apphub.backend.apps.saving.SavingAppModule;
import com.apphub.backend.sys.billing.model.EntitlementItemView;
import com.apphub.backend.sys.billing.model.EntitlementOverviewView;
import com.apphub.backend.sys.billing.service.SysBillingService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * saving 会员权益辅助服务。
 * 中文说明：这里把 saving 的“是否 Pro”判断集中在应用模块内，内部仍复用统一计费服务。
 * 后续增加其他 APP 时，不应在通用计费内核写 saving 专属逻辑，而应继续在各 APP 模块中做轻量适配。
 */
@Service
public class SavingEntitlementService {
    private static final String PRO_MONTHLY_PLAN = "pro_monthly";
    private static final String FREE_PLAN = "free";

    private final SysBillingService sysBillingService;

    public SavingEntitlementService(SysBillingService sysBillingService) {
        this.sysBillingService = sysBillingService;
    }

    public String currentPlanCode(Long userId) {
        return isPaidActive(userId) ? PRO_MONTHLY_PLAN : FREE_PLAN;
    }

    public boolean isPaidActive(Long userId) {
        EntitlementOverviewView overview = sysBillingService.getEntitlements(SavingAppModule.APP_CODE, userId);
        List<EntitlementItemView> items = overview == null || overview.entitlements() == null ? List.of() : overview.entitlements();
        return items.stream().anyMatch(item ->
            item != null
                && "active".equalsIgnoreCase(item.status())
                && item.entitlementCode() != null
                && !FREE_PLAN.equalsIgnoreCase(item.entitlementCode())
        );
    }
}
