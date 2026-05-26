import Foundation

/// 周报历史本地缓存。
///
/// 第一版只缓存后端已经生成的历史周报结果，用于后端服务掉线时展示“离线历史预览”。
/// 不在本地伪造权益、不缓存原始图片/音频/OCR 全文，降低个人开发者的合规和运维风险。
final class WeeklyReportLocalCache {
    private struct CachedHistory: Codable {
        let cachedAt: String
        let accountId: String
        let planCode: String
        let scope: String
        let childId: String
        let history: WeeklyParentReportHistory
    }

    private let fileManager: FileManager
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    init(fileManager: FileManager = .default) {
        self.fileManager = fileManager
        self.encoder = JSONEncoder()
        self.decoder = JSONDecoder()
    }

    func save(
        _ history: WeeklyParentReportHistory,
        accountId: String,
        planCode: String?,
        childId: String?,
        scope: String
    ) {
        guard history.offlineHistoryPreviewEnabled || !history.reports.isEmpty else { return }
        let normalizedPlanCode = planCode ?? "free"
        guard normalizedPlanCode != "free" else { return }
        let record = CachedHistory(
            cachedAt: ISO8601DateFormatter().string(from: Date()),
            accountId: accountId,
            planCode: normalizedPlanCode,
            scope: scope,
            childId: childId?.isEmpty == false ? childId! : "family",
            history: clipped(history, planCode: normalizedPlanCode)
        )
        do {
            let url = try cacheURL(accountId: accountId, planCode: normalizedPlanCode, childId: childId, scope: scope)
            try fileManager.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try encoder.encode(record).write(to: url, options: [.atomic])
        } catch {
            // 周报缓存是离线体验增强，不应阻断主流程。
        }
    }

    func load(
        accountId: String,
        planCode: String?,
        childId: String?,
        scope: String
    ) -> WeeklyParentReportHistory? {
        let normalizedPlanCode = planCode ?? "free"
        guard normalizedPlanCode != "free" else { return nil }
        do {
            let url = try cacheURL(accountId: accountId, planCode: normalizedPlanCode, childId: childId, scope: scope)
            let data = try Data(contentsOf: url)
            let record = try decoder.decode(CachedHistory.self, from: data)
            guard record.accountId == accountId,
                  record.planCode == normalizedPlanCode,
                  record.scope == scope else { return nil }
            return clipped(record.history, planCode: normalizedPlanCode)
        } catch {
            return nil
        }
    }

    func clear(accountId: String) {
        try? clearOrThrow(accountId: accountId)
    }

    func clearOrThrow(accountId: String) throws {
        do {
            let root = try rootDirectory().appendingPathComponent(safe(accountId), isDirectory: true)
            if fileManager.fileExists(atPath: root.path) {
                try fileManager.removeItem(at: root)
            }
        } catch {
            throw error
        }
    }

    private func clipped(_ history: WeeklyParentReportHistory, planCode: String) -> WeeklyParentReportHistory {
        let maxWeeks = planCode.contains("family") ? 12 : 4
        return WeeklyParentReportHistory(
            scope: history.scope,
            childId: history.childId,
            isPremiumPlan: history.isPremiumPlan,
            historyWeeks: min(history.historyWeeks ?? maxWeeks, maxWeeks),
            availableHistoryWeeks: min(history.availableHistoryWeeks ?? history.reports.count, maxWeeks),
            reports: Array(history.reports.prefix(maxWeeks)),
            offlineHistoryPreviewEnabled: history.offlineHistoryPreviewEnabled
        )
    }

    private func cacheURL(accountId: String, planCode: String, childId: String?, scope: String) throws -> URL {
        try rootDirectory()
            .appendingPathComponent(safe(accountId), isDirectory: true)
            .appendingPathComponent(safe(planCode), isDirectory: true)
            .appendingPathComponent(safe(scope), isDirectory: true)
            .appendingPathComponent(safe(childId?.isEmpty == false ? childId! : "family") + ".json")
    }

    private func rootDirectory() throws -> URL {
        let base = try fileManager.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        return base.appendingPathComponent("weekly-report-v1", isDirectory: true)
    }

    private func safe(_ raw: String) -> String {
        raw.replacingOccurrences(of: "[^A-Za-z0-9._-]", with: "-", options: .regularExpression)
    }
}

struct LocalWeeklyReportRecord: Identifiable, Codable, Hashable {
    let id: String
    let childId: String
    let childName: String
    let weekStart: String
    let weekEnd: String
    let localeCode: String
    let report: WeeklyParentReport
    let openCount: Int
    let generatedAt: String
    let lastOpenedAt: String?

