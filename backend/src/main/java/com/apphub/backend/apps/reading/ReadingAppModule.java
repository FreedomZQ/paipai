package com.apphub.backend.apps.reading;

import com.apphub.backend.apps.common.AppModule;
import com.apphub.backend.sys.app.model.AppCodes;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 拍拍伴读 app module。
 *
 * 对外 appCode 故意与内部 reading 领域名分离：
 * - appCode: 参与 auth / billing / entitlement / PowerSync 隔离。
 * - internalDomain/tablePrefix: 保留当前物理实现命名空间。
 */
@Component
public class ReadingAppModule implements AppModule {
    /**
     * 对外产品身份常量。
     *
     * <p>这里故意不再直接散落字符串字面量，避免 P3 模板化过程中继续把产品 appCode
     * 和内部 reading 实现名绑死在一起。</p>
     */
    public static final String APP_CODE = AppCodes.PAIPAI_READINGCOMPANION;
    public static final String INTERNAL_DOMAIN = "reading";
    public static final String TABLE_PREFIX = "reading_";
    public static final String API_PREFIX = "/api/v1";

    private final AppDefinitionService appDefinitionService;

    public ReadingAppModule(AppDefinitionService appDefinitionService) {
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
