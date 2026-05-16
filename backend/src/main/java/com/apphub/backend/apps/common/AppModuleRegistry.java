package com.apphub.backend.apps.common;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime registry for app modules hosted by the unified backend.
 *
 * This is the first guardrail for multi-app templateization: unknown appCode values
 * must fail explicitly and never fall back to the first app.
 */
@Component
public class AppModuleRegistry {
    private final Map<String, AppModule> modulesByAppCode;

    public AppModuleRegistry(List<AppModule> modules) {
        Map<String, AppModule> ordered = new LinkedHashMap<>();
        for (AppModule module : modules) {
            if (module == null || module.appCode() == null || module.appCode().isBlank()) {
                continue;
            }
            AppModule previous = ordered.put(module.appCode(), module);
            if (previous != null) {
                throw new IllegalStateException("Duplicate AppModule appCode: " + module.appCode());
            }
        }
        this.modulesByAppCode = Map.copyOf(ordered);
    }

    public List<AppModule> activeModules() {
        return List.copyOf(modulesByAppCode.values());
    }

    public Optional<AppModule> get(String appCode) {
        return Optional.ofNullable(modulesByAppCode.get(appCode));
    }

    public AppModule require(String appCode) {
        return get(appCode).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "APP_NOT_CONFIGURED: " + appCode));
    }
}
