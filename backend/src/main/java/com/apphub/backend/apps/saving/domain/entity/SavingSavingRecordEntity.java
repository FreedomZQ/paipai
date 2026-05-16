package com.apphub.backend.apps.saving.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("saving_saving_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavingSavingRecordEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private String savingType;
    private String categoryCode;
    private String categoryName;
    private String scenario;
    private String note;
    private String source;
    private OffsetDateTime occurredAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
