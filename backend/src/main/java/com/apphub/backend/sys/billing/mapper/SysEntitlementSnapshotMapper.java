package com.apphub.backend.sys.billing.mapper;

import com.apphub.backend.sys.billing.entity.SysEntitlementSnapshotEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis Plus Mapper 接口 `SysEntitlementSnapshotMapper`。
 * 负责 计费 领域的数据访问，封装对数据库表或视图的读写操作。
 */

@Mapper
public interface SysEntitlementSnapshotMapper extends BaseMapper<SysEntitlementSnapshotEntity> {
}
