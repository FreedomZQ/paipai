package com.apphub.backend.apps.reading.domain.service.crud.impl;

import com.apphub.backend.apps.reading.domain.entity.ReadingUsageSessionEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingUsageSessionMapper;
import com.apphub.backend.apps.reading.domain.service.crud.ReadingUsageSessionCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus CRUD ServiceImpl for `ReadingUsageSessionEntity`.
 */
@Service
public class ReadingUsageSessionCrudServiceImpl extends ServiceImpl<ReadingUsageSessionMapper, ReadingUsageSessionEntity> implements ReadingUsageSessionCrudService {
}
