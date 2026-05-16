package com.apphub.backend.sys.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 认证服务 `AppleIdentityTokenDecoder`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Component
public class AppleIdentityTokenDecoder {

    private final ObjectMapper objectMapper;

    public AppleIdentityTokenDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DecodedAppleIdentityToken decode(String identityToken) {
        if (identityToken == null || identityToken.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "identityToken is required for Apple exchange.");
        }
        String[] parts = identityToken.split("\\.");
        if (parts.length < 2) {
            throw new ResponseStatusException(BAD_REQUEST, "identityToken is not a valid JWT/JWS string.");
        }
        try {
            JsonNode header = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
            JsonNode payload = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            return new DecodedAppleIdentityToken(
                textValue(payload, "sub"),
                textValue(payload, "email"),
                booleanValue(payload, "email_verified"),
                textValue(payload, "iss"),
                audienceValues(payload.get("aud")),
                textValue(payload, "nonce"),
                booleanValue(payload, "nonce_supported"),
                booleanValue(payload, "is_private_email"),
                dateTimeValue(payload, "exp"),
                dateTimeValue(payload, "iat"),
                textValue(header, "alg"),
                textValue(header, "kid")
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Unable to decode Apple identity token payload.");
        }
    }

    private List<String> audienceValues(JsonNode audNode) {
        if (audNode == null || audNode.isNull()) {
            return List.of();
        }
        if (audNode.isArray()) {
            List<String> values = new ArrayList<>();
            audNode.forEach(item -> values.add(item.asText()));
            return values;
        }
        return List.of(audNode.asText());
    }

    private Boolean booleanValue(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return Boolean.parseBoolean(value.asText());
    }

    private OffsetDateTime dateTimeValue(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(value.asLong()), ZoneOffset.UTC);
        }
        try {
            return OffsetDateTime.parse(value.asText());
        } catch (Exception ex) {
            return null;
        }
    }

    private String textValue(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    public record DecodedAppleIdentityToken(
        String subject,
        String email,
        Boolean emailVerified,
        String issuer,
        List<String> audience,
        String nonce,
        Boolean nonceSupported,
        Boolean privateEmail,
        OffsetDateTime expiresAt,
        OffsetDateTime issuedAt,
        String algorithm,
        String keyId
    ) {
    }
}
