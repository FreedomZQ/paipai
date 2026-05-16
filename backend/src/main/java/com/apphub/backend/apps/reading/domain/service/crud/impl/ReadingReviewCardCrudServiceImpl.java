package com.apphub.backend.apps.reading.domain.service.crud.impl;

import com.apphub.backend.apps.reading.domain.entity.ReadingReviewCardEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingReviewCardMapper;
import com.apphub.backend.apps.reading.domain.service.crud.ReadingReviewCardCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus CRUD ServiceImpl for `ReadingReviewCardEntity`.
 */
@Service
public class ReadingReviewCardCrudServiceImpl extends ServiceImpl<ReadingReviewCardMapper, ReadingReviewCardEntity> implements ReadingReviewCardCrudService {
}
