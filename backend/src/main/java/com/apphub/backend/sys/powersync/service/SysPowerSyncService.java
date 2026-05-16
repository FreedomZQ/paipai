package com.apphub.backend.sys.powersync.service;

import com.apphub.backend.sys.powersync.entity.SysSyncInstallationEntity;
import com.apphub.backend.sys.powersync.model.PowerSyncBootstrapRequest;
import com.apphub.backend.sys.powersync.model.PowerSyncBootstrapView;
import com.apphub.backend.sys.powersync.model.PowerSyncRebuildRequest;
import com.apphub.backend.sys.powersync.model.PowerSyncRebuildView;
import com.apphub.backend.sys.powersync.model.PowerSyncTokenClaimsView;
import com.apphub.backend.sys.powersync.model.PowerSyncTokenRequest;
import com.apphub.backend.sys.powersync.model.PowerSyncTokenView;
import com.apphub.backend.sys.powersync.support.PowerSyncAppAdapter;
import com.apphub.backend.sys.powersync.support.PowerSyncAppAdapterRegistry;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SysPowerSyncService {
    private final SysPowerSyncSessionService sessionService;
    private final SysSyncInstallationService installationService;
    private final SysSyncAuditService auditService;
    private final PowerSyncAppAdapterRegistry adapterRegistry;

    @Value("${backend.powersync.endpoint:${BACKEND_POWERSYNC_ENDPOINT:https://sync.example.com}}")
    private String powerSyncEndpoint;

    @Value("${backend.powersync.tokenIssuer:${BACKEND_POWERSYNC_TOKEN_ISSUER:apphub-backend}}")
    private String tokenIssuer;

    @Value("${backend.powersync.tokenSecret:${BACKEND_POWERSYNC_TOKEN_SECRET:development-powersync-secret-change-me-32b}}")
    private String tokenSecret;

    @Value("${backend.powersync.tokenTtlMinutes:${BACKEND_POWERSYNC_TOKEN_TTL_MINUTES:30}}")
    private long tokenTtlMinutes;

    public SysPowerSyncService(
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

    public PowerSyncBootstrapView bootstrap(String appCode, PowerSyncBootstrapRequest request, HttpServletRequest servletRequest, String requestId) {
        SysPowerSyncSessionService.PowerSyncSessionContext principal = sessionService.require(appCode, servletRequest);
        PowerSyncAppAdapter adapter = adapterRegistry.require(appCode);
        if (request.cloudSyncEnabled()) {
            adapter.validateSyncAccess(principal.userId());
        }
        SysSyncInstallationEntity installation = installationService.upsertBootstrap(appCode, principal.userId(), request);
        auditService.log(
            appCode,
            principal.userId(),
            installation.getInstallationId(),
            "bootstrap",
            null,
            null,
            requestId,
            "accepted",
            Map.of(
                "clientPlatform", request.clientPlatform(),
                "deviceModel", defaultString(request.deviceModel()),
                "appVersion", defaultString(request.appVersion()),
                "cloudSyncEnabled", installation.getCloudSyncEnabled()
            )
        );
        String tokenExpiresAt = Boolean.TRUE.equals(installation.getCloudSyncEnabled())
            ? OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(Math.max(1L, tokenTtlMinutes)).toString()
            : null;
        return new PowerSyncBootstrapView(
            appCode,
            installation.getInstallationId(),
            Boolean.TRUE.equals(installation.getCloudSyncEnabled()),
            Boolean.TRUE.equals(installation.getInitialSyncCompleted()),
            powerSyncEndpoint,
            tokenExpiresAt,
            false,
            OffsetDateTime.now(ZoneOffset.UTC).toString()
        );
    }

    public PowerSyncTokenView issueToken(String appCode, PowerSyncTokenRequest request, HttpServletRequest servletRequest, String requestId) {
        SysPowerSyncSessionService.PowerSyncSessionContext principal = sessionService.require(appCode, servletRequest);
        PowerSyncAppAdapter adapter = adapterRegistry.require(appCode);
        adapter.validateSyncAccess(principal.userId());
        SysSyncInstallationEntity installation = installationService.requireOwned(appCode, principal.userId(), request.installationId());
        if (!Boolean.TRUE.equals(installation.getCloudSyncEnabled())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "POWERSYNC_DISABLED");
        }
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(Math.max(1L, tokenTtlMinutes));
        String token = signToken(appCode, principal.userId(), installation.getInstallationId(), expiresAt);
        PowerSyncTokenView view = new PowerSyncTokenView(
            powerSyncEndpoint,
            token,
            expiresAt.toString(),
            new PowerSyncTokenClaimsView(appCode, principal.userId(), installation.getInstallationId())
        );
        auditService.log(
            appCode,
            principal.userId(),
            installation.getInstallationId(),
            "issue_token",
            null,
            null,
            requestId,
            "accepted",
            Map.of("expiresAt", expiresAt.toString())
        );
        return view;
    }

    public PowerSyncRebuildView requestRebuild(String appCode, PowerSyncRebuildRequest request, HttpServletRequest servletRequest, String requestId) {
        SysPowerSyncSessionService.PowerSyncSessionContext principal = sessionService.require(appCode, servletRequest);
        adapterRegistry.require(appCode).validateSyncAccess(principal.userId());
        SysSyncInstallationEntity installation = installationService.requestRebuild(appCode, principal.userId(), request.installationId(), request.reason());
        auditService.log(
            appCode,
            principal.userId(),
            installation.getInstallationId(),
            "rebuild_requested",
            null,
            null,
            requestId,
            "accepted",
            Map.of("reason", defaultString(request.reason()))
        );
        return new PowerSyncRebuildView(installation.getInstallationId(), true, "Rebuild has been scheduled.");
    }

    private String signToken(String appCode, Long userId, String installationId, OffsetDateTime expiresAt) {
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(tokenIssuer)
                .subject(String.valueOf(userId))
                .issueTime(Date.from(now.toInstant()))
                .expirationTime(Date.from(expiresAt.toInstant()))
                .claim("appCode", appCode)
                .claim("userId", userId)
                .claim("installationId", installationId)
                .build();
            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
            signedJWT.sign(new MACSigner(normalizeSecret(tokenSecret)));
            return signedJWT.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign PowerSync token.", exception);
        }
    }

    private byte[] normalizeSecret(String value) {
        String secret = value == null || value.isBlank() ? "development-powersync-secret-change-me-32b" : value.trim();
        if (secret.length() < 32) {
            secret = (secret + "00000000000000000000000000000000").substring(0, 32);
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
