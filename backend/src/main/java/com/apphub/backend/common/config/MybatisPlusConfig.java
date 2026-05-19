package com.apphub.backend.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * `MybatisPlusConfig` 配置类。
 * 用于集中声明和注册 通用基础设施 相关的 Spring 配置、Bean 或配置属性绑定规则。
 */

@Configuration
@MapperScan({
    "com.apphub.backend.sys.auth.mapper",
    "com.apphub.backend.sys.configcenter.mapper",
    "com.apphub.backend.sys.billing.mapper",
    "com.apphub.backend.sys.entitlement.mapper",
    "com.apphub.backend.sys.compensation.mapper",
    "com.apphub.backend.sys.appstore.mapper",
    "com.apphub.backend.apps.reading.domain.mapper"
})
public class MybatisPlusConfig {
}
