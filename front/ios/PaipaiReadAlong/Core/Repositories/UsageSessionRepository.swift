import Foundation
import PowerSync

final class UsageSessionRepository {
    private let database: PowerSyncDatabaseProtocol

    init(database: PowerSyncDatabaseProtocol) {
        self.database = database
    }

    func loadAll() async -> [UsageSessionRecord] {
        (try? await database.getAll(
            sql: "SELECT id, child_id, source_page, started_at, ended_at, duration_seconds, client_platform, device_model, deleted_at, updated_at FROM \(ReadingSyncTableName.usageSession) ORDER BY COALESCE(updated_at, started_at, '') DESC",
            parameters: []
        ) { cursor in
            UsageSessionRecord(
                id: try cursor.getString(name: "id"),
                childId: try cursor.getString(name: "child_id"),
                sourcePage: try cursor.getString(name: "source_page"),
                startedAt: try cursor.getString(name: "started_at"),
                endedAt: try cursor.getStringOptional(name: "ended_at"),
                durationSeconds: try cursor.getIntOptional(name: "duration_seconds") ?? 0,
                clientPlatform: try cursor.getStringOptional(name: "client_platform"),
                deviceModel: try cursor.getStringOptional(name: "device_model"),
                deletedAt: try cursor.getStringOptional(name: "deleted_at"),
                updatedAt: try cursor.getStringOptional(name: "updated_at")
            )
        }) ?? []
    }

