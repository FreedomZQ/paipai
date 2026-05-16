package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.domain.entity.ReadingOcrAuditEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * reading OCR 审计 Mapper。
 * 只记录诊断状态，不保存原始图片，降低儿童图片数据风险。
 */
@Mapper
public interface ReadingOcrAuditMapper extends BaseMapper<ReadingOcrAuditEntity> {
}
