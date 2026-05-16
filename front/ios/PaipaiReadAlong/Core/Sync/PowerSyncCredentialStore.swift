import Foundation

final class PowerSyncCredentialStore {
    private let store = ScopedJSONStore(namespace: "\(AppIdentity.appCode).sync.credentials")

    func load(scope: String) -> StoredPowerSyncCredentials? {
        store.load(StoredPowerSyncCredentials?.self, scope: scope, fallback: nil)
    }

    func save(_ credentials: StoredPowerSyncCredentials, scope: String) {
        store.save(Optional(credentials), scope: scope)
    }

    func clear(scope: String) {
        store.clear(scope: scope)
    }
}
