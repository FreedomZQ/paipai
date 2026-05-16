import Foundation

final class SyncSettingsStore {
    private let store = ScopedJSONStore(namespace: "\(AppIdentity.appCode).sync.settings")

    func cloudSyncEnabled(scope: String) -> Bool {
        store.load(Bool.self, scope: scope, fallback: false)
    }

    func setCloudSyncEnabled(_ enabled: Bool, scope: String) {
        store.save(enabled, scope: scope)
    }

    func clear(scope: String) {
        store.clear(scope: scope)
    }
}
