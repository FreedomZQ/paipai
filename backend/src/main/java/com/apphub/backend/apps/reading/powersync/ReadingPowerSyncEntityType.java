package com.apphub.backend.apps.reading.powersync;

import java.util.Arrays;

public enum ReadingPowerSyncEntityType {
    CHILD_PROFILE("child_profile"),
    REVIEW_CARD("review_card"),
    REVIEW_EVENT("review_event"),
    USAGE_SESSION("usage_session"),
    USER_PREFERENCE("user_preference");

    private final String code;

    ReadingPowerSyncEntityType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ReadingPowerSyncEntityType fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
            .filter(item -> item.code.equalsIgnoreCase(value.trim()))
            .findFirst()
            .orElse(null);
    }
}
