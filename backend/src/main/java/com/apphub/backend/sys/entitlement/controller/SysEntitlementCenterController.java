package com.apphub.backend.sys.entitlement.controller;

import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.auth.model.AuthenticatedSessionView;
import com.apphub.backend.sys.auth.service.SessionTokenResolver;
import com.apphub.backend.sys.auth.service.SysAuthSessionService;
import com.apphub.backend.sys.entitlement.entity.SysEntitlementFeatureEntity;
import com.apphub.backend.sys.entitlement.entity.SysMembershipPlanEntity;
import com.apphub.backend.sys.entitlement.entity.SysPlanFeatureRuleEntity;
import com.apphub.backend.sys.entitlement.model.FeatureAccessView;
import com.apphub.backend.sys.entitlement.model.UserEntitlementDecisionView;
import com.apphub.backend.sys.entitlement.service.SysEntitlementCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 统一权益中心只读接口。
 * 中文说明：第一阶段只开放查询能力，后台写操作暂不暴露，避免没有管理员鉴权体系时误改多个 App 的付费权益。
 */
@RestController
@RequestMapping("/api/v1/system/entitlement-center")
@Tag(name = "统一权益中心", description = "多 App 通用的会员等级、购买快照、赠送补偿和功能覆盖合并决策。")
public class SysEntitlementCenterController {
    private final SysEntitlementCenterService entitlementCenterService;
    private final SessionTokenResolver sessionTokenResolver;
    private final SysAuthSessionService sysAuthSessionService;

    public SysEntitlementCenterController(
        SysEntitlementCenterService entitlementCenterService,
        SessionTokenResolver sessionTokenResolver,
        SysAuthSessionService sysAuthSessionService
    ) {
        this.entitlementCenterService = entitlementCenterService;
        this.sessionTokenResolver = sessionTokenResolver;
        this.sysAuthSessionService = sysAuthSessionService;
    }

    @Operation(summary = "查询当前用户最终权益", description = "按当前配置、购买时快照、赠送补偿和用户级覆盖合并，返回统一权益中心标准决策。")
    @GetMapping("/apps/{appCode}/users/me/entitlement")
    public ApiResponse<UserEntitlementDecisionView> myEntitlement(@PathVariable String appCode, @Parameter(hidden = true) HttpServletRequest request) {
        Long userId = requireUserId(appCode, request);
        return ApiResponse.success(requestId(), entitlementCenterService.resolveUserEntitlement(appCode, userId));
    }

    @Operation(summary = "判断当前用户单个功能权益", description = "用于后端或 App 调试某个 featureCode 的最终开关、额度、范围。")
    @GetMapping("/apps/{appCode}/users/me/features/{featureCode}")
    public ApiResponse<FeatureAccessView> myFeature(@PathVariable String appCode, @PathVariable String featureCode, @Parameter(hidden = true) HttpServletRequest request) {
        Long userId = requireUserId(appCode, request);
        return ApiResponse.success(requestId(), entitlementCenterService.decideFeatureAccess(appCode, userId, featureCode));
    }

    @Operation(summary = "查询 App 权益策略概览", description = "只读后台接口：返回会员等级和说明；写接口需管理员鉴权后再开放。")
    @GetMapping("/apps/{appCode}/policy")
    public ApiResponse<Map<String, Object>> policy(@PathVariable String appCode) {
        return ApiResponse.success(requestId(), Map.of(
            "appCode", appCode,
            "mode", "mode3_purchase_snapshot_protection",
            "mergeOrder", List.of("current_plan", "purchase_snapshot", "new_benefits", "user_grant", "user_feature_override"),
            "note", "中文说明：权益减少不能影响有效期内已付款老用户；免费权益按当前配置；人工赠送和覆盖不写 sys_entitlement_snapshot，避免被 App Store 刷新覆盖。",
            "plans", entitlementCenterService.listPlans(appCode)
        ));
    }

    @Operation(summary = "查询 App 会员等级", description = "只读返回某个 appCode 下的会员等级列表。")
    @GetMapping("/apps/{appCode}/plans")
    public ApiResponse<List<SysMembershipPlanEntity>> plans(@PathVariable String appCode) {
        return ApiResponse.success(requestId(), entitlementCenterService.listPlans(appCode));
    }

    @Operation(summary = "查询 App 功能定义", description = "只读返回某个 appCode 下可被后台控制的 featureCode 定义。")
    @GetMapping("/apps/{appCode}/features")
    public ApiResponse<List<SysEntitlementFeatureEntity>> features(@PathVariable String appCode) {
        return ApiResponse.success(requestId(), entitlementCenterService.listFeatures(appCode));
    }

    @Operation(summary = "查询会员等级功能规则", description = "只读返回某个 planCode 的权益功能规则。")
    @GetMapping("/apps/{appCode}/plans/{planCode}/rules")
    public ApiResponse<List<SysPlanFeatureRuleEntity>> rules(@PathVariable String appCode, @PathVariable String planCode) {
        return ApiResponse.success(requestId(), entitlementCenterService.listRules(appCode, planCode));
    }

    private Long requireUserId(String appCode, HttpServletRequest request) {
        String token = sessionTokenResolver.resolve(request)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token"));
        AuthenticatedSessionView session = sysAuthSessionService.findCurrentSession(token)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bearer token"));
        if (!appCode.equals(session.appCode())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token appCode mismatch");
        }
        return session.user().userId();
    }

    private String requestId() {
        String value = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
