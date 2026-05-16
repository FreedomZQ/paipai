package com.apphub.backend.sys.auth.service;

import com.apphub.backend.sys.auth.entity.SysAuthProviderTokenEntity;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import com.apphub.backend.sys.auth.entity.SysUserIdentityEntity;
import com.apphub.backend.sys.auth.model.AuthSessionIssuedView;
import com.apphub.backend.sys.auth.model.AuthenticatedSessionView;
import com.apphub.backend.sys.auth.model.CurrentUserView;
import com.apphub.backend.sys.auth.model.DemoSessionCreateRequest;
import com.apphub.backend.sys.auth.model.DemoSessionCreatedView;
import com.apphub.backend.sys.auth.model.LogoutResultView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 认证服务 `SysAuthSessionService`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Service
public class SysAuthSessionService {

    private static final String ACTIVE_STATUS = "active";
    private static final String REVOKED_STATUS = "revoked";
    private static final String GUEST_USER_TYPE = "guest";
    private static final String MEMBER_USER_TYPE = "member";
    private static final String DEMO_SESSION_SOURCE = "demo";
    private static final String APPLE_SESSION_SOURCE = "apple";
    private static final String APPLE_PROVIDER = "apple";

    private final SysAuthDataService authDataService;
    private final SessionTokenHashService sessionTokenHashService;
    private final AppleRefreshTokenVaultService appleRefreshTokenVaultService;
    private final ObjectMapper objectMapper;

