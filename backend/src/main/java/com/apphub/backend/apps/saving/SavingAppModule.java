package com.apphub.backend.apps.saving;

import com.apphub.backend.apps.common.AppModule;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 省钱项目 app module。
 *
 * saving 已有自己的 compat / billing / appstore 对外接口，因此必须参与与 reading 相同的
 * 多 App registry / release-gate 契约，不能继续作为仅靠零散字符串字面量工作的未注册定义。
 */
@Component
public class SavingAppModule implements AppModule {
    public static final String APP_CODE = "saving";
    public static final String INTERNAL_DOMAIN = "saving";
    public static final String TABLE_PREFIX = "saving_";
    public static final String API_PREFIX = "/v1";

    private final AppDefinitionService appDefinitionService;

    public SavingAppModule(AppDefinitionService appDefinitionService) {
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
