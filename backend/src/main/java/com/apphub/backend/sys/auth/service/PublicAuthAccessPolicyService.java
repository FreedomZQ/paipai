package com.apphub.backend.sys.auth.service;

import com.apphub.backend.sys.app.model.AppDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 认证服务 `PublicAuthAccessPolicyService`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Service
public class PublicAuthAccessPolicyService {

    private final String environment;

    public PublicAuthAccessPolicyService(@Value("${backend.environment:${BACKEND_ENV:dev}}") String environment) {
        this.environment = environment;
    }

    public boolean demoSessionsEnabled(AppDefinition appDefinition) {
        String configured = raw(appDefinition, "app.auth.demoSessionEnabled");
        if (configured != null && !configured.isBlank()) {
            return Boolean.parseBoolean(configured);
        }
        return false;
    }

    public boolean bootstrapSessionsEnabled(AppDefinition appDefinition) {
        String configured = raw(appDefinition, "app.auth.bootstrapSessionEnabled");
        if (configured != null && !configured.isBlank()) {
            return Boolean.parseBoolean(configured);
        }
        return true;
    }

    private String raw(AppDefinition appDefinition, String key) {
        if (appDefinition == null || appDefinition.raw() == null) {
            return null;
        }
        Object value = appDefinition.raw().get(key);
        return value == null ? null : String.valueOf(value).trim();
    }
}
