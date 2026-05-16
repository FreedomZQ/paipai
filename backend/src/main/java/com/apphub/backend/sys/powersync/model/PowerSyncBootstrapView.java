package com.apphub.backend.sys.powersync.model;

public record PowerSyncBootstrapView(
    String appCode,
    String installationId,
    boolean cloudSyncEnabled,
    boolean initialSyncCompleted,
    String powerSyncEndpoint,
    String tokenExpiresAt,
    boolean shouldRebuild,
    String serverTime
) {
}
