package com.apphub.backend.sys.powersync.model;

import java.util.List;

public record PowerSyncUploadResult(
    List<PowerSyncAcceptedItem> accepted,
    List<PowerSyncRejectedItem> rejected
) {
    public PowerSyncUploadResult {
        accepted = accepted == null ? List.of() : List.copyOf(accepted);
        rejected = rejected == null ? List.of() : List.copyOf(rejected);
    }
}
