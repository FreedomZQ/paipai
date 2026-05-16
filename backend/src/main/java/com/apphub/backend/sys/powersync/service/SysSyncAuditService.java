package com.apphub.backend.sys.powersync.service;

import com.apphub.backend.sys.powersync.entity.SysSyncAuditLogEntity;
import com.apphub.backend.sys.powersync.service.crud.SysSyncAuditLogCrudService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
public class SysSyncAuditService {
    private final SysSyncAuditLogCrudService auditLogCrudService;
    private final ObjectMapper objectMapper;

    public SysSyncAuditService(SysSyncAuditLogCrudService auditLogCrudService, ObjectMapper objectMapper) {
        this.auditLogCrudService = auditLogCrudService;
        this.objectMapper = objectMapper;
    }

    public void log(
        String appCode,
        Long userId,
        String installationId,
        String actionType,
        String entityType,
        String entityId,
        String requestId,
        String resultStatus,
        Object detail
    ) {
        SysSyncAuditLogEntity entity = new SysSyncAuditLogEntity();
        entity.setAppCode(appCode);
        entity.setUserId(userId);
        entity.setInstallationId(blankToNull(installationId));
        entity.setActionType(actionType);
        entity.setEntityType(blankToNull(entityType));
        entity.setEntityId(blankToNull(entityId));
        entity.setRequestId(blankToNull(requestId));
        entity.setResultStatus(resultStatus == null || resultStatus.isBlank() ? "accepted" : resultStatus.trim());
        entity.setDetailJson(toJson(detail));
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        auditLogCrudService.insertJsonb(entity);
    }

    private String toJson(Object detail) {
        try {
            if (detail == null) {
                return objectMapper.writeValueAsString(Map.of());
            }
            return objectMapper.writeValueAsString(detail);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
