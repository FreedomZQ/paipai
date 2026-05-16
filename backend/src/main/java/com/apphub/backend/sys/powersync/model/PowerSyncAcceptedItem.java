package com.apphub.backend.sys.powersync.model;

public record PowerSyncAcceptedItem(
    String entityType,
    String entityId,
    String serverUpdatedAt
) {
}
