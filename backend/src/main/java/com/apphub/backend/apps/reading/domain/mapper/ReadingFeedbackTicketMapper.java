package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.domain.entity.ReadingFeedbackTicketEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * reading 反馈工单 Mapper。
 * 仅保存低敏反馈元数据，便于个人开发者按 ticketNo 追踪问题。
 */
@Mapper
public interface ReadingFeedbackTicketMapper extends BaseMapper<ReadingFeedbackTicketEntity> {
}
