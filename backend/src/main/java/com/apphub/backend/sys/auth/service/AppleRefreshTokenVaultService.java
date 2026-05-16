package com.apphub.backend.sys.auth.service;

import com.apphub.backend.sys.auth.entity.SysAuthProviderTokenEntity;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 认证服务 `AppleRefreshTokenVaultService`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Service
public class AppleRefreshTokenVaultService {

    private final AppleCredentialEncryptionService encryptionService;

    public AppleRefreshTokenVaultService(AppleCredentialEncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    public boolean isEncryptionReady() {
        return encryptionService.isReady();
    }

    public CaptureResult capture(SysAuthProviderTokenEntity token, String refreshToken, OffsetDateTime now) {
        if (token == null || !isAppleToken(token)) {
            return new CaptureResult("not_applicable", "Provider token is not backed by Sign in with Apple.", false);
        }
        boolean hadEncryptedToken = hasEncryptedRefreshToken(token);
        if (refreshToken == null || refreshToken.isBlank()) {
            if (hadEncryptedToken || hasPlaintextRefreshToken(token)) {
                return new CaptureResult("reused_existing_refresh_token", "Apple did not return a new refresh token, but an existing refresh token is already on file.", hadEncryptedToken);
            }
            return new CaptureResult("missing_refresh_token", "Apple did not return a refresh token and no stored refresh token exists.", false);
        }
        if (!encryptionService.isReady()) {
            return new CaptureResult("failed_refresh_token_storage_not_configured", "APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY is missing or invalid. Falling back to plaintext refresh token storage for this runtime.", false);
        }
        try {
            AppleCredentialEncryptionService.EncryptionEnvelope envelope = encryptionService.encrypt(refreshToken);
            token.setRefreshTokenKeyId(envelope.keyId());
            token.setRefreshTokenEncryptionAlgorithm(envelope.algorithm());
            token.setRefreshTokenNonceBase64(envelope.nonceBase64());
            token.setRefreshTokenCiphertextBase64(envelope.ciphertextBase64());
            token.setRefreshTokenLastCapturedAt(now);
            return new CaptureResult(hadEncryptedToken ? "rotated_refresh_token" : "stored_refresh_token", "Apple refresh token was encrypted at the application layer.", true);
        } catch (AppleCredentialEncryptionService.CryptoConfigurationException ex) {
            return new CaptureResult("failed_refresh_token_storage_not_configured", ex.getMessage(), false);
        } catch (RuntimeException ex) {
            return new CaptureResult("failed_refresh_token_storage", "Apple refresh token encryption/storage failed.", false);
        }
    }

    public ResolvedTokenResult resolve(SysAuthProviderTokenEntity token, OffsetDateTime now) {
        if (token == null || !isAppleToken(token)) {
            return new ResolvedTokenResult("not_applicable", "Provider token is not backed by Sign in with Apple.", null, false);
        }
        if (hasEncryptedRefreshToken(token)) {
            try {
                String refreshToken = encryptionService.decrypt(token.getRefreshTokenNonceBase64(), token.getRefreshTokenCiphertextBase64());
                token.setRefreshTokenLastUsedAt(now);
                return new ResolvedTokenResult("loaded_encrypted_refresh_token", "Encrypted Apple refresh token decrypted successfully.", refreshToken, true);
            } catch (AppleCredentialEncryptionService.CryptoConfigurationException ex) {
                return new ResolvedTokenResult("not_configured", ex.getMessage(), null, false);
            } catch (AppleCredentialEncryptionService.CryptoOperationException ex) {
                return new ResolvedTokenResult("failed_refresh_token_decryption", ex.getMessage(), null, false);
            }
        }
        if (hasPlaintextRefreshToken(token)) {
            if (encryptionService.isReady()) {
                String plaintext = token.getRefreshToken();
                CaptureResult migration = capture(token, plaintext, now);
                if (migration.encrypted()) {
                    token.setRefreshToken(null);
                    token.setRefreshTokenLastUsedAt(now);
                    return new ResolvedTokenResult("migrated_plaintext_refresh_token", "Plaintext Apple refresh token was migrated into encrypted storage during resolution.", plaintext, true);
                }
            }
            return new ResolvedTokenResult("loaded_plaintext_refresh_token", "Plaintext Apple refresh token loaded. Configure APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY to harden storage.", token.getRefreshToken(), false);
        }
        return new ResolvedTokenResult("token_missing", "No Apple refresh token is available for this provider token.", null, false);
    }

    public void purge(SysAuthProviderTokenEntity token) {
        if (token == null) {
            return;
        }
        token.setRefreshToken(null);
        token.setRefreshTokenKeyId(null);
        token.setRefreshTokenEncryptionAlgorithm(null);
        token.setRefreshTokenNonceBase64(null);
        token.setRefreshTokenCiphertextBase64(null);
        token.setRefreshTokenLastCapturedAt(null);
        token.setRefreshTokenLastUsedAt(null);
    }

    private boolean isAppleToken(SysAuthProviderTokenEntity token) {
        return token.getProviderCode() != null && "apple".equalsIgnoreCase(token.getProviderCode());
    }

    public boolean hasEncryptedRefreshToken(SysAuthProviderTokenEntity token) {
        return token.getRefreshTokenNonceBase64() != null && !token.getRefreshTokenNonceBase64().isBlank()
            && token.getRefreshTokenCiphertextBase64() != null && !token.getRefreshTokenCiphertextBase64().isBlank();
    }

    public boolean hasPlaintextRefreshToken(SysAuthProviderTokenEntity token) {
        return token.getRefreshToken() != null && !token.getRefreshToken().isBlank();
    }

    public record CaptureResult(String status, String note, boolean encrypted) {
    }

    public record ResolvedTokenResult(String status, String note, String refreshToken, boolean encrypted) {
    }
}
