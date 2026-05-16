package com.apphub.backend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * `OpenApiConfig` 配置类。
 * 用于集中声明和注册 通用基础设施 相关的 Spring 配置、Bean 或配置属性绑定规则。
 */

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
            .title("AppHub Unified Backend API")
            .version("0.1.0")
            .description("Unified multi-app backend baseline for app-scoped auth, billing, sync, and release gates."));
    }
}
