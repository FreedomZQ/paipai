package com.apphub.backend.sys.auth.service.crud.impl;

import com.apphub.backend.sys.auth.entity.SysAuthProviderTokenEntity;
import com.apphub.backend.sys.auth.mapper.SysAuthProviderTokenMapper;
import com.apphub.backend.sys.auth.service.crud.SysAuthProviderTokenCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus CRUD ServiceImpl for `SysAuthProviderTokenEntity`.
 */
@Service
public class SysAuthProviderTokenCrudServiceImpl extends ServiceImpl<SysAuthProviderTokenMapper, SysAuthProviderTokenEntity> implements SysAuthProviderTokenCrudService {
}
