package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * reading 反馈工单实体。
 * 仅保存低敏反馈内容和可选联系方式，避免上传图片、音频、OCR 原文等高风险儿童数据。
 */
@TableName("reading_feedback_ticket")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingFeedbackTicketEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private Long userId;
    private String ticketNo;
    private String category;
    private String content;
    private String contactEmail;
    private String authMode;
    private String traceId;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
