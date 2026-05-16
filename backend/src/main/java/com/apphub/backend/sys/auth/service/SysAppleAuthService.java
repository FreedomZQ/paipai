package com.apphub.backend.sys.auth.service;

import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.auth.entity.SysAuthProviderTokenEntity;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.model.AppleExchangePreviewView;
import com.apphub.backend.sys.auth.model.AppleExchangeRequest;
import com.apphub.backend.sys.auth.model.AppleRevokeResultView;
import com.apphub.backend.sys.auth.model.AppleSessionRefreshView;
import com.apphub.backend.sys.auth.model.AuthSessionIssuedView;
import com.apphub.backend.sys.auth.model.DecodedAppleIdentityTokenView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 认证服务 `SysAppleAuthService`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Service
public class SysAppleAuthService {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_PROVIDER = "apple";
    private static final String REVOKED_STATUS = "revoked";

    private final AppleIdentityTokenVerifier appleIdentityTokenVerifier;
    private final AppleAuthorizationCodeExchangeClient appleAuthorizationCodeExchangeClient;
    private final AppleTokenRefreshClient appleTokenRefreshClient;
    private final AppleTokenRevocationClient appleTokenRevocationClient;
    private final AppleRefreshTokenVaultService appleRefreshTokenVaultService;
    private final SysAuthSessionService sysAuthSessionService;
    private final SysAuthDataService authDataService;
    private final SessionTokenHashService sessionTokenHashService;
    private final ObjectMapper objectMapper;

    public SysAppleAuthService(
        AppleIdentityTokenVerifier appleIdentityTokenVerifier,
        AppleAuthorizationCodeExchangeClient appleAuthorizationCodeExchangeClient,
        AppleTokenRefreshClient appleTokenRefreshClient,
        AppleTokenRevocationClient appleTokenRevocationClient,
        AppleRefreshTokenVaultService appleRefreshTokenVaultService,
        SysAuthSessionService sysAuthSessionService,
        SysAuthDataService authDataService,
        SessionTokenHashService sessionTokenHashService,
        ObjectMapper objectMapper
    ) {
        this.appleIdentityTokenVerifier = appleIdentityTokenVerifier;
        this.appleAuthorizationCodeExchangeClient = appleAuthorizationCodeExchangeClient;
        this.appleTokenRefreshClient = appleTokenRefreshClient;
        this.appleTokenRevocationClient = appleTokenRevocationClient;
        this.appleRefreshTokenVaultService = appleRefreshTokenVaultService;
        this.sysAuthSessionService = sysAuthSessionService;
        this.authDataService = authDataService;
        this.sessionTokenHashService = sessionTokenHashService;
        this.objectMapper = objectMapper;
    }

