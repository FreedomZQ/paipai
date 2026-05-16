package com.apphub.backend.apps.common;

import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.auth.model.AuthenticatedSessionView;
import com.apphub.backend.sys.auth.service.SessionTokenResolver;
import com.apphub.backend.sys.auth.service.SysAuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 统一 app-scoped 控制器辅助类。
 *
 * <p>P3 的目标之一，是把“先校验 app 是否存在，再校验 bearer 会话确实属于该 app”
 * 这一套门禁逻辑从各控制器里收敛出来，避免未来新增 App 时复制粘贴、再各自漂移。</p>
 */
@Component
public class AppCompatControllerSupport {
    private final AppDefinitionService appDefinitionService;
    private final ObjectProvider<SessionTokenResolver> sessionTokenResolverProvider;
    private final ObjectProvider<SysAuthSessionService> sysAuthSessionServiceProvider;

    public AppCompatControllerSupport(
        AppDefinitionService appDefinitionService,
        ObjectProvider<SessionTokenResolver> sessionTokenResolverProvider,
        ObjectProvider<SysAuthSessionService> sysAuthSessionServiceProvider
    ) {
        this.appDefinitionService = appDefinitionService;
        this.sessionTokenResolverProvider = sessionTokenResolverProvider;
        this.sysAuthSessionServiceProvider = sysAuthSessionServiceProvider;
    }

    public AppDefinition requireAppDefinition(String appCode) {
        return appDefinitionService.get(appCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "App definition not found: " + appCode));
    }

    /**
     * 只有真正涉及 bearer 会话的接口才需要鉴权依赖。
     *
     * <p>这里使用延迟获取，而不是在构造阶段强绑所有认证 Bean，
     * 这样像 App Store webhook 这种只需要 app definition 的控制器，
     * 在 WebMvc slice test 中就不必额外 mock 一整套会话服务。</p>
     */
    public String requireSessionToken(HttpServletRequest request) {
        SessionTokenResolver sessionTokenResolver = sessionTokenResolverProvider.getIfAvailable();
        if (sessionTokenResolver == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SessionTokenResolver is not configured");
        }
        return sessionTokenResolver.resolve(request)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    public AuthenticatedSessionView requireCurrentSession(String sessionToken) {
        SysAuthSessionService sysAuthSessionService = sysAuthSessionServiceProvider.getIfAvailable();
        if (sysAuthSessionService == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SysAuthSessionService is not configured");
        }
        return sysAuthSessionService.findCurrentSession(sessionToken)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    public AuthenticatedSessionView requireCurrentSessionForApp(String appCode, HttpServletRequest request) {
        requireAppDefinition(appCode);
        AuthenticatedSessionView session = requireCurrentSession(requireSessionToken(request));
        ensureSessionBelongsToApp(appCode, session.appCode());
        return session;
    }

    public void ensureSessionBelongsToApp(String expectedAppCode, String actualAppCode) {
        if (!expectedAppCode.equals(actualAppCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session appCode mismatch");
        }
    }
}
