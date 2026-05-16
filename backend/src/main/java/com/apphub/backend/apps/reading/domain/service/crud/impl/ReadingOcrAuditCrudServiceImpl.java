package com.apphub.backend.apps.reading.domain.service.crud.impl;

import com.apphub.backend.apps.reading.domain.entity.ReadingOcrAuditEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingOcrAuditMapper;
import com.apphub.backend.apps.reading.domain.service.crud.ReadingOcrAuditCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus CRUD ServiceImpl for `ReadingOcrAuditEntity`.
 */
@Service
public class ReadingOcrAuditCrudServiceImpl extends ServiceImpl<ReadingOcrAuditMapper, ReadingOcrAuditEntity> implements ReadingOcrAuditCrudService {
}
