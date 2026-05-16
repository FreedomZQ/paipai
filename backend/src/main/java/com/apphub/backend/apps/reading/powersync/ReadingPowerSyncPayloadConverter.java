package com.apphub.backend.apps.reading.powersync;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.domain.entity.ReadingChildProfileEntity;
import com.apphub.backend.apps.reading.domain.entity.ReadingReviewCardEntity;
import com.apphub.backend.apps.reading.domain.entity.ReadingReviewEventV2Entity;
import com.apphub.backend.apps.reading.domain.entity.ReadingUsageSessionV2Entity;
import com.apphub.backend.apps.reading.domain.entity.ReadingUserPreferenceEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

@Component
public class ReadingPowerSyncPayloadConverter {
    private static final String APP_CODE = ReadingAppModule.APP_CODE;

    public ReadingChildProfileEntity toChildProfile(
        Map<String, Object> payload,
        String entityId,
        Long userId,
        String installationId,
        OffsetDateTime now
    ) {
        ReadingChildProfileEntity entity = new ReadingChildProfileEntity();
        entity.setId(firstNonBlank(stringValue(payload, "id"), entityId));
        entity.setAppCode(APP_CODE);
        entity.setUserId(userId);
        entity.setNickname(defaultIfBlank(stringValue(payload, "nickname"), "未命名孩子"));
        entity.setAgeBand(defaultIfBlank(stringValue(payload, "ageBand"), "5_7"));
        entity.setLearningTrackCode(defaultIfBlank(stringValue(payload, "learningTrackCode"), "zh_to_en"));
        entity.setAvatarEmoji(defaultIfBlank(stringValue(payload, "avatarEmoji"), "🧸"));
        entity.setProfileStatus(defaultIfBlank(stringValue(payload, "profileStatus"), "active"));
        entity.setDeletedAt(parseTime(payload.get("deletedAt")));
        entity.setLastModifiedByInstallationId(installationId);
        entity.setRecordVersion(intValue(payload.get("recordVersion"), 1));
        entity.setCreatedAt(parseTime(payload.get("createdAt")) == null ? now : parseTime(payload.get("createdAt")));
        entity.setUpdatedAt(parseTime(payload.get("updatedAt")) == null ? now : parseTime(payload.get("updatedAt")));
        return entity;
    }

    public ReadingReviewCardEntity toReviewCard(
        Map<String, Object> payload,
        String entityId,
        Long userId,
        String installationId,
        OffsetDateTime now
    ) {
        String sourceText = blankToNull(stringValue(payload, "sourceText"));
        String textPreview = firstNonBlank(blankToNull(stringValue(payload, "textPreview")), sourceText, "已保存句卡");
        String translatedText = blankToNull(stringValue(payload, "translatedText"));
        String supportHint = firstNonBlank(blankToNull(stringValue(payload, "supportHint")), translatedText, "");
        OffsetDateTime deletedAt = parseTime(payload.get("deletedAt"));
        ReadingReviewCardEntity entity = new ReadingReviewCardEntity();
        entity.setId(firstNonBlank(stringValue(payload, "id"), entityId));
        entity.setAppCode(APP_CODE);
        entity.setUserId(userId);
        entity.setChildId(blankToNull(stringValue(payload, "childId")));
        entity.setLearningTrackCode(defaultIfBlank(stringValue(payload, "learningTrackCode"), "zh_to_en"));
        entity.setSourceText(sourceText);
        entity.setTranslatedText(translatedText);
        entity.setSourceLanguageCode(blankToNull(stringValue(payload, "sourceLanguageCode")));
        entity.setTargetLanguageCode(blankToNull(stringValue(payload, "targetLanguageCode")));
        entity.setSourceType(defaultIfBlank(stringValue(payload, "sourceType"), "manual"));
        entity.setContentEncryptionVersion(blankToNull(stringValue(payload, "contentEncryptionVersion")));
        entity.setContentKeyId(blankToNull(stringValue(payload, "contentKeyId")));
        entity.setEncryptedText(firstNonBlank(blankToNull(stringValue(payload, "encryptedText")), encodePreviewSource(firstNonBlank(sourceText, textPreview))));
        entity.setTextPreview(truncate(textPreview, 256));
        entity.setSupportHint(truncate(supportHint, 512));
        entity.setProficiency(intValue(payload.get("proficiency"), 0));
        entity.setNextReviewAt(parseTime(payload.get("nextReviewAt")));
        entity.setSyncEnabled(boolValue(payload.get("syncEnabled"), true));
        entity.setStorageMode(defaultIfBlank(stringValue(payload, "storageMode"), "server_synced"));
        entity.setCardStatus(defaultIfBlank(stringValue(payload, "cardStatus"), deletedAt == null ? "active" : "deleted"));
        entity.setLastReviewedAt(parseTime(payload.get("lastReviewedAt")));
        entity.setDeletedAt(deletedAt);
        entity.setLastModifiedByInstallationId(installationId);
        entity.setRecordVersion(intValue(payload.get("recordVersion"), 1));
        entity.setCreatedAt(parseTime(payload.get("createdAt")) == null ? now : parseTime(payload.get("createdAt")));
        entity.setUpdatedAt(parseTime(payload.get("updatedAt")) == null ? now : parseTime(payload.get("updatedAt")));
        return entity;
    }

