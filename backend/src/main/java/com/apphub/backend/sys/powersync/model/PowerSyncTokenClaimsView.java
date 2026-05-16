package com.apphub.backend.sys.powersync.model;

public record PowerSyncTokenClaimsView(
    String appCode,
    Long userId,
    String installationId
) {
}
