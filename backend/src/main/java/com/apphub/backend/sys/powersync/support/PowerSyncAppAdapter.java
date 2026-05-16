package com.apphub.backend.sys.powersync.support;

import com.apphub.backend.sys.powersync.model.PowerSyncChangeItem;
import com.apphub.backend.sys.powersync.model.PowerSyncUploadResult;

import java.util.List;

public interface PowerSyncAppAdapter {
    String appCode();

    default boolean supports(String otherAppCode) {
        return appCode() != null && appCode().equalsIgnoreCase(otherAppCode);
    }

    default void validateSyncAccess(Long userId) {
        // default no-op for app adapters without extra entitlement checks
    }

    PowerSyncUploadResult applyBatch(Long userId, String installationId, List<PowerSyncChangeItem> changes);
}