    public AppleExchangePreviewView exchange(AppDefinition appDefinition, AppleExchangeRequest request) {
        if (appDefinition == null) {
            throw new ResponseStatusException(BAD_REQUEST, "App definition is required.");
        }
        if (!appDefinition.support().appleSignInRequired()) {
            throw new ResponseStatusException(BAD_REQUEST, "This app does not enable Sign in with Apple.");
        }
        validateExchangeBoundary(appDefinition, request);

        AppleAuthorizationCodeExchangeClient.AppleAuthConfiguration authConfiguration = authConfiguration(appDefinition);
        String clientId = authConfiguration.clientId();
        String jwksUrl = firstNonBlank(authConfiguration.jwksUrl(), "https://appleid.apple.com/auth/keys");
        String expectedIssuer = firstNonBlank(rawValue(appDefinition, "app.auth.apple.issuer"), APPLE_ISSUER);
        String expectedNonce = firstNonBlank(request.expectedNonce(), request.nonce());
        boolean verificationConfigured = authConfiguration.isReadyForIdentityVerification();

        AppleIdentityTokenVerifier.VerificationResult verificationResult = appleIdentityTokenVerifier.verify(
            request.identityToken(),
            new AppleIdentityTokenVerifier.VerificationCommand(clientId, expectedNonce, expectedIssuer, verificationConfigured)
        );
        AppleIdentityTokenDecoder.DecodedAppleIdentityToken decoded = verificationResult.decoded();
        String identityStatus = verificationResult.status();

        Map<String, String> diagnostics = new LinkedHashMap<>(verificationResult.diagnostics());
        diagnostics.put("appCode", appDefinition.code());
        diagnostics.put("apiPrefix", appDefinition.apiPrefix());
        diagnostics.put("exchangeRedirectUri", normalize(request.redirectUri()));
        diagnostics.put("state", normalize(request.state()));
        diagnostics.put("expectedState", normalize(request.expectedState()));
        diagnostics.put("nonce", normalize(request.nonce()));
        diagnostics.put("expectedNonce", normalize(request.expectedNonce()));
        diagnostics.put("configuredClientId", clientId);
        diagnostics.put("configuredJwksUrl", jwksUrl);
        diagnostics.put("appleSignInRequired", String.valueOf(appDefinition.support().appleSignInRequired()));

        AppleAuthorizationCodeExchangeClient.ExchangeResult exchangeResult = verificationResult.allowsSessionIssue()
            ? appleAuthorizationCodeExchangeClient.exchange(
                new AppleAuthorizationCodeExchangeClient.ExchangeCommand(request.authorizationCode(), request.redirectUri()),
                authConfiguration
            )
            : new AppleAuthorizationCodeExchangeClient.ExchangeResult(
                "skipped_identity_not_verified",
                false,
                "Apple identity token must verify before remote /auth/token exchange is attempted.",
                null,
                null,
                null,
                null,
                Map.of()
            );
        diagnostics.putAll(prefix("exchange.", exchangeResult.diagnostics()));

        AppleIdentityTokenVerifier.VerificationResult exchangedIdentityVerification = verificationResult.allowsSessionIssue() && exchangeResult.isSuccessful() && hasText(exchangeResult.identityToken())
            ? appleIdentityTokenVerifier.verify(
                exchangeResult.identityToken(),
                new AppleIdentityTokenVerifier.VerificationCommand(clientId, expectedNonce, expectedIssuer, verificationConfigured)
            )
            : null;
        if (exchangedIdentityVerification != null) {
            diagnostics.putAll(prefix("exchangeIdentity.", exchangedIdentityVerification.diagnostics()));
        }

        AppleIdentityTokenDecoder.DecodedAppleIdentityToken authoritativeDecoded = exchangedIdentityVerification != null && exchangedIdentityVerification.decoded() != null
            ? exchangedIdentityVerification.decoded()
            : decoded;
        boolean authoritativeIdentityVerified = exchangedIdentityVerification == null
            ? verificationResult.allowsSessionIssue()
            : exchangedIdentityVerification.allowsSessionIssue();

        boolean nativeIdentityTokenSessionAllowed = nativeIdentityTokenSessionEnabled(appDefinition)
            && authoritativeIdentityVerified
            && !exchangeResult.isSuccessful()
            && ("remote_exchange_disabled".equalsIgnoreCase(exchangeResult.status()) || "not_configured".equalsIgnoreCase(exchangeResult.status()));
        diagnostics.put("nativeIdentityTokenSessionAllowed", String.valueOf(nativeIdentityTokenSessionAllowed));

        FormalSessionSecurity formalSessionSecurity = authoritativeIdentityVerified && exchangeResult.isSuccessful()
            ? formalSessionSecurity(appDefinition.code(), authoritativeDecoded == null ? null : authoritativeDecoded.subject(), exchangeResult.refreshToken())
            : (nativeIdentityTokenSessionAllowed
                ? new FormalSessionSecurity(true, "native_identity_token_session", "Native iOS Sign in with Apple session issued after JWKS identity-token verification; no Apple refresh token is stored.")
                : new FormalSessionSecurity(false, "not_attempted", null));
        diagnostics.put("formalSessionSecurityStatus", formalSessionSecurity.status());
        if (hasText(formalSessionSecurity.note())) {
            diagnostics.put("formalSessionSecurityNote", formalSessionSecurity.note());
        }

        boolean canIssueSession = authoritativeIdentityVerified && (exchangeResult.isSuccessful() || nativeIdentityTokenSessionAllowed) && formalSessionSecurity.allowed();
        AuthSessionIssuedView issuedSession = canIssueSession
            ? sysAuthSessionService.issueAppleSession(
                appDefinition.code(),
                authoritativeDecoded.subject(),
                null,
                authoritativeDecoded.emailVerified(),
                authoritativeDecoded.privateEmail(),
                resolveDisplayName(request, authoritativeDecoded),
                buildIdentityPayload(request, verificationResult, exchangedIdentityVerification, exchangeResult),
                exchangeResult.refreshToken(),
                exchangeResult.accessToken(),
                exchangeResult.tokenType(),
                buildTokenPayload(exchangeResult)
            )
            : null;

        diagnostics.put("sessionIssued", String.valueOf(issuedSession != null));
        String effectiveIdentityStatus = exchangedIdentityVerification != null ? exchangedIdentityVerification.status() : identityStatus;
        String overallStatus = resolveOverallStatus(effectiveIdentityStatus, exchangeResult.status(), issuedSession != null, formalSessionSecurity);
        String note = resolveExchangeNote(
            effectiveIdentityStatus,
            authoritativeIdentityVerified ? verificationResult.note() : (exchangedIdentityVerification != null ? exchangedIdentityVerification.note() : verificationResult.note()),
            exchangeResult,
            issuedSession != null,
            formalSessionSecurity
        );

        return new AppleExchangePreviewView(
            issuedSession != null,
            overallStatus,
            exchangeResult.status(),
            effectiveIdentityStatus,
            note,
            new DecodedAppleIdentityTokenView(
                authoritativeDecoded.subject(),
                null,
                authoritativeDecoded.emailVerified(),
                authoritativeDecoded.issuer(),
                authoritativeDecoded.audience(),
                authoritativeDecoded.nonce(),
                authoritativeDecoded.nonceSupported(),
                authoritativeDecoded.privateEmail(),
                authoritativeDecoded.expiresAt(),
                authoritativeDecoded.issuedAt(),
                authoritativeDecoded.algorithm(),
                authoritativeDecoded.keyId()
            ),
            diagnostics,
            issuedSession
        );
    }


@Transactional
public Optional<AppleSessionRefreshView> refresh(AppDefinition appDefinition, String rawSessionToken) {
    if (appDefinition == null) {
        throw new ResponseStatusException(BAD_REQUEST, "App definition is required.");
    }
    if (!appDefinition.support().appleSignInRequired()) {
        throw new ResponseStatusException(BAD_REQUEST, "This app does not enable Sign in with Apple.");
    }
    if (!hasText(rawSessionToken)) {
        return Optional.empty();
    }

    OffsetDateTime now = now();
    SysAuthSessionEntity currentSession = authDataService.activeSessionByTokenHash(sessionTokenHashService.hash(rawSessionToken), now);
    if (currentSession == null || !appDefinition.code().equals(currentSession.getAppCode())) {
        return Optional.empty();
    }

    Map<String, String> diagnostics = new LinkedHashMap<>();
    diagnostics.put("appCode", appDefinition.code());
    diagnostics.put("currentSessionId", String.valueOf(currentSession.getId()));
    diagnostics.put("appleSignInRequired", String.valueOf(appDefinition.support().appleSignInRequired()));

    SysAuthProviderTokenEntity providerToken = authDataService.providerTokenByUserAndProvider(currentSession.getAppCode(), currentSession.getUserId(), APPLE_PROVIDER);
    if (providerToken == null) {
        diagnostics.put("providerTokenStatus", "not_found");
        return Optional.of(new AppleSessionRefreshView(
            false,
            "token_missing",
            "token_missing",
            "not_attempted",
            "No Apple provider token is stored for this user, so refresh cannot be attempted. Current session remains active.",
            null,
            diagnostics,
            null
        ));
    }

    AppleAuthorizationCodeExchangeClient.AppleAuthConfiguration authConfiguration = authConfiguration(appDefinition);
    String expectedIssuer = firstNonBlank(rawValue(appDefinition, "app.auth.apple.issuer"), APPLE_ISSUER);
    boolean verificationConfigured = authConfiguration.isReadyForIdentityVerification();

    AppleRefreshTokenVaultService.ResolvedTokenResult resolvedRefreshToken = appleRefreshTokenVaultService.resolve(providerToken, now);
    diagnostics.put("refreshTokenResolutionStatus", resolvedRefreshToken.status());
    if (hasText(resolvedRefreshToken.note())) {
        diagnostics.put("refreshTokenResolutionNote", resolvedRefreshToken.note());
    }
    if (resolvedRefreshToken.encrypted()) {
        providerToken.setUpdatedAt(now);
        authDataService.updateProviderToken(providerToken);
    }

    if (resolvedRefreshToken.refreshToken() == null) {
        return Optional.of(new AppleSessionRefreshView(
            false,
            resolvedRefreshToken.status(),
            resolvedRefreshToken.status(),
            "not_attempted",
            resolvedRefreshToken.note() + " Current session remains active.",
            null,
            diagnostics,
            null
        ));
    }
    if (!resolvedRefreshToken.encrypted()) {
        diagnostics.put("formalSessionSecurityStatus", "blocked_plaintext_refresh_token_fallback");
        return Optional.of(new AppleSessionRefreshView(
            false,
            "blocked_plaintext_refresh_token_fallback",
            "blocked_plaintext_refresh_token_fallback",
            "not_attempted",
            "Stored Apple refresh token is only available in plaintext fallback mode. Configure APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY and rotate credentials before formal session refresh is allowed. Current session remains active.",
            null,
            diagnostics,
            null
        ));
    }

    AppleTokenRefreshClient.RefreshResult refreshResult = appleTokenRefreshClient.refresh(
        new AppleTokenRefreshClient.RefreshCommand(resolvedRefreshToken.refreshToken()),
        authConfiguration
    );
    diagnostics.putAll(prefix("refresh.", refreshResult.diagnostics()));

    AppleIdentityTokenVerifier.VerificationResult refreshIdentityVerification = refreshResult.isSuccessful() && hasText(refreshResult.identityToken())
        ? appleIdentityTokenVerifier.verify(
            refreshResult.identityToken(),
            new AppleIdentityTokenVerifier.VerificationCommand(authConfiguration.clientId(), null, expectedIssuer, verificationConfigured)
        )
        : null;
    if (refreshIdentityVerification != null) {
        diagnostics.putAll(prefix("refreshIdentity.", refreshIdentityVerification.diagnostics()));
    }

    AppleIdentityTokenDecoder.DecodedAppleIdentityToken authoritativeDecoded = refreshIdentityVerification == null ? null : refreshIdentityVerification.decoded();
    boolean subjectMatchesStoredProvider = authoritativeDecoded == null
        || !hasText(providerToken.getProviderSubject())
        || providerToken.getProviderSubject().equals(authoritativeDecoded.subject());
    diagnostics.put("subjectMatchesStoredProvider", String.valueOf(subjectMatchesStoredProvider));

    boolean canIssueSession = refreshIdentityVerification != null
        && refreshIdentityVerification.allowsSessionIssue()
        && authoritativeDecoded != null
        && subjectMatchesStoredProvider;

    AuthSessionIssuedView issuedSession = canIssueSession
        ? sysAuthSessionService.issueAppleSession(
            appDefinition.code(),
            authoritativeDecoded.subject(),
            null,
            authoritativeDecoded.emailVerified(),
            authoritativeDecoded.privateEmail(),
            null,
            buildRefreshIdentityPayload(currentSession, refreshResult, refreshIdentityVerification),
            refreshResult.refreshToken(),
            refreshResult.accessToken(),
            refreshResult.tokenType(),
            buildRefreshTokenPayload(refreshResult)
        )
        : null;

    if (issuedSession != null) {
        authDataService.revokeSession(currentSession.getId(), now);
    }

    diagnostics.put("sessionIssued", String.valueOf(issuedSession != null));
    diagnostics.put("previousSessionRevoked", String.valueOf(issuedSession != null));
    String identityStatus = refreshIdentityVerification == null
        ? "not_attempted"
        : (subjectMatchesStoredProvider ? refreshIdentityVerification.status() : "rejected_subject_mismatch");
    String overallStatus = issuedSession != null
        ? "session_issued"
        : (refreshResult.isSuccessful() ? identityStatus : refreshResult.status());
    String note = resolveRefreshNote(refreshResult, refreshIdentityVerification, issuedSession != null, subjectMatchesStoredProvider);

    return Optional.of(new AppleSessionRefreshView(
        issuedSession != null,
        overallStatus,
        refreshResult.status(),
        identityStatus,
        note,
        toDecodedTokenView(authoritativeDecoded),
        diagnostics,
        issuedSession
    ));
}

