package com.apphub.backend.sys.auth.service;

import com.apphub.backend.sys.auth.entity.SysEmailVerificationTicketEntity;
import com.apphub.backend.sys.auth.service.crud.SysEmailVerificationTicketCrudService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SysEmailVerificationServiceTest {

    @Test
    void requestCodeShouldReturnDebugCodeInNonProd() {
        SysEmailVerificationTicketCrudService ticketCrudService = mock(SysEmailVerificationTicketCrudService.class);
        doAnswer(invocation -> {
            SysEmailVerificationTicketEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return true;
        }).when(ticketCrudService).save(any(SysEmailVerificationTicketEntity.class));

        SysEmailVerificationService service = new SysEmailVerificationService(ticketCrudService, new SessionTokenHashService(), new ObjectMapper());
        ReflectionTestUtils.setField(service, "backendEnvironment", "dev");

        var receipt = service.requestCode("paipai_readingcompanion", "parent@example.com", "login", null, null);
        assertThat(receipt.maskedEmail()).contains("***");
        assertThat(receipt.debugCode()).hasSize(6);
        assertThat(receipt.deliveryStatus()).isEqualTo("logged_only");
    }

    @Test
    void consumeCodeShouldMarkTicketConsumed() {
        SessionTokenHashService hashService = new SessionTokenHashService();
        SysEmailVerificationTicketCrudService ticketCrudService = mock(SysEmailVerificationTicketCrudService.class);
        SysEmailVerificationTicketEntity entity = new SysEmailVerificationTicketEntity();
        entity.setId(11L);
        entity.setAppCode("paipai_readingcompanion");
        entity.setEmail(hashService.hash("email-verification:parent@example.com"));
        entity.setSceneCode("login");
        entity.setCodeHash(hashService.hash("123456"));
        entity.setStatus("pending");
        entity.setAttemptCount(0);
        entity.setMaxAttemptCount(6);
        entity.setExpiresAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(5));
        when(ticketCrudService.selectLatest("paipai_readingcompanion", hashService.hash("email-verification:parent@example.com"), "login")).thenReturn(entity);

        SysEmailVerificationService service = new SysEmailVerificationService(ticketCrudService, hashService, new ObjectMapper());
        ReflectionTestUtils.setField(service, "backendEnvironment", "dev");
        var consumed = service.consumeCode("paipai_readingcompanion", "parent@example.com", "login", "123456");
        assertThat(consumed.email()).contains("***");
        assertThat(entity.getStatus()).isEqualTo("consumed");
        assertThat(entity.getConsumedAt()).isNotNull();
    }

    @Test
    void consumeCodeShouldRejectInvalidCode() {
        SessionTokenHashService hashService = new SessionTokenHashService();
        SysEmailVerificationTicketCrudService ticketCrudService = mock(SysEmailVerificationTicketCrudService.class);
        SysEmailVerificationTicketEntity entity = new SysEmailVerificationTicketEntity();
        entity.setId(22L);
        entity.setAppCode("paipai_readingcompanion");
        entity.setEmail(hashService.hash("email-verification:parent@example.com"));
        entity.setSceneCode("login");
        entity.setCodeHash(hashService.hash("123456"));
        entity.setStatus("pending");
        entity.setAttemptCount(0);
        entity.setMaxAttemptCount(6);
        entity.setExpiresAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(5));
        when(ticketCrudService.selectLatest("paipai_readingcompanion", hashService.hash("email-verification:parent@example.com"), "login")).thenReturn(entity);

        SysEmailVerificationService service = new SysEmailVerificationService(ticketCrudService, hashService, new ObjectMapper());
        ReflectionTestUtils.setField(service, "backendEnvironment", "dev");
        assertThatThrownBy(() -> service.consumeCode("paipai_readingcompanion", "parent@example.com", "login", "000000"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(entity.getAttemptCount()).isEqualTo(1);
    }
}
