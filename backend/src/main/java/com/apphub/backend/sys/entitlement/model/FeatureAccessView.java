package com.apphub.backend.sys.entitlement.model;

/**
 * 单个功能的最终权益决策。
 * 中文说明：该视图是统一权益中心向各 App 旧接口投影的最小单元，前端可继续消费原接口，后端负责合并新逻辑。
 */
public record FeatureAccessView(
    String featureCode,
    boolean enabled,
    String accessLevel,
    Integer limitValue,
    String limitUnit,
    String scopeCode,
    String source
) {}
