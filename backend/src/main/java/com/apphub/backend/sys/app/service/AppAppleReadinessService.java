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
            return new AppAppleReadinessView.AppleAuthReadiness(
                "not_required",
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
        require(blockers, issuerId, "billing.appstore.issuerId missing");
        require(blockers, keyId, "billing.appstore.keyId missing");
        require(blockers, privateKey, "billing.appstore.privateKey missing");
        if (!appAppleId) {
            warnings.add("billing.appstore.appAppleId missing; direct transaction/subscription lookup can still work, but ops/readiness is incomplete");
        }
        if (allowSandbox) {
            String message = "billing.appstore.allowSandbox is true; verify this is intended for the target runtime";
            if (isProductionEnvironment(definition)) {
                blockers.add("billing.appstore.allowSandbox must be false in production");
            } else {
                warnings.add(message);
            }
        }

        String status = bundleId && environment && issuerId && keyId && privateKey && productionSandboxSafe ? "ready" : "blocked";
        return new AppAppleReadinessView.AppStoreReadiness(
            status,
            true,
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
