package com.apphub.backend.apps.reading.compat.service;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.domain.entity.ReadingUserPreferenceEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingUserPreferenceMapper;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadingPreferenceServiceTest {

    @Test
    void getShouldReturnDefaultsWhenPreferenceMissing() {
        ReadingUserPreferenceMapper mapper = mock(ReadingUserPreferenceMapper.class);
        ReadingCompatService compatService = mock(ReadingCompatService.class);
        when(mapper.selectById(101L)).thenReturn(null);
        ReadingPreferenceService service = new ReadingPreferenceService(mapper, compatService);
        var result = service.get(user());
        assertThat(result.uiLocale()).isEqualTo("zh-Hans");
        assertThat(result.persisted()).isFalse();
    }

    @Test
    void updateShouldInsertAndReturnPersistedPreference() {
        ReadingUserPreferenceMapper mapper = mock(ReadingUserPreferenceMapper.class);
        ReadingCompatService compatService = mock(ReadingCompatService.class);
        when(mapper.selectById(101L)).thenReturn(null);
        doAnswer(invocation -> 1).when(mapper).insert(any(ReadingUserPreferenceEntity.class));
        ReadingPreferenceService service = new ReadingPreferenceService(mapper, compatService);
        var result = service.update(user(), new ReadingPreferenceService.PreferencePatchRequest("en", "en", "zh-Hans", "zh_to_en", null, "device_first", false));
        assertThat(result.uiLocale()).isEqualTo("en");
        assertThat(result.persisted()).isTrue();
    }

    private ReadingAuthenticatedUser user() {
        SysAuthSessionEntity session = new SysAuthSessionEntity();
        session.setId(1L);
        session.setAppCode("paipai_readingcompanion");
        session.setUserId(101L);
        SysUserEntity user = new SysUserEntity();
        user.setId(101L);
        user.setAppCode("paipai_readingcompanion");
        user.setStatus("active");
        return new ReadingAuthenticatedUser(session, user, "token");
    }
}
