package com.apphub.backend.sys.appstore.service;

import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * App Store服务 `AppStoreSignedJwsVerifier`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Component
public class AppStoreSignedJwsVerifier {

    private static final String WWDR_INTERMEDIATE_OID = "1.2.840.113635.100.6.2.1";
    private static final String RECEIPT_SIGNER_OID = "1.2.840.113635.100.6.11.1";
    private static final String CODE_SIGNING_EKU = "1.3.6.1.5.5.7.3.3";

    private final ObjectMapper objectMapper;
    private final CertificateFactory certificateFactory;
    private final Set<TrustAnchor> trustAnchors;
    private final Clock clock;

    @Autowired
    public AppStoreSignedJwsVerifier(ObjectMapper objectMapper) {
        this(objectMapper, loadDefaultRoots(), Clock.systemUTC());
    }

    AppStoreSignedJwsVerifier(ObjectMapper objectMapper, List<X509Certificate> trustedRoots, Clock clock) {
        try {
            this.objectMapper = objectMapper;
            this.certificateFactory = CertificateFactory.getInstance("X.509");
            this.trustAnchors = trustedRoots.stream()
                .map(root -> new TrustAnchor(root, null))
                .collect(Collectors.toUnmodifiableSet());
            this.clock = clock;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize App Store signed JWS verifier.", ex);
        }
    }

    public VerifiedJws verify(String compactJws, String fieldName) {
        if (!hasText(compactJws)) {
            throw new VerificationException("failed_missing_jws", fieldName + " is required.", Map.of("fieldName", fieldName));
        }

        SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(compactJws);
        } catch (Exception ex) {
            throw new VerificationException("failed_malformed_jws", fieldName + " is not a valid compact JWS.", Map.of("fieldName", fieldName));
        }

        JWSHeader header = signedJWT.getHeader();
        if (!JWSAlgorithm.ES256.equals(header.getAlgorithm())) {
            throw new VerificationException(
                "failed_invalid_algorithm",
                fieldName + " must use Apple ES256 signing.",
                Map.of("fieldName", fieldName, "algorithm", header.getAlgorithm() != null ? header.getAlgorithm().getName() : "unknown")
            );
        }

        List<Base64> x5c = header.getX509CertChain();
        if (x5c == null || x5c.size() < 2) {
            throw new VerificationException("failed_missing_x5c", fieldName + " did not include an Apple x5c certificate chain.", Map.of("fieldName", fieldName));
        }
        if (x5c.size() > 3) {
            throw new VerificationException(
                "failed_invalid_certificate_chain",
                fieldName + " included an unexpected x5c chain length.",
                Map.of("fieldName", fieldName, "certificateChainLength", String.valueOf(x5c.size()))
            );
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(signedJWT.getPayload().toBytes());
        } catch (Exception ex) {
            throw new VerificationException("failed_unparseable_payload", fieldName + " payload could not be decoded as JSON.", Map.of("fieldName", fieldName));
        }

        OffsetDateTime effectiveAt = resolveEffectiveAt(payload);
        List<X509Certificate> certificates = parseCertificates(x5c, fieldName);
        X509Certificate leaf = validateCertificateChain(certificates, effectiveAt, fieldName);

        if (!(leaf.getPublicKey() instanceof ECPublicKey publicKey)) {
            throw new VerificationException("failed_invalid_certificate", fieldName + " signing certificate is not backed by an EC public key.", Map.of("fieldName", fieldName));
        }

