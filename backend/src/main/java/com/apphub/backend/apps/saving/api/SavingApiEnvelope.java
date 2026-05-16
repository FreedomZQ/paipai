package com.apphub.backend.apps.saving.api;

/** API envelope kept compatible with the existing SaveMoney iOS client. */
public record SavingApiEnvelope<T>(
    String code,
    String message,
    String requestId,
    T data
) {
    public static <T> SavingApiEnvelope<T> ok(String requestId, T data) {
        return new SavingApiEnvelope<>("SUCCESS", "OK", requestId, data);
    }
}
