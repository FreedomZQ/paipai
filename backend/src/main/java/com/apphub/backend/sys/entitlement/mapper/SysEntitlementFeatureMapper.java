package com.apphub.backend.sys.entitlement.mapper;

import com.apphub.backend.sys.entitlement.entity.SysEntitlementFeatureEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 中文说明：统一权益中心功能定义数据访问接口，查询和写入都必须限定 appCode。 */
@Mapper
public interface SysEntitlementFeatureMapper extends BaseMapper<SysEntitlementFeatureEntity> {
}
