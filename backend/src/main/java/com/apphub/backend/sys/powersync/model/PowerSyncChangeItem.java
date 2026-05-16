package com.apphub.backend.sys.powersync.model;

import java.util.Map;

public record PowerSyncChangeItem(
    String entityType,
    String operation,
    String entityId,
    String clientUpdatedAt,
    Map<String, Object> payload
) {
    public PowerSyncChangeItem {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