    public SysAuthSessionService(
        SysAuthDataService authDataService,
        SessionTokenHashService sessionTokenHashService,
        AppleRefreshTokenVaultService appleRefreshTokenVaultService,
        ObjectMapper objectMapper
    ) {
        this.authDataService = authDataService;
        this.sessionTokenHashService = sessionTokenHashService;
        this.appleRefreshTokenVaultService = appleRefreshTokenVaultService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DemoSessionCreatedView createDemoSession(String appCode, DemoSessionCreateRequest request) {
        DemoSessionCreateRequest safeRequest = request == null ? DemoSessionCreateRequest.empty() : request;
        OffsetDateTime now = now();

        SysUserEntity user = new SysUserEntity();
        user.setAppCode(appCode);
        user.setUserType(GUEST_USER_TYPE);
        user.setDisplayName(resolveDisplayName(safeRequest.displayName()));
        user.setStatus(ACTIVE_STATUS);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        authDataService.saveUser(user);

        AuthSessionIssuedView session = issueSession(
            user,
            appCode,
            DEMO_SESSION_SOURCE,
            normalize(safeRequest.deviceId()),
            normalize(safeRequest.clientPlatform()),
            normalize(safeRequest.clientVersion()),
            now
        );

        return new DemoSessionCreatedView(
            session.appCode(),
            session.sessionSource(),
            session.sessionToken(),
            session.expiresAt(),
            session.user()
        );
    }

    @Transactional
    public AuthSessionIssuedView issueAppleSession(
        String appCode,
        String appleSubject,
        String email,
        Boolean emailVerified,
        Boolean privateEmail,
        String displayName,
        Map<String, Object> payload,
        String refreshToken,
        String accessToken,
        String tokenType,
        Map<String, Object> tokenPayload
    ) {
        if (appleSubject == null || appleSubject.isBlank()) {
            throw new IllegalArgumentException("appleSubject must not be blank");
        }
        OffsetDateTime now = now();
        SysUserIdentityEntity identity = authDataService.identityByProvider(appCode, APPLE_PROVIDER, appleSubject.trim());
        SysUserEntity user;
        if (identity == null) {
            user = new SysUserEntity();
            user.setAppCode(appCode);
            user.setUserType(MEMBER_USER_TYPE);
            user.setDisplayName(resolveMemberDisplayName(displayName, email));
            user.setStatus(ACTIVE_STATUS);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            authDataService.saveUser(user);

            identity = new SysUserIdentityEntity();
            identity.setAppCode(appCode);
            identity.setUserId(user.getId());
            identity.setProviderCode(APPLE_PROVIDER);
            identity.setProviderSubject(appleSubject.trim());
            identity.setCreatedAt(now);
        } else {
            user = authDataService.userById(identity.getUserId());
            if (user == null) {
                user = new SysUserEntity();
                user.setAppCode(appCode);
                user.setUserType(MEMBER_USER_TYPE);
                user.setDisplayName(resolveMemberDisplayName(displayName, email));
                user.setStatus(ACTIVE_STATUS);
                user.setCreatedAt(now);
                user.setUpdatedAt(now);
                authDataService.saveUser(user);
                identity.setUserId(user.getId());
            }
        }

        if (user.getDisplayName() == null || user.getDisplayName().isBlank()) {
            user.setDisplayName(resolveMemberDisplayName(displayName, email));
            user.setUpdatedAt(now);
            authDataService.updateUser(user);
        }

        identity.setEmail(null);
        identity.setEmailVerified(emailVerified);
        identity.setPrivateEmail(privateEmail);
        identity.setStatus(ACTIVE_STATUS);
        identity.setPayloadJson(toJson(payload));
        identity.setUpdatedAt(now);
        if (identity.getId() == null) {
            authDataService.saveIdentity(identity);
        } else {
            authDataService.updateIdentity(identity);
        }

        upsertProviderToken(appCode, user.getId(), appleSubject.trim(), refreshToken, accessToken, tokenType, tokenPayload, now);

        return issueSession(user, appCode, APPLE_SESSION_SOURCE, null, null, null, now);
    }

    private void upsertProviderToken(
        String appCode,
        Long userId,
        String providerSubject,
        String refreshToken,
        String accessToken,
        String tokenType,
        Map<String, Object> tokenPayload,
        OffsetDateTime now
    ) {
        if (!hasText(refreshToken) && !hasText(accessToken)) {
            return;
        }
        SysAuthProviderTokenEntity token = authDataService.providerTokenByIdentity(appCode, APPLE_PROVIDER, providerSubject);
        boolean isNew = token == null;
        if (isNew) {
            token = new SysAuthProviderTokenEntity();
            token.setAppCode(appCode);
            token.setUserId(userId);
            token.setProviderCode(APPLE_PROVIDER);
            token.setProviderSubject(providerSubject);
            token.setCreatedAt(now);
        }
        token.setUserId(userId);
        AppleRefreshTokenVaultService.CaptureResult refreshTokenCapture = hasText(refreshToken)
            ? appleRefreshTokenVaultService.capture(token, refreshToken, now)
            : new AppleRefreshTokenVaultService.CaptureResult("not_updated", "No new refresh token was returned by Apple.", token.getRefreshTokenCiphertextBase64() != null && !token.getRefreshTokenCiphertextBase64().isBlank());
        if (hasText(refreshToken) && !refreshTokenCapture.encrypted()) {
            token.setRefreshToken(refreshToken);
        } else if (hasText(refreshToken)) {
            token.setRefreshToken(null);
        }
        if (hasText(accessToken)) {
            token.setAccessToken(accessToken);
        }
        token.setTokenType(normalize(tokenType));
        token.setStatus(ACTIVE_STATUS);
        token.setPayloadJson(toJson(enrichTokenPayload(tokenPayload, refreshTokenCapture)));
        token.setUpdatedAt(now);
        if (isNew) {
            authDataService.saveProviderToken(token);
        } else {
            authDataService.updateProviderToken(token);
        }
    }

    public Optional<AuthenticatedSessionView> findCurrentSession(String rawSessionToken) {
        OffsetDateTime now = now();
        SysAuthSessionEntity session = authDataService.activeSessionByTokenHash(sessionTokenHashService.hash(rawSessionToken), now);
        if (session == null) {
            return Optional.empty();
        }

        SysUserEntity user = authDataService.userById(session.getUserId());
        if (user == null || !ACTIVE_STATUS.equalsIgnoreCase(user.getStatus())) {
            return Optional.empty();
        }

        authDataService.touchSessionLastSeen(session.getId(), now);
        return Optional.of(new AuthenticatedSessionView(
            session.getAppCode(),
            session.getSessionSource(),
            session.getStatus(),
            session.getExpiresAt(),
            toCurrentUserView(user)
        ));
    }

    @Transactional
    public Optional<LogoutResultView> logout(String rawSessionToken) {
        OffsetDateTime now = now();
        SysAuthSessionEntity session = authDataService.activeSessionByTokenHash(sessionTokenHashService.hash(rawSessionToken), now);
        if (session == null) {
            return Optional.empty();
        }
        authDataService.revokeSession(session.getId(), now);
        return Optional.of(new LogoutResultView(session.getAppCode(), REVOKED_STATUS, now));
    }

    private AuthSessionIssuedView issueSession(
        SysUserEntity user,
        String appCode,
        String sessionSource,
        String deviceId,
        String clientPlatform,
        String clientVersion,
        OffsetDateTime now
    ) {
        String rawSessionToken = generateSessionToken();
        SysAuthSessionEntity session = new SysAuthSessionEntity();
        session.setAppCode(appCode);
        session.setUserId(user.getId());
        session.setSessionTokenHash(sessionTokenHashService.hash(rawSessionToken));
        session.setSessionSource(sessionSource);
        session.setDeviceId(deviceId);
        session.setClientPlatform(clientPlatform);
        session.setClientVersion(clientVersion);
        session.setStatus(ACTIVE_STATUS);
        session.setExpiresAt(now.plusDays(30));
        session.setLastSeenAt(now);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        authDataService.saveSession(session);

        return new AuthSessionIssuedView(
            appCode,
            sessionSource,
            rawSessionToken,
            session.getExpiresAt(),
            toCurrentUserView(user)
        );
    }

    private CurrentUserView toCurrentUserView(SysUserEntity user) {
        return new CurrentUserView(
            user.getId(),
            user.getAppCode(),
            user.getUserType(),
            user.getDisplayName(),
            user.getStatus()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String resolveDisplayName(String displayName) {
        String normalized = normalize(displayName);
        if (normalized != null) {
            return normalized;
        }
        return "Guest-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String resolveMemberDisplayName(String displayName, String email) {
        String normalized = normalize(displayName);
        if (normalized != null) {
            return normalized;
        }
        return "AppleUser-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private Map<String, Object> enrichTokenPayload(
        Map<String, Object> tokenPayload,
        AppleRefreshTokenVaultService.CaptureResult refreshTokenCapture
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (tokenPayload != null) {
            payload.putAll(tokenPayload);
        }
        if (refreshTokenCapture != null) {
            payload.put("refreshTokenStorageStatus", refreshTokenCapture.status());
            payload.put("refreshTokenStorageNote", refreshTokenCapture.note());
            payload.put("refreshTokenEncrypted", refreshTokenCapture.encrypted());
        }
        return payload;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String generateSessionToken() {
        return UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
