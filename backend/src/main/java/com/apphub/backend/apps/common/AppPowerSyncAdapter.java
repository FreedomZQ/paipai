package com.apphub.backend.apps.common;

import com.apphub.backend.sys.powersync.support.PowerSyncAppAdapter;

import java.util.List;

/**
 * Multi-app PowerSync adapter contract.
 *
 * Each app adapter should expose both the owning AppModule and a compact entity-spec
 * list so new apps can follow a template instead of cloning the reading implementation.
 */
public interface AppPowerSyncAdapter extends PowerSyncAppAdapter {
    AppModule appModule();

    List<SyncEntitySpec> entities();

    @Override
    default String appCode() {
        return appModule().appCode();
    }

    record SyncEntitySpec(
        String entityType,
        String ownershipField,
        boolean createAllowed,
        boolean updateAllowed,
        boolean deleteAllowed,
        String entitlementGate,
        String versionPolicy
    ) {}
}
