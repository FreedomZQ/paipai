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
        let hasPrivacyNoticeAccepted = deviceInfoService.hasAcceptedPrivacyConsent
        return try await backendClient.powerSyncBootstrap(
            installationId: installationId,
            deviceId: nil,
            // 中文说明：云同步 bootstrap 只传账号同步必要字段；设备型号属于可形成指纹的字段，不能因一般隐私告知确认而上传。
            clientPlatform: hasPrivacyNoticeAccepted ? "ios" : nil,
            deviceModel: nil,
            appVersion: hasPrivacyNoticeAccepted ? BackendClient.defaultAppVersion() : nil,
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
