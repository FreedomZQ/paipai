package com.apphub.backend.sys.auth.service.crud.impl;

import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.mapper.SysAuthSessionMapper;
import com.apphub.backend.sys.auth.service.crud.SysAuthSessionCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus CRUD ServiceImpl for `SysAuthSessionEntity`.
 */
@Service
public class SysAuthSessionCrudServiceImpl extends ServiceImpl<SysAuthSessionMapper, SysAuthSessionEntity> implements SysAuthSessionCrudService {
}
