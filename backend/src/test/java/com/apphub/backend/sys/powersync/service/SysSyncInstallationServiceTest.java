package com.apphub.backend.sys.powersync.service;

import com.apphub.backend.sys.powersync.entity.SysSyncInstallationEntity;
import com.apphub.backend.sys.powersync.model.PowerSyncBootstrapRequest;
import com.apphub.backend.sys.powersync.service.crud.SysSyncInstallationCrudService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysSyncInstallationServiceTest {

    @Test
    void bootstrapShouldDropDeviceIdentifiersFromLegacyClients() {
        SysSyncInstallationCrudService crudService = mock(SysSyncInstallationCrudService.class);
        when(crudService.selectByInstallationId("install-1")).thenReturn(null);
        SysSyncInstallationService service = new SysSyncInstallationService(crudService);

        PowerSyncBootstrapRequest request = new PowerSyncBootstrapRequest(
            "install-1",
            "legacy-device-id",
            "ios",
            "iPhone16,2",
            "1.0.0",
            true,
            "ps-client-1"
        );

        SysSyncInstallationEntity entity = service.upsertBootstrap("paipai_readingcompanion", 101L, request);

        assertThat(entity.getDeviceId()).isNull();
        assertThat(entity.getDeviceModel()).isNull();
        assertThat(entity.getClientPlatform()).isEqualTo("ios");
        assertThat(entity.getAppVersion()).isEqualTo("1.0.0");
        verify(crudService).save(any(SysSyncInstallationEntity.class));
    }
}
