package com.apphub.backend.sys.powersync.service;

import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import com.apphub.backend.sys.auth.service.SessionTokenHashService;
import com.apphub.backend.sys.auth.service.SessionTokenResolver;
import com.apphub.backend.sys.auth.service.SysAuthDataService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * PowerSync 多 App 会话隔离测试。
 *
 * 中文说明：同步 token/upload/rebuild 是儿童学习数据和家庭付费数据的高敏边界。
 * A App 的 session 即使 token 有效，也不能用于 B App 的 PowerSync 入口。
 */
@ExtendWith(MockitoExtension.class)
class SysPowerSyncSessionServiceMultiAppIsolationTest {

    @Mock private AppDefinitionService appDefinitionService;
    @Mock private SessionTokenResolver sessionTokenResolver;
    @Mock private SysAuthDataService authDataService;
    @Mock private HttpServletRequest request;

    private SysPowerSyncSessionService service;

    @BeforeEach
    void setUp() {
        service = new SysPowerSyncSessionService(
            appDefinitionService,
            sessionTokenResolver,
            new SessionTokenHashService(),
            authDataService
        );
    }

    @Test
    void shouldRejectSessionTokenFromAnotherApp() {
        when(appDefinitionService.get("future_story_app")).thenReturn(Optional.of(app("future_story_app")));
        when(sessionTokenResolver.resolve(request)).thenReturn(Optional.of("valid-token-from-paipai"));

        SysAuthSessionEntity paipaiSession = new SysAuthSessionEntity();
        paipaiSession.setId(11L);
        paipaiSession.setAppCode("paipai_readingcompanion");
        paipaiSession.setUserId(21L);
        paipaiSession.setStatus("active");
        when(authDataService.activeSessionByTokenHash(any(), any(OffsetDateTime.class))).thenReturn(paipaiSession);

        assertThatThrownBy(() -> service.require("future_story_app", request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("AUTH_REQUIRED");
    }

    @Test
    void shouldAcceptOnlyWhenSessionAndUserBelongToRequestedApp() {
        when(appDefinitionService.get("future_story_app")).thenReturn(Optional.of(app("future_story_app")));
        when(sessionTokenResolver.resolve(request)).thenReturn(Optional.of("future-token"));

        SysAuthSessionEntity session = new SysAuthSessionEntity();
        session.setId(12L);
        session.setAppCode("future_story_app");
        session.setUserId(22L);
        session.setStatus("active");
        when(authDataService.activeSessionByTokenHash(any(), any(OffsetDateTime.class))).thenReturn(session);

        SysUserEntity user = new SysUserEntity();
        user.setId(22L);
        user.setAppCode("future_story_app");
        user.setStatus("active");
        when(authDataService.userById(eq(22L))).thenReturn(user);

        SysPowerSyncSessionService.PowerSyncSessionContext context = service.require("future_story_app", request);

        assertThat(context.appCode()).isEqualTo("future_story_app");
        assertThat(context.userId()).isEqualTo(22L);
        assertThat(context.sessionId()).isEqualTo(12L);
    }

    private AppDefinition app(String appCode) {
        return new AppDefinition(
            appCode,
            appCode,
            "/api/v1",
            appCode + "_",
            new AppDefinition.Support(true, true, true),
            Map.of()
        );
    }
}
