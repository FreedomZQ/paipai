package com.apphub.backend.sys.powersync.support;

import com.apphub.backend.apps.common.AppModuleRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PowerSyncAppAdapterRegistry {
    private final Map<String, PowerSyncAppAdapter> adaptersByAppCode;

    public PowerSyncAppAdapterRegistry(List<PowerSyncAppAdapter> adapters, AppModuleRegistry appModuleRegistry) {
        Map<String, PowerSyncAppAdapter> ordered = new LinkedHashMap<>();
        if (adapters != null) {
            for (PowerSyncAppAdapter adapter : adapters) {
                if (adapter == null || adapter.appCode() == null || adapter.appCode().isBlank()) {
                    continue;
                }
                String appCode = adapter.appCode().trim();
                appModuleRegistry.require(appCode);
                PowerSyncAppAdapter previous = ordered.putIfAbsent(appCode, adapter);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate PowerSyncAppAdapter appCode: " + appCode);
                }
            }
        }
        this.adaptersByAppCode = Map.copyOf(ordered);
    }

    public List<PowerSyncAppAdapter> activeAdapters() {
        return List.copyOf(adaptersByAppCode.values());
    }

    public Optional<PowerSyncAppAdapter> get(String appCode) {
        if (appCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(adaptersByAppCode.get(appCode.trim()));
    }

    public PowerSyncAppAdapter require(String appCode) {
        return get(appCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POWERSYNC_APP_UNSUPPORTED"));
    }
}
