package com.apphub.backend.sys.auth.service;

import com.apphub.backend.sys.auth.entity.SysAuthProviderTokenEntity;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import com.apphub.backend.sys.auth.entity.SysUserIdentityEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多 App 账号隔离测试。
 *
 * 中文说明：同一个 Apple subject 在不同 appCode 下必须生成不同 sys_user、identity 与 session。
 * 这是未来新增第二个 App 时最低成本、最低法律风险的账号边界：不能让 A App 的儿童数据、购买权益或同步状态串到 B App。
 */
@ExtendWith(MockitoExtension.class)
class SysAuthSessionServiceMultiAppIsolationTest {

    @Mock private SysAuthDataService authDataService;
    @Mock private AppleRefreshTokenVaultService appleRefreshTokenVaultService;

    private SysAuthSessionService service;
    private final AtomicLong userIds = new AtomicLong(100L);
    private final AtomicLong identityIds = new AtomicLong(200L);
    private final AtomicLong sessionIds = new AtomicLong(300L);

    @BeforeEach
    void setUp() {
        service = new SysAuthSessionService(
            authDataService,
            new SessionTokenHashService(),
            appleRefreshTokenVaultService,
            new ObjectMapper()
        );
        doAnswer(invocation -> {
            SysUserEntity entity = invocation.getArgument(0);
            entity.setId(userIds.incrementAndGet());
            return true;
        }).when(authDataService).saveUser(any(SysUserEntity.class));
        doAnswer(invocation -> {
            SysUserIdentityEntity entity = invocation.getArgument(0);
            entity.setId(identityIds.incrementAndGet());
            return true;
        }).when(authDataService).saveIdentity(any(SysUserIdentityEntity.class));
        doAnswer(invocation -> {
            SysAuthSessionEntity entity = invocation.getArgument(0);
            entity.setId(sessionIds.incrementAndGet());
            return true;
        }).when(authDataService).saveSession(any(SysAuthSessionEntity.class));
    }

    @Test
    void sameAppleSubjectShouldCreateIndependentUsersForDifferentApps() {
        when(authDataService.identityByProvider("paipai_readingcompanion", "apple", "apple-sub-123"))
            .thenReturn(null);
        when(authDataService.identityByProvider("future_story_app", "apple", "apple-sub-123"))
            .thenReturn(null);

        var paipaiSession = service.issueAppleSession(
            "paipai_readingcompanion",
            "apple-sub-123",
            null,
            true,
            false,
            "Parent",
            Map.of("source", "paipai"),
            null,
            null,
            null,
            null
        );
        var futureSession = service.issueAppleSession(
            "future_story_app",
            "apple-sub-123",
            null,
            true,
            false,
            "Parent",
            Map.of("source", "future"),
            null,
            null,
            null,
            null
        );

        assertThat(paipaiSession.appCode()).isEqualTo("paipai_readingcompanion");
        assertThat(futureSession.appCode()).isEqualTo("future_story_app");
        assertThat(paipaiSession.user().userId()).isNotEqualTo(futureSession.user().userId());

        ArgumentCaptor<SysUserEntity> userCaptor = ArgumentCaptor.forClass(SysUserEntity.class);
        verify(authDataService, org.mockito.Mockito.times(2)).saveUser(userCaptor.capture());
        assertThat(userCaptor.getAllValues())
            .extracting(SysUserEntity::getAppCode)
            .containsExactly("paipai_readingcompanion", "future_story_app");

        ArgumentCaptor<SysUserIdentityEntity> identityCaptor = ArgumentCaptor.forClass(SysUserIdentityEntity.class);
        verify(authDataService, org.mockito.Mockito.times(2)).saveIdentity(identityCaptor.capture());
        assertThat(identityCaptor.getAllValues())
            .extracting(SysUserIdentityEntity::getAppCode)
            .containsExactly("paipai_readingcompanion", "future_story_app");
        assertThat(identityCaptor.getAllValues())
            .extracting(SysUserIdentityEntity::getProviderSubject)
            .containsExactly("apple-sub-123", "apple-sub-123");

        ArgumentCaptor<SysAuthSessionEntity> sessionCaptor = ArgumentCaptor.forClass(SysAuthSessionEntity.class);
        verify(authDataService, org.mockito.Mockito.times(2)).saveSession(sessionCaptor.capture());
        assertThat(sessionCaptor.getAllValues())
            .extracting(SysAuthSessionEntity::getAppCode)
            .containsExactly("paipai_readingcompanion", "future_story_app");
    }

    @Test
    void existingIdentityLookupMustStayInsideRequestedAppCode() {
        SysUserIdentityEntity existingPaipaiIdentity = new SysUserIdentityEntity();
        existingPaipaiIdentity.setId(1L);
        existingPaipaiIdentity.setAppCode("paipai_readingcompanion");
        existingPaipaiIdentity.setUserId(88L);
        existingPaipaiIdentity.setProviderCode("apple");
        existingPaipaiIdentity.setProviderSubject("apple-sub-123");

        // 即使 Paipai 已经存在同 subject，future_story_app 也必须按自己的 appCode 查询；查不到就创建自己的用户。
        when(authDataService.identityByProvider("future_story_app", "apple", "apple-sub-123"))
            .thenReturn(null);

        var futureSession = service.issueAppleSession(
            "future_story_app",
            "apple-sub-123",
            null,
            true,
            false,
            "Parent",
            Map.of(),
            null,
            null,
            null,
            null
        );

        assertThat(futureSession.appCode()).isEqualTo("future_story_app");
        assertThat(futureSession.user().userId()).isNotEqualTo(existingPaipaiIdentity.getUserId());
        verify(authDataService).identityByProvider(eq("future_story_app"), eq("apple"), eq("apple-sub-123"));
    }
}
