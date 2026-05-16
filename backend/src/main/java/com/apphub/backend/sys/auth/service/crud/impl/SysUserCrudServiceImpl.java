package com.apphub.backend.sys.auth.service.crud.impl;

import com.apphub.backend.sys.auth.entity.SysUserEntity;
import com.apphub.backend.sys.auth.mapper.SysUserMapper;
import com.apphub.backend.sys.auth.service.crud.SysUserCrudService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus CRUD ServiceImpl for `SysUserEntity`.
 */
@Service
public class SysUserCrudServiceImpl extends ServiceImpl<SysUserMapper, SysUserEntity> implements SysUserCrudService {
}
