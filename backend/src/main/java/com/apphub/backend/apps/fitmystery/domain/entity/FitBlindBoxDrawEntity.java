package com.apphub.backend.apps.fitmystery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("fit_blind_box_draw")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitBlindBoxDrawEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private Long userId;
    private String poolCode;
    private String itemCode;
    private String rarity;
    private String consumeType;
    private Integer pointsSpent;
    private Integer chancesSpent;
    private String rngVersion;
    private String oddsVersion;
    private String idempotencyKey;
    private OffsetDateTime createdAt;
}
