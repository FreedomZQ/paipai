package com.apphub.backend.apps.saving.service;

import com.apphub.backend.apps.saving.SavingAppModule;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.sys.auth.model.AuthenticatedSessionView;
import com.apphub.backend.sys.auth.service.SessionTokenResolver;
import com.apphub.backend.sys.auth.service.SysAuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SavingRequestSupport {
    private final SessionTokenResolver sessionTokenResolver;
    private final SysAuthSessionService sysAuthSessionService;

    public SavingRequestSupport(SessionTokenResolver sessionTokenResolver, SysAuthSessionService sysAuthSessionService) {
        this.sessionTokenResolver = sessionTokenResolver;
        this.sysAuthSessionService = sysAuthSessionService;
    }

    public Long requireUserId(HttpServletRequest request) {
        String token = sessionTokenResolver.resolve(request)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token"));
        AuthenticatedSessionView session = sysAuthSessionService.findCurrentSession(token)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bearer token"));
        if (!SavingAppModule.APP_CODE.equals(session.appCode())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token appCode mismatch");
        }
        return session.user().userId();
    }

    public String requestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null || requestId.isBlank() ? "unknown" : requestId;
    }
}
