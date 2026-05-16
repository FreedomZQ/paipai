package com.apphub.backend.sys.auth.service;

import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.auth.entity.SysAuthProviderTokenEntity;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.model.AppleExchangePreviewView;
import com.apphub.backend.sys.auth.model.AppleExchangeRequest;
import com.apphub.backend.sys.auth.model.AppleRevokeResultView;
import com.apphub.backend.sys.auth.model.AppleSessionRefreshView;
import com.apphub.backend.sys.auth.model.AuthSessionIssuedView;
import com.apphub.backend.sys.auth.model.CurrentUserView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 针对 `SysAppleAuthService` 的测试类。
 * 用于验证核心业务逻辑、配置约束或关键边界条件。
 */

@ExtendWith(MockitoExtension.class)
class SysAppleAuthServiceTest {

    @Mock
    private AppleIdentityTokenVerifier appleIdentityTokenVerifier;

    @Mock
    private AppleAuthorizationCodeExchangeClient appleAuthorizationCodeExchangeClient;

    @Mock
    private AppleTokenRefreshClient appleTokenRefreshClient;

    @Mock
    private AppleTokenRevocationClient appleTokenRevocationClient;

    @Mock
    private AppleRefreshTokenVaultService appleRefreshTokenVaultService;

    @Mock
    private SysAuthSessionService sysAuthSessionService;

    @Mock
    private SysAuthDataService authDataService;

    @Mock
    private SessionTokenHashService sessionTokenHashService;

    private SysAppleAuthService sysAppleAuthService;

    @BeforeEach
    void setUp() {
        sysAppleAuthService = new SysAppleAuthService(
            appleIdentityTokenVerifier,
            appleAuthorizationCodeExchangeClient,
            appleTokenRefreshClient,
            appleTokenRevocationClient,
            appleRefreshTokenVaultService,
            sysAuthSessionService,
            authDataService,
            sessionTokenHashService,
            new ObjectMapper()
        );
    }

    @Test
    void exchangeShouldBlockFormalSessionWhenCredentialEncryptionMissing() {
        AppDefinition appDefinition = readingAppDefinition();
        AppleIdentityTokenDecoder.DecodedAppleIdentityToken decoded = new AppleIdentityTokenDecoder.DecodedAppleIdentityToken(
            "apple-sub-1",
            "apple@example.com",
            true,
            "https://appleid.apple.com",
            java.util.List.of("com.paipai.readalong"),
            "nonce-1",
            true,
            false,
            OffsetDateTime.parse("2100-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-04-16T00:00:00Z"),
            "ES256",
            "kid-1"
        );

        when(appleIdentityTokenVerifier.verify(eq("client-token"), any()))
            .thenReturn(new AppleIdentityTokenVerifier.VerificationResult("verified", "verified", decoded, Map.of("phase", "client")));
        when(appleAuthorizationCodeExchangeClient.exchange(any(), any()))
            .thenReturn(new AppleAuthorizationCodeExchangeClient.ExchangeResult(
                "exchanged",
                true,
                "ok",
                "remote-token",
                "refresh-123",
                "access-123",
                "Bearer",
                Map.of("httpStatus", "200")
            ));
        when(appleIdentityTokenVerifier.verify(eq("remote-token"), any()))
            .thenReturn(new AppleIdentityTokenVerifier.VerificationResult("verified", "verified", decoded, Map.of("phase", "remote")));
        when(appleRefreshTokenVaultService.isEncryptionReady()).thenReturn(false);

        AppleExchangePreviewView result = sysAppleAuthService.exchange(
            appDefinition,
            new AppleExchangeRequest("client-token", "auth-code", null, null, null, null, null, null, "https://example.com/apple/callback")
        );

        assertThat(result.sessionIssued()).isFalse();
        assertThat(result.overallStatus()).isEqualTo("blocked_missing_credential_encryption");
        assertThat(result.diagnostics()).containsEntry("formalSessionSecurityStatus", "blocked_missing_credential_encryption");
    }

