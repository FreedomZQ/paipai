import Foundation

final class PowerSyncInstallationStore {
    private let store = ScopedJSONStore(namespace: "\(AppIdentity.appCode).sync.installation")

    func installationId(scope: String) -> String {
        let existing = store.load(String.self, scope: scope, fallback: "")
        if !existing.isEmpty { return existing }
        let generated = UUID().uuidString.lowercased()
        store.save(generated, scope: scope)
        return generated
    }

    func clear(scope: String) {
        store.clear(scope: scope)
    }
}
