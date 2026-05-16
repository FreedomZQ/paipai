import Foundation

final class PowerSyncRejectionStore {
    private let store = ScopedJSONStore(namespace: "\(AppIdentity.appCode).sync.rejections")

    func load(scope: String) -> [PowerSyncRejectedItemView] {
        store.load([PowerSyncRejectedItemView].self, scope: scope, fallback: [])
    }

    func save(_ items: [PowerSyncRejectedItem], scope: String) {
        let mapped = items.map {
            PowerSyncRejectedItemView(
                entityType: $0.entityType,
                entityId: $0.entityId,
                reasonCode: $0.reasonCode,
                reasonMessage: $0.reasonMessage
            )
        }
        store.save(mapped, scope: scope)
    }

    func clear(scope: String) {
        store.clear(scope: scope)
    }
}
