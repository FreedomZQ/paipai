package com.apphub.backend.sys.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 认证服务 `AppleCredentialEncryptionService`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Component
public class AppleCredentialEncryptionService {

    private static final int AES_256_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    private final String configuredKey;
    private final String configuredKeyId;
    private final SecureRandom secureRandom = new SecureRandom();

    public AppleCredentialEncryptionService(
        @Value("${APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY:}") String configuredKey,
        @Value("${APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY_ID:v1}") String configuredKeyId
    ) {
        this.configuredKey = configuredKey;
        this.configuredKeyId = configuredKeyId;
    }

    public boolean isReady() {
        try {
            secretKey();
            return true;
        } catch (CryptoConfigurationException ex) {
            return false;
        }
    }

    public EncryptionEnvelope encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new CryptoOperationException("Refresh token plaintext is required for encryption.");
        }
        try {
            byte[] nonce = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptionEnvelope(
                configuredKeyId == null || configuredKeyId.isBlank() ? "v1" : configuredKeyId.trim(),
                ALGORITHM,
                Base64.getEncoder().encodeToString(nonce),
                Base64.getEncoder().encodeToString(ciphertext)
            );
        } catch (CryptoConfigurationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CryptoOperationException("Unable to encrypt Apple refresh token.", ex);
        }
    }

    public String decrypt(String nonceBase64, String ciphertextBase64) {
        if (nonceBase64 == null || nonceBase64.isBlank() || ciphertextBase64 == null || ciphertextBase64.isBlank()) {
            throw new CryptoOperationException("Encrypted Apple refresh token is required for decryption.");
        }
        try {
            byte[] nonce = Base64.getDecoder().decode(nonceBase64);
            byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (CryptoConfigurationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CryptoOperationException("Unable to decrypt Apple refresh token.", ex);
        }
    }

    private SecretKey secretKey() {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new CryptoConfigurationException("APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY is required before Apple refresh tokens can be encrypted.");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(configuredKey.trim());
            if (keyBytes.length != AES_256_KEY_BYTES) {
                throw new CryptoConfigurationException("APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY must be a Base64-encoded 32-byte AES key.");
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException ex) {
            throw new CryptoConfigurationException("APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY must be valid Base64.", ex);
        }
    }

    public record EncryptionEnvelope(
        String keyId,
        String algorithm,
        String nonceBase64,
        String ciphertextBase64
    ) {
    }

    public static class CryptoConfigurationException extends RuntimeException {
        public CryptoConfigurationException(String message) {
            super(message);
        }

        public CryptoConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class CryptoOperationException extends RuntimeException {
        public CryptoOperationException(String message) {
            super(message);
        }

        public CryptoOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
