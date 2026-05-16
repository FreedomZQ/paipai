package com.apphub.backend.apps.fitmystery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("fit_draw_chance_ledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitDrawChanceLedgerEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String appCode;
    private Long userId;
    private String ledgerType;
    private Integer chanceDelta;
    private Integer balanceAfter;
    private String sourceType;
    private String sourceId;
    private String idempotencyKey;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;
}