    @Transactional
    public Optional<AppleRevokeResultView> revoke(AppDefinition appDefinition, String rawSessionToken) {
        if (appDefinition == null) {
            throw new ResponseStatusException(BAD_REQUEST, "App definition is required.");
        }
        if (!appDefinition.support().appleSignInRequired()) {
            throw new ResponseStatusException(BAD_REQUEST, "This app does not enable Sign in with Apple.");
        }
        if (!hasText(rawSessionToken)) {
            return Optional.empty();
        }

        OffsetDateTime now = now();
        SysAuthSessionEntity session = authDataService.activeSessionByTokenHash(sessionTokenHashService.hash(rawSessionToken), now);
        if (session == null || !appDefinition.code().equals(session.getAppCode())) {
            return Optional.empty();
        }

        SysAuthProviderTokenEntity token = authDataService.providerTokenByUserAndProvider(session.getAppCode(), session.getUserId(), APPLE_PROVIDER);
        AppleTokenRevocationClient.RevocationResult revokeResult;
        String providerTokenStatus;
        if (token == null) {
            revokeResult = new AppleTokenRevocationClient.RevocationResult(
                "token_missing",
                false,
                "No Apple provider token is stored for this user, so upstream Apple revoke cannot be attempted.",
                Map.of("appCode", session.getAppCode(), "userId", String.valueOf(session.getUserId()))
            );
            providerTokenStatus = "not_found";
        } else {
            AppleRefreshTokenVaultService.ResolvedTokenResult resolvedRefreshToken = appleRefreshTokenVaultService.resolve(token, now);
            if (resolvedRefreshToken.encrypted()) {
                token.setUpdatedAt(now);
            }
            revokeResult = resolvedRefreshToken.refreshToken() == null
                ? new AppleTokenRevocationClient.RevocationResult(
                    resolvedRefreshToken.status(),
                    false,
                    resolvedRefreshToken.note(),
                    Map.of()
                )
                : appleTokenRevocationClient.revoke(
                    new AppleTokenRevocationClient.RevokeCommand(resolvedRefreshToken.refreshToken()),
                    revokeConfiguration(appDefinition)
                );
            providerTokenStatus = REVOKED_STATUS;
            appleRefreshTokenVaultService.purge(token);
            token.setAccessToken(null);
            token.setTokenType(null);
            token.setStatus(REVOKED_STATUS);
            token.setPayloadJson(buildRevocationPayload(token.getPayloadJson(), revokeResult, now));
            token.setUpdatedAt(now);
            authDataService.updateProviderToken(token);
        }

        authDataService.revokeSession(session.getId(), now);

        return Optional.of(new AppleRevokeResultView(
            session.getAppCode(),
            REVOKED_STATUS,
            providerTokenStatus,
            revokeResult.status(),
            revokeResult.remoteRevokeAttempted(),
            now,
            appendLocalRevokeNote(revokeResult.note(), token != null)
        ));
    }