    @discardableResult
    func startSession(childId: String, sessionId: String, sourcePage: String, clientPlatform: String, deviceModel: String?) async -> UsageSessionRecord {
        let all = await loadAll()
        if let existing = all.first(where: { $0.id == sessionId && $0.endedAt == nil }) {
            return existing
        }
        let now = SyncClock.nowString()
        let record = UsageSessionRecord(
            id: sessionId,
            childId: childId,
            sourcePage: sourcePage,
            startedAt: now,
            endedAt: nil,
            durationSeconds: 0,
            clientPlatform: clientPlatform,
            deviceModel: deviceModel,
            deletedAt: nil,
            updatedAt: now
        )
        _ = try? await database.execute(
            sql: """
                INSERT OR REPLACE INTO \(ReadingSyncTableName.usageSession)
                (id, app_code, child_id, source_page, started_at, ended_at, duration_seconds, client_platform, device_model, deleted_at, created_at, updated_at)
                VALUES (?, '\(AppIdentity.appCode)', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            parameters: [
                record.id,
                record.childId,
                record.sourcePage,
                record.startedAt,
                record.endedAt,
                record.durationSeconds,
                record.clientPlatform,
                record.deviceModel,
                record.deletedAt,
                record.startedAt,
                record.updatedAt
            ]
        )
        return record
    }

    @discardableResult
    func endSession(sessionId: String) async -> UsageSessionRecord? {
        let sessions = await loadAll()
        guard let existing = sessions.first(where: { $0.id == sessionId }) else { return nil }
        let endedAt = SyncClock.nowString()
        let durationSeconds = max(1, Int((SyncClock.date(from: endedAt)?.timeIntervalSince(SyncClock.date(from: existing.startedAt) ?? Date()) ?? 1).rounded()))
        _ = try? await database.execute(
            sql: "UPDATE \(ReadingSyncTableName.usageSession) SET ended_at = ?, duration_seconds = ?, updated_at = ? WHERE id = ?",
            parameters: [endedAt, durationSeconds, endedAt, sessionId]
        )
        let refreshed = await loadAll()
        return refreshed.first(where: { $0.id == sessionId })
    }

    @discardableResult
    func tickSession(sessionId: String, at timestamp: String = SyncClock.nowString()) async -> UsageSessionRecord? {
        let sessions = await loadAll()
        guard let existing = sessions.first(where: { $0.id == sessionId }) else { return nil }
        guard existing.endedAt == nil else { return existing }
        let startDate = SyncClock.date(from: existing.startedAt) ?? Date()
        let tickDate = SyncClock.date(from: timestamp) ?? Date()
        let durationSeconds = max(1, Int(floor(tickDate.timeIntervalSince(startDate) / 60.0) * 60))
        _ = try? await database.execute(
            sql: "UPDATE \(ReadingSyncTableName.usageSession) SET duration_seconds = ?, updated_at = ? WHERE id = ?",
            parameters: [durationSeconds, timestamp, sessionId]
        )
        let refreshed = await loadAll()
        return refreshed.first(where: { $0.id == sessionId })
    }

    func familySummary(children: [ChildProfile], recentSummaryDays: Int = 7, retentionDays: Int = 30) async -> FamilyUsageSummary {
        await cleanupExpired(retentionDays: retentionDays)
        let loaded = await loadAll()
        let active = meteredUsageSessions(from: loaded)
        let today = Date()
        let todayString = SyncClock.dateOnly(from: today)
        let calendar = Calendar(identifier: .gregorian)
        let weekStart = calendar.date(byAdding: .day, value: -7, to: today) ?? today
        let todaySessions = active.filter { SyncClock.dateOnly(from: SyncClock.date(from: $0.startedAt) ?? today) == todayString }
        let total = active.reduce(0) { $0 + $1.durationSeconds }
        let weekly = active.filter { (SyncClock.date(from: $0.startedAt) ?? .distantPast) >= weekStart }.reduce(0) { $0 + $1.durationSeconds }
        let lastUsed = active.compactMap { $0.endedAt ?? $0.updatedAt ?? $0.startedAt }.sorted().last
        return FamilyUsageSummary(
            usageDate: todayString,
            todayDurationSeconds: todaySessions.reduce(0) { $0 + $1.durationSeconds },
            totalDurationSeconds: total,
            weeklyDurationSeconds: weekly,
            todaySessionCount: todaySessions.count,
            childCount: children.count,
            lastUsedAt: lastUsed,
            recentDailyUsage: recentDailyUsage(active, recentSummaryDays: recentSummaryDays),
            retentionDays: max(retentionDays, 1),
            recentSummaryDays: max(recentSummaryDays, 1),
            dayBoundary: "client_local"
        )
    }

    func childSummaries(children: [ChildProfile], recentSummaryDays: Int = 7, retentionDays: Int = 30) async -> [String: ChildUsageSummary] {
        await cleanupExpired(retentionDays: retentionDays)
        let loaded = await loadAll()
        let all = meteredUsageSessions(from: loaded)
        let today = Date()
        let todayString = SyncClock.dateOnly(from: today)
        let calendar = Calendar(identifier: .gregorian)
        let weekStart = calendar.date(byAdding: .day, value: -7, to: today) ?? today
        var result: [String: ChildUsageSummary] = [:]
        for child in children {
            let sessions = all.filter { $0.childId == child.id }
            let todaySessions = sessions.filter { SyncClock.dateOnly(from: SyncClock.date(from: $0.startedAt) ?? today) == todayString }
            result[child.id] = ChildUsageSummary(
                childId: child.id,
                childName: child.nickname,
                usageDate: todayString,
                todayDurationSeconds: todaySessions.reduce(0) { $0 + $1.durationSeconds },
                totalDurationSeconds: sessions.reduce(0) { $0 + $1.durationSeconds },
                weeklyDurationSeconds: sessions.filter { (SyncClock.date(from: $0.startedAt) ?? .distantPast) >= weekStart }.reduce(0) { $0 + $1.durationSeconds },
                todaySessionCount: todaySessions.count,
                lastUsedAt: sessions.compactMap { $0.endedAt ?? $0.updatedAt ?? $0.startedAt }.sorted().last,
                recentDailyUsage: recentDailyUsage(sessions, recentSummaryDays: recentSummaryDays),
                retentionDays: max(retentionDays, 1),
                recentSummaryDays: max(recentSummaryDays, 1),
                dayBoundary: "client_local"
            )
        }
        return result
    }

    func achievementActivity(childId: String) async -> ReadingAchievementActivity {
        let sessions = await loadAll()
            .filter { session in
                !session.isDeleted
                    && session.childId == childId
                    && session.durationSeconds > 0
                    && Self.achievementSourcePages.contains(session.sourcePage)
            }
        let activeDates = Set(sessions.map { SyncClock.dateOnly(from: SyncClock.date(from: $0.startedAt) ?? Date()) })
        return ReadingAchievementActivity(
            effectiveSessionCount: sessions.count,
            activeDayCount: activeDates.count,
            currentStreakDays: Self.currentStreakDays(activeDates: activeDates)
        )
    }

    private func recentDailyUsage(_ sessions: [UsageSessionRecord], recentSummaryDays: Int) -> [DailyUsagePoint] {
        let safeDays = min(max(recentSummaryDays, 1), 31)
        let calendar = Calendar(identifier: .gregorian)
        let today = Date()
        let dates = (0..<safeDays).compactMap { calendar.date(byAdding: .day, value: -(safeDays - 1) + $0, to: today) }
        let buckets = Dictionary(grouping: sessions) { SyncClock.dateOnly(from: SyncClock.date(from: $0.startedAt) ?? today) }
        return dates.map { date in
            let key = SyncClock.dateOnly(from: date)
            return DailyUsagePoint(
                usageDate: key,
                durationSeconds: (buckets[key] ?? []).reduce(0) { $0 + $1.durationSeconds }
            )
        }
    }

    private func meteredUsageSessions(from sessions: [UsageSessionRecord]) -> [UsageSessionRecord] {
        let active = sessions.filter { !$0.isDeleted }
        let appForeground = active.filter { $0.sourcePage == "app_foreground" }
        return appForeground.isEmpty ? active : appForeground
    }

    func clear() async {
        _ = try? await database.execute(sql: "DELETE FROM \(ReadingSyncTableName.usageSession)", parameters: [])
    }

    private func cleanupExpired(retentionDays: Int) async {
        let safeDays = min(max(retentionDays, 1), 366)
        guard let cutoff = Calendar(identifier: .gregorian).date(byAdding: .day, value: -safeDays, to: Date()) else { return }
        let cutoffString = SyncClock.string(from: cutoff)
        _ = try? await database.execute(
            sql: "DELETE FROM \(ReadingSyncTableName.usageSession) WHERE COALESCE(ended_at, updated_at, started_at) < ?",
            parameters: [cutoffString]
        )
    }

    private static let achievementSourcePages: Set<String> = [
        "learning_detail",
        "review",
        "capture"
    ]

    private static func currentStreakDays(activeDates: Set<String>) -> Int {
        guard !activeDates.isEmpty else { return 0 }
        let calendar = Calendar(identifier: .gregorian)
        let today = Date()
        let todayKey = SyncClock.dateOnly(from: today)
        let startDate: Date
        if activeDates.contains(todayKey) {
            startDate = today
        } else if let yesterday = calendar.date(byAdding: .day, value: -1, to: today),
                  activeDates.contains(SyncClock.dateOnly(from: yesterday)) {
            startDate = yesterday
        } else {
            return 0
        }

        var streak = 0
        var cursor = startDate
        while activeDates.contains(SyncClock.dateOnly(from: cursor)) {
            streak += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: cursor) else { break }
            cursor = previous
        }
        return streak
    }
}
