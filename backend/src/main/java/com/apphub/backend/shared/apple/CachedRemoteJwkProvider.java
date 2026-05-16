package com.apphub.backend.shared.apple;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 共享 Apple 能力类 `CachedRemoteJwkProvider`。
 * 用于封装多个业务域都会复用的 Apple 相关通用逻辑，降低重复实现成本。
 */

@Component
public class CachedRemoteJwkProvider {

    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestOperations restOperations;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public CachedRemoteJwkProvider(RestTemplateBuilder restTemplateBuilder) {
        this(
            restTemplateBuilder
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .build(),
            Clock.systemUTC()
        );
    }


    public CachedRemoteJwkProvider(RestOperations restOperations, Clock clock) {
        this.restOperations = restOperations;
        this.clock = clock;
    }

    public JwkResolveResult resolve(String jwksUrl, String keyId) {
        if (jwksUrl == null || jwksUrl.isBlank()) {
            return new JwkResolveResult(null, false, null, "jwks_url_missing");
        }
        if (keyId == null || keyId.isBlank()) {
            return new JwkResolveResult(null, false, null, "jwks_key_id_missing");
        }

        CacheEntry cached = cache.get(jwksUrl);
        boolean shouldRefresh = cached == null || cached.isExpired(clock.instant());
        if (shouldRefresh) {
            cached = refresh(jwksUrl);
        }

        if (cached != null) {
            JWK key = cached.key(keyId);
            if (key != null) {
                return new JwkResolveResult(key, !shouldRefresh, cached.fetchedAt(), null);
            }
        }

        CacheEntry refreshed = refresh(jwksUrl);
        if (refreshed != null) {
            JWK key = refreshed.key(keyId);
            if (key != null) {
                return new JwkResolveResult(key, false, refreshed.fetchedAt(), null);
            }
            return new JwkResolveResult(null, false, refreshed.fetchedAt(), "jwks_key_not_found");
        }
        return new JwkResolveResult(null, false, cached != null ? cached.fetchedAt() : null, "jwks_fetch_failed");
    }

    private CacheEntry refresh(String jwksUrl) {
        try {
            ResponseEntity<String> response = restOperations.getForEntity(jwksUrl, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                return null;
            }
            CacheEntry entry = new CacheEntry(JWKSet.parse(response.getBody()), clock.instant());
            cache.put(jwksUrl, entry);
            return entry;
        } catch (RestClientException | ParseException ex) {
            return null;
        }
    }

    public record JwkResolveResult(
        JWK jwk,
        boolean cacheHit,
        Instant fetchedAt,
        String errorCode
    ) {
    }

    private record CacheEntry(JWKSet jwkSet, Instant fetchedAt) {
        private boolean isExpired(Instant now) {
            return fetchedAt == null || now.isAfter(fetchedAt.plus(CACHE_TTL));
        }

        private JWK key(String keyId) {
            return jwkSet.getKeys().stream()
                .filter(item -> keyId.equals(item.getKeyID()))
                .findFirst()
                .orElse(null);
        }
    }
}
