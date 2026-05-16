import Foundation
import PowerSync

final class LearningEventRepository {
    private let database: PowerSyncDatabaseProtocol

    init(database: PowerSyncDatabaseProtocol) {
        self.database = database
    }

    @discardableResult
    func append(childId: String, sourcePage: String) async -> Bool {
        let now = SyncClock.nowString()
        let learningDate = SyncClock.dateOnly(from: Date())
        do {
            try await database.execute(
                sql: """
                    INSERT INTO \(ReadingSyncTableName.learningEvent)
                    (id, app_code, child_id, learning_date, source_page, event_at, created_at, updated_at)
                    VALUES (?, '\(AppIdentity.appCode)', ?, ?, ?, ?, ?, ?)
                    """,
                parameters: [
                    UUID().uuidString.lowercased(),
                    childId,
                    learningDate,
                    sourcePage,
                    now,
                    now,
                    now
                ]
            )
            return true
        } catch {
            return false
        }
    }

    func count(childId: String, date: String = SyncClock.dateOnly(from: Date())) async -> Int {
        (try? await database.getOptional(
            sql: """
                SELECT COUNT(*) AS count
                FROM \(ReadingSyncTableName.learningEvent)
                WHERE child_id = ? AND learning_date = ?
                """,
            parameters: [childId, date]
        ) { cursor in
            try cursor.getIntOptional(name: "count") ?? 0
        }) ?? 0
    }

    func countAll(childId: String) async -> Int {
        (try? await database.getOptional(
            sql: """
                SELECT COUNT(*) AS count
                FROM \(ReadingSyncTableName.learningEvent)
                WHERE child_id = ?
                """,
            parameters: [childId]
        ) { cursor in
            try cursor.getIntOptional(name: "count") ?? 0
        }) ?? 0
    }

    func clear() async {
        _ = try? await database.execute(sql: "DELETE FROM \(ReadingSyncTableName.learningEvent)", parameters: [])
    }
}