    var isUnread: Bool { openCount == 0 }
}

struct WeeklyReportGenerationResult {
    let generatedCount: Int
    let latestUnread: LocalWeeklyReportRecord?
}

final class LocalWeeklyReportRepository {
    private let database: LocalDatabase
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init(database: LocalDatabase) {
        self.database = database
    }

    func ensureReports(children: [ChildProfile], localeCode: String, earliestReportWeekStart: Date?, now: Date = Date()) async -> WeeklyReportGenerationResult {
        guard !children.isEmpty else {
            return WeeklyReportGenerationResult(generatedCount: 0, latestUnread: await latestUnreadReport(earliestReportWeekStart: earliestReportWeekStart))
        }
        await deleteReportsBefore(earliestReportWeekStart: earliestReportWeekStart)
        let completedWeeks = completedWeekStartsNeedingReports(now: now, earliestReportWeekStart: earliestReportWeekStart)
        guard !completedWeeks.isEmpty else {
            return WeeklyReportGenerationResult(generatedCount: 0, latestUnread: await latestUnreadReport(earliestReportWeekStart: earliestReportWeekStart))
        }

        var generatedCount = 0
        for child in children where !child.isDeleted {
            let existing = Set(await reportWeekStarts(childId: child.id))
            for weekStart in completedWeeks where !existing.contains(AppClock.dateOnly(from: weekStart)) {
                if await insertGeneratedReport(child: child, weekStart: weekStart, localeCode: localeCode) {
                    generatedCount += 1
                }
            }
        }
        return WeeklyReportGenerationResult(generatedCount: generatedCount, latestUnread: await latestUnreadReport(earliestReportWeekStart: earliestReportWeekStart))
    }

    func loadHistory(childId: String, months: Int = 3, earliestReportWeekStart: Date? = nil) async -> [LocalWeeklyReportRecord] {
        let cutoff = Calendar(identifier: .gregorian).date(byAdding: .month, value: -max(months, 1), to: Date()) ?? Date()
        let effectiveCutoff: Date
        if let earliestReportWeekStart {
            effectiveCutoff = max(Self.startOfWeek(containing: cutoff, calendar: Calendar(identifier: .gregorian)), earliestReportWeekStart)
        } else {
            effectiveCutoff = cutoff
        }
        let cutoffKey = AppClock.dateOnly(from: effectiveCutoff)
        return await loadRecords(
            sql: """
                SELECT id, child_id, child_name, week_start, week_end, locale_code, report_json, open_count, generated_at, last_opened_at
                FROM \(ReadingLocalTableName.weeklyReport)
                WHERE child_id = ? AND week_start >= ?
                ORDER BY week_start DESC
                """,
            parameters: [childId, cutoffKey]
        )
    }

    func latestUnreadReport(earliestReportWeekStart: Date? = nil) async -> LocalWeeklyReportRecord? {
        var sql = """
            SELECT id, child_id, child_name, week_start, week_end, locale_code, report_json, open_count, generated_at, last_opened_at
            FROM \(ReadingLocalTableName.weeklyReport)
            """
        var parameters: [Any?] = []
        if let earliestReportWeekStart {
            sql += " WHERE week_start >= ?"
            parameters.append(AppClock.dateOnly(from: earliestReportWeekStart))
        }
        sql += """

            ORDER BY week_start DESC, generated_at DESC
            LIMIT 1
            """
        return await loadRecords(
            sql: sql,
            parameters: parameters
        )
        .first
        .flatMap { $0.openCount == 0 ? $0 : nil }
    }

