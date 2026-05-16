package com.apphub.backend.apps.reading.common;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import com.apphub.backend.sys.auth.service.SysAuthDataService;
import com.apphub.backend.sys.auth.service.SessionTokenHashService;
import com.apphub.backend.sys.auth.service.SessionTokenResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

/**
 * 拍拍伴读 Bearer session 解析器。
 * 内部仍复用 reading 领域名，但这里承载的是 appCode=`paipai_readingcompanion` 的鉴权入口。
 */
@Component
public class ReadingAuthenticatedUserResolver {
    private final ReadingAppModule readingAppModule;
    private final SessionTokenResolver sessionTokenResolver;
    private final SessionTokenHashService sessionTokenHashService;
    private final SysAuthDataService authDataService;

    public ReadingAuthenticatedUserResolver(
        ReadingAppModule readingAppModule,
        SessionTokenResolver sessionTokenResolver,
        SessionTokenHashService sessionTokenHashService,
        SysAuthDataService authDataService
    ) {
        this.readingAppModule = readingAppModule;
        this.sessionTokenResolver = sessionTokenResolver;
        this.sessionTokenHashService = sessionTokenHashService;
        this.authDataService = authDataService;
    }

    public ReadingAuthenticatedUser require(HttpServletRequest request) {
        ReadingAuthenticatedUser user = resolveOptional(request);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
        }
        return user;
    }

    public ReadingAuthenticatedUser resolveOptional(HttpServletRequest request) {
        String rawToken = sessionTokenResolver.resolve(request).orElse(null);
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        SysAuthSessionEntity session = authDataService.activeSessionByTokenHash(
            sessionTokenHashService.hash(rawToken),
            OffsetDateTime.now()
        );
        String appCode = readingAppModule.appCode();
        if (session == null || !appCode.equals(session.getAppCode())) {
            return null;
        }
        SysUserEntity user = authDataService.userById(session.getUserId());
        if (user == null || !appCode.equals(user.getAppCode()) || "deleted".equalsIgnoreCase(user.getStatus())) {
            return null;
        }
        return new ReadingAuthenticatedUser(session, user, rawToken);
    }
}
