import Foundation
import PowerSync

@MainActor
final class PowerSyncConnector: PowerSyncBackendConnectorProtocol {
    private let bootstrapAPI: PowerSyncBootstrapAPI
    private let uploadAPI: PowerSyncUploadAPI
    private let credentialStore: PowerSyncCredentialStore
    private let rejectionStore: PowerSyncRejectionStore
    private let installationStore: PowerSyncInstallationStore
    private let scopeProvider: () -> String
    private let hasAuthenticatedSession: () -> Bool

    init(
        bootstrapAPI: PowerSyncBootstrapAPI,
        uploadAPI: PowerSyncUploadAPI,
        credentialStore: PowerSyncCredentialStore,
        rejectionStore: PowerSyncRejectionStore,
        installationStore: PowerSyncInstallationStore,
        scopeProvider: @escaping () -> String,
        hasAuthenticatedSession: @escaping () -> Bool
    ) {
        self.bootstrapAPI = bootstrapAPI
        self.uploadAPI = uploadAPI
        self.credentialStore = credentialStore
        self.rejectionStore = rejectionStore
        self.installationStore = installationStore
        self.scopeProvider = scopeProvider
        self.hasAuthenticatedSession = hasAuthenticatedSession
    }

    func fetchCredentials() async throws -> PowerSyncCredentials? {
        guard hasAuthenticatedSession() else {
            return nil
        }
        let scope = scopeProvider()
        let bootstrap = try await bootstrapAPI.bootstrap(scope: scope)
        guard bootstrap.cloudSyncEnabled else {
            return nil
        }
        let token = try await bootstrapAPI.issueToken(scope: scope)
        let credentials = PowerSyncCredentials(endpoint: token.endpoint, token: token.token)
        credentialStore.save(
            StoredPowerSyncCredentials(endpoint: token.endpoint, token: token.token, expiresAt: token.expiresAt),
            scope: scope
        )
        return credentials
    }

    func uploadData(database: PowerSyncDatabaseProtocol) async throws {
        let scope = scopeProvider()
        while let transaction = try await database.getNextCrudTransaction() {
            let items = transaction.crud.compactMap(mapCrudEntry)
            if items.isEmpty {
                try await transaction.complete()
                continue
            }
            let result = try await uploadAPI.upload(scope: scope, changes: items)
            if result.rejected.isEmpty {
                rejectionStore.clear(scope: scope)
            } else {
                rejectionStore.save(result.rejected, scope: scope)
            }
            try await transaction.complete()
        }
    }

    func requestRebuild(reason: String?) async throws -> PowerSyncRebuildView {
        try await bootstrapAPI.requestRebuild(scope: scopeProvider(), reason: reason)
    }

    private func mapCrudEntry(_ entry: any CrudEntry) -> PowerSyncChangeItemPayload? {
        guard let entityType = mapEntityType(table: entry.table) else {
            return nil
        }
        let operation = mapOperation(entry.op)
        if operation == "delete" {
            return PowerSyncChangeItemPayload(
                entityType: entityType,
                operation: operation,
                entityId: entry.id,
                clientUpdatedAt: SyncClock.nowString(),
                payload: ["id": .string(entry.id)]
            )
        }
        let payload = mapPayload(entry.opData, entityId: entry.id)
        return PowerSyncChangeItemPayload(
            entityType: entityType,
            operation: operation,
            entityId: entry.id,
            clientUpdatedAt: payload["updatedAt"]?.stringValue ?? payload["updated_at"]?.stringValue ?? SyncClock.nowString(),
            payload: payload
        )
    }

    private func mapEntityType(table: String) -> String? {
        switch table {
        case ReadingSyncTableName.childProfile:
            return "child_profile"
        case ReadingSyncTableName.reviewCard:
            return "review_card"
        case ReadingSyncTableName.reviewEvent:
            return "review_event"
        case ReadingSyncTableName.usageSession:
            return "usage_session"
        case ReadingSyncTableName.userPreference:
            return "user_preference"
        default:
            return nil
        }
    }

    private func mapOperation(_ updateType: UpdateType) -> String {
        switch updateType {
        case .delete:
            return "delete"
        case .put, .patch:
            return "upsert"
        }
    }

    private func mapPayload(_ opData: [String: String?]?, entityId: String) -> [String: PowerSyncPayloadValue] {
        guard let opData else {
            return ["id": .string(entityId)]
        }
        var payload: [String: PowerSyncPayloadValue] = [:]
        payload["id"] = .string(entityId)
        for (key, value) in opData {
            let normalizedKey = toCamelCase(key)
            if let value, !value.isEmpty {
                payload[normalizedKey] = .string(value)
            } else {
                payload[normalizedKey] = .null
            }
        }
        return payload
    }

    private func toCamelCase(_ value: String) -> String {
        let parts = value.split(separator: "_")
        guard let first = parts.first else { return value }
        return first.lowercased() + parts.dropFirst().map { $0.prefix(1).uppercased() + $0.dropFirst().lowercased() }.joined()
    }
}

private extension PowerSyncPayloadValue {
    var stringValue: String? {
        if case let .string(value) = self {
            return value
        }
        return nil
    }
}