    private void validateExchangeBoundary(AppDefinition appDefinition, AppleExchangeRequest request) {
        boolean nativeIdentityTokenSessionAllowed = nativeIdentityTokenSessionEnabled(appDefinition)
            && !Boolean.TRUE.equals(parseBoolean(rawValue(appDefinition, "app.auth.apple.remoteExchangeEnabled")));
        if (!hasText(request.authorizationCode()) && !nativeIdentityTokenSessionAllowed) {
            throw new ResponseStatusException(BAD_REQUEST, "authorizationCode is required for Apple exchange unless native identity-token session is explicitly enabled.");
        }
        validateExpectationPair("state", request.state(), request.expectedState());
        validateExpectationPair("nonce", request.nonce(), request.expectedNonce());
    }

    private void validateExpectationPair(String label, String actual, String expected) {
        boolean actualPresent = hasText(actual);
        boolean expectedPresent = hasText(expected);
        if (actualPresent != expectedPresent) {
            throw new ResponseStatusException(BAD_REQUEST, label + " and expected value must be provided together.");
        }
        if (actualPresent && !normalize(actual).equals(normalize(expected))) {
            throw new ResponseStatusException(BAD_REQUEST, label + " does not match the original request.");
        }
    }

    private String resolveOverallStatus(String identityStatus, String exchangeStatus, boolean sessionIssued, FormalSessionSecurity formalSessionSecurity) {
        if (sessionIssued) {
            return "session_issued";
        }
        if (formalSessionSecurity != null
            && !formalSessionSecurity.allowed()
            && "verified".equalsIgnoreCase(identityStatus)
            && "exchanged".equalsIgnoreCase(exchangeStatus)) {
            return formalSessionSecurity.status();
        }
        if (!"verified".equalsIgnoreCase(identityStatus)) {
            return identityStatus;
        }
        if ("exchanged".equalsIgnoreCase(exchangeStatus)) {
            return "pending_session_issue";
        }
        return exchangeStatus;
    }

