package com.apphub.backend.apps.reading.domain.service.crud.impl;

import com.apphub.backend.apps.reading.domain.entity.ReadingReviewEventEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingReviewEventMapper;
import com.apphub.backend.apps.reading.domain.service.crud.ReadingReviewEventCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus CRUD ServiceImpl for `ReadingReviewEventEntity`.
 */
@Service
public class ReadingReviewEventCrudServiceImpl extends ServiceImpl<ReadingReviewEventMapper, ReadingReviewEventEntity> implements ReadingReviewEventCrudService {
}
