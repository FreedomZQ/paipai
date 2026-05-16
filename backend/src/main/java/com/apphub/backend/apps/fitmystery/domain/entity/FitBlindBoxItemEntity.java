package com.apphub.backend.apps.fitmystery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("fit_blind_box_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FitBlindBoxItemEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String itemCode;
    private String poolCode;
    private String rarity;
    private String displayName;
    private String description;
    private String imageKey;
    private Integer weight;
}
