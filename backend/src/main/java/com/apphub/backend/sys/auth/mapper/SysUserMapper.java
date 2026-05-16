package com.apphub.backend.sys.auth.mapper;

import com.apphub.backend.sys.auth.entity.SysUserEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis Plus Mapper 接口 `SysUserMapper`。
 * 负责 认证 领域的数据访问，封装对数据库表或视图的读写操作。
 */

@Mapper
public interface SysUserMapper extends BaseMapper<SysUserEntity> {
    @Select("""
        SELECT *
        FROM sys_user
        WHERE app_code = #{appCode}
          AND status = 'active'
        ORDER BY id ASC
        """)
    List<SysUserEntity> selectActiveByAppCode(@Param("appCode") String appCode);

    @Select("""
        SELECT *
        FROM sys_user
        WHERE app_code = #{appCode}
        ORDER BY id ASC
        """)
    List<SysUserEntity> selectByAppCode(@Param("appCode") String appCode);
}
