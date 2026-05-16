package com.apphub.backend.sys.powersync.service.crud.impl;

import com.apphub.backend.sys.powersync.entity.SysSyncInstallationEntity;
import com.apphub.backend.sys.powersync.mapper.SysSyncInstallationMapper;
import com.apphub.backend.sys.powersync.service.crud.SysSyncInstallationCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus CRUD ServiceImpl for `SysSyncInstallationEntity`.
 */
@Service
public class SysSyncInstallationCrudServiceImpl extends ServiceImpl<SysSyncInstallationMapper, SysSyncInstallationEntity> implements SysSyncInstallationCrudService {
    @Override
    public SysSyncInstallationEntity selectByInstallationId(String installationId) {
        return baseMapper.selectByInstallationId(installationId);
    }

    @Override
    public SysSyncInstallationEntity selectOwned(String appCode, Long userId, String installationId) {
        return baseMapper.selectOwned(appCode, userId, installationId);
    }
}
