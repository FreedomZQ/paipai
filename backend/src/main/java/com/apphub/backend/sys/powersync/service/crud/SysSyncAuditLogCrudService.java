package com.apphub.backend.sys.powersync.service.crud;

import com.apphub.backend.sys.powersync.entity.SysSyncAuditLogEntity;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * MyBatis-Plus CRUD Service for `SysSyncAuditLogEntity`.
 */
public interface SysSyncAuditLogCrudService extends IService<SysSyncAuditLogEntity> {
    int insertJsonb(SysSyncAuditLogEntity entity);
}
