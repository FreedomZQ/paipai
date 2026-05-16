package com.apphub.backend.sys.configcenter.mapper;

import com.apphub.backend.sys.configcenter.entity.SysRemoteConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis Plus Mapper 接口 `SysRemoteConfigMapper`。
 * 负责 配置中心 领域的数据访问，封装对数据库表或视图的读写操作。
 */

@Mapper
public interface SysRemoteConfigMapper extends BaseMapper<SysRemoteConfigEntity> {
}
