package com.apphub.backend.sys.auth.service;

import com.apphub.backend.shared.apple.CachedRemoteJwkProvider;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 认证领域的可集成实现 `ReadyForIntegrationAppleIdentityTokenVerifier`。
 * 用于接入真实外部依赖或正式流程，避免在占位实现与生产实现之间混淆职责。
 */

@Service
@Primary
public class ReadyForIntegrationAppleIdentityTokenVerifier implements AppleIdentityTokenVerifier {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final AppleIdentityTokenDecoder decoder;
    private final CachedRemoteJwkProvider cachedRemoteJwkProvider;
    private final String defaultJwksUrl;
    private final DefaultJWSVerifierFactory verifierFactory = new DefaultJWSVerifierFactory();

    public ReadyForIntegrationAppleIdentityTokenVerifier(
        AppleIdentityTokenDecoder decoder,
        CachedRemoteJwkProvider cachedRemoteJwkProvider,
        @Value("${backend.auth.apple.jwks-url:https://appleid.apple.com/auth/keys}") String defaultJwksUrl
    ) {
        this.decoder = decoder;
        this.cachedRemoteJwkProvider = cachedRemoteJwkProvider;
        this.defaultJwksUrl = defaultJwksUrl;
    }

    @Override
    public VerificationResult verify(String identityToken, VerificationCommand command) {
        AppleIdentityTokenDecoder.DecodedAppleIdentityToken decoded = decoder.decode(identityToken);
        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("issuer", decoded.issuer());
        diagnostics.put("audience", String.join(",", decoded.audience()));
        diagnostics.put("keyId", decoded.keyId());
        diagnostics.put("algorithm", decoded.algorithm());
        diagnostics.put("expiresAt", decoded.expiresAt() != null ? decoded.expiresAt().toString() : null);
        diagnostics.put("jwksUrl", defaultJwksUrl);

        if (decoded.subject() == null || decoded.subject().isBlank()) {
            return new VerificationResult("rejected_missing_subject", "Apple identity token is missing the required 'sub' claim.", decoded, diagnostics);
        }
        String expectedIssuer = normalize(command.expectedIssuer()) != null ? normalize(command.expectedIssuer()) : APPLE_ISSUER;
        if (decoded.issuer() == null || !expectedIssuer.equals(normalize(decoded.issuer()))) {
            return new VerificationResult("rejected_issuer_mismatch", "Apple identity token issuer does not match Apple's issuer.", decoded, diagnostics);
        }
        String expectedAudience = normalize(command.expectedAudience());
        if (expectedAudience != null && decoded.audience().stream().map(this::normalize).noneMatch(expectedAudience::equals)) {
            return new VerificationResult("rejected_audience_mismatch", "Apple identity token audience does not match the configured client identifier.", decoded, diagnostics);
        }
        if (decoded.expiresAt() == null) {
            return new VerificationResult("rejected_missing_exp", "Apple identity token is missing the required exp claim.", decoded, diagnostics);
        }
        if (decoded.expiresAt().isBefore(OffsetDateTime.now())) {
            return new VerificationResult("rejected_token_expired", "Apple identity token has expired.", decoded, diagnostics);
        }
        String expectedNonce = normalize(command.expectedNonce());
        if (expectedNonce != null && !expectedNonce.equals(normalize(decoded.nonce()))) {
            return new VerificationResult("rejected_nonce_mismatch", "Apple identity token nonce does not match the original request nonce.", decoded, diagnostics);
        }
        if (!command.verificationConfigured()) {
            return new VerificationResult(
                "not_configured",
                "Apple identity token verification prerequisites are incomplete. Configure clientId + Apple JWKS endpoint before enabling formal sign-in.",
                decoded,
                diagnostics
            );
        }

        try {
            SignedJWT signedJWT = SignedJWT.parse(identityToken);
            CachedRemoteJwkProvider.JwkResolveResult jwkResolveResult = cachedRemoteJwkProvider.resolve(defaultJwksUrl, signedJWT.getHeader().getKeyID());
            diagnostics.put("jwksCacheHit", String.valueOf(jwkResolveResult.cacheHit()));
            diagnostics.put("jwksFetchedAt", jwkResolveResult.fetchedAt() != null ? jwkResolveResult.fetchedAt().toString() : null);
            diagnostics.put("jwksError", jwkResolveResult.errorCode());
            if (jwkResolveResult.jwk() == null) {
                return new VerificationResult(
                    "failed_jwks_unavailable",
                    "Unable to resolve the Apple JWKS key required for identity-token signature verification.",
                    decoded,
                    diagnostics
                );
            }
            if (!verifySignature(signedJWT, jwkResolveResult.jwk())) {
                return new VerificationResult(
                    "rejected_invalid_signature",
                    "Apple identity token signature verification failed.",
                    decoded,
                    diagnostics
                );
            }
            return new VerificationResult(
                "verified",
                "Apple identity token signature and claims were verified against Apple's JWKS.",
                decoded,
                diagnostics
            );
        } catch (Exception ex) {
            diagnostics.put("verificationException", ex.getClass().getSimpleName());
            return new VerificationResult(
                "failed_signature_verification_error",
                "Apple identity token signature verification failed unexpectedly.",
                decoded,
                diagnostics
            );
        }
    }

    private boolean verifySignature(SignedJWT signedJWT, JWK jwk) throws JOSEException {
        java.security.Key key;
        if (jwk instanceof RSAKey rsaKey) {
            key = rsaKey.toRSAPublicKey();
        } else if (jwk instanceof ECKey ecKey) {
            key = ecKey.toECPublicKey();
        } else {
            throw new JOSEException("Unsupported Apple JWKS key type: " + jwk.getKeyType());
        }
        JWSVerifier verifier = verifierFactory.createJWSVerifier(signedJWT.getHeader(), key);
        return signedJWT.verify(verifier);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
