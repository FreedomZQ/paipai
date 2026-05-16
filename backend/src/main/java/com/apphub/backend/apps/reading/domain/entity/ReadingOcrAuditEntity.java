package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * reading OCR 审计实体。
 * 该表只记录诊断编号和状态，不保存原始图片，降低儿童图片处理和合规风险。
 */
@TableName("reading_ocr_audit")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingOcrAuditEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String traceId;
    private String provider;
    private String model;
    private String status;
    private String note;
    private OffsetDateTime createdAt;
}