        boolean signatureValid;
        try {
            signatureValid = signedJWT.verify(new ECDSAVerifier(publicKey));
        } catch (JOSEException ex) {
            throw new VerificationException("failed_invalid_signature", fieldName + " signature could not be verified.", Map.of("fieldName", fieldName));
        }
        if (!signatureValid) {
            throw new VerificationException("failed_invalid_signature", fieldName + " signature verification failed.", Map.of("fieldName", fieldName));
        }

        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("fieldName", fieldName);
        diagnostics.put("algorithm", header.getAlgorithm().getName());
        diagnostics.put("certificateChainLength", String.valueOf(certificates.size()));
        diagnostics.put("effectiveAt", effectiveAt.toString());
        diagnostics.put("leafSubject", leaf.getSubjectX500Principal().getName());
        diagnostics.put("intermediateSubject", certificates.get(1).getSubjectX500Principal().getName());
        diagnostics.put("trustedRootSubject", resolveTrustedRootSubject(certificates));
        diagnostics.put("signatureVerified", "true");
        diagnostics.put("certificateChainVerified", "true");

        return new VerifiedJws(
            objectMapper.valueToTree(header.toJSONObject()),
            payload,
            Map.copyOf(diagnostics),
            effectiveAt
        );
    }

    private List<X509Certificate> parseCertificates(List<Base64> x5c, String fieldName) {
        List<X509Certificate> certificates = new ArrayList<>();
        for (Base64 item : x5c) {
            try (InputStream inputStream = new ByteArrayInputStream(item.decode())) {
                certificates.add((X509Certificate) certificateFactory.generateCertificate(inputStream));
            } catch (Exception ex) {
                throw new VerificationException("failed_invalid_certificate", fieldName + " x5c chain could not be parsed as X.509.", Map.of("fieldName", fieldName));
            }
        }
        return certificates;
    }

    private X509Certificate validateCertificateChain(List<X509Certificate> certificates, OffsetDateTime effectiveAt, String fieldName) {
        X509Certificate leaf = certificates.get(0);
        X509Certificate intermediate = certificates.get(1);

        requireLeafCertificate(leaf, fieldName);
        requireIntermediateCertificate(intermediate, fieldName);

        X509Certificate trustedRoot = certificates.size() == 3
            ? requireTrustedRoot(certificates.get(2), fieldName)
            : trustAnchors.stream().findFirst().map(TrustAnchor::getTrustedCert).orElseThrow(() ->
                new VerificationException("failed_invalid_certificate_chain", fieldName + " has no trusted Apple root configured.", Map.of("fieldName", fieldName))
            );

        try {
            Date validationDate = Date.from(effectiveAt.toInstant());
            leaf.checkValidity(validationDate);
            intermediate.checkValidity(validationDate);
            trustedRoot.checkValidity(validationDate);
            leaf.verify(intermediate.getPublicKey());
            intermediate.verify(trustedRoot.getPublicKey());
        } catch (Exception ex) {
            throw new VerificationException(
                "failed_invalid_certificate_chain",
                fieldName + " x5c chain did not validate against the trusted Apple root set.",
                Map.of("fieldName", fieldName)
            );
        }
        return leaf;
    }

    private void requireLeafCertificate(X509Certificate leaf, String fieldName) {
        if (leaf.getBasicConstraints() >= 0) {
            throw new VerificationException("failed_invalid_certificate", fieldName + " leaf certificate must not be a CA certificate.", Map.of("fieldName", fieldName));
        }
        boolean[] keyUsage = leaf.getKeyUsage();
        if (keyUsage != null && (keyUsage.length == 0 || !keyUsage[0])) {
            throw new VerificationException("failed_invalid_certificate", fieldName + " leaf certificate must allow digital signatures.", Map.of("fieldName", fieldName));
        }
        Collection<String> nonCriticalOids = leaf.getNonCriticalExtensionOIDs();
        if (nonCriticalOids == null || !nonCriticalOids.contains(RECEIPT_SIGNER_OID)) {
            throw new VerificationException(
                "failed_invalid_certificate",
                fieldName + " signing certificate is missing the App Store receipt signer extension.",
                Map.of("fieldName", fieldName, "requiredOid", RECEIPT_SIGNER_OID)
            );
        }
        try {
            List<String> extendedKeyUsage = leaf.getExtendedKeyUsage();
            if (extendedKeyUsage != null && !extendedKeyUsage.contains(CODE_SIGNING_EKU)) {
                throw new VerificationException(
                    "failed_invalid_certificate",
                    fieldName + " signing certificate does not advertise code signing usage.",
                    Map.of("fieldName", fieldName)
                );
            }
        } catch (VerificationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new VerificationException("failed_invalid_certificate", fieldName + " signing certificate EKU could not be inspected.", Map.of("fieldName", fieldName));
        }
    }

    private void requireIntermediateCertificate(X509Certificate intermediate, String fieldName) {
        if (intermediate.getBasicConstraints() < 0) {
            throw new VerificationException("failed_invalid_certificate", fieldName + " intermediate certificate must be a CA certificate.", Map.of("fieldName", fieldName));
        }
        Collection<String> nonCriticalOids = intermediate.getNonCriticalExtensionOIDs();
        if (nonCriticalOids == null || !nonCriticalOids.contains(WWDR_INTERMEDIATE_OID)) {
            throw new VerificationException(
                "failed_invalid_certificate",
                fieldName + " intermediate certificate is missing the Apple WWDR extension.",
                Map.of("fieldName", fieldName, "requiredOid", WWDR_INTERMEDIATE_OID)
            );
        }
    }

    private X509Certificate requireTrustedRoot(X509Certificate root, String fieldName) {
        return trustAnchors.stream()
            .map(TrustAnchor::getTrustedCert)
            .filter(anchor -> sameCertificate(root, anchor))
            .findFirst()
            .orElseThrow(() -> new VerificationException(
                "failed_invalid_certificate_chain",
                fieldName + " root certificate did not match the embedded Apple trust anchors.",
                Map.of("fieldName", fieldName)
            ));
    }

    private String resolveTrustedRootSubject(List<X509Certificate> certificates) {
        if (certificates.size() == 3) {
            return certificates.get(2).getSubjectX500Principal().getName();
        }
        return trustAnchors.stream()
            .findFirst()
            .map(anchor -> anchor.getTrustedCert().getSubjectX500Principal().getName())
            .orElse("unknown");
    }

    private boolean sameCertificate(X509Certificate left, X509Certificate right) {
        try {
            return java.util.Arrays.equals(left.getEncoded(), right.getEncoded());
        } catch (Exception ex) {
            return false;
        }
    }

    private OffsetDateTime resolveEffectiveAt(JsonNode payload) {
        OffsetDateTime signedDate = time(payload.get("signedDate"));
        return signedDate != null ? signedDate : OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private OffsetDateTime time(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            long raw = node.asLong();
            Instant instant = raw > 9_999_999_999L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        if (!hasText(node.asText())) {
            return null;
        }
        try {
            return OffsetDateTime.parse(node.asText());
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static List<X509Certificate> loadDefaultRoots() {
        try (InputStream inputStream = new ClassPathResource("apple/AppleRootCA-G3.pem").getInputStream()) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Collection<? extends java.security.cert.Certificate> certificates = certificateFactory.generateCertificates(inputStream);
            return certificates.stream()
                .map(X509Certificate.class::cast)
                .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load embedded Apple Root CA certificates.", ex);
        }
    }

    public record VerifiedJws(
        JsonNode header,
        JsonNode payload,
        Map<String, String> diagnostics,
        OffsetDateTime effectiveAt
    ) {
    }

    public static class VerificationException extends RuntimeException {

        private final String detailStatus;
        private final String note;
        private final Map<String, String> diagnostics;

        VerificationException(String detailStatus, String note, Map<String, String> diagnostics) {
            super(note);
            this.detailStatus = detailStatus;
            this.note = note;
            this.diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
        }

        public String detailStatus() {
            return detailStatus;
        }

        public String note() {
            return note;
        }

        public Map<String, String> diagnostics() {
            return diagnostics;
        }
    }
}