    @Test
    void revokeShouldRevokeProviderTokenAndSession() {
        AppDefinition appDefinition = readingAppDefinition();
        SysAuthSessionEntity session = new SysAuthSessionEntity();
        session.setId(11L);
        session.setAppCode("paipai_readingcompanion");
        session.setUserId(21L);
        SysAuthProviderTokenEntity token = new SysAuthProviderTokenEntity();
        token.setId(31L);
        token.setAppCode("paipai_readingcompanion");
        token.setUserId(21L);
        token.setProviderCode("apple");
        token.setProviderSubject("apple-sub-1");
        token.setRefreshToken("refresh-123");
        token.setAccessToken("access-123");
        token.setTokenType("Bearer");
        token.setStatus("active");
        token.setPayloadJson("{\"status\":\"exchanged\"}");

        when(sessionTokenHashService.hash("session-token")).thenReturn("hash-123");
        when(authDataService.activeSessionByTokenHash(eq("hash-123"), any(OffsetDateTime.class))).thenReturn(session);
        when(authDataService.providerTokenByUserAndProvider("paipai_readingcompanion", 21L, "apple")).thenReturn(token);
        when(appleRefreshTokenVaultService.resolve(eq(token), any(OffsetDateTime.class)))
            .thenReturn(new AppleRefreshTokenVaultService.ResolvedTokenResult(
                "loaded_plaintext_refresh_token",
                "loaded",
                "refresh-123",
                false
            ));
        doAnswer(invocation -> {
            SysAuthProviderTokenEntity entity = invocation.getArgument(0);
            entity.setRefreshToken(null);
            entity.setRefreshTokenKeyId(null);
            entity.setRefreshTokenEncryptionAlgorithm(null);
            entity.setRefreshTokenNonceBase64(null);
            entity.setRefreshTokenCiphertextBase64(null);
            entity.setRefreshTokenLastCapturedAt(null);
            entity.setRefreshTokenLastUsedAt(null);
            return null;
        }).when(appleRefreshTokenVaultService).purge(eq(token));
        when(appleTokenRevocationClient.revoke(any(), any()))
            .thenReturn(new AppleTokenRevocationClient.RevocationResult(
                "succeeded",
                true,
                "Apple revoke endpoint returned HTTP 200.",
                Map.of("httpStatus", "200")
            ));

        Optional<AppleRevokeResultView> result = sysAppleAuthService.revoke(appDefinition, "session-token");

        assertThat(result).isPresent();
        AppleRevokeResultView view = result.orElseThrow();
        assertThat(view.appCode()).isEqualTo("paipai_readingcompanion");
        assertThat(view.remoteRevokeStatus()).isEqualTo("succeeded");
        assertThat(view.sessionStatus()).isEqualTo("revoked");
        assertThat(view.providerTokenStatus()).isEqualTo("revoked");
        assertThat(view.remoteRevokeAttempted()).isTrue();

        ArgumentCaptor<SysAuthProviderTokenEntity> tokenCaptor = ArgumentCaptor.forClass(SysAuthProviderTokenEntity.class);
        verify(authDataService).updateProviderToken(tokenCaptor.capture());
        SysAuthProviderTokenEntity updated = tokenCaptor.getValue();
        assertThat(updated.getRefreshToken()).isNull();
        assertThat(updated.getAccessToken()).isNull();
        assertThat(updated.getTokenType()).isNull();
        assertThat(updated.getStatus()).isEqualTo("revoked");
        assertThat(updated.getPayloadJson()).contains("revocation");

        verify(authDataService).revokeSession(eq(11L), any(OffsetDateTime.class));
    }

    @Test
    void revokeShouldStillLogoutWhenProviderTokenMissing() {
        AppDefinition appDefinition = readingAppDefinition();
        SysAuthSessionEntity session = new SysAuthSessionEntity();
        session.setId(12L);
        session.setAppCode("paipai_readingcompanion");
        session.setUserId(22L);

        when(sessionTokenHashService.hash("session-token")).thenReturn("hash-123");
        when(authDataService.activeSessionByTokenHash(eq("hash-123"), any(OffsetDateTime.class))).thenReturn(session);
        when(authDataService.providerTokenByUserAndProvider("paipai_readingcompanion", 22L, "apple")).thenReturn(null);

        Optional<AppleRevokeResultView> result = sysAppleAuthService.revoke(appDefinition, "session-token");

        assertThat(result).isPresent();
        AppleRevokeResultView view = result.orElseThrow();
        assertThat(view.remoteRevokeStatus()).isEqualTo("token_missing");
        assertThat(view.providerTokenStatus()).isEqualTo("not_found");
        assertThat(view.remoteRevokeAttempted()).isFalse();
        verify(authDataService).revokeSession(eq(12L), any(OffsetDateTime.class));
    }

