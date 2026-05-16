package com.apphub.backend.sys.powersync.service;

import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import com.apphub.backend.sys.auth.service.SysAuthDataService;
import com.apphub.backend.sys.auth.service.SessionTokenHashService;
import com.apphub.backend.sys.auth.service.SessionTokenResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

@Service
public class SysPowerSyncSessionService {
    private final AppDefinitionService appDefinitionService;
    private final SessionTokenResolver sessionTokenResolver;
    private final SessionTokenHashService sessionTokenHashService;
    private final SysAuthDataService authDataService;

    public SysPowerSyncSessionService(
        AppDefinitionService appDefinitionService,
        SessionTokenResolver sessionTokenResolver,
        SessionTokenHashService sessionTokenHashService,
        SysAuthDataService authDataService
    ) {
        this.appDefinitionService = appDefinitionService;
        this.sessionTokenResolver = sessionTokenResolver;
        this.sessionTokenHashService = sessionTokenHashService;
        this.authDataService = authDataService;
    }

    public PowerSyncSessionContext require(String appCode, HttpServletRequest request) {
        appDefinitionService.get(appCode).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POWERSYNC_APP_UNSUPPORTED"));
        String rawToken = sessionTokenResolver.resolve(request)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED"));
        SysAuthSessionEntity session = authDataService.activeSessionByTokenHash(
            sessionTokenHashService.hash(rawToken),
            OffsetDateTime.now()
        );
        if (session == null || !appCode.equals(session.getAppCode())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
        }
        SysUserEntity user = authDataService.userById(session.getUserId());
        if (user == null || !appCode.equals(user.getAppCode()) || "deleted".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
        }
        return new PowerSyncSessionContext(appCode, rawToken, session, user);
    }

    public record PowerSyncSessionContext(
        String appCode,
        String rawToken,
        SysAuthSessionEntity session,
        SysUserEntity user
    ) {
        public Long userId() {
            return session.getUserId();
        }

        public Long sessionId() {
            return session.getId();
        }
    }
}
