package com.apphub.backend.apps.reading.compat.service;

import com.apphub.backend.apps.reading.domain.entity.ReadingDailyQuotaConfigEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingDailyQuotaConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadingDailyQuotaConfigServiceTest {
    @Mock
    private ReadingDailyQuotaConfigMapper quotaConfigMapper;

    @Test
    void dailyLimitShouldNormalizeImageRecognitionAliases() {
        when(quotaConfigMapper.selectActive(eq("paipai_readingcompanion"), eq("free"), eq("capture")))
            .thenReturn(quotaEntity(8));
        ReadingDailyQuotaConfigService service = new ReadingDailyQuotaConfigService(quotaConfigMapper);

        assertThat(service.dailyLimit("free", "image_text_recognition")).isEqualTo(8);
        assertThat(service.dailyLimit("free", "image_ocr")).isEqualTo(8);
        assertThat(service.dailyLimit("free", "picture_ocr")).isEqualTo(8);
    }

    @Test
    void dailyLimitShouldNormalizeSpeechAliases() {
        when(quotaConfigMapper.selectActive(eq("paipai_readingcompanion"), eq("free"), eq("speech")))
            .thenReturn(quotaEntity(16));
        ReadingDailyQuotaConfigService service = new ReadingDailyQuotaConfigService(quotaConfigMapper);

        assertThat(service.dailyLimit("free", "read_aloud")).isEqualTo(16);
        assertThat(service.dailyLimit("free", "voice_reading")).isEqualTo(16);
        assertThat(service.dailyLimit("free", "text_to_speech")).isEqualTo(16);
    }

    private ReadingDailyQuotaConfigEntity quotaEntity(int dailyLimit) {
        ReadingDailyQuotaConfigEntity entity = new ReadingDailyQuotaConfigEntity();
        entity.setDailyLimit(dailyLimit);
        return entity;
    }
}
