package com.apphub.backend.apps.fitmystery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("fit_activity_event")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitActivityEventEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String appCode;
    private Long userId;
    private String idempotencyKey;
    private String eventType;
    private String source;
    private BigDecimal quantity;
    private String unit;
    private LocalDate eventDate;
    private OffsetDateTime occurredAt;
    private OffsetDateTime clientRecordedAt;
    private String trustLevel;
    private String rawPayloadJson;
    private String status;
    private String rejectReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
