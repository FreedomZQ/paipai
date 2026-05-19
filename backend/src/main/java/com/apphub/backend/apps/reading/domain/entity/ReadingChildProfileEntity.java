package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * reading 孩子档案实体。
 * 孩子档案属于付费权益约束内的核心内容，必须以后端记录为准，避免客户端本地绕过数量限制。
 */
@TableName("reading_child_profile")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingChildProfileEntity {
    @TableId
    private String id;
    private String appCode;
    private Long userId;
    private String nickname;
    private String ageBand;
    private String learningTrackCode;
    private String avatarEmoji;
    private String profileStatus;
    private OffsetDateTime deletedAt;
    private Integer recordVersion;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
