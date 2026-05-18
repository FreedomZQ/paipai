import Foundation
import PowerSync

@MainActor
final class PowerSyncManager {
    let db: PowerSyncDatabaseProtocol

    private let connector: PowerSyncConnector
    private let rejectionStore: PowerSyncRejectionStore
    private let syncSettingsStore: SyncSettingsStore
    private let installationStore: PowerSyncInstallationStore
    private let scopeProvider: () -> String
    private let hasAuthenticatedSession: () -> Bool
    private var latestStatus: SyncRuntimeState = .idle

    init(
        connector: PowerSyncConnector,
        rejectionStore: PowerSyncRejectionStore,
        syncSettingsStore: SyncSettingsStore,
        installationStore: PowerSyncInstallationStore,
        scopeProvider: @escaping () -> String,
        hasAuthenticatedSession: @escaping () -> Bool,
        dbFilename: String = AppIdentity.powerSyncDatabaseFilename
    ) {
        self.db = PowerSyncDatabase(schema: ReadingPowerSyncSchema, dbFilename: dbFilename)
        self.connector = connector
        self.rejectionStore = rejectionStore
        self.syncSettingsStore = syncSettingsStore
        self.installationStore = installationStore
        self.scopeProvider = scopeProvider
        self.hasAuthenticatedSession = hasAuthenticatedSession
    }

    func currentState(scope: String) -> SyncRuntimeState {
        SyncRuntimeState(
            status: syncSettingsStore.cloudSyncEnabled(scope: scope) ? latestStatus.status : .disabled,
            installationId: installationStore.installationId(scope: scope),
            cloudSyncEnabled: syncSettingsStore.cloudSyncEnabled(scope: scope),
            initialSyncCompleted: latestStatus.initialSyncCompleted,
            pendingChangeCount: latestStatus.pendingChangeCount,
            lastSyncAt: latestStatus.lastSyncAt,
            lastErrorMessage: latestStatus.lastErrorMessage,
            rejectedItems: rejectionStore.load(scope: scope)
        )
    }

    func refreshState(scope: String) async -> SyncRuntimeState {
        latestStatus = await buildState(scope: scope)
        return latestStatus
    }

    func synchronize(scope: String, waitForFirstSync: Bool = false) async -> SyncRuntimeState {
        guard hasAuthenticatedSession() else {
            latestStatus = SyncRuntimeState(
                status: .disabled,
                installationId: installationStore.installationId(scope: scope),
                cloudSyncEnabled: false,
                initialSyncCompleted: false,
                pendingChangeCount: 0,
                lastSyncAt: nil,
                lastErrorMessage: nil,
                rejectedItems: []
            )
            return latestStatus
        }
        if !syncSettingsStore.cloudSyncEnabled(scope: scope) {
            try? await db.disconnect()
            latestStatus = await buildState(scope: scope, forcedStatus: .disabled)
            return latestStatus
        }
        do {
            try await db.connect(
                connector: connector,
                options: ConnectOptions(
                    appMetadata: [
                        "appCode": AppIdentity.appCode,
                        "surface": "ios"
                    ]
                )
            )
            if waitForFirstSync || latestStatus.initialSyncCompleted == false {
                try? await db.waitForFirstSync()
            }
            latestStatus = await buildState(scope: scope)
            return latestStatus
        } catch {
            if Self.isCancellationError(error) {
                latestStatus = await buildState(scope: scope)
                return latestStatus
            }
            latestStatus = await buildState(scope: scope, forcedStatus: .error, forcedError: error.localizedDescription)
            return latestStatus
        }
    }

    func requestRebuild(scope: String, reason: String?) async -> SyncRuntimeState {
        do {
            _ = try await connector.requestRebuild(reason: reason)
            try await db.disconnectAndClear(clearLocal: true, soft: false)
            rejectionStore.clear(scope: scope)
            return await synchronize(scope: scope, waitForFirstSync: true)
        } catch {
            if Self.isCancellationError(error) {
                latestStatus = await buildState(scope: scope)
                return latestStatus
            }
            latestStatus = await buildState(scope: scope, forcedStatus: .error, forcedError: error.localizedDescription)
            return latestStatus
        }
    }

    func disconnectForSignOut(scope: String) async {
        try? await db.disconnect()
        latestStatus = SyncRuntimeState(
            status: .disabled,
            installationId: installationStore.installationId(scope: scope),
            cloudSyncEnabled: false,
            initialSyncCompleted: false,
            pendingChangeCount: 0,
            lastSyncAt: nil,
            lastErrorMessage: nil,
            rejectedItems: rejectionStore.load(scope: scope)
        )
    }

    func clear(scope: String) async {
        try? await db.disconnectAndClear(clearLocal: true, soft: false)
        rejectionStore.clear(scope: scope)
        latestStatus = SyncRuntimeState.idle
    }



    private func buildState(scope: String, forcedStatus: PowerSyncRuntimeStatus? = nil, forcedError: String? = nil) async -> SyncRuntimeState {
        let status = db.currentStatus
        let pendingCount: Int
        if let batch = try? await db.getCrudBatch(limit: 500) {
            pendingCount = batch.crud.count
        } else {
            pendingCount = 0
        }
        let derivedStatus = forcedStatus ?? inferStatus(status: status, cloudSyncEnabled: syncSettingsStore.cloudSyncEnabled(scope: scope))
        let lastSyncedAt = status.lastSyncedAt.map { SyncClock.string(from: $0) }
        let lastErrorMessage = forcedError
            ?? stringifyError(status.anyError)
        return SyncRuntimeState(
            status: derivedStatus,
            installationId: installationStore.installationId(scope: scope),
            cloudSyncEnabled: syncSettingsStore.cloudSyncEnabled(scope: scope),
            initialSyncCompleted: status.hasSynced ?? false,
            pendingChangeCount: pendingCount,
            lastSyncAt: lastSyncedAt,
            lastErrorMessage: lastErrorMessage,
            rejectedItems: rejectionStore.load(scope: scope)
        )
    }

    private func inferStatus(status: SyncStatusData, cloudSyncEnabled: Bool) -> PowerSyncRuntimeStatus {
        guard cloudSyncEnabled else { return .disabled }
        if status.connecting { return .bootstrapping }
        if status.downloading || status.uploading { return .syncing }
        if status.anyError != nil { return .error }
        return .idle
    }

    private func stringifyError(_ value: Any?) -> String? {
        guard let value else { return nil }
        return String(describing: value)
    }

    private static func isCancellationError(_ error: Error) -> Bool {
        if error is CancellationError {
            return true
        }
        if let urlError = error as? URLError, urlError.code == .cancelled {
            return true
        }
        let nsError = error as NSError
        return nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorCancelled
    }
}
