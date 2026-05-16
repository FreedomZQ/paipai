package com.apphub.backend.sys.appstore.service;

import com.apphub.backend.common.util.Sha256HashService;
import com.apphub.backend.sys.appstore.entity.SysAppStoreNotificationEntity;
import com.apphub.backend.sys.appstore.service.crud.SysAppStoreNotificationCrudService;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationAcceptedView;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationIngestRequest;
import com.apphub.backend.sys.billing.service.SysBillingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 针对 `SysAppStoreNotificationService` 的测试类。
 * 用于验证核心业务逻辑、配置约束或关键边界条件。
 */

@ExtendWith(MockitoExtension.class)
class SysAppStoreNotificationServiceTest {

    @Mock
    private SysAppStoreNotificationCrudService sysAppStoreNotificationCrudService;

    @Mock
    private Sha256HashService sha256HashService;

    @Mock
    private AppStoreJwsVerificationService appStoreJwsVerificationService;

    @Mock
    private SysBillingService sysBillingService;

    private SysAppStoreNotificationService sysAppStoreNotificationService;

    @BeforeEach
    void setUp() {
        sysAppStoreNotificationService = new SysAppStoreNotificationService(
            sysAppStoreNotificationCrudService,
            sha256HashService,
            new ObjectMapper(),
            appStoreJwsVerificationService,
            sysBillingService
        );
    }

    @Test
    void ingestShouldReturnDuplicateViewWhenUniqueConstraintRaces() {
        when(appStoreJwsVerificationService.verifyNotification("signed-jws"))
            .thenReturn(new AppStoreJwsVerificationService.NotificationVerificationResult(
                "failed",
                "failed_signature",
                "bad sig",
                null,
                Map.of()
            ));
        when(sha256HashService.hash("signed-jws")).thenReturn("hash-1");
        when(sysAppStoreNotificationCrudService.getOne(any()))
            .thenReturn(null)
            .thenReturn(existing());
        when(sysAppStoreNotificationCrudService.save(any(SysAppStoreNotificationEntity.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        AppStoreNotificationAcceptedView result = sysAppStoreNotificationService.ingest(
            "paipai_readingcompanion",
            new AppStoreNotificationIngestRequest("signed-jws", "notify-1", "DID_RENEW", null, Map.of())
        );

        assertThat(result.appCode()).isEqualTo("paipai_readingcompanion");
        assertThat(result.notificationUuid()).isEqualTo("notify-1");
        assertThat(result.duplicate()).isTrue();
    }

    private SysAppStoreNotificationEntity existing() {
        SysAppStoreNotificationEntity entity = new SysAppStoreNotificationEntity();
        entity.setAppCode("paipai_readingcompanion");
        entity.setNotificationUuid("notify-1");
        entity.setNotificationType("DID_RENEW");
        entity.setSubtype(null);
        entity.setVerificationStatus("verified");
        entity.setProcessingStatus("reconciled");
        entity.setReceivedAt(OffsetDateTime.parse("2026-04-16T00:00:00Z"));
        return entity;
    }
}
