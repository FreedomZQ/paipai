package com.apphub.backend.sys.auth.controller;

import com.apphub.backend.apps.common.AppCompatControllerSupport;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.auth.model.AppleExchangePreviewView;
import com.apphub.backend.sys.auth.model.AppleRevokeResultView;
import com.apphub.backend.sys.auth.model.AppleSessionRefreshView;
import com.apphub.backend.sys.auth.model.AuthenticatedSessionView;
import com.apphub.backend.sys.auth.model.DecodedAppleIdentityTokenView;
import com.apphub.backend.sys.auth.model.CurrentUserView;
import com.apphub.backend.sys.auth.model.DemoSessionCreateRequest;
import com.apphub.backend.sys.auth.model.DemoSessionCreatedView;
import com.apphub.backend.sys.auth.model.LogoutResultView;
import com.apphub.backend.sys.auth.service.PublicAuthAccessPolicyService;
import com.apphub.backend.sys.auth.service.SessionTokenResolver;
import com.apphub.backend.sys.auth.service.SysAppleAuthService;
import com.apphub.backend.sys.auth.service.SysAuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 针对 `SysAuthController` 的 WebMvc 测试。
 * 用于验证控制器路由、请求校验、响应结构以及鉴权或门禁行为是否符合预期。
 */

@WebMvcTest(SysAuthController.class)
@Import({SessionTokenResolver.class, AppCompatControllerSupport.class})
class SysAuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppDefinitionService appDefinitionService;

    @MockBean
    private SysAuthSessionService sysAuthSessionService;

    @MockBean
    private SysAppleAuthService sysAppleAuthService;

    @MockBean
    private PublicAuthAccessPolicyService publicAuthAccessPolicyService;

    @Test
    void createDemoSessionShouldReturnCreatedSession() throws Exception {
        when(appDefinitionService.get("saving"))
            .thenReturn(Optional.of(appDefinition("saving", "/v1", "saving_", Map.of("app.auth.demoSessionEnabled", "true"))));
        when(publicAuthAccessPolicyService.demoSessionsEnabled(any(AppDefinition.class))).thenReturn(true);
        when(sysAuthSessionService.createDemoSession(eq("saving"), any(DemoSessionCreateRequest.class)))
            .thenReturn(new DemoSessionCreatedView(
                "saving",
                "demo",
                "token-123",
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                new CurrentUserView(101L, "saving", "guest", "Test Guest", "active")
            ));

        mockMvc.perform(post("/api/v1/system/auth/apps/saving/sessions/demo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "clientPlatform": "ios",
                      "clientVersion": "1.0.0",
                      "displayName": "Test Guest"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.appCode").value("saving"))
            .andExpect(jsonPath("$.data.sessionSource").value("demo"))
            .andExpect(jsonPath("$.data.sessionToken").value("token-123"))
            .andExpect(jsonPath("$.data.user.displayName").value("Test Guest"));
    }

    @Test
    void createDemoSessionShouldReturnForbiddenWhenDisabled() throws Exception {
        when(appDefinitionService.get("paipai_readingcompanion"))
            .thenReturn(Optional.of(appDefinition("paipai_readingcompanion", "/api/v1", "reading_", Map.of("app.auth.demoSessionEnabled", "false"))));
        when(publicAuthAccessPolicyService.demoSessionsEnabled(any(AppDefinition.class))).thenReturn(false);

        mockMvc.perform(post("/api/v1/system/auth/apps/paipai_readingcompanion/sessions/demo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void exchangeAppleShouldReturnPreview() throws Exception {
        when(appDefinitionService.get("paipai_readingcompanion"))
            .thenReturn(Optional.of(appDefinition()));
        when(sysAppleAuthService.exchange(eq(appDefinition()), any()))
            .thenReturn(new AppleExchangePreviewView(
                false,
                "pending_apple_server_integration",
                "placeholder_not_executed",
                "decoded_unverified",
                "preview only",
                new DecodedAppleIdentityTokenView(
                    "apple-user-1",
                    "apple@example.com",
                    true,
                    "https://appleid.apple.com",
                    java.util.List.of("com.example.reading"),
                    "nonce-123",
                    true,
                    false,
                    OffsetDateTime.parse("2100-01-01T00:00:00Z"),
                    OffsetDateTime.parse("2026-04-16T00:00:00Z"),
                    "ES256",
                    "kid-1"
                ),
                Map.of("appCode", "paipai_readingcompanion"),
                null
            ));

        mockMvc.perform(post("/api/v1/system/auth/apps/paipai_readingcompanion/apple/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "identityToken": "header.payload.signature",
                      "authorizationCode": "auth-code",
                      "state": "state-123",
                      "expectedState": "state-123",
                      "nonce": "nonce-123",
                      "expectedNonce": "nonce-123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.overallStatus").value("pending_apple_server_integration"))
            .andExpect(jsonPath("$.data.decodedToken.subject").value("apple-user-1"));
    }

    @Test
    void meShouldResolveBearerToken() throws Exception {
        when(sysAuthSessionService.findCurrentSession("token-123"))
            .thenReturn(Optional.of(new AuthenticatedSessionView(
                "paipai_readingcompanion",
                "demo",
                "active",
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                new CurrentUserView(101L, "paipai_readingcompanion", "guest", "Test Guest", "active")
            )));

        mockMvc.perform(get("/api/v1/system/auth/me")
                .header("Authorization", "Bearer token-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.sessionStatus").value("active"))
            .andExpect(jsonPath("$.data.user.userId").value(101));
    }

    @Test
    void meForAppShouldRequireMatchingSessionAppCode() throws Exception {
        when(appDefinitionService.get("paipai_readingcompanion"))
            .thenReturn(Optional.of(appDefinition()));
        when(sysAuthSessionService.findCurrentSession("token-123"))
            .thenReturn(Optional.of(new AuthenticatedSessionView(
                "paipai_readingcompanion",
                "apple",
                "active",
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                new CurrentUserView(101L, "paipai_readingcompanion", "member", "Apple User", "active")
            )));

        mockMvc.perform(get("/api/v1/system/auth/apps/paipai_readingcompanion/me")
                .header("Authorization", "Bearer token-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.sessionStatus").value("active"));
    }

    @Test
    void meForAppShouldRejectMismatchedSessionAppCode() throws Exception {
        when(appDefinitionService.get("saving"))
            .thenReturn(Optional.of(appDefinition("saving", "/v1", "saving_")));
        when(sysAuthSessionService.findCurrentSession("token-123"))
            .thenReturn(Optional.of(new AuthenticatedSessionView(
                "paipai_readingcompanion",
                "apple",
                "active",
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                new CurrentUserView(101L, "paipai_readingcompanion", "member", "Apple User", "active")
            )));

        mockMvc.perform(get("/api/v1/system/auth/apps/saving/me")
                .header("Authorization", "Bearer token-123"))
            .andExpect(status().isForbidden());
    }

    @Test
    void logoutShouldReturnRevokedStatus() throws Exception {
        when(sysAuthSessionService.logout("token-123"))
            .thenReturn(Optional.of(new LogoutResultView(
                "paipai_readingcompanion",
                "revoked",
                OffsetDateTime.parse("2026-04-16T00:00:00Z")
            )));

        mockMvc.perform(post("/api/v1/system/auth/logout")
                .header("X-Session-Token", "token-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.sessionStatus").value("revoked"));
    }

    @Test
    void logoutForAppShouldRequireMatchingSessionAppCode() throws Exception {
        when(appDefinitionService.get("paipai_readingcompanion"))
            .thenReturn(Optional.of(appDefinition()));
        when(sysAuthSessionService.findCurrentSession("token-123"))
            .thenReturn(Optional.of(new AuthenticatedSessionView(
                "paipai_readingcompanion",
                "apple",
                "active",
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                new CurrentUserView(101L, "paipai_readingcompanion", "member", "Apple User", "active")
            )));
        when(sysAuthSessionService.logout("token-123"))
            .thenReturn(Optional.of(new LogoutResultView(
                "paipai_readingcompanion",
                "revoked",
                OffsetDateTime.parse("2026-04-16T00:00:00Z")
            )));

        mockMvc.perform(post("/api/v1/system/auth/apps/paipai_readingcompanion/logout")
                .header("Authorization", "Bearer token-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.sessionStatus").value("revoked"));
    }

    @Test
    void logoutForAppShouldRejectMismatchedSessionAppCode() throws Exception {
        when(appDefinitionService.get("saving"))
            .thenReturn(Optional.of(appDefinition("saving", "/v1", "saving_")));
        when(sysAuthSessionService.findCurrentSession("token-123"))
            .thenReturn(Optional.of(new AuthenticatedSessionView(
                "paipai_readingcompanion",
                "apple",
                "active",
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                new CurrentUserView(101L, "paipai_readingcompanion", "member", "Apple User", "active")
            )));

        mockMvc.perform(post("/api/v1/system/auth/apps/saving/logout")
                .header("Authorization", "Bearer token-123"))
            .andExpect(status().isForbidden());
    }

    @Test
    void revokeAppleShouldReturnRevocationStatus() throws Exception {
        when(appDefinitionService.get("paipai_readingcompanion"))
            .thenReturn(Optional.of(appDefinition()));
        when(sysAuthSessionService.findCurrentSession("token-123"))
            .thenReturn(Optional.of(new AuthenticatedSessionView(
                "paipai_readingcompanion",
                "apple",
                "active",
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                new CurrentUserView(101L, "paipai_readingcompanion", "member", "Apple User", "active")
            )));
        when(sysAppleAuthService.revoke(eq(appDefinition()), eq("token-123")))
            .thenReturn(Optional.of(new AppleRevokeResultView(
                "paipai_readingcompanion",
                "revoked",
                "revoked",
                "succeeded",
                true,
                OffsetDateTime.parse("2026-04-16T00:00:00Z"),
                "done"
            )));

        mockMvc.perform(post("/api/v1/system/auth/apps/paipai_readingcompanion/apple/revoke")
                .header("Authorization", "Bearer token-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.remoteRevokeStatus").value("succeeded"))
            .andExpect(jsonPath("$.data.sessionStatus").value("revoked"));
    }

    @Test
    void revokeAppleShouldRejectMismatchedSessionAppCode() throws Exception {
        when(appDefinitionService.get("saving"))
            .thenReturn(Optional.of(appDefinition("saving", "/v1", "saving_")));
        when(sysAuthSessionService.findCurrentSession("token-123"))
            .thenReturn(Optional.of(new AuthenticatedSessionView(
                "paipai_readingcompanion",
                "apple",
                "active",
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                new CurrentUserView(101L, "paipai_readingcompanion", "member", "Apple User", "active")
            )));

        mockMvc.perform(post("/api/v1/system/auth/apps/saving/apple/revoke")
                .header("Authorization", "Bearer token-123"))
            .andExpect(status().isForbidden());
    }

    @Test
    void refreshAppleShouldReturnNewSession() throws Exception {
        when(appDefinitionService.get("paipai_readingcompanion"))
            .thenReturn(Optional.of(appDefinition()));
        when(sysAuthSessionService.findCurrentSession("token-123"))
            .thenReturn(Optional.of(new AuthenticatedSessionView(
                "paipai_readingcompanion",
                "apple",
                "active",
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                new CurrentUserView(101L, "paipai_readingcompanion", "member", "Apple User", "active")
            )));
        when(sysAppleAuthService.refresh(eq(appDefinition()), eq("token-123")))
            .thenReturn(Optional.of(new AppleSessionRefreshView(
                true,
                "session_issued",
                "refreshed",
                "verified",
                "done",
                new DecodedAppleIdentityTokenView(
                    "apple-user-1",
                    "apple@example.com",
                    true,
                    "https://appleid.apple.com",
                    java.util.List.of("com.example.reading"),
                    null,
                    true,
                    false,
                    OffsetDateTime.parse("2100-01-01T00:00:00Z"),
                    OffsetDateTime.parse("2026-04-16T00:00:00Z"),
                    "ES256",
                    "kid-1"
                ),
                Map.of("appCode", "paipai_readingcompanion"),
                new com.apphub.backend.sys.auth.model.AuthSessionIssuedView(
                    "paipai_readingcompanion",
                    "apple",
                    "new-token-123",
                    OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                    new CurrentUserView(101L, "paipai_readingcompanion", "member", "Apple User", "active")
                )
            )));

        mockMvc.perform(post("/api/v1/system/auth/apps/paipai_readingcompanion/apple/refresh")
                .header("Authorization", "Bearer token-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessionIssued").value(true))
            .andExpect(jsonPath("$.data.refreshStatus").value("refreshed"))
            .andExpect(jsonPath("$.data.issuedSession.sessionToken").value("new-token-123"));
    }

    @Test
    void refreshAppleShouldRejectMismatchedSessionAppCode() throws Exception {
        when(appDefinitionService.get("saving"))
            .thenReturn(Optional.of(appDefinition("saving", "/v1", "saving_")));
        when(sysAuthSessionService.findCurrentSession("token-123"))
            .thenReturn(Optional.of(new AuthenticatedSessionView(
                "paipai_readingcompanion",
                "apple",
                "active",
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                new CurrentUserView(101L, "paipai_readingcompanion", "member", "Apple User", "active")
            )));

        mockMvc.perform(post("/api/v1/system/auth/apps/saving/apple/refresh")
                .header("Authorization", "Bearer token-123"))
            .andExpect(status().isForbidden());
    }

    @Test
    void meShouldReturnUnauthorizedWhenSessionTokenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/system/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    private AppDefinition appDefinition() {
        return appDefinition("paipai_readingcompanion", "/api/v1", "reading_", Map.of());
    }

    private AppDefinition appDefinition(String code, String apiPrefix, String tablePrefix) {
        return appDefinition(code, apiPrefix, tablePrefix, Map.of());
    }

    private AppDefinition appDefinition(String code, String apiPrefix, String tablePrefix, Map<String, Object> raw) {
        return new AppDefinition(
            code,
            code,
            apiPrefix,
            tablePrefix,
            new AppDefinition.Support(true, true, true),
            raw
        );
    }
}
