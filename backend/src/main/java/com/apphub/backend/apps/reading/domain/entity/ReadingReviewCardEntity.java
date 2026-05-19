package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * reading 句卡实体。
 * 句卡是付费能力、复习统计和周报统计的源数据，创建与查询必须走后端鉴权。
 */
@TableName("reading_review_card")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingReviewCardEntity {
    @TableId
    private String id;
    private String appCode;
    private Long userId;
    private String childId;
    private String learningTrackCode;
    private String encryptedText;
    private String textPreview;
    private String supportHint;
    private Integer proficiency;
    private OffsetDateTime nextReviewAt;
    private String cardStatus;
    private String sourceText;
    private String translatedText;
    private String sourceLanguageCode;
    private String targetLanguageCode;
    private String sourceType;
    private String contentEncryptionVersion;
    private String contentKeyId;
    private OffsetDateTime lastReviewedAt;
    private OffsetDateTime deletedAt;
    private Integer recordVersion;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
