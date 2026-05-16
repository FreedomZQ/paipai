package com.apphub.backend.apps.reading.powersync;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.apps.reading.domain.entity.ReadingChildProfileEntity;
import com.apphub.backend.apps.reading.domain.entity.ReadingReviewCardEntity;
import com.apphub.backend.apps.reading.domain.entity.ReadingReviewEventV2Entity;
import com.apphub.backend.apps.reading.domain.entity.ReadingUsageSessionV2Entity;
import com.apphub.backend.apps.reading.domain.entity.ReadingUserPreferenceEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingChildProfileMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingReviewCardMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingReviewEventV2Mapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingUsageSessionV2Mapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingUserPreferenceMapper;
import com.apphub.backend.sys.powersync.model.PowerSyncChangeItem;
import com.apphub.backend.sys.powersync.model.PowerSyncUploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadingPowerSyncAdapterTest {

    @Mock
    private ReadingChildProfileMapper childProfileMapper;
    @Mock
    private ReadingReviewCardMapper reviewCardMapper;
    @Mock
    private ReadingReviewEventV2Mapper reviewEventV2Mapper;
    @Mock
    private ReadingUsageSessionV2Mapper usageSessionV2Mapper;
    @Mock
    private ReadingUserPreferenceMapper userPreferenceMapper;
    @Mock
    private ReadingAppModule readingAppModule;

    private ReadingPowerSyncPayloadConverter payloadMapper;
    private ReadingPowerSyncValidator validator;
    private ReadingPowerSyncAdapter adapter;

    @BeforeEach
    void setUp() {
        payloadMapper = new ReadingPowerSyncPayloadConverter();
        ReadingCompatService.AccountEntitlementView entitlement = new ReadingCompatService.AccountEntitlementView(
            "family_multi_child_lifetime",
            "Family",
            "family_multi_child",
            10,
            10,
            3,
            1,
            2,
            1,
            true,
            false,
            true,
            null,
            true,
            true,
            "per_child",
            "family",
            12,
            true,
            false,
            true,
            "backend_sys_billing",
            Map.<String, Object>of("allowed", true)
        );
        lenient().when(childProfileMapper.countActiveByUser(101L)).thenReturn(1);
        ReadingCompatService compatService = org.mockito.Mockito.mock(ReadingCompatService.class);
        lenient().when(compatService.accountState(eq(101L), any())).thenReturn(new ReadingCompatService.AccountStateView(
            "acc-1",
            "apple",
            entitlement,
            new ReadingCompatService.DailyQuotaView("2026-04-21", 10, 0, 10, 600, 0, 600)
        ));
        validator = new ReadingPowerSyncValidator(compatService, childProfileMapper, reviewCardMapper);
        adapter = new ReadingPowerSyncAdapter(readingAppModule, childProfileMapper, reviewCardMapper, reviewEventV2Mapper, usageSessionV2Mapper, userPreferenceMapper, payloadMapper, validator);
    }

    @Test
    void shouldExposeTemplateEntitySpecsWithOwnershipMetadata() {
        assertThat(adapter.appModule()).isSameAs(readingAppModule);
        assertThat(adapter.entities())
            .extracting(com.apphub.backend.apps.common.AppPowerSyncAdapter.SyncEntitySpec::entityType)
            .containsExactly("child_profile", "review_card", "review_event", "usage_session", "user_preference");
        assertThat(adapter.entities())
            .allSatisfy(entity -> assertThat(entity.ownershipField()).isEqualTo("userId"));
        assertThat(adapter.entities())
            .filteredOn(entity -> entity.entityType().equals("review_event"))
            .singleElement()
            .satisfies(entity -> {
                assertThat(entity.createAllowed()).isTrue();
                assertThat(entity.updateAllowed()).isFalse();
                assertThat(entity.deleteAllowed()).isFalse();
                assertThat(entity.versionPolicy()).isEqualTo("append_only");
            });
    }

    @Test
    void shouldInsertChildProfileWhenWithinLimit() {
        when(childProfileMapper.selectById("child-1")).thenReturn(null);

        PowerSyncUploadResult result = adapter.applyBatch(101L, "install-1", List.of(
            new PowerSyncChangeItem("child_profile", "upsert", "child-1", "2026-04-21T00:00:00Z", Map.of(
                "id", "child-1",
                "nickname", "Mia",
                "ageBand", "5_6",
                "learningTrackCode", "zh_to_en"
            ))
        ));

        assertThat(result.accepted()).hasSize(1);
        assertThat(result.rejected()).isEmpty();
        ArgumentCaptor<ReadingChildProfileEntity> captor = ArgumentCaptor.forClass(ReadingChildProfileEntity.class);
        verify(childProfileMapper).insert((ReadingChildProfileEntity) captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("Mia");
        assertThat(captor.getValue().getUserId()).isEqualTo(101L);
    }

    @Test
    void shouldRejectChildCreateWhenLimitExceeded() {
        when(childProfileMapper.selectById("child-2")).thenReturn(null);
        when(childProfileMapper.countActiveByUser(101L)).thenReturn(3);

        PowerSyncUploadResult result = adapter.applyBatch(101L, "install-1", List.of(
            new PowerSyncChangeItem("child_profile", "upsert", "child-2", "2026-04-21T00:00:00Z", Map.of(
                "id", "child-2",
                "nickname", "Leo"
            ))
        ));

        assertThat(result.accepted()).isEmpty();
        assertThat(result.rejected()).hasSize(1);
        assertThat(result.rejected().get(0).reasonCode()).isEqualTo("CHILD_LIMIT_EXCEEDED");
        verify(childProfileMapper, never()).insert(org.mockito.ArgumentMatchers.<ReadingChildProfileEntity>any());
    }

    @Test
    void shouldBeIdempotentForExistingReviewEvent() {
        ReadingReviewEventV2Entity existing = new ReadingReviewEventV2Entity();
        existing.setId("evt-1");
        existing.setUserId(101L);
        when(reviewEventV2Mapper.selectById("evt-1")).thenReturn(existing);

        PowerSyncUploadResult result = adapter.applyBatch(101L, "install-1", List.of(
            new PowerSyncChangeItem("review_event", "upsert", "evt-1", "2026-04-21T00:00:00Z", Map.of(
                "id", "evt-1",
                "childId", "child-1",
                "cardId", "card-1"
            ))
        ));

        assertThat(result.accepted()).hasSize(1);
        assertThat(result.rejected()).isEmpty();
        verify(reviewEventV2Mapper, never()).insert(org.mockito.ArgumentMatchers.<ReadingReviewEventV2Entity>any());
    }

    @Test
    void shouldSoftDeleteReviewCard() {
        ReadingReviewCardEntity card = new ReadingReviewCardEntity();
        card.setId("card-1");
        card.setUserId(101L);
        card.setRecordVersion(1);
        when(reviewCardMapper.selectById("card-1")).thenReturn(card);

        PowerSyncUploadResult result = adapter.applyBatch(101L, "install-1", List.of(
            new PowerSyncChangeItem("review_card", "delete", "card-1", "2026-04-21T00:00:00Z", Map.of())
        ));

        assertThat(result.accepted()).hasSize(1);
        ArgumentCaptor<ReadingReviewCardEntity> captor = ArgumentCaptor.forClass(ReadingReviewCardEntity.class);
        verify(reviewCardMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCardStatus()).isEqualTo("deleted");
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void shouldPersistReviewCardLanguageCodesFromPowerSyncPayload() {
        when(reviewCardMapper.selectById("card-lang")).thenReturn(null);
        when(reviewCardMapper.countActiveByUser(101L)).thenReturn(0);
        ReadingChildProfileEntity child = new ReadingChildProfileEntity();
        child.setId("child-1");
        child.setUserId(101L);
        when(childProfileMapper.selectActiveByIdAndUser("child-1", 101L)).thenReturn(child);

        PowerSyncUploadResult result = adapter.applyBatch(101L, "install-1", List.of(
            new PowerSyncChangeItem("review_card", "upsert", "card-lang", "2026-04-21T00:00:00Z", Map.of(
                "id", "card-lang",
                "childId", "child-1",
                "sourceText", "Good night.",
                "translatedText", "晚安。",
                "sourceLanguageCode", "en",
                "targetLanguageCode", "zh-Hans"
            ))
        ));

        assertThat(result.accepted()).hasSize(1);
        assertThat(result.rejected()).isEmpty();
        ArgumentCaptor<ReadingReviewCardEntity> captor = ArgumentCaptor.forClass(ReadingReviewCardEntity.class);
        verify(reviewCardMapper).insert(captor.capture());
        assertThat(captor.getValue().getSourceLanguageCode()).isEqualTo("en");
        assertThat(captor.getValue().getTargetLanguageCode()).isEqualTo("zh-Hans");
    }

    @Test
    void shouldUpsertUserPreference() {
        when(userPreferenceMapper.selectById(101L)).thenReturn(null);

        PowerSyncUploadResult result = adapter.applyBatch(101L, "install-1", List.of(
            new PowerSyncChangeItem("user_preference", "upsert", "pref-1", "2026-04-21T00:00:00Z", Map.of(
                "uiLocale", "en",
                "sourceLanguageCode", "en",
                "targetLanguageCode", "zh-Hans",
                "cloudSyncEnabled", true
            ))
        ));

        assertThat(result.accepted()).hasSize(1);
        ArgumentCaptor<ReadingUserPreferenceEntity> captor = ArgumentCaptor.forClass(ReadingUserPreferenceEntity.class);
        verify(userPreferenceMapper).insert((ReadingUserPreferenceEntity) captor.capture());
        assertThat(captor.getValue().getCloudSyncEnabled()).isTrue();
    }
}
