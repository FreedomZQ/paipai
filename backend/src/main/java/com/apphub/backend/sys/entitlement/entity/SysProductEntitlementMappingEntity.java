package com.apphub.backend.sys.entitlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * App Store 商品到统一权益码的映射。
 * 中文说明：productId 必须按 appCode + storeCode 隔离，防止多个 App 共用后端时串用商品权益。
 */
@TableName("sys_product_entitlement_mapping")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysProductEntitlementMappingEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private String storeCode;
    private String productId;
    private String planCode;
    private String entitlementCode;
    private String productType;
    private String status;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
