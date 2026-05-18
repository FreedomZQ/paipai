package com.apphub.backend.apps.reading.privacy;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingPrivacyRetentionJobTest {

    @Test
    void cleanupShouldRunAllRetentionStatements() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1, 2, 3, 4, 5);
        ReadingPrivacyRetentionJob job = new ReadingPrivacyRetentionJob(jdbcTemplate);

        ReadingPrivacyRetentionJob.CleanupReceipt receipt = job.cleanupNow(OffsetDateTime.parse("2026-05-17T00:00:00Z"));

        assertThat(receipt.expiredReservations()).isEqualTo(1);
        assertThat(receipt.diagnosticsDeleted()).isEqualTo(2);
        assertThat(receipt.privacyEventsDeleted()).isEqualTo(3);
        assertThat(receipt.purchaseRetentionDeleted()).isEqualTo(4);
        assertThat(receipt.tombstonesDeleted()).isEqualTo(5);
        verify(jdbcTemplate, times(5)).update(anyString(), any(Object[].class));
    }
}
