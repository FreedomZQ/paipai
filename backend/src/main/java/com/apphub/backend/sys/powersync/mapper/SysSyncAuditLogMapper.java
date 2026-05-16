package com.apphub.backend.sys.powersync.mapper;

import com.apphub.backend.sys.powersync.entity.SysSyncAuditLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysSyncAuditLogMapper extends BaseMapper<SysSyncAuditLogEntity> {
    @Insert("""
        INSERT INTO sys_sync_audit_log
        (app_code, user_id, installation_id, action_type, entity_type, entity_id, request_id, result_status, detail_json, created_at)
        VALUES
        (#{appCode}, #{userId}, #{installationId}, #{actionType}, #{entityType}, #{entityId}, #{requestId}, #{resultStatus}, CAST(#{detailJson} AS jsonb), #{createdAt})
        """)
    int insertJsonb(SysSyncAuditLogEntity entity);
}
