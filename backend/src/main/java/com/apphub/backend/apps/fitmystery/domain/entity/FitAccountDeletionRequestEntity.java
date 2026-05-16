package com.apphub.backend.apps.fitmystery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("fit_account_deletion_request")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitAccountDeletionRequestEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String appCode;
    private Long userId;
    private String requestStatus;
    private String deletionScope;
    private OffsetDateTime requestedAt;
    private OffsetDateTime completedAt;
    private String note;
}
