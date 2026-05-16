package com.apphub.backend.apps.fitmystery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("fit_report_generation_ledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitReportGenerationLedgerEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String appCode;
    private Long userId;
    private String ledgerType;
    private String reportType;
    private Integer quotaDelta;
    private Integer balanceAfter;
    private String sourceType;
    private String periodKey;
    private String localDataHash;
    private String idempotencyKey;
    private OffsetDateTime createdAt;
}
