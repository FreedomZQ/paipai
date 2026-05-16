package com.apphub.backend.sys.powersync.mapper;

import com.apphub.backend.sys.powersync.entity.SysSyncInstallationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysSyncInstallationMapper extends BaseMapper<SysSyncInstallationEntity> {

    @Select("""
        SELECT *
        FROM sys_sync_installation
        WHERE installation_id = #{installationId}
        LIMIT 1
        """)
    SysSyncInstallationEntity selectByInstallationId(@Param("installationId") String installationId);

    @Select("""
        SELECT *
        FROM sys_sync_installation
        WHERE installation_id = #{installationId}
          AND app_code = #{appCode}
          AND user_id = #{userId}
        LIMIT 1
        """)
    SysSyncInstallationEntity selectOwned(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("installationId") String installationId
    );
}