    private String resolveExchangeNote(
        String identityStatus,
        String identityNote,
        AppleAuthorizationCodeExchangeClient.ExchangeResult exchangeResult,
        boolean sessionIssued,
        FormalSessionSecurity formalSessionSecurity
    ) {
        if (sessionIssued) {
            return exchangeResult.isSuccessful()
                ? "Apple identity token 已通过 JWKS 验签，authorization code 也已完成 `/auth/token` 交换，并已在统一 backend 中创建/复用用户并签发正式 session。"
                : "Apple identity token 已通过 JWKS 验签，并已在统一 backend 中创建/复用用户并签发正式 session；当前未保存 Apple refresh token。";
        }
        if (!"verified".equalsIgnoreCase(identityStatus)) {
            return identityNote;
        }
        if (exchangeResult.isSuccessful() && formalSessionSecurity != null && !formalSessionSecurity.allowed()) {
            return formalSessionSecurity.note();
        }
        if (exchangeResult.isSuccessful()) {
            return "Apple identity token 已通过 JWKS 验签，authorization code 也已完成 `/auth/token` 交换；但正式会话尚未签发。";
        }
        return exchangeResult.note();
    }


private String resolveRefreshNote(
    AppleTokenRefreshClient.RefreshResult refreshResult,
    AppleIdentityTokenVerifier.VerificationResult refreshIdentityVerification,
    boolean sessionIssued,
    boolean subjectMatchesStoredProvider
) {
    if (sessionIssued) {
        return "Apple refresh_token 已完成 `/auth/token` refresh，远端返回的 id_token 也已再次验签，并已轮换签发新的正式 session。";
    }
    if (!refreshResult.isSuccessful()) {
        return refreshResult.note() + " Current session remains active.";
    }
    if (!subjectMatchesStoredProvider) {
        return "Apple refresh returned an id_token subject that does not match the stored provider subject. Current session remains active.";
    }
    if (refreshIdentityVerification == null) {
        return "Apple refresh completed, but no verifiable id_token was available to rotate the session. Current session remains active.";
    }
    if (!refreshIdentityVerification.allowsSessionIssue()) {
        return refreshIdentityVerification.note() + " Current session remains active.";
    }
    return "Apple refresh completed, but no new session was issued. Current session remains active.";
}

