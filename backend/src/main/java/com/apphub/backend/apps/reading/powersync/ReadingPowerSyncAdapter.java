package com.apphub.backend.apps.reading.powersync;

import com.apphub.backend.apps.common.AppPowerSyncAdapter;
import com.apphub.backend.apps.reading.ReadingAppModule;
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
import com.apphub.backend.sys.powersync.model.PowerSyncAcceptedItem;
import com.apphub.backend.sys.powersync.model.PowerSyncChangeItem;
import com.apphub.backend.sys.powersync.model.PowerSyncRejectedItem;
import com.apphub.backend.sys.powersync.model.PowerSyncUploadResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ReadingPowerSyncAdapter implements AppPowerSyncAdapter {
    private final ReadingAppModule appModule;
    private final ReadingChildProfileMapper childProfileMapper;
    private final ReadingReviewCardMapper reviewCardMapper;
    private final ReadingReviewEventV2Mapper reviewEventV2Mapper;
    private final ReadingUsageSessionV2Mapper usageSessionV2Mapper;
    private final ReadingUserPreferenceMapper userPreferenceMapper;
    private final ReadingPowerSyncPayloadConverter payloadMapper;
    private final ReadingPowerSyncValidator validator;

    public ReadingPowerSyncAdapter(
        ReadingAppModule appModule,
        ReadingChildProfileMapper childProfileMapper,
        ReadingReviewCardMapper reviewCardMapper,
        ReadingReviewEventV2Mapper reviewEventV2Mapper,
        ReadingUsageSessionV2Mapper usageSessionV2Mapper,
        ReadingUserPreferenceMapper userPreferenceMapper,
        ReadingPowerSyncPayloadConverter payloadMapper,
        ReadingPowerSyncValidator validator
    ) {
        this.appModule = appModule;
        this.childProfileMapper = childProfileMapper;
        this.reviewCardMapper = reviewCardMapper;
        this.reviewEventV2Mapper = reviewEventV2Mapper;
        this.usageSessionV2Mapper = usageSessionV2Mapper;
        this.userPreferenceMapper = userPreferenceMapper;
        this.payloadMapper = payloadMapper;
        this.validator = validator;
    }

    @Override
    public ReadingAppModule appModule() {
        return appModule;
    }

    @Override
    public List<SyncEntitySpec> entities() {
        return List.of(
            new SyncEntitySpec(ReadingPowerSyncEntityType.CHILD_PROFILE.code(), "userId", true, true, true, "cloudSyncEnabled", "recordVersion"),
            new SyncEntitySpec(ReadingPowerSyncEntityType.REVIEW_CARD.code(), "userId", true, true, true, "cloudSyncEnabled", "recordVersion"),
            new SyncEntitySpec(ReadingPowerSyncEntityType.REVIEW_EVENT.code(), "userId", true, false, false, "cloudSyncEnabled", "append_only"),
            new SyncEntitySpec(ReadingPowerSyncEntityType.USAGE_SESSION.code(), "userId", true, true, true, "cloudSyncEnabled", "updatedAt+tombstone"),
            new SyncEntitySpec(ReadingPowerSyncEntityType.USER_PREFERENCE.code(), "userId", true, true, false, "cloudSyncEnabled", "recordVersion")
        );
    }

    @Override
    public void validateSyncAccess(Long userId) {
        validator.requireCloudSyncEnabled(userId);
    }

    @Override
    @Transactional
    public PowerSyncUploadResult applyBatch(Long userId, String installationId, List<PowerSyncChangeItem> changes) {
        List<PowerSyncAcceptedItem> accepted = new ArrayList<>();
        List<PowerSyncRejectedItem> rejected = new ArrayList<>();
        if (changes == null) {
            return new PowerSyncUploadResult(accepted, rejected);
        }
        for (PowerSyncChangeItem change : changes) {
            String entityTypeCode = safeEntityType(change);
            String entityId = safeEntityId(change);
            try {
                ReadingPowerSyncEntityType entityType = ReadingPowerSyncEntityType.fromCode(entityTypeCode);
                if (entityType == null) {
                    throw new ReadingPowerSyncValidator.RejectedChangeException("POWERSYNC_ENTITY_TYPE_UNSUPPORTED", "Unsupported entity type: " + entityTypeCode);
                }
                String operation = normalizeOperation(change == null ? null : change.operation());
                switch (entityType) {
                    case CHILD_PROFILE -> applyChildProfile(userId, installationId, operation, change);
                    case REVIEW_CARD -> applyReviewCard(userId, installationId, operation, change);
                    case REVIEW_EVENT -> applyReviewEvent(userId, installationId, operation, change);
                    case USAGE_SESSION -> applyUsageSession(userId, installationId, operation, change);
                    case USER_PREFERENCE -> applyUserPreference(userId, installationId, operation, change);
                }
                accepted.add(new PowerSyncAcceptedItem(entityType.code(), entityId, OffsetDateTime.now(ZoneOffset.UTC).toString()));
            } catch (ReadingPowerSyncValidator.RejectedChangeException exception) {
                rejected.add(new PowerSyncRejectedItem(entityTypeCode, entityId, exception.reasonCode(), exception.getMessage()));
            } catch (Exception exception) {
                String reasonCode = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "POWERSYNC_APPLY_FAILED"
                    : exception.getMessage().trim();
                rejected.add(new PowerSyncRejectedItem(entityTypeCode, entityId, reasonCode, exception.getMessage() == null ? "PowerSync apply failed." : exception.getMessage()));
            }
        }
        return new PowerSyncUploadResult(accepted, rejected);
    }

    private void applyChildProfile(Long userId, String installationId, String operation, PowerSyncChangeItem change) {
        String entityId = safeEntityId(change);
        if ("delete".equals(operation)) {
            softDeleteChildProfile(userId, installationId, entityId);
            return;
        }
        OffsetDateTime now = now();
        ReadingChildProfileEntity existing = entityId == null ? null : childProfileMapper.selectById(entityId);
        if (existing != null && !userId.equals(existing.getUserId())) {
            throw new ReadingPowerSyncValidator.RejectedChangeException("CHILD_NOT_FOUND", "Child profile does not belong to current user.");
        }
        boolean createLike = existing == null || isDeleted(existing.getDeletedAt(), existing.getProfileStatus());
        if (createLike) {
            validator.validateChildCreateAllowed(userId);
        }
        ReadingChildProfileEntity entity = payloadMapper.toChildProfile(change.payload(), entityId, userId, installationId, now);
        if (existing != null) {
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setRecordVersion(nextVersion(existing.getRecordVersion(), entity.getRecordVersion()));
            childProfileMapper.updateById(entity);
        } else {
            childProfileMapper.insert(entity);
        }
    }

    private void softDeleteChildProfile(Long userId, String installationId, String entityId) {
        ReadingChildProfileEntity existing = validator.requireOwnedChild(userId, entityId);
        OffsetDateTime now = now();
        existing.setProfileStatus("deleted");
        if (existing.getDeletedAt() == null) {
            existing.setDeletedAt(now);
        }
        existing.setLastModifiedByInstallationId(installationId);
        existing.setRecordVersion(nextVersion(existing.getRecordVersion(), null));
        existing.setUpdatedAt(now);
        childProfileMapper.updateById(existing);
    }

    private void applyReviewCard(Long userId, String installationId, String operation, PowerSyncChangeItem change) {
        String entityId = safeEntityId(change);
        if ("delete".equals(operation)) {
            softDeleteReviewCard(userId, installationId, entityId);
            return;
        }
        OffsetDateTime now = now();
        ReadingReviewCardEntity existing = entityId == null ? null : reviewCardMapper.selectById(entityId);
        if (existing != null && !userId.equals(existing.getUserId())) {
            throw new ReadingPowerSyncValidator.RejectedChangeException("CARD_NOT_FOUND", "Review card does not belong to current user.");
        }
        ReadingReviewCardEntity entity = payloadMapper.toReviewCard(change.payload(), entityId, userId, installationId, now);
        validator.requireActiveChild(userId, entity.getChildId());
        if (existing == null) {
            validator.validateReviewCardCreateAllowed(userId);
        }
        if (existing != null) {
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setRecordVersion(nextVersion(existing.getRecordVersion(), entity.getRecordVersion()));
            reviewCardMapper.updateById(entity);
        } else {
            reviewCardMapper.insert(entity);
        }
    }

    private void softDeleteReviewCard(Long userId, String installationId, String entityId) {
        ReadingReviewCardEntity existing = validator.requireOwnedCard(userId, entityId);
        OffsetDateTime now = now();
        existing.setCardStatus("deleted");
        if (existing.getDeletedAt() == null) {
            existing.setDeletedAt(now);
        }
        existing.setLastModifiedByInstallationId(installationId);
        existing.setRecordVersion(nextVersion(existing.getRecordVersion(), null));
        existing.setUpdatedAt(now);
        reviewCardMapper.updateById(existing);
    }

    private void applyReviewEvent(Long userId, String installationId, String operation, PowerSyncChangeItem change) {
        if ("delete".equals(operation)) {
            throw new ReadingPowerSyncValidator.RejectedChangeException("EVENT_DELETE_NOT_SUPPORTED", "Delete review event is not supported.");
        }
        OffsetDateTime now = now();
        String entityId = safeEntityId(change);
        ReadingReviewEventV2Entity existing = entityId == null ? null : reviewEventV2Mapper.selectById(entityId);
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) {
                throw new ReadingPowerSyncValidator.RejectedChangeException("CARD_NOT_FOUND", "Review event does not belong to current user.");
            }
            return;
        }
        ReadingReviewEventV2Entity entity = payloadMapper.toReviewEvent(change.payload(), entityId, userId, installationId, now);
        validator.requireActiveChild(userId, entity.getChildId());
        validator.requireActiveCard(userId, entity.getCardId());
        reviewEventV2Mapper.insert(entity);
    }

    private void applyUsageSession(Long userId, String installationId, String operation, PowerSyncChangeItem change) {
        String entityId = safeEntityId(change);
        OffsetDateTime now = now();
        if ("delete".equals(operation)) {
            ReadingUsageSessionV2Entity existing = requireOwnedUsageSession(userId, entityId);
            if (existing.getDeletedAt() == null) {
                existing.setDeletedAt(now);
            }
            existing.setLastModifiedByInstallationId(installationId);
            existing.setUpdatedAt(now);
            usageSessionV2Mapper.updateById(existing);
            return;
        }
        ReadingUsageSessionV2Entity existing = entityId == null ? null : usageSessionV2Mapper.selectById(entityId);
        if (existing != null && !userId.equals(existing.getUserId())) {
            throw new ReadingPowerSyncValidator.RejectedChangeException("CHILD_NOT_FOUND", "Usage session does not belong to current user.");
        }
        ReadingUsageSessionV2Entity entity = payloadMapper.toUsageSession(change.payload(), entityId, userId, installationId, now);
        validator.requireActiveChild(userId, entity.getChildId());
        if (existing != null) {
            entity.setCreatedAt(existing.getCreatedAt());
            usageSessionV2Mapper.updateById(entity);
        } else {
            usageSessionV2Mapper.insert(entity);
        }
    }

    private void applyUserPreference(Long userId, String installationId, String operation, PowerSyncChangeItem change) {
        if ("delete".equals(operation)) {
            throw new ReadingPowerSyncValidator.RejectedChangeException("PREFERENCE_DELETE_NOT_SUPPORTED", "Delete user preference is not supported.");
        }
        OffsetDateTime now = now();
        ReadingUserPreferenceEntity existing = userPreferenceMapper.selectById(userId);
        ReadingUserPreferenceEntity entity = payloadMapper.toUserPreference(change.payload(), userId, installationId, now);
        if (existing != null) {
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setRecordVersion(nextVersion(existing.getRecordVersion(), entity.getRecordVersion()));
            userPreferenceMapper.updateById(entity);
        } else {
            userPreferenceMapper.insert(entity);
        }
    }

    private ReadingUsageSessionV2Entity requireOwnedUsageSession(Long userId, String entityId) {
        ReadingUsageSessionV2Entity entity = entityId == null ? null : usageSessionV2Mapper.selectById(entityId);
        if (entity == null || !userId.equals(entity.getUserId())) {
            throw new ReadingPowerSyncValidator.RejectedChangeException("CHILD_NOT_FOUND", "Usage session does not belong to current user.");
        }
        return entity;
    }

    private int nextVersion(Integer existing, Integer incoming) {
        int base = existing == null ? 0 : existing;
        int next = base + 1;
        return incoming == null ? next : Math.max(next, incoming);
    }

    private boolean isDeleted(OffsetDateTime deletedAt, String status) {
        return deletedAt != null || (status != null && "deleted".equalsIgnoreCase(status));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String normalizeOperation(String raw) {
        if (raw == null || raw.isBlank()) {
            return "upsert";
        }
        return raw.trim().toLowerCase();
    }

    private String safeEntityType(PowerSyncChangeItem change) {
        if (change == null || change.entityType() == null || change.entityType().isBlank()) {
            return "unknown";
        }
        return change.entityType().trim();
    }

    private String safeEntityId(PowerSyncChangeItem change) {
        if (change == null) {
            return "unknown";
        }
        if (change.entityId() != null && !change.entityId().isBlank()) {
            return change.entityId().trim();
        }
        Map<String, Object> payload = change.payload();
        Object payloadId = payload == null ? null : payload.get("id");
        return payloadId == null ? "unknown" : String.valueOf(payloadId);
    }
}
