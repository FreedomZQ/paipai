import Foundation

final class PowerSyncBootstrapAPI {
    private let backendClient: BackendClient
    private let installationStore: PowerSyncInstallationStore
    private let syncSettingsStore: SyncSettingsStore
    private let deviceInfoService: DeviceInfoService

    init(
        backendClient: BackendClient,
        installationStore: PowerSyncInstallationStore,
        syncSettingsStore: SyncSettingsStore,
        deviceInfoService: DeviceInfoService
    ) {
        self.backendClient = backendClient
        self.installationStore = installationStore
        self.syncSettingsStore = syncSettingsStore
        self.deviceInfoService = deviceInfoService
    }

    func bootstrap(scope: String) async throws -> PowerSyncBootstrapView {
        let installationId = installationStore.installationId(scope: scope)
        let device = deviceInfoService.currentDeviceInfo
        let hasConsent = deviceInfoService.hasAcceptedPrivacyConsent
        return try await backendClient.powerSyncBootstrap(
            installationId: installationId,
            deviceId: nil,
            clientPlatform: hasConsent ? device.deviceType.rawValue.lowercased() : "ios",
            deviceModel: hasConsent ? device.model : nil,
            appVersion: hasConsent ? device.appVersion : nil,
            cloudSyncEnabled: syncSettingsStore.cloudSyncEnabled(scope: scope),
            powersyncClientId: installationId
        )
    }

    func issueToken(scope: String) async throws -> PowerSyncTokenView {
        try await backendClient.powerSyncToken(installationId: installationStore.installationId(scope: scope))
    }

    func requestRebuild(scope: String, reason: String?) async throws -> PowerSyncRebuildView {
        try await backendClient.powerSyncRebuild(installationId: installationStore.installationId(scope: scope), reason: reason)
    }
}
