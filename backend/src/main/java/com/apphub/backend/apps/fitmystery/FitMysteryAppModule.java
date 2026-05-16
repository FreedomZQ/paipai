package com.apphub.backend.apps.fitmystery;

import com.apphub.backend.apps.common.AppModule;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** FitMystery app module. Owns only appCode=fitmystery and fit_ tables. */
@Component
public class FitMysteryAppModule implements AppModule {
    public static final String APP_CODE = "fitmystery";
    public static final String INTERNAL_DOMAIN = "fitmystery";
    public static final String TABLE_PREFIX = "fit_";
    public static final String API_PREFIX = "/api/v1/fitmystery";

    private final AppDefinitionService appDefinitionService;

    public FitMysteryAppModule(AppDefinitionService appDefinitionService) {
        this.appDefinitionService = appDefinitionService;
    }

    @Override
    public String appCode() {
        return APP_CODE;
    }

    @Override
    public String internalDomain() {
        return INTERNAL_DOMAIN;
    }

    @Override
    public String tablePrefix() {
        return TABLE_PREFIX;
    }

    @Override
    public String apiPrefix() {
        return API_PREFIX;
    }

    @Override
    public Optional<AppDefinition> definition() {
        return appDefinitionService.get(APP_CODE);
    }
}
