package com.apphub.backend.sys.powersync.service.crud;

import com.apphub.backend.sys.powersync.entity.SysSyncInstallationEntity;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * MyBatis-Plus CRUD Service for `SysSyncInstallationEntity`.
 */
public interface SysSyncInstallationCrudService extends IService<SysSyncInstallationEntity> {
    SysSyncInstallationEntity selectByInstallationId(String installationId);
    SysSyncInstallationEntity selectOwned(String appCode, Long userId, String installationId);
}