    private String resolveDisplayName(AppleExchangeRequest request, AppleIdentityTokenDecoder.DecodedAppleIdentityToken decoded) {
        String name = joinName(request.givenName(), request.familyName());
        if (name != null) {
            return name;
        }
        return "AppleUser-" + decoded.subject();
    }

    private String joinName(String givenName, String familyName) {
        String given = normalize(givenName);
        String family = normalize(familyName);
        if (given == null && family == null) {
            return null;
        }
        if (given == null) {
            return family;
        }
        if (family == null) {
            return given;
        }
        return (given + " " + family).trim();
    }

    private Map<String, Object> buildIdentityPayload(
        AppleExchangeRequest request,
        AppleIdentityTokenVerifier.VerificationResult verificationResult,
        AppleIdentityTokenVerifier.VerificationResult exchangedIdentityVerification,
        AppleAuthorizationCodeExchangeClient.ExchangeResult exchangeResult
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request", mapOfNonNull(
            "state", request.state(),
            "expectedState", request.expectedState(),
            "nonce", request.nonce(),
            "expectedNonce", request.expectedNonce(),
            "redirectUri", request.redirectUri(),
            "givenName", request.givenName(),
            "familyName", request.familyName()
        ));
        payload.put("identityVerification", mapOfNonNull(
            "status", verificationResult.status(),
            "note", verificationResult.note(),
            "diagnostics", verificationResult.diagnostics(),
            "decoded", toDecodedTokenView(verificationResult.decoded())
        ));
        if (exchangedIdentityVerification != null) {
            payload.put("remoteIdentityVerification", mapOfNonNull(
                "status", exchangedIdentityVerification.status(),
                "note", exchangedIdentityVerification.note(),
                "diagnostics", exchangedIdentityVerification.diagnostics(),
                "decoded", toDecodedTokenView(exchangedIdentityVerification.decoded())
            ));
        }
        payload.put("authorizationCodeExchange", mapOfNonNull(
            "status", exchangeResult.status(),
            "note", exchangeResult.note(),
            "remoteExchangeAttempted", exchangeResult.remoteExchangeAttempted(),
            "diagnostics", exchangeResult.diagnostics(),
            "hasRefreshToken", exchangeResult.refreshToken() != null,
            "hasAccessToken", exchangeResult.accessToken() != null,
            "tokenType", exchangeResult.tokenType()
        ));
        return payload;
    }

    private Map<String, Object> buildTokenPayload(AppleAuthorizationCodeExchangeClient.ExchangeResult exchangeResult) {
        return mapOfNonNull(
            "status", exchangeResult.status(),
            "note", exchangeResult.note(),
            "remoteExchangeAttempted", exchangeResult.remoteExchangeAttempted(),
            "diagnostics", exchangeResult.diagnostics(),
            "hasIdentityToken", exchangeResult.identityToken() != null,
            "hasRefreshToken", exchangeResult.refreshToken() != null,
            "hasAccessToken", exchangeResult.accessToken() != null,
            "tokenType", exchangeResult.tokenType()
        );
    }