    public ReadingReviewEventV2Entity toReviewEvent(
        Map<String, Object> payload,
        String entityId,
        Long userId,
        String installationId,
        OffsetDateTime now
    ) {
        OffsetDateTime eventAt = parseTime(payload.get("eventAt"));
        if (eventAt == null) {
            eventAt = parseTime(payload.get("clientUpdatedAt"));
        }
        if (eventAt == null) {
            eventAt = now;
        }
        ReadingReviewEventV2Entity entity = new ReadingReviewEventV2Entity();
        entity.setId(firstNonBlank(stringValue(payload, "id"), entityId));
        entity.setAppCode(APP_CODE);
        entity.setUserId(userId);
        entity.setChildId(blankToNull(stringValue(payload, "childId")));
        entity.setCardId(blankToNull(stringValue(payload, "cardId")));
        entity.setEventType(defaultIfBlank(stringValue(payload, "eventType"), "review"));
        entity.setResultLevel(defaultIfBlank(stringValue(payload, "resultLevel"), "remembered"));
        entity.setEventAt(eventAt);
        entity.setLastModifiedByInstallationId(installationId);
        entity.setCreatedAt(parseTime(payload.get("createdAt")) == null ? now : parseTime(payload.get("createdAt")));
        entity.setUpdatedAt(parseTime(payload.get("updatedAt")) == null ? now : parseTime(payload.get("updatedAt")));
        return entity;
    }

    public ReadingUsageSessionV2Entity toUsageSession(
        Map<String, Object> payload,
        String entityId,
        Long userId,
        String installationId,
        OffsetDateTime now
    ) {
        OffsetDateTime startedAt = parseTime(payload.get("startedAt"));
        if (startedAt == null) {
            startedAt = now;
        }
        OffsetDateTime endedAt = parseTime(payload.get("endedAt"));
        Integer durationSeconds = intValue(payload.get("durationSeconds"), null);
        if (durationSeconds == null && endedAt != null) {
            durationSeconds = (int) Math.max(0L, Duration.between(startedAt, endedAt).getSeconds());
        }
        ReadingUsageSessionV2Entity entity = new ReadingUsageSessionV2Entity();
        entity.setId(firstNonBlank(stringValue(payload, "id"), entityId));
        entity.setAppCode(APP_CODE);
        entity.setUserId(userId);
        entity.setChildId(blankToNull(stringValue(payload, "childId")));
        entity.setSourcePage(defaultIfBlank(stringValue(payload, "sourcePage"), "unknown"));
        entity.setStartedAt(startedAt);
        entity.setEndedAt(endedAt);
        entity.setDurationSeconds(durationSeconds);
        entity.setClientPlatform(blankToNull(stringValue(payload, "clientPlatform")));
        entity.setDeviceModel(blankToNull(stringValue(payload, "deviceModel")));
        entity.setLastModifiedByInstallationId(installationId);
        entity.setDeletedAt(parseTime(payload.get("deletedAt")));
        entity.setCreatedAt(parseTime(payload.get("createdAt")) == null ? now : parseTime(payload.get("createdAt")));
        entity.setUpdatedAt(parseTime(payload.get("updatedAt")) == null ? now : parseTime(payload.get("updatedAt")));
        return entity;
    }

    public ReadingUserPreferenceEntity toUserPreference(
        Map<String, Object> payload,
        Long userId,
        String installationId,
        OffsetDateTime now
    ) {
        ReadingUserPreferenceEntity entity = new ReadingUserPreferenceEntity();
        entity.setUserId(userId);
        entity.setAppCode(APP_CODE);
        entity.setUiLocale(defaultIfBlank(stringValue(payload, "uiLocale"), "zh-Hans"));
        entity.setSourceLanguageCode(defaultIfBlank(stringValue(payload, "sourceLanguageCode"), "en"));
        entity.setTargetLanguageCode(defaultIfBlank(stringValue(payload, "targetLanguageCode"), "zh-Hans"));
        entity.setReadingTrackCode(defaultIfBlank(stringValue(payload, "readingTrackCode"), "zh_to_en"));
        entity.setTtsVoiceCode(blankToNull(stringValue(payload, "ttsVoiceCode")));
        entity.setTranslationMode(defaultIfBlank(stringValue(payload, "translationMode"), "device_first"));
        entity.setCloudSyncEnabled(boolValue(payload.get("cloudSyncEnabled"), true));
        entity.setLastModifiedByInstallationId(installationId);
        entity.setRecordVersion(intValue(payload.get("recordVersion"), 1));
        entity.setCreatedAt(parseTime(payload.get("createdAt")) == null ? now : parseTime(payload.get("createdAt")));
        entity.setUpdatedAt(parseTime(payload.get("updatedAt")) == null ? now : parseTime(payload.get("updatedAt")));
        return entity;
    }

    private String encodePreviewSource(String value) {
        String raw = value == null || value.isBlank() ? "已保存句卡" : value.trim();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private OffsetDateTime parseTime(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            if (raw instanceof OffsetDateTime offsetDateTime) {
                return offsetDateTime;
            }
            return OffsetDateTime.parse(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stringValue(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Object raw, Integer defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private Boolean boolValue(Object raw, boolean defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return switch (String.valueOf(raw).trim().toLowerCase()) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> defaultValue;
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
