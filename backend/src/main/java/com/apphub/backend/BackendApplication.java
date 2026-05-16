package com.apphub.backend;

import com.apphub.backend.sys.app.config.AppCatalogProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot 应用启动入口。
 * 负责启用统一后端所需的自动配置、Mapper 扫描以及应用目录配置属性绑定。
 */

@SpringBootApplication
@EnableConfigurationProperties(AppCatalogProperties.class)
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
