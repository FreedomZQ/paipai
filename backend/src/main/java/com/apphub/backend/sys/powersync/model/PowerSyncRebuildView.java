package com.apphub.backend.sys.powersync.model;

public record PowerSyncRebuildView(
    String installationId,
    boolean shouldRebuild,
    String message
) {
}
