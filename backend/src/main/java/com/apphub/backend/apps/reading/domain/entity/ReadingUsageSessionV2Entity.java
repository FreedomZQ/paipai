package com.apphub.backend.apps.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("reading_usage_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingUsageSessionV2Entity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String appCode;
    private Long userId;
    private String childId;
    private String sourcePage;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private Integer durationSeconds;
    private String clientPlatform;
    private String deviceModel;
    private String lastModifiedByInstallationId;
    private OffsetDateTime deletedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
