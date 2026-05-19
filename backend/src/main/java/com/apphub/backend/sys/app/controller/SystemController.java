package com.apphub.backend.sys.app.controller;

import com.apphub.backend.apps.common.AppModule;
import com.apphub.backend.apps.common.AppModuleRegistry;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.app.config.AppCatalogProperties;
import com.apphub.backend.sys.app.model.AppAppleOpsGateView;
import com.apphub.backend.sys.app.model.SystemPublicSurfaceView;
import com.apphub.backend.sys.app.model.SystemReleaseGateView;
import com.apphub.backend.sys.app.model.AppAppleTokenStorageView;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.billing.model.EntitlementObservabilityView;
import com.apphub.backend.sys.app.model.AppAppleReadinessView;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.billing.service.SysBillingService;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationObservabilityView;
import com.apphub.backend.sys.appstore.service.SysAppStoreNotificationService;
import com.apphub.backend.sys.auth.service.SysAuthDataService;
import com.apphub.backend.sys.auth.service.PublicAuthAccessPolicyService;
import com.apphub.backend.sys.app.service.AppAppleReadinessService;
import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;


/**
 * 应用编排与发布门禁控制器 `SystemController`。
 * 负责暴露统一后端的 HTTP 接口，并将请求委派给对应服务层处理。
 */

