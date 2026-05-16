package com.apphub.backend.shared.apple;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * 共享 Apple 能力类 `AppleJwtTokenFactory`。
 * 用于封装多个业务域都会复用的 Apple 相关通用逻辑，降低重复实现成本。
 */

@Component
public class AppleJwtTokenFactory {

    private static final Duration SIGN_IN_CLIENT_SECRET_TTL = Duration.ofMinutes(5);
    private static final Duration APP_STORE_SERVER_API_TOKEN_TTL = Duration.ofMinutes(5);

    private final Clock clock;

    public AppleJwtTokenFactory() {
        this(Clock.systemUTC());
    }

    public AppleJwtTokenFactory(Clock clock) {
        this.clock = clock;
    }

    public String createSignInClientSecret(String teamId, String clientId, String keyId, String privateKeyPem, String audience) {
        Instant now = clock.instant();
        return sign(
            keyId,
            privateKeyPem,
            new JWTClaimsSet.Builder()
                .issuer(teamId)
                .subject(clientId)
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(SIGN_IN_CLIENT_SECRET_TTL)))
                .build()
        );
    }

    public String createAppStoreServerApiToken(String issuerId, String bundleId, String keyId, String privateKeyPem) {
        Instant now = clock.instant();
        return sign(
            keyId,
            privateKeyPem,
            new JWTClaimsSet.Builder()
                .issuer(issuerId)
                .audience("appstoreconnect-v1")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(APP_STORE_SERVER_API_TOKEN_TTL)))
                .claim("bid", bundleId)
                .build()
        );
    }

    public Map<String, Object> inspectClaims(String jwt) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(jwt);
            return signedJWT.getJWTClaimsSet().getClaims();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to inspect JWT claims.", ex);
        }
    }

    private String sign(String keyId, String privateKeyPem, JWTClaimsSet claimsSet) {
        try {
            ECPrivateKey privateKey = loadEcPrivateKey(privateKeyPem);
            SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .keyID(keyId)
                    .type(JOSEObjectType.JWT)
                    .build(),
                claimsSet
            );
            signedJWT.sign(new ECDSASigner(privateKey));
            return signedJWT.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to sign Apple JWT.", ex);
        }
    }

    private ECPrivateKey loadEcPrivateKey(String privateKeyPem) {
        try {
            String normalized = normalizePem(privateKeyPem);
            String base64 = normalized
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(base64.getBytes(StandardCharsets.UTF_8));
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PrivateKey privateKey = keyFactory.generatePrivate(spec);
            if (!(privateKey instanceof ECPrivateKey ecPrivateKey)) {
                throw new IllegalStateException("Apple private key is not an EC private key.");
            }
            return ecPrivateKey;
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to parse Apple private key PEM.", ex);
        }
    }

    private String normalizePem(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Apple private key PEM is required.");
        }
        return value.trim()
            .replace("\\r", "")
            .replace("\\n", "\n")
            .replace("\r", "")
            .replace("\n\n", "\n")
            .trim();
    }
}