private Map<String, Object> buildRefreshIdentityPayload(
    SysAuthSessionEntity currentSession,
    AppleTokenRefreshClient.RefreshResult refreshResult,
    AppleIdentityTokenVerifier.VerificationResult refreshIdentityVerification
) {
    return mapOfNonNull(
        "refreshGrant", mapOfNonNull(
            "status", refreshResult.status(),
            "note", refreshResult.note(),
            "remoteRefreshAttempted", refreshResult.remoteRefreshAttempted(),
            "diagnostics", refreshResult.diagnostics(),
            "hasRefreshToken", refreshResult.refreshToken() != null,
            "hasAccessToken", refreshResult.accessToken() != null,
            "tokenType", refreshResult.tokenType(),
            "rotatedFromSessionId", currentSession.getId()
        ),
        "remoteIdentityVerification", refreshIdentityVerification == null ? null : mapOfNonNull(
            "status", refreshIdentityVerification.status(),
            "note", refreshIdentityVerification.note(),
            "diagnostics", refreshIdentityVerification.diagnostics(),
            "decoded", toDecodedTokenView(refreshIdentityVerification.decoded())
        )
    );
}

private Map<String, Object> buildRefreshTokenPayload(AppleTokenRefreshClient.RefreshResult refreshResult) {
    return mapOfNonNull(
        "status", refreshResult.status(),
        "note", refreshResult.note(),
        "remoteRefreshAttempted", refreshResult.remoteRefreshAttempted(),
        "diagnostics", refreshResult.diagnostics(),
        "hasIdentityToken", refreshResult.identityToken() != null,
        "hasRefreshToken", refreshResult.refreshToken() != null,
        "hasAccessToken", refreshResult.accessToken() != null,
        "tokenType", refreshResult.tokenType()
    );
}

    private String buildRevocationPayload(String existingPayloadJson, AppleTokenRevocationClient.RevocationResult revokeResult, OffsetDateTime now) {
        Map<String, Object> payload = readJsonMap(existingPayloadJson);
        payload.put("revocation", mapOfNonNull(
            "status", revokeResult.status(),
            "note", revokeResult.note(),
            "remoteRevokeAttempted", revokeResult.remoteRevokeAttempted(),
            "diagnostics", revokeResult.diagnostics(),
            "revokedAt", now.toString()
        ));
        return toJson(payload);
    }


