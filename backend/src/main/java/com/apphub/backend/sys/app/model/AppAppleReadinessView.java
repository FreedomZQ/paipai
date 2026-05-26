package com.apphub.backend.sys.app.model;

import java.util.List;

/**
 * 响应模型 `AppAppleReadinessView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record AppAppleReadinessView(
    String appCode,
    String overallStatus,
    AppleAuthReadiness auth,
    AppStoreReadiness appStore,
    List<String> blockers,
    List<String> warnings
) {
    public record AppleAuthReadiness(
        String status,
        boolean required,
        boolean remoteExchangeEnabled,
        boolean clientIdConfigured,
        boolean jwksUrlConfigured,
        boolean teamIdConfigured,
        boolean keyIdConfigured,
        boolean privateKeyConfigured,
        boolean redirectUriConfigured,
        boolean tokenEndpointConfigured,
        boolean revokeEndpointConfigured,
        boolean credentialEncryptionReady,
        boolean formalSessionReady,
        boolean bundleIdentityAligned
    ) {
    }

    public record AppStoreReadiness(
        String status,
        boolean required,
        boolean serverApiRequired,
        boolean localIapOnly,
        boolean localDeviceCreditsEnabled,
        boolean apiCreditsReservedOnly,
        boolean paidApiCreditsEnabled,
        boolean externalCloudProcessingEnabled,
        boolean serverWalletEnabled,
        boolean consumableHistoryRestoreEnabled,
        boolean bundleIdConfigured,
        boolean environmentConfigured,
        boolean allowSandbox,
        boolean issuerIdConfigured,
        boolean keyIdConfigured,
        boolean privateKeyConfigured,
        boolean appAppleIdConfigured,
        boolean productionSandboxSafe
    ) {
    }
}
