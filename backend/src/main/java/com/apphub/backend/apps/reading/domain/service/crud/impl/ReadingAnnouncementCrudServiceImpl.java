package com.apphub.backend.apps.reading.domain.service.crud.impl;

import com.apphub.backend.apps.reading.domain.entity.ReadingAnnouncementEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingAnnouncementMapper;
import com.apphub.backend.apps.reading.domain.service.crud.ReadingAnnouncementCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus CRUD ServiceImpl for `ReadingAnnouncementEntity`.
 */
@Service
public class ReadingAnnouncementCrudServiceImpl extends ServiceImpl<ReadingAnnouncementMapper, ReadingAnnouncementEntity> implements ReadingAnnouncementCrudService {
}
