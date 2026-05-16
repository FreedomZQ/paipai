package com.apphub.backend.apps.saving.domain.service.crud.impl;

import com.apphub.backend.apps.saving.domain.entity.SavingExpenseRecordEntity;
import com.apphub.backend.apps.saving.domain.mapper.SavingFinanceMapper;
import com.apphub.backend.apps.saving.domain.service.crud.SavingExpenseRecordCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus CRUD ServiceImpl for `SavingExpenseRecordEntity`.
 */
@Service
public class SavingExpenseRecordCrudServiceImpl extends ServiceImpl<SavingFinanceMapper, SavingExpenseRecordEntity> implements SavingExpenseRecordCrudService {
}
