package com.apphub.backend.apps.common;

import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppVersionPolicyServiceTest {

    @Test
    void policyShouldReportUpdateWhenLatestVersionDiffers() {
        SysRemoteConfigService remoteConfigService = mock(SysRemoteConfigService.class);
        AppVersionPolicyService service = new AppVersionPolicyService(registry(), remoteConfigService);
        when(remoteConfigService.loadNamespace("paipai_readingcompanion", AppVersionPolicyService.NAMESPACE_CODE))
            .thenReturn(new RemoteConfigNamespaceView(
                "paipai_readingcompanion",
                AppVersionPolicyService.NAMESPACE_CODE,
                Map.of("ios", Map.of("latestVersion", "1.0.1", "latestBuild", "1"))
            ));

        Map<String, Object> policy = service.policy("paipai_readingcompanion", "ios", "1.0.0", "1");

        assertThat(policy.get("appCode")).isEqualTo("paipai_readingcompanion");
        assertThat(policy.get("latestVersion")).isEqualTo("1.0.1");
        assertThat(policy.get("updateAvailable")).isEqualTo(true);
    }

    @Test
    void policyShouldReportUpdateWhenOnlyBuildDiffers() {
        SysRemoteConfigService remoteConfigService = mock(SysRemoteConfigService.class);
        AppVersionPolicyService service = new AppVersionPolicyService(registry(), remoteConfigService);
        when(remoteConfigService.loadNamespace("paipai_readingcompanion", AppVersionPolicyService.NAMESPACE_CODE))
            .thenReturn(new RemoteConfigNamespaceView(
                "paipai_readingcompanion",
                AppVersionPolicyService.NAMESPACE_CODE,
                Map.of("ios", Map.of("latestVersion", "1.0.0", "latestBuild", "2"))
            ));

        Map<String, Object> policy = service.policy("paipai_readingcompanion", "ios", "1.0.0", "1");

        assertThat(policy.get("latestBuild")).isEqualTo("2");
        assertThat(policy.get("updateAvailable")).isEqualTo(true);
    }

    @Test
    void policyShouldNotReportUpdateWhenVersionAndBuildMatch() {
        SysRemoteConfigService remoteConfigService = mock(SysRemoteConfigService.class);
        AppVersionPolicyService service = new AppVersionPolicyService(registry(), remoteConfigService);
        when(remoteConfigService.loadNamespace("paipai_readingcompanion", AppVersionPolicyService.NAMESPACE_CODE))
            .thenReturn(new RemoteConfigNamespaceView(
                "paipai_readingcompanion",
                AppVersionPolicyService.NAMESPACE_CODE,
                Map.of("ios", Map.of("latestVersion", "1.0.0", "latestBuild", "1"))
            ));

        Map<String, Object> policy = service.policy("paipai_readingcompanion", "ios", "1.0.0", "1");

        assertThat(policy.get("updateAvailable")).isEqualTo(false);
        assertThat(policy.get("severity")).isEqualTo("none");
    }

    private AppModuleRegistry registry() {
        return new AppModuleRegistry(List.of(new TestAppModule()));
    }

    private static final class TestAppModule implements AppModule {
        @Override
        public String appCode() {
            return "paipai_readingcompanion";
        }

        @Override
        public String internalDomain() {
            return "reading";
        }

        @Override
        public String tablePrefix() {
            return "reading_";
        }

        @Override
        public String apiPrefix() {
            return "/api/v1";
        }

        @Override
        public Optional<AppDefinition> definition() {
            return Optional.of(new AppDefinition(
                "paipai_readingcompanion",
                "拍拍伴读",
                "/api/v1",
                "reading_",
                new AppDefinition.Support(true, true, true),
                Map.of()
            ));
        }
    }
}
