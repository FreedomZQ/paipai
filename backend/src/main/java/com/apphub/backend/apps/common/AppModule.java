package com.apphub.backend.apps.common;

import com.apphub.backend.sys.app.model.AppDefinition;

import java.util.Optional;

/**
 * Standard descriptor for one product app hosted by the unified backend.
 *
 * App-specific code should depend on this module contract instead of scattering
 * literal appCode / tablePrefix / route-prefix constants across controllers and services.
 */
public interface AppModule {
    /** Public appCode used by auth, billing, entitlement and remote config boundaries. */
    String appCode();

    /** Internal business-domain name, e.g. reading. This is not necessarily the public appCode. */
    String internalDomain();

    /** Physical table prefix owned by this app module, e.g. reading_. */
    String tablePrefix();

    /** API prefix used by current app-specific compat surfaces, when any. */
    String apiPrefix();

    /** Loaded app definition, when configured for the current runtime. */
    Optional<AppDefinition> definition();
}
