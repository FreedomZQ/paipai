package com.apphub.backend.apps.fitmystery.domain.service.crud.impl;

import com.apphub.backend.apps.fitmystery.domain.entity.FitAccountDeletionRequestEntity;
import com.apphub.backend.apps.fitmystery.domain.mapper.FitMysteryAccountMapper;
import com.apphub.backend.apps.fitmystery.domain.service.crud.FitAccountDeletionRequestCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus CRUD ServiceImpl for `FitAccountDeletionRequestEntity`.
 */
@Service
public class FitAccountDeletionRequestCrudServiceImpl extends ServiceImpl<FitMysteryAccountMapper, FitAccountDeletionRequestEntity> implements FitAccountDeletionRequestCrudService {
}
