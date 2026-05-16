package com.apphub.backend.apps.reading.compat.service;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.domain.entity.ReadingUserPreferenceEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingUserPreferenceMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class ReadingPreferenceService {
    private static final String APP_CODE = ReadingAppModule.APP_CODE;

    private final ReadingUserPreferenceMapper preferenceMapper;
    private final ReadingCompatService readingCompatService;

    public ReadingPreferenceService(ReadingUserPreferenceMapper preferenceMapper, ReadingCompatService readingCompatService) {
        this.preferenceMapper = preferenceMapper;
        this.readingCompatService = readingCompatService;
    }

    public PreferenceView get(ReadingAuthenticatedUser user) {
        ReadingUserPreferenceEntity entity = preferenceMapper.selectById(user.userId());
        return toView(entity, user.userId());
    }

    @Transactional
    public PreferenceView update(ReadingAuthenticatedUser user, PreferencePatchRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ReadingUserPreferenceEntity entity = preferenceMapper.selectById(user.userId());
        boolean created = false;
        if (entity == null) {
            entity = new ReadingUserPreferenceEntity();
            entity.setUserId(user.userId());
            entity.setAppCode(APP_CODE);
            entity.setCloudSyncEnabled(Boolean.FALSE);
            entity.setRecordVersion(1);
            entity.setCreatedAt(now);
            created = true;
        }
        if (hasText(request.uiLocale())) {
            entity.setUiLocale(request.uiLocale().trim());
        }
        if (hasText(request.sourceLanguageCode())) {
            entity.setSourceLanguageCode(request.sourceLanguageCode().trim());
        }
        if (hasText(request.targetLanguageCode())) {
            entity.setTargetLanguageCode(request.targetLanguageCode().trim());
        }
        if (hasText(request.readingTrackCode())) {
            entity.setReadingTrackCode(request.readingTrackCode().trim());
        }
        if (hasText(request.ttsVoiceCode())) {
            entity.setTtsVoiceCode(request.ttsVoiceCode().trim());
        }
        if (hasText(request.translationMode())) {
            entity.setTranslationMode(request.translationMode().trim());
        }
        if (request.cloudSyncEnabled() != null) {
            if (Boolean.TRUE.equals(request.cloudSyncEnabled())) {
                ReadingCompatService.AccountStateView state = readingCompatService.accountState(user);
                if (state == null || state.entitlement() == null || !Boolean.TRUE.equals(state.entitlement().cloudSyncEnabled())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "POWERSYNC_DISABLED");
                }
            }
            entity.setCloudSyncEnabled(request.cloudSyncEnabled());
        }
        entity.setRecordVersion(entity.getRecordVersion() == null ? 1 : entity.getRecordVersion() + 1);
        entity.setUpdatedAt(now);
        if (created) {
            preferenceMapper.insert(entity);
        } else {
            preferenceMapper.updateById(entity);
        }
        return toView(entity, user.userId());
    }

    private PreferenceView toView(ReadingUserPreferenceEntity entity, Long userId) {
        if (entity == null) {
            return new PreferenceView(
                userId,
                "zh-Hans",
                "en",
                "zh-Hans",
                "zh_to_en",
                null,
                "device_first",
                false,
                null,
                false
            );
        }
        return new PreferenceView(
            userId,
            defaultIfBlank(entity.getUiLocale(), "zh-Hans"),
            defaultIfBlank(entity.getSourceLanguageCode(), "en"),
            defaultIfBlank(entity.getTargetLanguageCode(), "zh-Hans"),
            defaultIfBlank(entity.getReadingTrackCode(), "zh_to_en"),
            entity.getTtsVoiceCode(),
            defaultIfBlank(entity.getTranslationMode(), "device_first"),
            Boolean.TRUE.equals(entity.getCloudSyncEnabled()),
            entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString(),
            true
        );
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return hasText(value) ? value.trim() : defaultValue;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record PreferencePatchRequest(
        @Schema(description = "界面语言。", example = "zh-Hans") String uiLocale,
        @Schema(description = "源语言编码。", example = "zh") String sourceLanguageCode,
        @Schema(description = "目标语言编码。", example = "en") String targetLanguageCode,
        @Schema(description = "学习轨道编码。", example = "zh_to_en") String readingTrackCode,
        @Schema(description = "TTS 声音编码。", example = "default_female") String ttsVoiceCode,
        @Schema(description = "翻译模式。", example = "sentence_first") String translationMode,
        @Schema(description = "是否开启云同步。", example = "true") Boolean cloudSyncEnabled
    ) {}

    public record PreferenceView(
        Long userId,
        String uiLocale,
        String sourceLanguageCode,
        String targetLanguageCode,
        String readingTrackCode,
        String ttsVoiceCode,
        String translationMode,
        boolean cloudSyncEnabled,
        String updatedAt,
        boolean persisted
    ) {}
}