private DecodedAppleIdentityTokenView toDecodedTokenView(AppleIdentityTokenDecoder.DecodedAppleIdentityToken decoded) {
    if (decoded == null) {
        return null;
    }
    return new DecodedAppleIdentityTokenView(
        decoded.subject(),
        null,
        decoded.emailVerified(),
        decoded.issuer(),
        decoded.audience(),
        decoded.nonce(),
        decoded.nonceSupported(),
        decoded.privateEmail(),
        decoded.expiresAt(),
        decoded.issuedAt(),
        decoded.algorithm(),
        decoded.keyId()
    );
}

    private Map<String, Object> readJsonMap(String payloadJson) {
        if (!hasText(payloadJson)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception exception) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("previousRawPayload", payloadJson);
            return fallback;
        }
    }

    private String appendLocalRevokeNote(String remoteNote, boolean providerTokenFound) {
        String base = hasText(remoteNote) ? remoteNote.trim() : "Apple revoke flow completed.";
        if (providerTokenFound) {
            return base + " Local provider token has been revoked and the current session has been logged out.";
        }
        return base + " The current session has still been logged out locally.";
    }

    private FormalSessionSecurity formalSessionSecurity(String appCode, String providerSubject, String refreshToken) {
        if (hasText(refreshToken)) {
            if (appleRefreshTokenVaultService.isEncryptionReady()) {
                return new FormalSessionSecurity(true, "secure_refresh_token_ready", null);
            }
            return new FormalSessionSecurity(
                false,
                "blocked_missing_credential_encryption",
                "Apple remote exchange returned a refresh_token, but APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY is not configured. Formal session issuance is blocked until encrypted refresh-token storage is ready."
            );
        }
        if (!hasText(providerSubject)) {
            return new FormalSessionSecurity(
                false,
                "blocked_missing_secure_refresh_token",
                "Apple exchange did not provide a reusable refresh_token, and no encrypted Apple refresh token can be matched to this user. Formal session issuance is blocked."
            );
        }
        SysAuthProviderTokenEntity existingToken = authDataService.providerTokenByIdentity(appCode, APPLE_PROVIDER, providerSubject.trim());
        if (existingToken != null && appleRefreshTokenVaultService.hasEncryptedRefreshToken(existingToken)) {
            return new FormalSessionSecurity(true, "encrypted_refresh_token_on_file", null);
        }
        if (existingToken != null && appleRefreshTokenVaultService.hasPlaintextRefreshToken(existingToken)) {
            return new FormalSessionSecurity(
                false,
                "blocked_plaintext_refresh_token_fallback",
                "An existing Apple refresh token is only available in plaintext fallback mode. Configure APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY and rotate credentials before formal session issuance is allowed."
            );
        }
        return new FormalSessionSecurity(
            false,
            "blocked_missing_secure_refresh_token",
            "Apple exchange did not provide a reusable refresh_token, and no encrypted Apple refresh token is on file. Formal session issuance is blocked."
        );
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private Map<String, Object> mapOfNonNull(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String key = String.valueOf(pairs[i]);
            Object value = pairs[i + 1];
            if (value != null) {
                values.put(key, value);
            }
        }
        return values;
    }

    private AppleAuthorizationCodeExchangeClient.AppleAuthConfiguration authConfiguration(AppDefinition appDefinition) {
        return new AppleAuthorizationCodeExchangeClient.AppleAuthConfiguration(
            rawValue(appDefinition, "app.auth.apple.clientId"),
            rawValue(appDefinition, "app.auth.apple.teamId"),
            rawValue(appDefinition, "app.auth.apple.keyId"),
            rawValue(appDefinition, "app.auth.apple.privateKey"),
            firstNonBlank(rawValue(appDefinition, "app.auth.apple.audience"), "https://appleid.apple.com"),
            rawValue(appDefinition, "app.auth.apple.redirectUri"),
            firstNonBlank(rawValue(appDefinition, "app.auth.apple.environment"), "production"),
            firstNonBlank(rawValue(appDefinition, "app.auth.apple.tokenEndpoint"), "https://appleid.apple.com/auth/token"),
            firstNonBlank(rawValue(appDefinition, "app.auth.apple.jwksUrl"), "https://appleid.apple.com/auth/keys"),
            parseBoolean(rawValue(appDefinition, "app.auth.apple.remoteExchangeEnabled"))
        );
    }

    private AppleTokenRevocationClient.AppleRevokeConfiguration revokeConfiguration(AppDefinition appDefinition) {
        return new AppleTokenRevocationClient.AppleRevokeConfiguration(
            rawValue(appDefinition, "app.auth.apple.clientId"),
            rawValue(appDefinition, "app.auth.apple.teamId"),
            rawValue(appDefinition, "app.auth.apple.keyId"),
            rawValue(appDefinition, "app.auth.apple.privateKey"),
            firstNonBlank(rawValue(appDefinition, "app.auth.apple.audience"), "https://appleid.apple.com"),
            firstNonBlank(rawValue(appDefinition, "app.auth.apple.revokeEndpoint"), "https://appleid.apple.com/auth/revoke")
        );
    }

    private boolean nativeIdentityTokenSessionEnabled(AppDefinition appDefinition) {
        return Boolean.parseBoolean(firstNonBlank(rawValue(appDefinition, "app.auth.apple.nativeIdentityTokenSessionEnabled"), "false"));
    }

    private Boolean parseBoolean(String value) {
        return value == null ? null : Boolean.parseBoolean(value);
    }

    private Map<String, String> prefix(String prefix, Map<String, String> source) {
        Map<String, String> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            result.put(prefix + entry.getKey(), entry.getValue());
        }
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String rawValue(AppDefinition appDefinition, String key) {
        Object value = appDefinition.raw().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private record FormalSessionSecurity(boolean allowed, String status, String note) {
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
