package com.apphub.backend.sys.auth.service.crud;

import com.apphub.backend.sys.auth.entity.SysEmailVerificationTicketEntity;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * MyBatis-Plus CRUD Service for `SysEmailVerificationTicketEntity`.
 */
import java.time.OffsetDateTime;

public interface SysEmailVerificationTicketCrudService extends IService<SysEmailVerificationTicketEntity> {
    SysEmailVerificationTicketEntity selectLatest(String appCode, String emailKey, String sceneCode);
    int expirePending(String appCode, String emailKey, String sceneCode, OffsetDateTime now);
}
