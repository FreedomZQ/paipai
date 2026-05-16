package com.apphub.backend.apps.common;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AppModuleRegistryTest {

    @Test
    void shouldRegisterReadingModuleWithoutFallback() {
        AppDefinitionService appDefinitionService = mock(AppDefinitionService.class);
        AppModuleRegistry registry = new AppModuleRegistry(java.util.List.of(
            new ReadingAppModule(appDefinitionService)
        ));

        assertThat(registry.activeModules())
            .extracting(AppModule::appCode)
            .containsExactlyInAnyOrder(ReadingAppModule.APP_CODE);
        assertThat(registry.require(ReadingAppModule.APP_CODE).tablePrefix()).isEqualTo(ReadingAppModule.TABLE_PREFIX);
        assertThat(registry.get("unknown_app")).isEmpty();
    }

    @Test
    void shouldRejectDuplicateAppCodes() {
        AppDefinitionService appDefinitionService = mock(AppDefinitionService.class);

        assertThatThrownBy(() -> new AppModuleRegistry(java.util.List.of(
            new ReadingAppModule(appDefinitionService),
            new ReadingAppModule(appDefinitionService)
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate AppModule appCode");
    }
}
