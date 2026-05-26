package com.apphub.backend.sys.app.service;

import com.apphub.backend.sys.app.model.AppAppleReadinessView;
import com.apphub.backend.sys.app.model.AppDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 应用编排与发布门禁服务 `AppAppleReadinessService`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Service
public class AppAppleReadinessService {

    public AppAppleReadinessView inspect(AppDefinition definition) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        AppAppleReadinessView.AppleAuthReadiness auth = inspectAuth(definition, blockers, warnings);
        AppAppleReadinessView.AppStoreReadiness appStore = inspectAppStore(definition, blockers, warnings);

        String overallStatus = blockers.isEmpty()
            ? (warnings.isEmpty() ? "ready" : "warning")
            : "blocked";

        return new AppAppleReadinessView(definition.code(), overallStatus, auth, appStore, List.copyOf(blockers), List.copyOf(warnings));
    }

    private AppAppleReadinessView.AppleAuthReadiness inspectAuth(
        AppDefinition definition,
        List<String> blockers,
        List<String> warnings
    ) {
        boolean required = definition.support().appleSignInRequired();
        boolean localOnlyLaunchMode = localOnlyLaunchMode(definition);
        boolean remoteExchangeEnabled = bool(definition, "app.auth.apple.remoteExchangeEnabled");
        boolean clientId = configured(definition, "app.auth.apple.clientId");
        boolean jwksUrl = configured(definition, "app.auth.apple.jwksUrl");
        boolean teamId = configured(definition, "app.auth.apple.teamId");
        boolean keyId = configured(definition, "app.auth.apple.keyId");
        boolean privateKey = configured(definition, "app.auth.apple.privateKey");
        boolean redirectUri = configured(definition, "app.auth.apple.redirectUri");
        boolean tokenEndpoint = configured(definition, "app.auth.apple.tokenEndpoint");
        boolean revokeEndpoint = configured(definition, "app.auth.apple.revokeEndpoint");
        boolean credentialEncryptionReady = credentialEncryptionReady();
        boolean bundleIdentityAligned = bundleIdentityAligned(definition);

        if (!required) {
            String status = localOnlyLaunchMode ? "local_no_backend" : "not_required";
            if (localOnlyLaunchMode && remoteExchangeEnabled) {
                // 中文说明：无自有后端首发不能留下远端 Apple code exchange，
                // 否则会重新形成账号/凭证服务器路径，和儿童数据最小化口径冲突。
                blockers.add("auth.apple.remoteExchangeEnabled must be false in local-only launch mode");
                status = "blocked";
            }
            return new AppAppleReadinessView.AppleAuthReadiness(
                status,
                false,
                remoteExchangeEnabled,
                clientId,
                jwksUrl,
                teamId,
                keyId,
                privateKey,
                redirectUri,
                tokenEndpoint,
                revokeEndpoint,
                credentialEncryptionReady,
                false,
                bundleIdentityAligned
            );
        }

        require(blockers, clientId, "auth.apple.clientId missing");
        require(blockers, jwksUrl, "auth.apple.jwksUrl missing");
        require(blockers, teamId, "auth.apple.teamId missing");
        require(blockers, keyId, "auth.apple.keyId missing");
        require(blockers, privateKey, "auth.apple.privateKey missing");
        require(blockers, redirectUri, "auth.apple.redirectUri missing");
        require(blockers, tokenEndpoint, "auth.apple.tokenEndpoint missing");
        require(blockers, revokeEndpoint, "auth.apple.revokeEndpoint missing");
        if (!bundleIdentityAligned) {
            blockers.add("auth.apple.clientId and billing.appstore.bundleId mismatch");
        }
        if (!remoteExchangeEnabled) {
            warnings.add("auth.apple.remoteExchangeEnabled is false; formal Apple code exchange will stay disabled in this runtime");
        } else if (!credentialEncryptionReady) {
            blockers.add("auth.apple.credentialEncryptionKey missing");
        }

        boolean formalSessionReady = remoteExchangeEnabled
            && clientId
            && teamId
            && keyId
            && privateKey
            && redirectUri
            && tokenEndpoint
            && revokeEndpoint
            && credentialEncryptionReady
            && bundleIdentityAligned;

        String status = clientId && jwksUrl && teamId && keyId && privateKey && redirectUri && tokenEndpoint && revokeEndpoint && (!remoteExchangeEnabled || credentialEncryptionReady) && bundleIdentityAligned
            ? (remoteExchangeEnabled ? "ready" : "exchange_disabled")
            : "blocked";
        return new AppAppleReadinessView.AppleAuthReadiness(
            status,
            true,
            remoteExchangeEnabled,
            clientId,
            jwksUrl,
            teamId,
            keyId,
            privateKey,
            redirectUri,
            tokenEndpoint,
            revokeEndpoint,
            credentialEncryptionReady,
            formalSessionReady,
            bundleIdentityAligned
        );
    }

    private AppAppleReadinessView.AppStoreReadiness inspectAppStore(
        AppDefinition definition,
        List<String> blockers,
        List<String> warnings
    ) {
        boolean required = definition.support().billingRequired();
        boolean localOnlyLaunchMode = localOnlyLaunchMode(definition);
        boolean localDeviceCreditsEnabled = bool(definition, "app.billing.localDeviceCredits.enabled");
        boolean apiCreditsReservedOnly = bool(definition, "app.billing.apiCallCredits.reservedOnly");
        boolean paidApiCreditsEnabled = bool(definition, "app.billing.apiCallCredits.paidEnabled");
        boolean externalCloudProcessingEnabled = bool(definition, "app.privacy.cloudContentProcessingEnabled")
            || bool(definition, "app.billing.externalCloudProcessingEnabled");
        boolean serverWalletEnabled = bool(definition, "app.billing.serverWalletEnabled");
        boolean appStoreServerApiEnabled = bool(definition, "app.billing.appstore.serverApiEnabled");
        boolean consumableHistoryRestoreEnabled = bool(definition, "app.billing.appstore.consumableHistoryRestoreEnabled");
        boolean localIapOnly = required
            && localOnlyLaunchMode
            && localDeviceCreditsEnabled
            && !paidApiCreditsEnabled
            && !externalCloudProcessingEnabled
            && !serverWalletEnabled
            && !appStoreServerApiEnabled
            && !consumableHistoryRestoreEnabled;
        boolean serverApiRequired = required && !localIapOnly;
        boolean bundleId = configured(definition, "app.billing.appstore.bundleId");
        boolean environment = configured(definition, "app.billing.appstore.environment");
        boolean allowSandbox = bool(definition, "app.billing.appstore.allowSandbox");
        boolean appAppleId = configured(definition, "app.billing.appstore.appAppleId");
        boolean issuerId = configured(definition, "app.billing.appstore.issuerId");
        boolean keyId = configured(definition, "app.billing.appstore.keyId");
        boolean privateKey = configured(definition, "app.billing.appstore.privateKey");
        boolean productionSandboxSafe = !isProductionEnvironment(definition) || !allowSandbox;

        if (!required) {
            return new AppAppleReadinessView.AppStoreReadiness(
                "not_required",
                false,
                false,
                false,
                localDeviceCreditsEnabled,
                apiCreditsReservedOnly,
                paidApiCreditsEnabled,
                externalCloudProcessingEnabled,
                serverWalletEnabled,
                consumableHistoryRestoreEnabled,
                bundleId,
                environment,
                allowSandbox,
                issuerId,
                keyId,
                privateKey,
                appAppleId,
                productionSandboxSafe
            );
        }

        require(blockers, bundleId, "billing.appstore.bundleId missing");
        require(blockers, environment, "billing.appstore.environment missing");
        if (localOnlyLaunchMode) {
            require(blockers, localDeviceCreditsEnabled, "billing.localDeviceCredits.enabled must be true in local-only launch mode");
            require(blockers, apiCreditsReservedOnly, "billing.apiCallCredits.reservedOnly must be true in local-only launch mode");
            if (paidApiCreditsEnabled) {
                blockers.add("billing.apiCallCredits.paidEnabled must be false in local-only launch mode");
            }
            if (externalCloudProcessingEnabled) {
                blockers.add("cloud content processing must be false in local-only launch mode");
            }
            if (serverWalletEnabled) {
                blockers.add("billing.serverWalletEnabled must be false in local-only launch mode");
            }
            if (appStoreServerApiEnabled) {
                blockers.add("billing.appstore.serverApiEnabled must be false in local-only launch mode");
            }
            if (consumableHistoryRestoreEnabled) {
                blockers.add("billing.appstore.consumableHistoryRestoreEnabled must be false without server reconciliation");
            }
            validateLocalOnlyProductMappings(definition, blockers);
        }
        if (serverApiRequired) {
            require(blockers, issuerId, "billing.appstore.issuerId missing");
            require(blockers, keyId, "billing.appstore.keyId missing");
            require(blockers, privateKey, "billing.appstore.privateKey missing");
        } else if (issuerId || keyId || privateKey || appAppleId) {
            warnings.add("App Store Server API credentials are configured but ignored in local-only launch mode");
        }
        if (!appAppleId) {
            if (serverApiRequired) {
                warnings.add("billing.appstore.appAppleId missing; direct transaction/subscription lookup can still work, but ops/readiness is incomplete");
            }
        }
        if (allowSandbox) {
            String message = "billing.appstore.allowSandbox is true; verify this is intended for the target runtime";
            if (isProductionEnvironment(definition)) {
                blockers.add("billing.appstore.allowSandbox must be false in production");
            } else {
                warnings.add(message);
            }
        }

        String status;
        if (!bundleId || !environment || !productionSandboxSafe) {
            status = "blocked";
        } else if (localIapOnly) {
            status = "local_iap_only";
        } else {
            status = issuerId && keyId && privateKey ? "ready" : "blocked";
        }
        return new AppAppleReadinessView.AppStoreReadiness(
            status,
            true,
            serverApiRequired,
            localIapOnly,
            localDeviceCreditsEnabled,
            apiCreditsReservedOnly,
            paidApiCreditsEnabled,
            externalCloudProcessingEnabled,
            serverWalletEnabled,
            consumableHistoryRestoreEnabled,
            bundleId,
            environment,
            allowSandbox,
            issuerId,
            keyId,
            privateKey,
            appAppleId,
            productionSandboxSafe
        );
    }

    private boolean localOnlyLaunchMode(AppDefinition definition) {
        return bool(definition, "app.launch.localNoBackendFirstRelease")
            || bool(definition, "app.privacy.noDeveloperBackend");
    }

    private void validateLocalOnlyProductMappings(AppDefinition definition, List<String> blockers) {
        List<String> values = definition.raw().entrySet().stream()
            .filter(entry -> String.valueOf(entry.getKey()).startsWith("app.billing.entitlements.productMappings."))
            .map(entry -> String.valueOf(entry.getValue()).trim())
            .filter(value -> !value.isBlank())
            .toList();
        if (values.isEmpty()) {
            blockers.add("billing.entitlements.productMappings must include local OCR/TTS products for local-only launch mode");
            return;
        }
        if (!values.contains("local_ocr") || !values.contains("local_tts")) {
            blockers.add("billing.entitlements.productMappings must include both local_ocr and local_tts in local-only launch mode");
        }
        values.stream()
            .filter(value -> !"local_ocr".equals(value) && !"local_tts".equals(value))
            .findFirst()
            .ifPresent(value -> blockers.add("local-only productMappings may only map to local_ocr/local_tts, found " + value));
    }

    private boolean credentialEncryptionReady() {
        String configured = System.getProperty("APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY");
        }
        if (configured == null || configured.isBlank()) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(configured.trim()).length == 32;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private void require(List<String> blockers, boolean configured, String message) {
        if (!configured) {
            blockers.add(message);
        }
    }

    private boolean configured(AppDefinition definition, String key) {
        String value = raw(definition, key);
        return value != null && !value.isBlank();
    }

    private boolean bool(AppDefinition definition, String key) {
        String value = raw(definition, key);
        return value != null && Boolean.parseBoolean(value);
    }

    private boolean bundleIdentityAligned(AppDefinition definition) {
        String clientId = raw(definition, "app.auth.apple.clientId");
        String bundleId = raw(definition, "app.billing.appstore.bundleId");
        return clientId != null && bundleId != null && clientId.equals(bundleId);
    }

    private boolean isProductionEnvironment(AppDefinition definition) {
        String environment = raw(definition, "app.billing.appstore.environment");
        return environment != null && "production".equalsIgnoreCase(environment);
    }

    private String raw(AppDefinition definition, String key) {
        Object value = definition.raw().get(key);
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        if (normalized.isEmpty()
            || normalized.startsWith("__FILL_FROM_DB_")
            || normalized.startsWith("__FILL_ME__")
            || normalized.startsWith("__PLACEHOLDER__")) {
            return null;
        }
        return normalized;
    }
}
