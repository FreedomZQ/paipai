package com.apphub.backend.sys.powersync.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record PowerSyncUploadEnvelope(
    @NotBlank String installationId,
    List<PowerSyncChangeItem> changes
) {
    public PowerSyncUploadEnvelope {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
