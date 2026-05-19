package com.apphub.backend.apps.reading.compat.service;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.domain.entity.ReadingDailyQuotaConfigEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingDailyQuotaConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadingDailyQuotaConfigService {
    public static final String FEATURE_LOCAL_OCR = "local_ocr";
    public static final String FEATURE_LOCAL_TTS = "local_tts";
    public static final String FEATURE_CLOUD_OCR = "cloud_ocr";
    public static final String FEATURE_CLOUD_TTS = "cloud_tts";

    private static final String APP_CODE = ReadingAppModule.APP_CODE;
    private static final String FREE_PLAN = "free";
    private static final int DEFAULT_LOCAL_OCR_LIMIT = 5;
    private static final int DEFAULT_LOCAL_TTS_LIMIT = 10;

    private final ReadingDailyQuotaConfigMapper quotaConfigMapper;

    public ReadingDailyQuotaConfigService(ReadingDailyQuotaConfigMapper quotaConfigMapper) {
        this.quotaConfigMapper = quotaConfigMapper;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int dailyLimit(String planCode, String featureCode) {
        String safePlanCode = blankToDefault(planCode, FREE_PLAN);
        String safeFeatureCode = normalizeFeatureCode(featureCode);
        Integer configured = configuredLimit(safePlanCode, safeFeatureCode);
        if (configured == null && !FREE_PLAN.equals(safePlanCode)) {
            configured = configuredLimit(FREE_PLAN, safeFeatureCode);
        }
        return configured == null ? fallbackLimit(safeFeatureCode) : configured;
    }

    private Integer configuredLimit(String planCode, String featureCode) {
        try {
            ReadingDailyQuotaConfigEntity entity = quotaConfigMapper.selectActive(APP_CODE, planCode, featureCode);
            if (entity == null || entity.getDailyLimit() == null || entity.getDailyLimit() < 0) {
                return null;
            }
            return entity.getDailyLimit();
        } catch (Exception ignored) {
            return null;
        }
    }

    private int fallbackLimit(String featureCode) {
        if (FEATURE_CLOUD_OCR.equals(featureCode) || FEATURE_CLOUD_TTS.equals(featureCode)) {
            return 0;
        }
        if (FEATURE_LOCAL_TTS.equals(featureCode)) {
            return DEFAULT_LOCAL_TTS_LIMIT;
        }
        return DEFAULT_LOCAL_OCR_LIMIT;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim().toLowerCase();
    }

    private String normalizeFeatureCode(String featureCode) {
        String normalized = blankToDefault(featureCode, FEATURE_LOCAL_OCR);
        return switch (normalized) {
            case "ocr", "local_ocr", "image_text_recognition", "image_ocr", "picture_ocr", "photo_ocr" -> FEATURE_LOCAL_OCR;
            case "local_tts", "tts", "read_aloud", "voice_reading", "text_to_speech", "speech_synthesis" -> FEATURE_LOCAL_TTS;
            default -> normalized;
        };
    }
}
