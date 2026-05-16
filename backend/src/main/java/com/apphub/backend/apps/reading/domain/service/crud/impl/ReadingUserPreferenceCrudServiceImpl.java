package com.apphub.backend.apps.reading.domain.service.crud.impl;

import com.apphub.backend.apps.reading.domain.entity.ReadingUserPreferenceEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingUserPreferenceMapper;
import com.apphub.backend.apps.reading.domain.service.crud.ReadingUserPreferenceCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus CRUD ServiceImpl for `ReadingUserPreferenceEntity`.
 */
@Service
public class ReadingUserPreferenceCrudServiceImpl extends ServiceImpl<ReadingUserPreferenceMapper, ReadingUserPreferenceEntity> implements ReadingUserPreferenceCrudService {
}
