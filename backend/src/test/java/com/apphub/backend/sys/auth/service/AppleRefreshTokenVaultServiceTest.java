package com.apphub.backend.sys.auth.service;

import com.apphub.backend.sys.auth.entity.SysAuthProviderTokenEntity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对 `AppleRefreshTokenVaultService` 的测试类。
 * 用于验证核心业务逻辑、配置约束或关键边界条件。
 */

class AppleRefreshTokenVaultServiceTest {

    @Test
    void resolveShouldMigratePlaintextRefreshTokenWhenEncryptionKeyIsReady() {
        AppleCredentialEncryptionService encryptionService = new AppleCredentialEncryptionService(
            Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8)),
            "v1"
        );
        AppleRefreshTokenVaultService vaultService = new AppleRefreshTokenVaultService(encryptionService);
        SysAuthProviderTokenEntity token = new SysAuthProviderTokenEntity();
        token.setProviderCode("apple");
        token.setRefreshToken("refresh-123");

        AppleRefreshTokenVaultService.ResolvedTokenResult result = vaultService.resolve(token, OffsetDateTime.parse("2026-04-16T00:00:00Z"));

        assertThat(result.status()).isEqualTo("migrated_plaintext_refresh_token");
        assertThat(result.encrypted()).isTrue();
        assertThat(result.refreshToken()).isEqualTo("refresh-123");
        assertThat(token.getRefreshToken()).isNull();
        assertThat(token.getRefreshTokenCiphertextBase64()).isNotBlank();
        assertThat(token.getRefreshTokenNonceBase64()).isNotBlank();
        assertThat(token.getRefreshTokenLastCapturedAt()).isNotNull();
        assertThat(token.getRefreshTokenLastUsedAt()).isNotNull();
    }

    @Test
    void resolveShouldStayPlaintextWhenEncryptionKeyMissing() {
        AppleCredentialEncryptionService encryptionService = new AppleCredentialEncryptionService("", "v1");
        AppleRefreshTokenVaultService vaultService = new AppleRefreshTokenVaultService(encryptionService);
        SysAuthProviderTokenEntity token = new SysAuthProviderTokenEntity();
        token.setProviderCode("apple");
        token.setRefreshToken("refresh-123");

        AppleRefreshTokenVaultService.ResolvedTokenResult result = vaultService.resolve(token, OffsetDateTime.parse("2026-04-16T00:00:00Z"));

        assertThat(result.status()).isEqualTo("loaded_plaintext_refresh_token");
        assertThat(result.encrypted()).isFalse();
        assertThat(result.refreshToken()).isEqualTo("refresh-123");
        assertThat(token.getRefreshToken()).isEqualTo("refresh-123");
        assertThat(token.getRefreshTokenCiphertextBase64()).isNull();
    }
}