@Tag(name = "系统运维", description = "统一后端健康检查、应用目录、发布门禁和观测接口。")
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final AppCatalogProperties appCatalogProperties;
    private static final String APPLE_PROVIDER = "apple";

    private final AppDefinitionService appDefinitionService;
    private final AppModuleRegistry appModuleRegistry;
    private final AppAppleReadinessService appAppleReadinessService;
    private final SysBillingService sysBillingService;
    private final SysAppStoreNotificationService sysAppStoreNotificationService;
    private final SysAuthDataService authDataService;
    private final PublicAuthAccessPolicyService publicAuthAccessPolicyService;
    private final SysRemoteConfigService sysRemoteConfigService;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${backend.environment}")
    private String environment;

    @Value("${backend.ops.token:${BACKEND_OPS_TOKEN:}}")
    private String opsToken;

    @Value("${management.endpoints.web.exposure.include:health,info,metrics}")
    private String actuatorExposure;

    @Value("${springdoc.api-docs.enabled:true}")
    private boolean apiDocsEnabled;

    @Value("${springdoc.swagger-ui.enabled:true}")
    private boolean swaggerUiEnabled;

    public SystemController(
        AppCatalogProperties appCatalogProperties,
        AppDefinitionService appDefinitionService,
        AppModuleRegistry appModuleRegistry,
        AppAppleReadinessService appAppleReadinessService,
        SysBillingService sysBillingService,
        SysAppStoreNotificationService sysAppStoreNotificationService,
        SysAuthDataService authDataService,
        PublicAuthAccessPolicyService publicAuthAccessPolicyService,
        SysRemoteConfigService sysRemoteConfigService
    ) {
        this.appCatalogProperties = appCatalogProperties;
        this.appDefinitionService = appDefinitionService;
        this.appModuleRegistry = appModuleRegistry;
        this.appAppleReadinessService = appAppleReadinessService;
        this.sysBillingService = sysBillingService;
        this.sysAppStoreNotificationService = sysAppStoreNotificationService;
        this.authDataService = authDataService;
        this.publicAuthAccessPolicyService = publicAuthAccessPolicyService;
        this.sysRemoteConfigService = sysRemoteConfigService;
    }

    @Operation(summary = "健康检查", description = "返回最小化健康状态，生产环境不暴露敏感应用细节。")
    @GetMapping("/healthz")
    public ApiResponse<Map<String, Object>> healthz() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("application", applicationName);
        payload.put("environment", environment);
        payload.put("status", "ok");
        if (!"prod".equalsIgnoreCase(environment)) {
            payload.put("supportedApps", appCatalogProperties.getSupported());
            payload.put("definitionResources", appCatalogProperties.getDefinitions());
        }
        return ApiResponse.success(currentRequestId(), payload);
    }

    @Operation(summary = "查询应用列表", description = "查询统一后端当前支持的应用定义列表。")
    @GetMapping("/apps")
    public ApiResponse<List<AppDefinition>> apps() {
        return ApiResponse.success(currentRequestId(), appDefinitionService.list());
    }

    @Operation(summary = "查询应用详情", description = "按应用编码查询应用定义详情。")
    @GetMapping("/apps/{appCode}")
    public ApiResponse<AppDefinition> app(@Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode) {
        AppDefinition definition = appDefinitionService.get(appCode)
            .orElseThrow(() -> new AppNotFoundException(appCode));
        return ApiResponse.success(currentRequestId(), definition);
    }

    @Operation(summary = "查询 Apple 配置就绪状态", description = "查询指定应用 Apple 登录和 Server API 配置是否就绪。")
    @GetMapping("/apps/{appCode}/apple/readiness")
    public ApiResponse<AppAppleReadinessView> appleReadiness(@Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode) {
        AppDefinition definition = appDefinitionService.get(appCode)
            .orElseThrow(() -> new AppNotFoundException(appCode));
        return ApiResponse.success(currentRequestId(), appAppleReadinessService.inspect(definition));
    }

    @Operation(summary = "查询 Apple Token 存储状态", description = "查询指定应用 Apple token 加密存储和明文兜底状态。")
    @GetMapping("/apps/{appCode}/apple/token-storage")
    public ApiResponse<AppAppleTokenStorageView> appleTokenStorage(@Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode) {
        appDefinitionService.get(appCode)
            .orElseThrow(() -> new AppNotFoundException(appCode));
        return ApiResponse.success(currentRequestId(), buildTokenStorage(appCode));
    }

    @Operation(summary = "查询单应用 Apple 运维门禁", description = "查询指定应用 Apple 能力是否满足上线前门禁。")
    @GetMapping("/apps/{appCode}/apple/ops-gate")
    public ApiResponse<AppAppleOpsGateView> appleOpsGate(@Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode) {
        AppDefinition definition = appDefinitionService.get(appCode)
            .orElseThrow(() -> new AppNotFoundException(appCode));
        return ApiResponse.success(currentRequestId(), buildAppleOpsGate(definition));
    }

    @Operation(summary = "查询全部 Apple 运维门禁", description = "查询全部应用的 Apple 能力上线前门禁。")
    @GetMapping("/apple/ops-gates")
    public ApiResponse<List<AppAppleOpsGateView>> appleOpsGates() {
        return ApiResponse.success(currentRequestId(), appDefinitionService.list().stream()
            .map(this::buildAppleOpsGate)
            .toList());
    }

    @Operation(summary = "查询发布门禁", description = "汇总系统配置、公开面和各应用门禁，判断是否可发布。")
    @GetMapping("/release-gate")
    public ApiResponse<SystemReleaseGateView> releaseGate() {
        List<AppDefinition> releaseScopedDefinitions = appDefinitionService.list().stream()
            .filter(this::includedInCurrentReleaseGate)
            .toList();
        List<AppAppleOpsGateView> appGates = releaseScopedDefinitions.stream()
            .map(this::buildAppleOpsGate)
            .toList();
        int blockedCount = (int) appGates.stream().filter(item -> "blocked".equalsIgnoreCase(item.status())).count();
        int warningCount = (int) appGates.stream().filter(item -> "warning".equalsIgnoreCase(item.status())).count();

        ArrayList<SystemReleaseGateView.ReleaseGateCheckView> checks = new ArrayList<>();
        ArrayList<String> blockers = new ArrayList<>();
        ArrayList<String> warnings = new ArrayList<>();

        addOpsTokenCheck(checks, blockers, warnings);
        addAppModuleRegistryCheck(checks, blockers, warnings);
        addActuatorExposureCheck(checks, blockers, warnings);
        addSwaggerExposureCheck(checks, blockers, warnings);
        addPublicSurfaceCheck(checks);
        addReleaseScopeCheck(checks, releaseScopedDefinitions);
        addAppReleaseConfigChecks(checks, blockers, warnings);

        for (AppAppleOpsGateView appGate : appGates) {
            for (String blocker : appGate.blockers()) {
                blockers.add(appGate.appCode() + ": " + blocker);
            }
            for (String warning : appGate.warnings()) {
                warnings.add(appGate.appCode() + ": " + warning);
            }
        }

        List<String> codeBlockers = blockers.stream()
            .filter(blocker -> !isExternalReleaseBlocker(blocker))
            .toList();
        List<String> externalBlockers = blockers.stream()
            .filter(this::isExternalReleaseBlocker)
            .toList();

        String status = !blockers.isEmpty()
            ? "blocked"
            : (!warnings.isEmpty() || warningCount > 0 ? "warning" : "ready");
        String codeStatus = codeBlockers.isEmpty() ? "ready" : "blocked";
        String externalStatus = externalBlockers.isEmpty() ? "ready" : "blocked";
        return ApiResponse.success(currentRequestId(), new SystemReleaseGateView(
            status,
            codeStatus,
            externalStatus,
            environment,
            appGates.size(),
            blockedCount,
            warningCount,
            List.copyOf(checks),
            List.copyOf(blockers),
            List.copyOf(codeBlockers),
            List.copyOf(externalBlockers),
            List.copyOf(warnings),
            appGates.stream()
                .map(item -> new SystemReleaseGateView.AppReleaseGateSummaryView(
                    item.appCode(),
                    item.status(),
                    item.blockers().size(),
                    item.warnings().size(),
                    item.blockers(),
                    item.warnings()
                ))
                .toList()
        ));
    }

    private boolean isExternalReleaseBlocker(String blocker) {
        return blocker.contains("release_ios.development_team")
            || blocker.endsWith("auth.apple.teamId missing")
            || blocker.endsWith("auth.apple.keyId missing")
            || blocker.endsWith("auth.apple.privateKey missing")
            || blocker.endsWith("auth.apple.redirectUri missing")
            || blocker.endsWith("auth.apple.credentialEncryptionKey missing")
            || blocker.endsWith("billing.appstore.bundleId missing")
            || blocker.endsWith("billing.appstore.environment missing")
            || blocker.endsWith("billing.appstore.issuerId missing")
            || blocker.endsWith("billing.appstore.keyId missing")
            || blocker.endsWith("billing.appstore.privateKey missing");
    }

    private boolean includedInCurrentReleaseGate(AppDefinition definition) {
        String raw = normalizeConfigValue(definition.raw().get("app.release.requiredForCurrentWave"));
        return raw == null || Boolean.parseBoolean(raw);
    }

    private void addOpsTokenCheck(
        List<SystemReleaseGateView.ReleaseGateCheckView> checks,
        List<String> blockers,
        List<String> warnings
    ) {
        if (opsToken != null && !opsToken.isBlank()) {
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                "opsToken",
                "ready",
                "BACKEND_OPS_TOKEN / backend.ops.token is configured.",
                "configured",
                "configured"
            ));
            return;
        }
        if ("prod".equalsIgnoreCase(environment)) {
            blockers.add("system.ops.token missing in prod");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                "opsToken",
                "blocked",
                "BACKEND_OPS_TOKEN / backend.ops.token must be configured in prod.",
                "missing",
                "configured"
            ));
        } else {
            warnings.add("system.ops.token not configured outside prod");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                "opsToken",
                "warning",
                "Ops token is not configured; non-prod system endpoints remain open for local compatibility.",
                "missing",
                "configured"
            ));
        }
    }

    private void addAppModuleRegistryCheck(
        List<SystemReleaseGateView.ReleaseGateCheckView> checks,
        List<String> blockers,
        List<String> warnings
    ) {
        List<String> definitionCodes = appDefinitionService.list().stream()
            .map(AppDefinition::code)
            .toList();
        List<String> moduleCodes = appModuleRegistry.activeModules().stream()
            .map(AppModule::appCode)
            .toList();

        List<String> definitionsWithoutModule = definitionCodes.stream()
            .filter(code -> !moduleCodes.contains(code))
            .toList();
        List<String> modulesWithoutDefinition = moduleCodes.stream()
            .filter(code -> !definitionCodes.contains(code))
            .toList();

        if (definitionsWithoutModule.isEmpty() && modulesWithoutDefinition.isEmpty()) {
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                "appModuleRegistry",
                "ready",
                "Every loaded app definition has a registered AppModule and every AppModule has an app definition.",
                "definitions=" + definitionCodes + ",modules=" + moduleCodes,
                "1:1 app definition/module mapping"
            ));
            return;
        }

        String message = "AppDefinition/AppModule mismatch: definitionsWithoutModule="
            + definitionsWithoutModule + ", modulesWithoutDefinition=" + modulesWithoutDefinition;
        if ("prod".equalsIgnoreCase(environment)) {
            blockers.add(message);
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                "appModuleRegistry",
                "blocked",
                message,
                "definitions=" + definitionCodes + ",modules=" + moduleCodes,
                "1:1 app definition/module mapping"
            ));
        } else {
            warnings.add(message);
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                "appModuleRegistry",
                "warning",
                message,
                "definitions=" + definitionCodes + ",modules=" + moduleCodes,
                "1:1 app definition/module mapping"
            ));
        }
    }

    private void addActuatorExposureCheck(
        List<SystemReleaseGateView.ReleaseGateCheckView> checks,
        List<String> blockers,
        List<String> warnings
    ) {
        String exposure = actuatorExposure == null ? "" : actuatorExposure.trim().toLowerCase(java.util.Locale.ROOT);
        if ("prod".equalsIgnoreCase(environment) && !"health".equals(exposure)) {
            blockers.add("management.endpoints.web.exposure.include must be health in prod");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                "actuatorExposure",
                "blocked",
                "Prod actuator exposure must be restricted to health. Current: " + exposure,
                exposure,
                "health"
            ));
            return;
        }
        checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
            "actuatorExposure",
            "ready",
            "Actuator exposure is acceptable for the current runtime: " + exposure,
            exposure,
            "health in prod"
        ));
    }

    private void addSwaggerExposureCheck(
        List<SystemReleaseGateView.ReleaseGateCheckView> checks,
        List<String> blockers,
        List<String> warnings
    ) {
        if ("prod".equalsIgnoreCase(environment) && (apiDocsEnabled || swaggerUiEnabled)) {
            blockers.add("springdoc api-docs/swagger-ui must be disabled in prod");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                "swaggerExposure",
                "blocked",
                "Springdoc API docs and Swagger UI must be disabled in prod.",
                "apiDocs=" + apiDocsEnabled + ",swaggerUi=" + swaggerUiEnabled,
                "apiDocs=false,swaggerUi=false"
            ));
            return;
        }
        checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
            "swaggerExposure",
            "ready",
            "Springdoc exposure is acceptable for the current runtime.",
            "apiDocs=" + apiDocsEnabled + ",swaggerUi=" + swaggerUiEnabled,
            "apiDocs=false,swaggerUi=false in prod"
        ));
    }

    private void addPublicSurfaceCheck(List<SystemReleaseGateView.ReleaseGateCheckView> checks) {
        List<SystemPublicSurfaceView.PublicEndpointView> endpoints = publicSurfaceEndpoints();
        checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
            "publicSurface",
            "ready",
            "Intentional public endpoints are documented by /api/v1/system/public-surface and docs/release-checklist.md.",
            endpoints.size() + " endpoints",
            "documented intentional public endpoints only"
        ));
    }

    private void addReleaseScopeCheck(
        List<SystemReleaseGateView.ReleaseGateCheckView> checks,
        List<AppDefinition> releaseScopedDefinitions
    ) {
        List<String> includedApps = releaseScopedDefinitions.stream().map(AppDefinition::code).toList();
        List<String> excludedApps = appDefinitionService.list().stream()
            .filter(definition -> !includedInCurrentReleaseGate(definition))
            .map(AppDefinition::code)
            .toList();
        checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
            "releaseScope",
            "ready",
            "本次 release-gate 仅统计当前发布波次必须上线的应用。",
            "included=" + includedApps + ", excluded=" + excludedApps,
            "included apps match current release wave"
        ));
    }

    private void addAppReleaseConfigChecks(
        List<SystemReleaseGateView.ReleaseGateCheckView> checks,
        List<String> blockers,
        List<String> warnings
    ) {
        for (AppModule module : appModuleRegistry.activeModules()) {
            AppDefinition definition = module.definition()
                .orElseGet(() -> appDefinitionService.get(module.appCode()).orElse(null));
            if (definition == null) {
                warnings.add(module.appCode() + ": app definition missing; skipping release_ios checks");
                checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                    module.appCode() + ".release_ios",
                    "warning",
                    "AppModule is registered but no AppDefinition could be loaded for release_ios checks.",
                    "definition-missing",
                    "definition-present"
                ));
                continue;
            }
            if (!includedInCurrentReleaseGate(definition)) {
                continue;
            }
            Map<String, Object> items = loadNamespaceItems(module.appCode(), "release_ios");
            if (!requiresIosReleaseConfig(definition, items)) {
                continue;
            }
            addRequiredReleaseConfigCheck(checks, blockers, items, module.appCode(), "development_team", "configured Apple development team");
            addRequiredReleaseConfigCheck(checks, blockers, items, module.appCode(), "marketing_version", "configured marketing version");
            addRequiredReleaseConfigCheck(checks, blockers, items, module.appCode(), "current_project_version", "configured build number");
            addReleaseMinimumPlatformVersionCheck(checks, blockers, warnings, items, definition, "minimum_ios_version", "app.release.minimumIosVersion", "iOS");
            addReleaseMinimumPlatformVersionCheck(checks, blockers, warnings, items, definition, "minimum_ipados_version", "app.release.minimumIpadosVersion", "iPadOS");
            addReleaseBundleIdentifierCheck(checks, blockers, items, definition);
            addReleaseApiBaseUrlCheck(checks, blockers, module, items);
            addBundleConsistencyCheck(checks, blockers, definition);
            addReleaseProductIdConsistencyCheck(checks, blockers, items, definition);
            addAppleRemoteExchangeCheck(checks, blockers, definition);
            addProductionSandboxCheck(checks, blockers, definition);
        }
    }

    private void addRequiredReleaseConfigCheck(
        List<SystemReleaseGateView.ReleaseGateCheckView> checks,
        List<String> blockers,
        Map<String, Object> items,
        String appCode,
        String key,
        String expectedValue
    ) {
        String value = normalizeConfigValue(items.get(key));
        String checkKey = appCode + ".release_ios." + key;
        if (value == null) {
            blockers.add(checkKey + " missing or placeholder");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "blocked",
                "release_ios." + key + " must be configured before iOS release preflight.",
                "missing",
                expectedValue
            ));
            return;
        }
        checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
            checkKey,
            "ready",
            "release_ios." + key + " is configured.",
            value,
            expectedValue
        ));
    }

    /**
     * 检查当前 App 的最低系统版本发布口径。
     *
     * <p>这是 release_ios 命名空间下的 App 级检查，不是统一后端的全局限制。
     * 这样后续新增 App 时，可以用自己的 appCode 配置不同的最低系统版本，避免被拍拍伴读首发 iOS 18 策略污染。</p>
     */
    private void addReleaseMinimumPlatformVersionCheck(
        List<SystemReleaseGateView.ReleaseGateCheckView> checks,
        List<String> blockers,
        List<String> warnings,
        Map<String, Object> items,
        AppDefinition definition,
        String releaseKey,
        String definitionKey,
        String platformName
    ) {
        String expectedMinimumVersion = normalizeConfigValue(definition.raw().get(definitionKey));
        String configuredVersion = normalizeConfigValue(items.get(releaseKey));
        String checkKey = definition.code() + ".release_ios." + releaseKey;
        if (expectedMinimumVersion == null && configuredVersion == null) {
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "ready",
                platformName + " minimum version is not required for this app release.",
                "not-required",
                "app-specific"
            ));
            return;
        }
        if (configuredVersion == null) {
            blockers.add(checkKey + " missing");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "blocked",
                "release_ios." + releaseKey + " must be configured when app-definition declares " + definitionKey + ".",
                "missing",
                expectedMinimumVersion
            ));
            return;
        }
        if (!isValidDottedVersion(configuredVersion)) {
            blockers.add(checkKey + " invalid version format");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "blocked",
                platformName + " minimum version must use dotted numeric format, for example 18.0.",
                configuredVersion,
                expectedMinimumVersion == null ? "numeric dotted version" : expectedMinimumVersion
            ));
            return;
        }
        if (expectedMinimumVersion != null && !isValidDottedVersion(expectedMinimumVersion)) {
            blockers.add(definition.code() + "." + definitionKey + " invalid version format");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "blocked",
                definitionKey + " must use dotted numeric format, for example 18.0.",
                expectedMinimumVersion,
                "numeric dotted version"
            ));
            return;
        }
        if (expectedMinimumVersion != null) {
            int comparison = compareDottedVersion(configuredVersion, expectedMinimumVersion);
            if (comparison < 0) {
                blockers.add(checkKey + " lower than app-definition minimum");
                checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                    checkKey,
                    "blocked",
                    platformName + " minimum version must not be lower than the app-definition release policy.",
                    configuredVersion,
                    ">= " + expectedMinimumVersion
                ));
                return;
            }
            if (comparison > 0) {
                warnings.add(checkKey + " higher than app-definition minimum; sync App Store/legal wording before release");
                checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                    checkKey,
                    "warning",
                    platformName + " minimum version is stricter than app-definition; confirm App Store metadata and legal wording are updated.",
                    configuredVersion,
                    expectedMinimumVersion
                ));
                return;
            }
        }
        checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
            checkKey,
            "ready",
            platformName + " minimum version is aligned with the app release policy.",
            configuredVersion,
            expectedMinimumVersion == null ? "app-specific" : expectedMinimumVersion
        ));
    }

    private boolean isValidDottedVersion(String value) {
        return value != null && value.trim().matches("\\d+(\\.\\d+){0,2}");
    }

    private int compareDottedVersion(String left, String right) {
        String[] leftParts = left == null ? new String[0] : left.trim().split("\\.");
        String[] rightParts = right == null ? new String[0] : right.trim().split("\\.");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < max; index++) {
            int leftValue = index < leftParts.length ? parseVersionPart(leftParts[index]) : 0;
            int rightValue = index < rightParts.length ? parseVersionPart(rightParts[index]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private int parseVersionPart(String value) {
        try {
            return Integer.parseInt(value == null ? "0" : value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void addReleaseApiBaseUrlCheck(
        List<SystemReleaseGateView.ReleaseGateCheckView> checks,
        List<String> blockers,
        AppModule module,
        Map<String, Object> items
    ) {
        String key = releaseApiBaseUrlKeys(module).stream()
            .filter(items::containsKey)
            .findFirst()
            .orElse(module.internalDomain() + "_api_base_url");
        String apiBaseUrl = normalizeConfigValue(items.get(key));
        String checkKey = module.appCode() + ".release_ios." + key;
        if (apiBaseUrl == null) {
            blockers.add(checkKey + " missing or placeholder");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "blocked",
                "release_ios api base URL must be configured for iOS release preflight. Accepted keys: " + releaseApiBaseUrlKeys(module),
                "missing",
                "https://..."
            ));
        } else if (!apiBaseUrl.startsWith("https://")) {
            blockers.add(checkKey + " must be https");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "blocked",
                "Release API base URL must be HTTPS for Apple distribution.",
                apiBaseUrl,
                "https://..."
            ));
        } else {
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "ready",
                "Release API base URL is configured.",
                apiBaseUrl,
                "https://..."
            ));
        }
    }

    private void addReleaseBundleIdentifierCheck(
        List<SystemReleaseGateView.ReleaseGateCheckView> checks,
        List<String> blockers,
        Map<String, Object> items,
        AppDefinition definition
    ) {
        String bundleIdentifier = normalizeConfigValue(items.get("bundle_identifier"));
        String checkKey = definition.code() + ".release_ios.bundle_identifier";
        if (bundleIdentifier == null) {
            blockers.add(checkKey + " missing or placeholder");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "blocked",
                "release_ios.bundle_identifier must match the iOS PRODUCT_BUNDLE_IDENTIFIER before release preflight.",
                "missing",
                "com.example.app"
            ));
            return;
        }

        String authClientId = normalizeConfigValue(definition.raw().get("app.auth.apple.clientId"));
        String billingBundleId = normalizeConfigValue(definition.raw().get("app.billing.appstore.bundleId"));
        List<String> mismatches = new ArrayList<>();
        if (authClientId != null && !bundleIdentifier.equals(authClientId)) {
            mismatches.add("auth.apple.clientId=" + authClientId);
        }
        if (billingBundleId != null && !bundleIdentifier.equals(billingBundleId)) {
            mismatches.add("billing.appstore.bundleId=" + billingBundleId);
        }
        if (!mismatches.isEmpty()) {
            blockers.add(checkKey + " mismatch against backend Apple config");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "blocked",
                "release_ios.bundle_identifier must stay aligned with Apple auth/billing bundle identity.",
                "release_ios.bundle_identifier=" + bundleIdentifier + ", " + String.join(", ", mismatches),
                "same bundle identifier in release_ios, auth.apple.clientId, and billing.appstore.bundleId"
            ));
            return;
        }

        checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
            checkKey,
            "ready",
            "release_ios.bundle_identifier is configured and aligned with backend Apple identity.",
            bundleIdentifier,
            "same bundle identifier in release_ios, auth.apple.clientId, and billing.appstore.bundleId"
        ));
    }

    /**
     * release_ios 下的 base URL 允许同时兼容三类键：
     *
     * <p>1. 通用键 `api_base_url`
     * 2. 产品身份键，例如 `paipai_api_base_url`
     * 3. 内部域名键，例如 `reading_api_base_url`
     *
     * <p>P3 阶段内部实现名仍保留 reading，但对外产品身份已经切到 paipai_readingcompanion。
     * 这里同时接受两种命名，避免 release gate 因内部域名/产品名并存而误报。</p>
     */
    private List<String> releaseApiBaseUrlKeys(AppModule module) {
        ArrayList<String> keys = new ArrayList<>();
        keys.add("api_base_url");
        String appPrefix = module.appCode();
        int separatorIndex = appPrefix.indexOf('_');
        if (separatorIndex > 0) {
            appPrefix = appPrefix.substring(0, separatorIndex);
        }
        keys.add(appPrefix + "_api_base_url");
        keys.add(module.internalDomain() + "_api_base_url");
        return keys.stream().distinct().toList();
    }

    private void addBundleConsistencyCheck(
        List<SystemReleaseGateView.ReleaseGateCheckView> checks,
        List<String> blockers,
        AppDefinition definition
    ) {
        String authClientId = normalizeConfigValue(definition.raw().get("app.auth.apple.clientId"));
        String billingBundleId = normalizeConfigValue(definition.raw().get("app.billing.appstore.bundleId"));
        String checkKey = definition.code() + ".bundleIdentityConsistency";
        if (authClientId != null && billingBundleId != null && !authClientId.equals(billingBundleId)) {
            blockers.add(checkKey + " mismatch between auth.apple.clientId and billing.appstore.bundleId");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "blocked",
                "Apple auth clientId and billing bundleId must match the iOS PRODUCT_BUNDLE_IDENTIFIER for the same app.",
                "auth.apple.clientId=" + authClientId + ", billing.appstore.bundleId=" + billingBundleId,
                "same bundle identifier in both auth and billing config"
            ));
            return;
        }
        checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
            checkKey,
            "ready",
            "Apple auth clientId and billing bundleId are aligned for this app.",
            authClientId == null && billingBundleId == null ? "not-configured" : "auth.apple.clientId=" + authClientId + ", billing.appstore.bundleId=" + billingBundleId,
            "same bundle identifier in both auth and billing config"
        ));
    }

    /**
     * 校验 release_ios 中登记的 App Store 商品 ID 与 app-definition 中的计费映射一致。
     *
     * <p>统一后端可以同时承载多个 App，但每个 App 的商品 ID 必须按 appCode 隔离。
     * 这里把 release_ios.product_ids 与 app.billing.entitlements.productMappings.* 做集合比对，
     * 避免不同商品 ID 在上线门禁中互相串用。</p>
     */
    private void addReleaseProductIdConsistencyCheck(
        List<SystemReleaseGateView.ReleaseGateCheckView> checks,
        List<String> blockers,
        Map<String, Object> items,
        AppDefinition definition
    ) {
        String checkKey = definition.code() + ".release_ios.product_ids";
        List<String> definitionProductIds = billingProductIds(definition);
        if (!definition.support().billingRequired() && definitionProductIds.isEmpty()) {
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "ready",
                "App Store product ID alignment is not required because this app does not require billing.",
                "not-required",
                "billing apps declare product IDs in app-definition and release_ios"
            ));
            return;
        }
        if (definitionProductIds.isEmpty()) {
            blockers.add(definition.code() + ".billing.productMappings missing");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "blocked",
                "Billing-required apps must declare explicit productId -> entitlement mappings in app-definition.",
                "missing",
                "app.billing.entitlements.productMappings.*"
            ));
            return;
        }

        List<String> releaseProductIds = stringList(items.get("product_ids"));
        if (releaseProductIds.isEmpty()) {
            blockers.add(checkKey + " missing");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "blocked",
                "release_ios.product_ids must list the exact App Store product IDs used by this app before release.",
                "missing",
                definitionProductIds.toString()
            ));
            return;
        }

        List<String> missingFromRelease = definitionProductIds.stream()
            .filter(productId -> releaseProductIds.stream().noneMatch(other -> sameConfigValue(productId, other)))
            .toList();
        List<String> extraInRelease = releaseProductIds.stream()
            .filter(productId -> definitionProductIds.stream().noneMatch(other -> sameConfigValue(productId, other)))
            .toList();
        if (!missingFromRelease.isEmpty() || !extraInRelease.isEmpty()) {
            blockers.add(checkKey + " mismatch against billing productMappings");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "blocked",
                "release_ios.product_ids must match app.billing.entitlements.productMappings exactly for this appCode.",
                "release_ios=" + releaseProductIds + ", billing=" + definitionProductIds + ", missing=" + missingFromRelease + ", extra=" + extraInRelease,
                definitionProductIds.toString()
            ));
            return;
        }

        checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
            checkKey,
            "ready",
            "release_ios.product_ids are aligned with app-definition billing product mappings.",
            releaseProductIds.toString(),
            definitionProductIds.toString()
        ));
    }

    private List<String> billingProductIds(AppDefinition definition) {
        String prefix = "app.billing.entitlements.productMappings.";
        return definition.raw().keySet().stream()
            .filter(key -> key != null && key.startsWith(prefix))
            .map(key -> key.substring(prefix.length()))
            .map(this::normalizeConfigValue)
            .filter(value -> value != null)
            .distinct()
            .sorted()
            .toList();
    }

    private List<String> stringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                .map(this::normalizeConfigValue)
                .filter(value -> value != null)
                .distinct()
                .sorted()
                .toList();
        }
        String value = normalizeConfigValue(raw);
        if (value == null) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
            .map(this::normalizeConfigValue)
            .filter(item -> item != null)
            .distinct()
            .sorted()
            .toList();
    }

    private boolean sameConfigValue(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private void addAppleRemoteExchangeCheck(
        List<SystemReleaseGateView.ReleaseGateCheckView> checks,
        List<String> blockers,
        AppDefinition definition
    ) {
        String checkKey = definition.code() + ".apple.remoteExchangeEnabled";
        boolean enabled = Boolean.parseBoolean(String.valueOf(definition.raw().getOrDefault("app.auth.apple.remoteExchangeEnabled", "false")));
        if (!enabled) {
            blockers.add(checkKey + " must be true");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "blocked",
                "Sign in with Apple formal session exchange must be enabled before release.",
                "false",
                "true"
            ));
            return;
        }
        checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
            checkKey,
            "ready",
            "Sign in with Apple formal session exchange is enabled.",
            "true",
            "true"
        ));
    }

    private void addProductionSandboxCheck(
        List<SystemReleaseGateView.ReleaseGateCheckView> checks,
        List<String> blockers,
        AppDefinition definition
    ) {
        String checkKey = definition.code() + ".billing.allowSandbox";
        boolean allowSandbox = Boolean.parseBoolean(String.valueOf(definition.raw().getOrDefault("app.billing.appstore.allowSandbox", "false")));
        if (allowSandbox) {
            blockers.add(checkKey + " must be false in production-like release config");
            checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
                checkKey,
                "blocked",
                "Production/TestFlight release preflight must fail closed when App Store sandbox traffic is still allowed.",
                "true",
                "false"
            ));
            return;
        }
        checks.add(new SystemReleaseGateView.ReleaseGateCheckView(
            checkKey,
            "ready",
            "App Store sandbox traffic is disabled for release preflight.",
            "false",
            "false"
        ));
    }

    private boolean requiresIosReleaseConfig(AppDefinition definition, Map<String, Object> items) {
        return normalizeConfigValue(definition.raw().get("app.auth.apple.clientId")) != null
            || normalizeConfigValue(definition.raw().get("app.billing.appstore.bundleId")) != null
            || (items != null && !items.isEmpty());
    }

    private Map<String, Object> loadNamespaceItems(String appCode, String namespaceCode) {
        try {
            RemoteConfigNamespaceView view = sysRemoteConfigService.loadNamespace(appCode, namespaceCode);
            return view == null || view.items() == null ? Map.of() : view.items();
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String normalizeConfigValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty()
            || value.startsWith("__FILL_FROM_DB_")
            || value.startsWith("__FILL_ME__")
            || value.startsWith("__PLACEHOLDER__")) {
            return null;
        }
        return value;
    }

    @Operation(summary = "查询公开入口清单", description = "返回当前故意保持公网可达的接口清单。")
    @GetMapping("/public-surface")
    public ApiResponse<SystemPublicSurfaceView> publicSurface() {
        return ApiResponse.success(currentRequestId(), new SystemPublicSurfaceView(publicSurfaceEndpoints()));
    }

    private List<SystemPublicSurfaceView.PublicEndpointView> publicSurfaceEndpoints() {
        ArrayList<SystemPublicSurfaceView.PublicEndpointView> endpoints = new ArrayList<>();
        endpoints.add(new SystemPublicSurfaceView.PublicEndpointView(
            "GET",
            "/api/v1/system/healthz",
            "public",
            "Minimal health response; prod redacts detailed app metadata."
        ));
        endpoints.add(new SystemPublicSurfaceView.PublicEndpointView(
            "GET",
            "/actuator/health",
            "public",
            "Spring Boot actuator health endpoint; prod profile exposes only health and hides details."
        ));
        endpoints.add(new SystemPublicSurfaceView.PublicEndpointView(
            "POST",
            "/api/v1/system/auth/apps/{appCode}/apple/exchange",
            "public",
            "AppCode-scoped Sign in with Apple exchange entry for all apps; protected by Apple identity token verification and downstream session issuance rules. Compatibility auth routes without appCode are not exposed."
        ));
        if (appDefinitionService.list().stream().anyMatch(publicAuthAccessPolicyService::demoSessionsEnabled)) {
            endpoints.add(new SystemPublicSurfaceView.PublicEndpointView(
                "POST",
                "/api/v1/system/auth/apps/{appCode}/sessions/demo",
                "public_when_app_explicitly_enables_demo",
                "AppCode-scoped demo session entry; disabled by default and only inventoried when an app explicitly opts in via app.auth.demoSessionEnabled."
            ));
        }
        endpoints.add(new SystemPublicSurfaceView.PublicEndpointView(
            "POST",
            "/api/v1/system/appstore/apps/{appCode}/notifications",
            "public",
            "Generic appCode-scoped App Store Server Notification route; Apple JWS signature verification + notification UUID dedupe protect processing."
        ));
        endpoints.add(new SystemPublicSurfaceView.PublicEndpointView(
            "POST",
            "/api/v1/webhooks/app-store/notifications",
            "public",
            "Public App Store webhook compatibility route for reading; Apple JWS signature verification + notification UUID dedupe protect processing."
        ));
        return List.copyOf(endpoints);
    }

    @Operation(summary = "查询权益观测", description = "查询指定应用权益、刷新候选和待处理交易观测信息。")
    @GetMapping("/apps/{appCode}/billing/entitlements/observability")
    public ApiResponse<EntitlementObservabilityView> entitlementObservability(@Parameter(description = "应用编码，例如 paipai_readingcompanion。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @PathVariable String appCode) {
        appDefinitionService.get(appCode)
            .orElseThrow(() -> new AppNotFoundException(appCode));
        return ApiResponse.success(currentRequestId(), sysBillingService.describeEntitlementObservability(appCode));
    }

    private AppAppleOpsGateView buildAppleOpsGate(AppDefinition definition) {
        String appCode = definition.code();
        AppAppleReadinessView readiness = appAppleReadinessService.inspect(definition);
        AppAppleTokenStorageView tokenStorage = buildTokenStorage(appCode);
        EntitlementObservabilityView entitlementObservability = sysBillingService.describeEntitlementObservability(appCode);
        AppStoreNotificationObservabilityView notificationObservability = sysAppStoreNotificationService.describeObservability(appCode);

        java.util.ArrayList<String> blockers = new java.util.ArrayList<>(readiness.blockers());
        java.util.ArrayList<String> warnings = new java.util.ArrayList<>(readiness.warnings());
        java.util.ArrayList<AppAppleOpsGateView.OpsGateCheckView> checks = new java.util.ArrayList<>();

        checks.add(new AppAppleOpsGateView.OpsGateCheckView(
            "authReadiness",
            readiness.auth().required() ? readiness.auth().status() : "not_required",
            readiness.auth().required() ? "Sign in with Apple runtime readiness." : "This app does not require Sign in with Apple."
        ));
        checks.add(new AppAppleOpsGateView.OpsGateCheckView(
            "serverApiReadiness",
            readiness.appStore().required() ? readiness.appStore().status() : "not_required",
            readiness.appStore().required() ? "App Store Server API credential completeness." : "This app does not require App Store billing."
        ));

        if (tokenStorage.plaintextFallbackPresent()) {
            blockers.add("auth.apple.refreshTokenPlaintextFallback present");
            checks.add(new AppAppleOpsGateView.OpsGateCheckView(
                "tokenStorage",
                "blocked",
                "Plaintext Apple refresh-token fallback still exists and should be rotated out before production release."
            ));
        } else {
            checks.add(new AppAppleOpsGateView.OpsGateCheckView(
                "tokenStorage",
                "ready",
                "No plaintext Apple refresh-token fallback was detected."
            ));
        }

        addPublicEntryChecks(definition, checks, blockers, warnings);

        if (definition.support().billingRequired() && entitlementObservability.effectiveMappingCount() == 0) {
            if ("prod".equalsIgnoreCase(environment)) {
                blockers.add("billing.entitlements.mapping empty; productId fallback is not allowed in prod");
                checks.add(new AppAppleOpsGateView.OpsGateCheckView(
                    "entitlementMappings",
                    "blocked",
                    "No explicit entitlement mapping is configured; production must not rely on productId fallback."
                ));
            } else {
                warnings.add("billing.entitlements.mapping empty; productId fallback will be used");
                checks.add(new AppAppleOpsGateView.OpsGateCheckView(
                    "entitlementMappings",
                    "warning",
                    "No explicit entitlement mapping is configured; productId fallback will be used."
                ));
            }
        } else {
            checks.add(new AppAppleOpsGateView.OpsGateCheckView(
                "entitlementMappings",
                "ready",
                "Effective entitlement mappings are present."
            ));
        }

        if ("prod".equalsIgnoreCase(environment) && !readiness.appStore().productionSandboxSafe()) {
            blockers.add("billing.appstore.allowSandbox must be false in production");
        }

        if (notificationObservability.failed() > 0 || notificationObservability.rejected() > 0) {
            warnings.add("appstore.notifications recent failures or rejections present");
            checks.add(new AppAppleOpsGateView.OpsGateCheckView(
                "notificationPipeline",
                "warning",
                "Recent App Store notifications include failed/rejected items."
            ));
        } else if (definition.support().billingRequired() && notificationObservability.total() == 0) {
            warnings.add("appstore.notifications have not been observed in this environment yet");
            checks.add(new AppAppleOpsGateView.OpsGateCheckView(
                "notificationPipeline",
                "warning",
                "No App Store notifications have been observed in this environment yet."
            ));
        } else if (definition.support().billingRequired() && notificationObservability.reconciled() == 0) {
            warnings.add("appstore.notifications not yet reconciled in this environment");
            checks.add(new AppAppleOpsGateView.OpsGateCheckView(
                "notificationPipeline",
                "warning",
                "No reconciled App Store notifications have been observed in this environment yet."
            ));
        } else {
            checks.add(new AppAppleOpsGateView.OpsGateCheckView(
                "notificationPipeline",
                "ready",
                "Recent App Store notifications look healthy."
            ));
        }

        String status = blockers.isEmpty()
            ? (warnings.isEmpty() ? "ready" : "warning")
            : "blocked";

        return new AppAppleOpsGateView(
            appCode,
            status,
            readiness,
            tokenStorage,
            entitlementObservability,
            notificationObservability,
            List.copyOf(checks),
            List.copyOf(blockers),
            List.copyOf(warnings)
        );
    }

    private void addPublicEntryChecks(
        AppDefinition definition,
        List<AppAppleOpsGateView.OpsGateCheckView> checks,
        List<String> blockers,
        List<String> warnings
    ) {
        boolean demoEnabled = publicAuthAccessPolicyService.demoSessionsEnabled(definition);
        if (definition.support().appleSignInRequired()) {
            if (demoEnabled) {
                blockers.add("public.demoSession enabled for Apple-sign-in app");
                checks.add(new AppAppleOpsGateView.OpsGateCheckView(
                    "publicDemoSession",
                    "blocked",
                    "Apple-sign-in apps must not expose demo sessions."
                ));
            } else {
                checks.add(new AppAppleOpsGateView.OpsGateCheckView(
                    "publicDemoSession",
                    "ready",
                    "Demo sessions are disabled; Apple sign-in is the public auth entry."
                ));
            }
            return;
        }

        boolean bootstrapEnabled = publicAuthAccessPolicyService.bootstrapSessionsEnabled(definition);
        if (!bootstrapEnabled) {
            blockers.add("public.bootstrapSession disabled");
            checks.add(new AppAppleOpsGateView.OpsGateCheckView(
                "publicBootstrap",
                "blocked",
                "Bootstrap endpoint is disabled and this app has no required Apple sign-in fallback."
            ));
        } else {
            checks.add(new AppAppleOpsGateView.OpsGateCheckView(
                "publicBootstrap",
                "ready",
                "Bootstrap endpoint is enabled for this non-Apple-sign-in app."
            ));
        }
    }

    private AppAppleTokenStorageView buildTokenStorage(String appCode) {
        int total = authDataService.countProviderTokens(appCode, APPLE_PROVIDER);
        int encrypted = authDataService.countEncryptedRefreshTokens(appCode, APPLE_PROVIDER);
        int plaintext = authDataService.countPlaintextRefreshTokenFallbacks(appCode, APPLE_PROVIDER);
        return new AppAppleTokenStorageView(
            appCode,
            total,
            encrypted,
            plaintext,
            plaintext > 0
        );
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class AppNotFoundException extends RuntimeException {
        private AppNotFoundException(String appCode) {
            super("App definition not found: " + appCode);
        }
    }

    private String currentRequestId() {
        String requestId = org.slf4j.MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
