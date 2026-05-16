package com.apphub.backend.sys.powersync.service;

import com.apphub.backend.sys.powersync.entity.SysSyncInstallationEntity;
import com.apphub.backend.sys.powersync.model.PowerSyncUploadEnvelope;
import com.apphub.backend.sys.powersync.model.PowerSyncUploadResult;
import com.apphub.backend.sys.powersync.support.PowerSyncAppAdapter;
import com.apphub.backend.sys.powersync.support.PowerSyncAppAdapterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SysPowerSyncUploadService {
    private final SysPowerSyncSessionService sessionService;
    private final SysSyncInstallationService installationService;
    private final SysSyncAuditService auditService;
    private final PowerSyncAppAdapterRegistry adapterRegistry;

    public SysPowerSyncUploadService(
        SysPowerSyncSessionService sessionService,
        SysSyncInstallationService installationService,
        SysSyncAuditService auditService,
        PowerSyncAppAdapterRegistry adapterRegistry
    ) {
        this.sessionService = sessionService;
        this.installationService = installationService;
        this.auditService = auditService;
        this.adapterRegistry = adapterRegistry;
    }

    public PowerSyncUploadResult upload(String appCode, PowerSyncUploadEnvelope envelope, HttpServletRequest request, String requestId) {
        SysPowerSyncSessionService.PowerSyncSessionContext principal = sessionService.require(appCode, request);
        if (envelope == null || envelope.changes() == null || envelope.changes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "POWERSYNC_UPLOAD_EMPTY");
        }
        PowerSyncAppAdapter adapter = adapterRegistry.require(appCode);
        adapter.validateSyncAccess(principal.userId());
        SysSyncInstallationEntity installation = installationService.requireOwned(appCode, principal.userId(), envelope.installationId());
        if (!Boolean.TRUE.equals(installation.getCloudSyncEnabled())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "POWERSYNC_DISABLED");
        }
        try {
            PowerSyncUploadResult result = adapter.applyBatch(principal.userId(), installation.getInstallationId(), envelope.changes());
            installationService.markPushProcessed(appCode, principal.userId(), installation.getInstallationId(), result.accepted().size(), result.rejected().size());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("changeCount", envelope.changes().size());
            detail.put("acceptedCount", result.accepted().size());
            detail.put("rejectedCount", result.rejected().size());
            auditService.log(
                appCode,
                principal.userId(),
                installation.getInstallationId(),
                "upload_batch",
                null,
                null,
                requestId,
                result.rejected().isEmpty() ? "accepted" : "partial",
                detail
            );
            return result;
        } catch (RuntimeException exception) {
            installationService.markFailure(appCode, principal.userId(), installation.getInstallationId(), "POWERSYNC_UPLOAD_FAILED", exception.getMessage());
            auditService.log(
                appCode,
                principal.userId(),
                installation.getInstallationId(),
                "upload_batch",
                null,
                null,
                requestId,
                "failed",
                Map.of("message", exception.getMessage() == null ? "" : exception.getMessage())
            );
            throw exception;
        }
    }
}
