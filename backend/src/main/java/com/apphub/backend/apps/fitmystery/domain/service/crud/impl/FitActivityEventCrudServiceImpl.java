package com.apphub.backend.apps.fitmystery.domain.service.crud.impl;

import com.apphub.backend.apps.fitmystery.domain.entity.FitActivityEventEntity;
import com.apphub.backend.apps.fitmystery.domain.mapper.FitMysteryActivityMapper;
import com.apphub.backend.apps.fitmystery.domain.service.crud.FitActivityEventCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus CRUD ServiceImpl for `FitActivityEventEntity`.
 */
@Service
public class FitActivityEventCrudServiceImpl extends ServiceImpl<FitMysteryActivityMapper, FitActivityEventEntity> implements FitActivityEventCrudService {
}
