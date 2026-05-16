package com.apphub.backend.sys.powersync.model;

public record PowerSyncTokenView(
    String endpoint,
    String token,
    String expiresAt,
    PowerSyncTokenClaimsView claims
) {
}