    func markOpened(reportId: String) async {
        let now = AppClock.nowString()
        _ = try? await database.execute(
            sql: """
                UPDATE \(ReadingLocalTableName.weeklyReport)
                SET open_count = COALESCE(open_count, 0) + 1,
                    last_opened_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
            parameters: [now, now, reportId]
        )
    }

    func clear() async {
        _ = try? await database.execute(
            sql: "DELETE FROM \(ReadingLocalTableName.weeklyReport)",
            parameters: []
        )
    }

    private func insertGeneratedReport(child: ChildProfile, weekStart: Date, localeCode: String) async -> Bool {
        guard let weekEnd = Calendar(identifier: .gregorian).date(byAdding: .day, value: 6, to: weekStart) else { return false }
        let weekStartKey = AppClock.dateOnly(from: weekStart)
        let weekEndKey = AppClock.dateOnly(from: weekEnd)
        let stats = await weeklyStats(childId: child.id, weekStart: weekStart, weekEnd: weekEnd)
        let daily = await dailyUsageMinutes(childId: child.id, weekStart: weekStart)
        let report = buildReport(
            child: child,
            weekStart: weekStartKey,
            weekEnd: weekEndKey,
            localeCode: localeCode,
            stats: stats,
            dailyMinutes: daily
        )
        guard let data = try? encoder.encode(report),
              let json = String(data: data, encoding: .utf8) else {
            return false
        }
        let now = AppClock.nowString()
        let id = "weekly|\(child.id)|\(weekStartKey)"
        do {
            try await database.execute(
                sql: """
                    INSERT OR IGNORE INTO \(ReadingLocalTableName.weeklyReport)
                    (id, app_code, child_id, child_name, week_start, week_end, locale_code, report_json, open_count, generated_at, last_opened_at, created_at, updated_at)
                    VALUES (?, '\(AppIdentity.appCode)', ?, ?, ?, ?, ?, ?, 0, ?, NULL, ?, ?)
                    """,
                parameters: [id, child.id, child.nickname, weekStartKey, weekEndKey, localeCode, json, now, now, now]
            )
            return true
        } catch {
            return false
        }
    }

    private func buildReport(child: ChildProfile, weekStart: String, weekEnd: String, localeCode: String, stats: WeeklyParentReportStats, dailyMinutes: [Int]) -> WeeklyParentReport {
        let isEnglish = !AppLocaleCatalog.normalize(localeCode).hasPrefix("zh")
        let active = stats.weeklyActiveDays
        let reviews = stats.weeklyReviewCount
        let cards = stats.displaySavedCardCount
        let summary = isEnglish
            ? "\(child.nickname) read on \(active) day\(active == 1 ? "" : "s") this week, completed \(reviews) review\(reviews == 1 ? "" : "s"), and kept \(cards) saved sentence card\(cards == 1 ? "" : "s")."
            : "\(child.nickname) 本周有 \(active) 天保持伴读，共完成 \(reviews) 次复习，句卡累计 \(cards) 张。"
        let highlight = active >= 4
            ? (isEnglish ? "A steady weekly rhythm is forming." : "本周陪读节奏比较稳定，已经形成连续回看的基础。")
            : (isEnglish ? "Small starts still count; one focused session can restart the rhythm." : "本周可以先从一次轻量陪读重新建立节奏。")
        let suggestion = stats.reviewDueCount ?? 0 > 0
            ? (isEnglish ? "Start next week with due cards, then add one new sentence after review." : "下周优先复习待巩固句卡，再补充 1 句新内容。")
            : (isEnglish ? "Keep one short read-aloud session on school days and save useful sentences." : "下周保持工作日短时伴读，遇到好句及时保存。")
        let suggestions = isEnglish
            ? ["Keep sessions short and repeatable.", "Review before adding new cards.", "Pick one familiar sentence for read-aloud practice."]
            : ["每次陪读保持短而稳定。", "先复习，再添加新句卡。", "挑一句熟悉内容做跟读练习。"]
        return WeeklyParentReport(
            id: "weekly|\(child.id)|\(weekStart)",
            scope: "child",
            weekStart: weekStart,
            weekEnd: weekEnd,
            isPremiumPlan: true,
            childId: child.id,
            childName: child.nickname,
            stats: stats,
            summary: summary,
            highlight: highlight,
            nextWeekSuggestion: suggestion,
            suggestions: suggestions,
            disclaimer: isEnglish ? "Generated locally from on-device reading activity. No external AI analysis is used." : "本报告根据设备内阅读记录按固定规则生成，未调用外部大模型分析。",
            generatedAt: AppClock.nowString(),
            planCode: "local",
            tier: "local",
            pageShareEnabled: true,
            exportReportEnabled: false,
            offlineHistoryPreviewEnabled: true,
            modules: [
                WeeklyReportModule.local(code: "daily_minutes", title: isEnglish ? "Daily reading minutes" : "每日伴读分钟", payload: [
                    "values": .array(dailyMinutes.map { .int($0) })
                ])
            ]
        )
    }

    private func weeklyStats(childId: String, weekStart: Date, weekEnd: Date) async -> WeeklyParentReportStats {
        let start = AppClock.string(from: weekStart)
        let endExclusive = AppClock.string(from: Calendar(identifier: .gregorian).date(byAdding: .day, value: 1, to: weekEnd) ?? weekEnd)
        let activeDays = await scalarInt(
            sql: """
                SELECT COUNT(DISTINCT substr(started_at, 1, 10)) AS count
                FROM \(ReadingLocalTableName.usageSession)
                WHERE child_id = ? AND deleted_at IS NULL AND started_at >= ? AND started_at < ?
                """,
            parameters: [childId, start, endExclusive]
        )
        let reviewCount = await scalarInt(
            sql: """
                SELECT COUNT(*) AS count
                FROM \(ReadingLocalTableName.reviewEvent)
                WHERE child_id = ? AND event_at >= ? AND event_at < ?
                """,
            parameters: [childId, start, endExclusive]
        )
        let weeklySaved = await scalarInt(
            sql: """
                SELECT COUNT(*) AS count
                FROM \(ReadingLocalTableName.reviewCard)
                WHERE child_id = ? AND deleted_at IS NULL AND created_at >= ? AND created_at < ?
                """,
            parameters: [childId, start, endExclusive]
        )
        let savedTotal = await scalarInt(
            sql: """
                SELECT COUNT(*) AS count
                FROM \(ReadingLocalTableName.reviewCard)
                WHERE child_id = ? AND deleted_at IS NULL
                """,
            parameters: [childId]
        )
        let due = await scalarInt(
            sql: """
                SELECT COUNT(*) AS count
                FROM \(ReadingLocalTableName.reviewCard)
                WHERE child_id = ? AND deleted_at IS NULL AND proficiency < 3
                """,
            parameters: [childId]
        )
        return WeeklyParentReportStats(
            weeklyActiveDays: activeDays,
            weeklyReviewCount: reviewCount,
            weeklySavedCardCount: weeklySaved,
            savedCardCount: savedTotal,
            currentStreakDays: activeDays,
            reviewDueCount: due,
            newSavedCardCount: weeklySaved
        )
    }

    private func dailyUsageMinutes(childId: String, weekStart: Date) async -> [Int] {
        let calendar = Calendar(identifier: .gregorian)
        var result: [Int] = []
        for offset in 0..<7 {
            guard let day = calendar.date(byAdding: .day, value: offset, to: weekStart),
                  let nextDay = calendar.date(byAdding: .day, value: 1, to: day) else {
                result.append(0)
                continue
            }
            let seconds = await scalarInt(
                sql: """
                    SELECT COALESCE(SUM(duration_seconds), 0) AS count
                    FROM \(ReadingLocalTableName.usageSession)
                    WHERE child_id = ? AND deleted_at IS NULL AND started_at >= ? AND started_at < ?
                    """,
                parameters: [childId, AppClock.string(from: day), AppClock.string(from: nextDay)]
            )
            result.append(Int((Double(seconds) / 60.0).rounded()))
        }
        return result
    }

    private func reportWeekStarts(childId: String) async -> [String] {
        (try? await database.getAll(
            sql: "SELECT week_start FROM \(ReadingLocalTableName.weeklyReport) WHERE child_id = ?",
            parameters: [childId]
        ) { cursor in
            try cursor.getString(name: "week_start")
        }) ?? []
    }

    private func loadRecords(sql: String, parameters: [Any?]) async -> [LocalWeeklyReportRecord] {
        (try? await database.getAll(sql: sql, parameters: parameters) { cursor in
            let id = try cursor.getString(name: "id")
            let childId = try cursor.getString(name: "child_id")
            let childName = try cursor.getString(name: "child_name")
            let weekStart = try cursor.getString(name: "week_start")
            let weekEnd = try cursor.getString(name: "week_end")
            let localeCode = try cursor.getString(name: "locale_code")
            let openCount = try cursor.getIntOptional(name: "open_count") ?? 0
            let generatedAt = try cursor.getString(name: "generated_at")
            let lastOpenedAt = try cursor.getStringOptional(name: "last_opened_at")
            let json = try cursor.getString(name: "report_json")
            let report = (json.data(using: .utf8)).flatMap { try? self.decoder.decode(WeeklyParentReport.self, from: $0) } ?? WeeklyParentReport(
                scope: "child",
                weekStart: weekStart,
                weekEnd: weekEnd,
                isPremiumPlan: true,
                childId: childId,
                childName: childName,
                stats: .empty,
                summary: "",
                highlight: "",
                nextWeekSuggestion: ""
            )
            return LocalWeeklyReportRecord(
                id: id,
                childId: childId,
                childName: childName,
                weekStart: weekStart,
                weekEnd: weekEnd,
                localeCode: localeCode,
                report: report,
                openCount: openCount,
                generatedAt: generatedAt,
                lastOpenedAt: lastOpenedAt
            )
        }) ?? []
    }

    private func scalarInt(sql: String, parameters: [Any?]) async -> Int {
        (try? await database.getOptional(sql: sql, parameters: parameters) { cursor in
            try cursor.getIntOptional(name: "count") ?? 0
        }) ?? 0
    }

    func earliestReportWeekStart(afterAccountCreatedAt accountCreatedAt: Date?) -> Date? {
        guard let accountCreatedAt else { return nil }
        let calendar = Calendar(identifier: .gregorian)
        let registrationWeekStart = Self.startOfWeek(containing: accountCreatedAt, calendar: calendar)
        return calendar.date(byAdding: .day, value: 7, to: registrationWeekStart)
    }

    func earliestLocalActivityDate() async -> Date? {
        let values = (try? await database.getAll(
            sql: """
                SELECT created_at AS occurred_at FROM \(ReadingLocalTableName.childProfile) WHERE created_at IS NOT NULL
                UNION ALL
                SELECT started_at AS occurred_at FROM \(ReadingLocalTableName.usageSession) WHERE started_at IS NOT NULL
                UNION ALL
                SELECT event_at AS occurred_at FROM \(ReadingLocalTableName.reviewEvent) WHERE event_at IS NOT NULL
                UNION ALL
                SELECT event_at AS occurred_at FROM \(ReadingLocalTableName.learningEvent) WHERE event_at IS NOT NULL
                UNION ALL
                SELECT created_at AS occurred_at FROM \(ReadingLocalTableName.reviewCard) WHERE created_at IS NOT NULL
                ORDER BY occurred_at ASC
                LIMIT 1
                """,
            parameters: []
        ) { cursor in
            try cursor.getStringOptional(name: "occurred_at")
        }) ?? []
        return values.compactMap { AppClock.date(from: $0) }.first
    }

    private func deleteReportsBefore(earliestReportWeekStart: Date?) async {
        guard let earliestReportWeekStart else { return }
        _ = try? await database.execute(
            sql: "DELETE FROM \(ReadingLocalTableName.weeklyReport) WHERE week_start < ?",
            parameters: [AppClock.dateOnly(from: earliestReportWeekStart)]
        )
    }

    private func completedWeekStartsNeedingReports(now: Date, earliestReportWeekStart: Date?) -> [Date] {
        let calendar = Calendar(identifier: .gregorian)
        let rawCurrentWeek = Self.startOfWeek(containing: now, calendar: calendar)
        let generationMoment = calendar.date(byAdding: .second, value: 1, to: rawCurrentWeek) ?? rawCurrentWeek
        let currentWeek = now >= generationMoment
            ? rawCurrentWeek
            : (calendar.date(byAdding: .day, value: -7, to: rawCurrentWeek) ?? rawCurrentWeek)
        let earliest = calendar.date(byAdding: .month, value: -3, to: currentWeek) ?? currentWeek
        var weeks: [Date] = []
        var cursor = Self.startOfWeek(containing: earliest, calendar: calendar)
        if let earliestReportWeekStart {
            cursor = max(cursor, earliestReportWeekStart)
        }
        while cursor < currentWeek {
            weeks.append(cursor)
            guard let next = calendar.date(byAdding: .day, value: 7, to: cursor) else { break }
            cursor = next
        }
        return weeks
    }

    private static func startOfWeek(containing date: Date, calendar rawCalendar: Calendar) -> Date {
        var calendar = rawCalendar
        calendar.firstWeekday = 2
        let components = calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: date)
        return calendar.date(from: components) ?? date
    }
}

private extension WeeklyReportModule {
    static func local(code: String, title: String, payload: [String: LocalPayloadValue]) -> WeeklyReportModule {
        let data = try? JSONSerialization.data(withJSONObject: [
            "code": code,
            "title": title,
            "access": "full",
            "payload": payload.mapValues { $0.jsonValue }
        ])
        return (data.flatMap { try? JSONDecoder().decode(WeeklyReportModule.self, from: $0) }) ?? fallback(code: code, title: title)
    }

    private static func fallback(code: String, title: String) -> WeeklyReportModule {
        let data = """
        {"code":"\(code)","title":"\(title)","access":"full","payload":{}}
        """.data(using: .utf8)!
        return try! JSONDecoder().decode(WeeklyReportModule.self, from: data)
    }
}

private extension LocalPayloadValue {
    var jsonValue: Any {
        switch self {
        case let .string(value): return value
        case let .int(value): return value
        case let .double(value): return value
        case let .bool(value): return value
        case let .array(values): return values.map(\.jsonValue)
        case let .object(values): return values.mapValues(\.jsonValue)
        case .null: return NSNull()
        }
    }
}
