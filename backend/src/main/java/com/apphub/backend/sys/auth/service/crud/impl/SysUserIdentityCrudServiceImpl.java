package com.apphub.backend.sys.auth.service.crud.impl;

import com.apphub.backend.sys.auth.entity.SysUserIdentityEntity;
import com.apphub.backend.sys.auth.mapper.SysUserIdentityMapper;
import com.apphub.backend.sys.auth.service.crud.SysUserIdentityCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus CRUD ServiceImpl for `SysUserIdentityEntity`.
 */
@Service
public class SysUserIdentityCrudServiceImpl extends ServiceImpl<SysUserIdentityMapper, SysUserIdentityEntity> implements SysUserIdentityCrudService {
}
