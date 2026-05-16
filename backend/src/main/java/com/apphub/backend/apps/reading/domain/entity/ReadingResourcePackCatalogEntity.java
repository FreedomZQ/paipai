package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("reading_resource_pack_catalog")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingResourcePackCatalogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private String packageCode;
    private String packageType;
    private String serviceType;
    private String displayNameJson;
    private String displayDescriptionJson;
    private Integer priceAmountCents;
    private String currencyCode;
    private Integer includedQuantity;
    private String quantityUnit;
    private Integer validDays;
    private String status;
    private Integer sortOrder;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
