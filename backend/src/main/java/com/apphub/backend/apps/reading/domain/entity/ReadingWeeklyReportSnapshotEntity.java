package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * reading 周报快照实体。
 *
 * <p>快照用于把历史周报从“每次即时重算”升级为“首次生成后稳定复用”。这样可以降低数据库重复聚合压力，
 * 也能避免历史报告随底层数据轻微变动而反复变化，更适合个人开发者低运维、低争议的 App Store 首发策略。
 * 后续其他 App 复用统一后端时，必须通过 app_code 隔离自己的快照数据。</p>
 */
@TableName("reading_weekly_report_snapshot")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingWeeklyReportSnapshotEntity {
    @TableId
    private String id;
    private String appCode;
    private Long userId;
    private String childId;
    private String scope;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private String planCode;
    private String tier;
    private Integer payloadVersion;
    private String reportPayloadJson;
    private String reportStatus;
    private OffsetDateTime generatedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
