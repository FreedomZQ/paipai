import Foundation

final class PowerSyncUploadAPI {
    private let backendClient: BackendClient
    private let installationStore: PowerSyncInstallationStore

    init(backendClient: BackendClient, installationStore: PowerSyncInstallationStore) {
        self.backendClient = backendClient
        self.installationStore = installationStore
    }

    func upload(scope: String, changes: [PowerSyncChangeItemPayload]) async throws -> PowerSyncUploadResult {
        try await backendClient.powerSyncUpload(
            installationId: installationStore.installationId(scope: scope),
            changes: changes
        )
    }
}
