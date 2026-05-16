package com.apphub.backend.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对 `ProductionProfileConfiguration` 的测试类。
 * 用于验证核心业务逻辑、配置约束或关键边界条件。
 */

class ProductionProfileConfigurationTest {

    @Test
    void productionProfileShouldDisableSwaggerAndRestrictActuatorExposure() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application-prod.yml"));
        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(properties.getProperty("backend.environment")).isEqualTo("prod");
    }
}
