package com.apphub.backend.sys.appstore.service.crud;

import com.apphub.backend.sys.appstore.entity.SysAppStoreNotificationEntity;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * MyBatis-Plus CRUD Service for `SysAppStoreNotificationEntity`.
 */
import java.util.List;

public interface SysAppStoreNotificationCrudService extends IService<SysAppStoreNotificationEntity> {
    int countByApp(String appCode);
    int countByAppAndVerificationStatus(String appCode, String verificationStatus);
    int countByAppAndProcessingStatus(String appCode, String processingStatus);
    List<SysAppStoreNotificationEntity> selectRecentByApp(String appCode, int limit);
}
