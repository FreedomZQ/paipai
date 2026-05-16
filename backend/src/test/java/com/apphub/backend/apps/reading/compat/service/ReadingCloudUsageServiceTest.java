package com.apphub.backend.apps.reading.compat.service;

import com.apphub.backend.apps.reading.domain.entity.ReadingCloudServiceCreditGrantEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingCloudServiceCreditGrantMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

class ReadingCloudUsageServiceTest {
    @Test
    void creditGrantUsePriorityPrefersEarliestExpiration() {
        OffsetDateTime base = OffsetDateTime.of(2026, 5, 4, 0, 0, 0, 0, ZoneOffset.UTC);
        List<ReadingCloudServiceCreditGrantEntity> sorted = List.of(
            grant(1L, "gift", base.plusDays(3)),
            grant(2L, "paid", base.plusDays(1)),
            grant(3L, "gift", base.plusDays(2))
        ).stream().sorted(ReadingCloudUsageService.creditGrantUsePriorityComparator()).toList();

        assertThat(sorted).extracting(ReadingCloudServiceCreditGrantEntity::getId).containsExactly(2L, 3L, 1L);
    }

    @Test
    void creditGrantUsePriorityPrefersGiftWhenExpirationMatches() {
        OffsetDateTime expiresAt = OffsetDateTime.of(2026, 5, 10, 0, 0, 0, 0, ZoneOffset.UTC);
        List<ReadingCloudServiceCreditGrantEntity> sorted = List.of(
            grant(1L, "paid", expiresAt),
            grant(2L, "gift", expiresAt),
            grant(3L, "paid", expiresAt)
        ).stream().sorted(ReadingCloudUsageService.creditGrantUsePriorityComparator()).toList();

        assertThat(sorted).extracting(ReadingCloudServiceCreditGrantEntity::getId).containsExactly(2L, 1L, 3L);
    }

    @Test
    void creditGrantUsePriorityCoversMixedExpirationAndGrantType() {
        OffsetDateTime base = OffsetDateTime.of(2026, 5, 4, 0, 0, 0, 0, ZoneOffset.UTC);
        List<ReadingCloudServiceCreditGrantEntity> sorted = List.of(
            grant(1L, "gift", base.plusDays(5)),
            grant(2L, "paid", base.plusDays(2)),
            grant(3L, "gift", base.plusDays(2)),
            grant(4L, "paid", base.plusDays(1)),
            grant(5L, "gift", base.plusDays(1))
        ).stream().sorted(ReadingCloudUsageService.creditGrantUsePriorityComparator()).toList();

        assertThat(sorted).extracting(ReadingCloudServiceCreditGrantEntity::getId).containsExactly(5L, 4L, 3L, 2L, 1L);
    }

    @Test
    void recentCreditEntitlementsShouldKeepExpiredHistoryReturnedByMapper() {
        ReadingCloudServiceCreditGrantMapper mapper = mock(ReadingCloudServiceCreditGrantMapper.class);
        ReadingCloudUsageService service = new ReadingCloudUsageService(null, null, mapper, null);
        OffsetDateTime now = OffsetDateTime.of(2026, 5, 5, 0, 0, 0, 0, ZoneOffset.UTC);
        ReadingCloudServiceCreditGrantEntity expired = grant(9L, "gift", now.minusDays(2));
        expired.setAppCode("paipai_readingcompanion");
        expired.setUserId(42L);
        expired.setServiceType(ReadingCloudUsageService.LOCAL_SPEECH);
        expired.setSourceType("admin");
        expired.setTotalCount(6);
        expired.setUsedCount(1);
        expired.setCreatedAt(now.minusDays(10));
        given(mapper.selectRecentByUser(eq("paipai_readingcompanion"), eq(42L), eq(ReadingCloudUsageService.LOCAL_SPEECH), any(), any()))
            .willReturn(List.of(expired));

        List<ReadingCloudUsageService.ActiveEntitlementView> records = service.recentCreditEntitlements(42L, ReadingCloudUsageService.LOCAL_SPEECH, 60);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).id()).isEqualTo("9");
        assertThat(records.get(0).remainingCount()).isEqualTo(0);
    }

    private ReadingCloudServiceCreditGrantEntity grant(Long id, String grantType, OffsetDateTime expiresAt) {
        ReadingCloudServiceCreditGrantEntity entity = new ReadingCloudServiceCreditGrantEntity();
        entity.setId(id);
        entity.setGrantType(grantType);
        entity.setExpiresAt(expiresAt);
        return entity;
    }
}