    @Test
    void refreshShouldIssueNewSessionAndRevokeOldOne() {
        AppDefinition appDefinition = readingAppDefinition();
        SysAuthSessionEntity session = new SysAuthSessionEntity();
        session.setId(15L);
        session.setAppCode("paipai_readingcompanion");
        session.setUserId(25L);
        SysAuthProviderTokenEntity token = new SysAuthProviderTokenEntity();
        token.setId(35L);
        token.setAppCode("paipai_readingcompanion");
        token.setUserId(25L);
        token.setProviderCode("apple");
        token.setProviderSubject("apple-sub-1");
        token.setRefreshToken("refresh-123");

        AppleIdentityTokenDecoder.DecodedAppleIdentityToken decoded = new AppleIdentityTokenDecoder.DecodedAppleIdentityToken(
            "apple-sub-1",
            "apple@example.com",
            true,
            "https://appleid.apple.com",
            java.util.List.of("com.paipai.readalong"),
            null,
            true,
            false,
            OffsetDateTime.parse("2100-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-04-16T00:00:00Z"),
            "ES256",
            "kid-1"
        );

        when(sessionTokenHashService.hash("session-token")).thenReturn("hash-123");
        when(authDataService.activeSessionByTokenHash(eq("hash-123"), any(OffsetDateTime.class))).thenReturn(session);
        when(authDataService.providerTokenByUserAndProvider("paipai_readingcompanion", 25L, "apple")).thenReturn(token);
        when(appleRefreshTokenVaultService.resolve(eq(token), any(OffsetDateTime.class)))
            .thenReturn(new AppleRefreshTokenVaultService.ResolvedTokenResult(
                "loaded_encrypted_refresh_token",
                "loaded",
                "refresh-123",
                true
            ));
        when(appleTokenRefreshClient.refresh(any(), any()))
            .thenReturn(new AppleTokenRefreshClient.RefreshResult(
                "refreshed",
                true,
                "refreshed",
                "header.payload.signature",
                null,
                "access-123",
                "Bearer",
                Map.of("httpStatus", "200")
            ));
        when(appleIdentityTokenVerifier.verify(eq("header.payload.signature"), any()))
            .thenReturn(new AppleIdentityTokenVerifier.VerificationResult(
                "verified",
                "verified",
                decoded,
                Map.of("source", "refresh")
            ));
        when(sysAuthSessionService.issueAppleSession(eq("paipai_readingcompanion"), eq("apple-sub-1"), eq(null), eq(true), eq(false), eq(null), any(), eq(null), eq("access-123"), eq("Bearer"), any()))
            .thenReturn(new AuthSessionIssuedView(
                "paipai_readingcompanion",
                "apple",
                "new-session-token",
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                new CurrentUserView(25L, "paipai_readingcompanion", "member", "Apple User", "active")
            ));

        Optional<AppleSessionRefreshView> result = sysAppleAuthService.refresh(appDefinition, "session-token");

        assertThat(result).isPresent();
        AppleSessionRefreshView view = result.orElseThrow();
        assertThat(view.sessionIssued()).isTrue();
        assertThat(view.refreshStatus()).isEqualTo("refreshed");
        assertThat(view.identityStatus()).isEqualTo("verified");
        assertThat(view.issuedSession()).isNotNull();
        assertThat(view.issuedSession().sessionToken()).isEqualTo("new-session-token");
        verify(authDataService).revokeSession(eq(15L), any(OffsetDateTime.class));
    }

    @Test
    void refreshShouldRejectPlaintextFallbackForFormalSessionRotation() {
        AppDefinition appDefinition = readingAppDefinition();
        SysAuthSessionEntity session = new SysAuthSessionEntity();
        session.setId(16L);
        session.setAppCode("paipai_readingcompanion");
        session.setUserId(26L);
        SysAuthProviderTokenEntity token = new SysAuthProviderTokenEntity();
        token.setId(36L);
        token.setAppCode("paipai_readingcompanion");
        token.setUserId(26L);
        token.setProviderCode("apple");
        token.setProviderSubject("apple-sub-1");
        token.setRefreshToken("refresh-plaintext");

        when(sessionTokenHashService.hash("session-token")).thenReturn("hash-124");
        when(authDataService.activeSessionByTokenHash(eq("hash-124"), any(OffsetDateTime.class))).thenReturn(session);
        when(authDataService.providerTokenByUserAndProvider("paipai_readingcompanion", 26L, "apple")).thenReturn(token);
        when(appleRefreshTokenVaultService.resolve(eq(token), any(OffsetDateTime.class)))
            .thenReturn(new AppleRefreshTokenVaultService.ResolvedTokenResult(
                "loaded_plaintext_refresh_token",
                "loaded",
                "refresh-plaintext",
                false
            ));

        Optional<AppleSessionRefreshView> result = sysAppleAuthService.refresh(appDefinition, "session-token");

        assertThat(result).isPresent();
        AppleSessionRefreshView view = result.orElseThrow();
        assertThat(view.sessionIssued()).isFalse();
        assertThat(view.overallStatus()).isEqualTo("blocked_plaintext_refresh_token_fallback");
        assertThat(view.refreshStatus()).isEqualTo("blocked_plaintext_refresh_token_fallback");
        assertThat(view.issuedSession()).isNull();
    }

    private AppDefinition readingAppDefinition() {
        return new AppDefinition(
            "paipai_readingcompanion",
            "拍拍伴读",
            "/api/v1",
            "reading_",
            new AppDefinition.Support(true, true, true),
            Map.of(
                "app.auth.apple.clientId", "com.paipai.readalong",
                "app.auth.apple.teamId", "TEAM123",
                "app.auth.apple.keyId", "KEY123",
                "app.auth.apple.privateKey", "PRIVATEKEY",
                "app.auth.apple.audience", "https://appleid.apple.com",
                "app.auth.apple.revokeEndpoint", "https://appleid.apple.com/auth/revoke"
            )
        );
    }
}
