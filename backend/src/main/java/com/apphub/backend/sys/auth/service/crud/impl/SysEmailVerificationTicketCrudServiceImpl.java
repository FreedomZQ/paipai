package com.apphub.backend.sys.auth.service.crud.impl;

import com.apphub.backend.sys.auth.entity.SysEmailVerificationTicketEntity;
import com.apphub.backend.sys.auth.mapper.SysEmailVerificationTicketMapper;
import com.apphub.backend.sys.auth.service.crud.SysEmailVerificationTicketCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * MyBatis-Plus CRUD ServiceImpl for `SysEmailVerificationTicketEntity`.
 */
@Service
public class SysEmailVerificationTicketCrudServiceImpl extends ServiceImpl<SysEmailVerificationTicketMapper, SysEmailVerificationTicketEntity> implements SysEmailVerificationTicketCrudService {
    @Override
    public SysEmailVerificationTicketEntity selectLatest(String appCode, String emailKey, String sceneCode) {
        return baseMapper.selectLatest(appCode, emailKey, sceneCode);
    }

    @Override
    public int expirePending(String appCode, String emailKey, String sceneCode, OffsetDateTime now) {
        return baseMapper.expirePending(appCode, emailKey, sceneCode, now);
    }
}
