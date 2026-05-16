package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("reading_announcement")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingAnnouncementEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appCode;
    private String announcementUuid;
    private String title;
    private String content;
    private String status;
    private OffsetDateTime visibleStartAt;
    private OffsetDateTime visibleEndAt;
    private String announcementType;
    private Integer priority;
    private String actionUrl;
    private String actionText;
    private Boolean dismissible;
    private Integer maxDisplayCount;
    private Integer minIntervalSeconds;
    private String triggerScene;
    private String targetLocale;
    private String targetPlanCode;
    private String targetMinAppVersion;
    private String targetMaxAppVersion;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
