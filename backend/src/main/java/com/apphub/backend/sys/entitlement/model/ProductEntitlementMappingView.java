package com.apphub.backend.sys.entitlement.model;

/** 中文说明：App Store 商品到权益码的结构化映射结果，所有字段都带 appCode/storeCode 边界。 */
public record ProductEntitlementMappingView(
    String appCode,
    String storeCode,
    String productId,
    String planCode,
    String entitlementCode,
    String productType,
    String source
) {}
