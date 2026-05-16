package com.apphub.backend.sys.appstore.service.crud.impl;

import com.apphub.backend.sys.appstore.entity.SysAppStoreNotificationEntity;
import com.apphub.backend.sys.appstore.mapper.SysAppStoreNotificationMapper;
import com.apphub.backend.sys.appstore.service.crud.SysAppStoreNotificationCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MyBatis-Plus CRUD ServiceImpl for `SysAppStoreNotificationEntity`.
 */
@Service
public class SysAppStoreNotificationCrudServiceImpl extends ServiceImpl<SysAppStoreNotificationMapper, SysAppStoreNotificationEntity> implements SysAppStoreNotificationCrudService {
    @Override
    public int countByApp(String appCode) {
        return baseMapper.countByApp(appCode);
    }

    @Override
    public int countByAppAndVerificationStatus(String appCode, String verificationStatus) {
        return baseMapper.countByAppAndVerificationStatus(appCode, verificationStatus);
    }

    @Override
    public int countByAppAndProcessingStatus(String appCode, String processingStatus) {
        return baseMapper.countByAppAndProcessingStatus(appCode, processingStatus);
    }

    @Override
    public List<SysAppStoreNotificationEntity> selectRecentByApp(String appCode, int limit) {
        return baseMapper.selectRecentByApp(appCode, limit);
    }
}
