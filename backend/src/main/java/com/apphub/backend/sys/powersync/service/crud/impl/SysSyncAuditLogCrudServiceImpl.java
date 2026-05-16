package com.apphub.backend.sys.powersync.service.crud.impl;

import com.apphub.backend.sys.powersync.entity.SysSyncAuditLogEntity;
import com.apphub.backend.sys.powersync.mapper.SysSyncAuditLogMapper;
import com.apphub.backend.sys.powersync.service.crud.SysSyncAuditLogCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus CRUD ServiceImpl for `SysSyncAuditLogEntity`.
 */
@Service
public class SysSyncAuditLogCrudServiceImpl extends ServiceImpl<SysSyncAuditLogMapper, SysSyncAuditLogEntity> implements SysSyncAuditLogCrudService {
    @Override
    public int insertJsonb(SysSyncAuditLogEntity entity) {
        return baseMapper.insertJsonb(entity);
    }
}
