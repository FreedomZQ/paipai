package com.apphub.backend.sys.powersync.service;

import com.apphub.backend.sys.powersync.entity.SysSyncInstallationEntity;
import com.apphub.backend.sys.powersync.model.PowerSyncBootstrapRequest;
import com.apphub.backend.sys.powersync.service.crud.SysSyncInstallationCrudService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class SysSyncInstallationService {
    private final SysSyncInstallationCrudService installationCrudService;

    public SysSyncInstallationService(SysSyncInstallationCrudService installationCrudService) {
        this.installationCrudService = installationCrudService;
    }

    @Transactional
    public SysSyncInstallationEntity upsertBootstrap(String appCode, Long userId, PowerSyncBootstrapRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String installationId = trimRequired(request.installationId(), "installationId");
        SysSyncInstallationEntity existing = installationCrudService.selectByInstallationId(installationId);
        if (existing != null && (!appCode.equals(existing.getAppCode()) || !userId.equals(existing.getUserId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "POWERSYNC_INSTALLATION_USER_MISMATCH");
        }
        boolean created = existing == null;
        SysSyncInstallationEntity entity = created ? new SysSyncInstallationEntity() : existing;
        entity.setInstallationId(installationId);
        entity.setAppCode(appCode);
        entity.setUserId(userId);
        entity.setDeviceId(blankToNull(request.deviceId()));
        entity.setClientPlatform(trimRequired(request.clientPlatform(), "clientPlatform"));
        entity.setDeviceModel(blankToNull(request.deviceModel()));
        entity.setAppVersion(blankToNull(request.appVersion()));
        entity.setPowersyncClientId(blankToNull(request.powersyncClientId()));
        entity.setCloudSyncEnabled(request.cloudSyncEnabled());
        if (created) {
            entity.setInitialSyncCompleted(Boolean.FALSE);
            entity.setCreatedAt(now);
        } else if (entity.getInitialSyncCompleted() == null) {
            entity.setInitialSyncCompleted(Boolean.FALSE);
        }
        entity.setUpdatedAt(now);
        if (created) {
            installationCrudService.save(entity);
        } else {
            installationCrudService.updateById(entity);
        }
        return entity;
    }

    public SysSyncInstallationEntity requireOwned(String appCode, Long userId, String installationId) {
        SysSyncInstallationEntity entity = installationCrudService.selectByInstallationId(trimRequired(installationId, "installationId"));
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "POWERSYNC_INSTALLATION_NOT_FOUND");
        }
        if (!appCode.equals(entity.getAppCode()) || !userId.equals(entity.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "POWERSYNC_INSTALLATION_USER_MISMATCH");
        }
        return entity;
    }

    @Transactional
    public SysSyncInstallationEntity requestRebuild(String appCode, Long userId, String installationId, String reason) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SysSyncInstallationEntity entity = requireOwned(appCode, userId, installationId);
        entity.setInitialSyncCompleted(Boolean.FALSE);
        entity.setLastErrorCode("REBUILD_REQUESTED");
        entity.setLastErrorMessage(blankToNull(reason));
        entity.setUpdatedAt(now);
        installationCrudService.updateById(entity);
        return entity;
    }

    @Transactional
    public void markPushProcessed(String appCode, Long userId, String installationId, int acceptedCount, int rejectedCount) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SysSyncInstallationEntity entity = requireOwned(appCode, userId, installationId);
        entity.setLastPushAt(now);
        if (acceptedCount > 0) {
            entity.setLastSyncAt(now);
            entity.setInitialSyncCompleted(Boolean.TRUE);
        }
        entity.setLastErrorCode(null);
        entity.setLastErrorMessage(null);
        entity.setUpdatedAt(now);
        installationCrudService.updateById(entity);
    }

    @Transactional
    public void markFailure(String appCode, Long userId, String installationId, String errorCode, String errorMessage) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SysSyncInstallationEntity entity = requireOwned(appCode, userId, installationId);
        entity.setLastErrorCode(blankToNull(errorCode));
        entity.setLastErrorMessage(truncate(blankToNull(errorMessage), 1000));
        entity.setUpdatedAt(now);
        installationCrudService.updateById(entity);
    }

    private String trimRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + "_REQUIRED");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
