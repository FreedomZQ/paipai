package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("reading_user_preference")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingUserPreferenceEntity {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private String appCode;
    private String uiLocale;
    private String sourceLanguageCode;
    private String targetLanguageCode;
    private String readingTrackCode;
    private String ttsVoiceCode;
    private String translationMode;
    private Integer recordVersion;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
