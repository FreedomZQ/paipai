package com.apphub.backend.apps.reading.powersync;

import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.apps.reading.domain.entity.ReadingChildProfileEntity;
import com.apphub.backend.apps.reading.domain.entity.ReadingReviewCardEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingChildProfileMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingReviewCardMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ReadingPowerSyncValidator {
    private final ReadingCompatService readingCompatService;
    private final ReadingChildProfileMapper childProfileMapper;
    private final ReadingReviewCardMapper reviewCardMapper;

    public ReadingPowerSyncValidator(
        ReadingCompatService readingCompatService,
        ReadingChildProfileMapper childProfileMapper,
        ReadingReviewCardMapper reviewCardMapper
    ) {
        this.readingCompatService = readingCompatService;
        this.childProfileMapper = childProfileMapper;
        this.reviewCardMapper = reviewCardMapper;
    }

    public void requireCloudSyncEnabled(Long userId) {
        ReadingCompatService.AccountStateView state = readingCompatService.accountState(userId, "powersync");
        if (state == null || state.entitlement() == null || !Boolean.TRUE.equals(state.entitlement().cloudSyncEnabled())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "POWERSYNC_DISABLED");
        }
    }

    public void validateChildCreateAllowed(Long userId) {
        ReadingCompatService.AccountStateView state = readingCompatService.accountState(userId, "powersync");
        int childLimit = state == null || state.entitlement() == null || state.entitlement().childLimit() == null
            ? 0
            : state.entitlement().childLimit();
        int childCount = childProfileMapper.countActiveByUser(userId);
        if (childCount >= childLimit) {
            throw new RejectedChangeException("CHILD_LIMIT_EXCEEDED", "Current entitlement only allows " + childLimit + " children.");
        }
    }

    public void validateReviewCardCreateAllowed(Long userId) {
        ReadingCompatService.AccountStateView state = readingCompatService.accountState(userId, "powersync");
        int localCardLimit = state == null || state.entitlement() == null || state.entitlement().localCardLimit() == null
            ? 0
            : state.entitlement().localCardLimit();
        int activeCardCount = reviewCardMapper.countActiveByUser(userId);
        if (activeCardCount >= localCardLimit) {
            throw new RejectedChangeException("LOCAL_CARD_LIMIT_REACHED", "Current entitlement only allows " + localCardLimit + " active review cards.");
        }
    }

    public ReadingChildProfileEntity requireOwnedChild(Long userId, String childId) {
        ReadingChildProfileEntity entity = childId == null ? null : childProfileMapper.selectById(childId);
        if (entity == null || !userId.equals(entity.getUserId())) {
            throw new RejectedChangeException("CHILD_NOT_FOUND", "Child profile does not belong to current user.");
        }
        return entity;
    }

    public ReadingChildProfileEntity requireActiveChild(Long userId, String childId) {
        ReadingChildProfileEntity entity = childId == null ? null : childProfileMapper.selectActiveByIdAndUser(childId, userId);
        if (entity == null) {
            throw new RejectedChangeException("CHILD_NOT_FOUND", "Child profile not found or not active.");
        }
        return entity;
    }

    public ReadingReviewCardEntity requireOwnedCard(Long userId, String cardId) {
        ReadingReviewCardEntity entity = cardId == null ? null : reviewCardMapper.selectById(cardId);
        if (entity == null || !userId.equals(entity.getUserId())) {
            throw new RejectedChangeException("CARD_NOT_FOUND", "Review card does not belong to current user.");
        }
        return entity;
    }

    public ReadingReviewCardEntity requireActiveCard(Long userId, String cardId) {
        ReadingReviewCardEntity entity = cardId == null ? null : reviewCardMapper.selectActiveByIdAndUser(cardId, userId);
        if (entity == null) {
            throw new RejectedChangeException("CARD_NOT_FOUND", "Review card not found or not active.");
        }
        return entity;
    }

    public static class RejectedChangeException extends RuntimeException {
        private final String reasonCode;

        public RejectedChangeException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }

        public String reasonCode() {
            return reasonCode;
        }
    }
}
