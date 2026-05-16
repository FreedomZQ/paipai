package com.apphub.backend.sys.entitlement.mapper;

import com.apphub.backend.sys.entitlement.entity.SysPlanFeatureRuleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 中文说明：统一权益中心 SysPlanFeatureRuleEntity 数据访问接口，调用方必须按 appCode 过滤，避免多 App 串权。 */
@Mapper
public interface SysPlanFeatureRuleMapper extends BaseMapper<SysPlanFeatureRuleEntity> {
}
