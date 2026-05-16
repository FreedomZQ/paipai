package com.apphub.backend.sys.powersync.model;

public record PowerSyncRejectedItem(
    String entityType,
    String entityId,
    String reasonCode,
    String reasonMessage
) {
}
