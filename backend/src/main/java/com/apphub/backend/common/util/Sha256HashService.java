package com.apphub.backend.common.util;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * `Sha256HashService` 工具类。
 * 用于封装可复用的通用能力，避免在业务代码中重复实现相同细节。
 */

@Service
public class Sha256HashService {

    public String hash(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawValue.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash value", exception);
        }
    }
}
