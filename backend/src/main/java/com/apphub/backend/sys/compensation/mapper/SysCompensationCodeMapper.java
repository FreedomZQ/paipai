package com.apphub.backend.sys.compensation.mapper;

import com.apphub.backend.sys.compensation.entity.SysCompensationCodeEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 中文说明：补偿码主表 Mapper，所有查询必须按 appCode 过滤。 */
@Mapper
public interface SysCompensationCodeMapper extends BaseMapper<SysCompensationCodeEntity> {
}
