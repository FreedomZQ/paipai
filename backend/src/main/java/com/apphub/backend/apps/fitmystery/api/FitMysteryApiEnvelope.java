package com.apphub.backend.apps.fitmystery.api;

/** API envelope for FitMystery V2 clients. */
public record FitMysteryApiEnvelope<T>(
    String code,
    String message,
    String requestId,
    T data
) {
    public static <T> FitMysteryApiEnvelope<T> ok(String requestId, T data) {
        return new FitMysteryApiEnvelope<>("SUCCESS", "OK", requestId, data);
    }
}
