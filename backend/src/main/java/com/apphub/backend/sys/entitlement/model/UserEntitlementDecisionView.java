package com.apphub.backend.sys.entitlement.model;

import java.util.List;
import java.util.Map;

/**
 * 用户最终权益决策视图。
 * 中文说明：这是模式三合并后的稳定响应，包含当前等级、有效权益码、功能矩阵和可审计证明，供多个 App 的兼容接口复用。
 */
public record UserEntitlementDecisionView(
    String appCode,
    Long userId,
    String planCode,
    String planName,
    String entitlementCode,
    String status,
    boolean paid,
    boolean serverVerified,
    String verificationSource,
    String expiresAt,
    List<String> activeEntitlements,
    Map<String, FeatureAccessView> features,
    Map<String, Object> accessProof
) {}
