import Foundation

struct WeeklyParentReport: Codable, Hashable, Identifiable {
    let id: String
    let scope: String
    let weekStart: String
    let weekEnd: String
    let isPremiumPlan: Bool
    let childId: String?
    let childName: String?
    let stats: WeeklyParentReportStats
    let summary: String
    let highlight: String
    let nextWeekSuggestion: String
    let suggestions: [String]
    let disclaimer: String?
    let childSummaries: [WeeklyChildReportSummary]
    let generatedAt: String?
    let planCode: String?
    let tier: String?
    let pageShareEnabled: Bool
    let exportReportEnabled: Bool
    let offlineHistoryPreviewEnabled: Bool
    let modules: [WeeklyReportModule]

    var hasDisplayContent: Bool {
        stats.hasActivity
            || !summary.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || !highlight.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || !nextWeekSuggestion.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || !suggestions.isEmpty
            || !childSummaries.isEmpty
            || !modules.isEmpty
    }

    init(
        id: String? = nil,
        scope: String,
        weekStart: String,
        weekEnd: String,
        isPremiumPlan: Bool,
        childId: String?,
        childName: String? = nil,
        stats: WeeklyParentReportStats,
        summary: String,
        highlight: String,
        nextWeekSuggestion: String,
        suggestions: [String] = [],
        disclaimer: String? = nil,
        childSummaries: [WeeklyChildReportSummary] = [],
        generatedAt: String? = nil,
        planCode: String? = nil,
        tier: String? = nil,
        pageShareEnabled: Bool = true,
        exportReportEnabled: Bool = false,
        offlineHistoryPreviewEnabled: Bool = false,
        modules: [WeeklyReportModule] = []
    ) {
        self.id = id ?? [scope, childId, weekStart, weekEnd].compactMap { $0 }.joined(separator: "|")
        self.scope = scope
        self.weekStart = weekStart
        self.weekEnd = weekEnd
        self.isPremiumPlan = isPremiumPlan
        self.childId = childId
        self.childName = childName
        self.stats = stats
        self.summary = summary
        self.highlight = highlight
        self.nextWeekSuggestion = nextWeekSuggestion
        self.suggestions = suggestions
        self.disclaimer = disclaimer
        self.childSummaries = childSummaries
        self.generatedAt = generatedAt
        self.planCode = planCode
        self.tier = tier
        self.pageShareEnabled = pageShareEnabled
        self.exportReportEnabled = exportReportEnabled
        self.offlineHistoryPreviewEnabled = offlineHistoryPreviewEnabled
        self.modules = modules
    }

    private enum CodingKeys: String, CodingKey {
        case id
        case scope
        case reportScope
        case weekStart
        case weekEnd
        case week_start
        case week_end
        case isPremiumPlan
        case childId
        case child_id
        case childName
        case childNickname
        case nickname
        case stats
        case summary
        case summaryText
        case summary_text
        case highlight
        case highlightText
        case highlight_text
        case nextWeekSuggestion
        case nextWeekSuggestionText
        case next_week_suggestion
        case suggestions
        case disclaimer
        case childSummaries
        case child_summaries
        case generatedAt
        case generated_at
        case planCode
        case plan_code
        case tier
        case pageShareEnabled
        case page_share_enabled
        case exportReportEnabled
        case export_report_enabled
        case offlineHistoryPreviewEnabled
        case offline_history_preview_enabled
        case modules
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let decodedScope = try container.decodeIfPresent(String.self, forKey: .scope)
            ?? container.decodeIfPresent(String.self, forKey: .reportScope)
            ?? "child"
        let decodedWeekStart = try container.decodeIfPresent(String.self, forKey: .weekStart)
            ?? container.decodeIfPresent(String.self, forKey: .week_start)
            ?? ""
        let decodedWeekEnd = try container.decodeIfPresent(String.self, forKey: .weekEnd)
            ?? container.decodeIfPresent(String.self, forKey: .week_end)
            ?? ""
        let decodedChildId = try container.decodeIfPresent(String.self, forKey: .childId)
            ?? container.decodeIfPresent(String.self, forKey: .child_id)

        id = try container.decodeIfPresent(String.self, forKey: .id)
            ?? [decodedScope, decodedChildId, decodedWeekStart, decodedWeekEnd].compactMap { $0 }.joined(separator: "|")
        scope = decodedScope
        weekStart = decodedWeekStart
        weekEnd = decodedWeekEnd
        isPremiumPlan = try container.decodeIfPresent(Bool.self, forKey: .isPremiumPlan) ?? false
        childId = decodedChildId
        childName = try container.decodeIfPresent(String.self, forKey: .childName)
            ?? container.decodeIfPresent(String.self, forKey: .childNickname)
            ?? container.decodeIfPresent(String.self, forKey: .nickname)
        stats = try container.decodeIfPresent(WeeklyParentReportStats.self, forKey: .stats) ?? .empty
        summary = try container.decodeIfPresent(String.self, forKey: .summary)
            ?? container.decodeIfPresent(String.self, forKey: .summaryText)
            ?? container.decodeIfPresent(String.self, forKey: .summary_text)
            ?? ""
        highlight = try container.decodeIfPresent(String.self, forKey: .highlight)
            ?? container.decodeIfPresent(String.self, forKey: .highlightText)
            ?? container.decodeIfPresent(String.self, forKey: .highlight_text)
            ?? ""
        nextWeekSuggestion = try container.decodeIfPresent(String.self, forKey: .nextWeekSuggestion)
            ?? container.decodeIfPresent(String.self, forKey: .nextWeekSuggestionText)
            ?? container.decodeIfPresent(String.self, forKey: .next_week_suggestion)
            ?? ""
        suggestions = try container.decodeIfPresent([String].self, forKey: .suggestions) ?? []
        disclaimer = try container.decodeIfPresent(String.self, forKey: .disclaimer)
        childSummaries = try container.decodeIfPresent([WeeklyChildReportSummary].self, forKey: .childSummaries)
            ?? container.decodeIfPresent([WeeklyChildReportSummary].self, forKey: .child_summaries)
            ?? []
        generatedAt = try container.decodeIfPresent(String.self, forKey: .generatedAt)
            ?? container.decodeIfPresent(String.self, forKey: .generated_at)
        planCode = try container.decodeIfPresent(String.self, forKey: .planCode)
            ?? container.decodeIfPresent(String.self, forKey: .plan_code)
        tier = try container.decodeIfPresent(String.self, forKey: .tier)
        pageShareEnabled = try container.decodeIfPresent(Bool.self, forKey: .pageShareEnabled)
            ?? container.decodeIfPresent(Bool.self, forKey: .page_share_enabled)
            ?? true
        exportReportEnabled = try container.decodeIfPresent(Bool.self, forKey: .exportReportEnabled)
            ?? container.decodeIfPresent(Bool.self, forKey: .export_report_enabled)
            ?? false
        offlineHistoryPreviewEnabled = try container.decodeIfPresent(Bool.self, forKey: .offlineHistoryPreviewEnabled)
            ?? container.decodeIfPresent(Bool.self, forKey: .offline_history_preview_enabled)
            ?? false
        modules = try container.decodeIfPresent([WeeklyReportModule].self, forKey: .modules) ?? []
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(scope, forKey: .scope)
        try container.encode(weekStart, forKey: .weekStart)
        try container.encode(weekEnd, forKey: .weekEnd)
        try container.encode(isPremiumPlan, forKey: .isPremiumPlan)
        try container.encodeIfPresent(childId, forKey: .childId)
        try container.encodeIfPresent(childName, forKey: .childName)
        try container.encode(stats, forKey: .stats)
        try container.encode(summary, forKey: .summary)
        try container.encode(highlight, forKey: .highlight)
        try container.encode(nextWeekSuggestion, forKey: .nextWeekSuggestion)
        try container.encode(suggestions, forKey: .suggestions)
        try container.encodeIfPresent(disclaimer, forKey: .disclaimer)
        try container.encode(childSummaries, forKey: .childSummaries)
        try container.encodeIfPresent(generatedAt, forKey: .generatedAt)
        try container.encodeIfPresent(planCode, forKey: .planCode)
        try container.encodeIfPresent(tier, forKey: .tier)
        try container.encode(pageShareEnabled, forKey: .pageShareEnabled)
        try container.encode(exportReportEnabled, forKey: .exportReportEnabled)
        try container.encode(offlineHistoryPreviewEnabled, forKey: .offlineHistoryPreviewEnabled)
        try container.encode(modules, forKey: .modules)
    }
}

struct WeeklyReportModule: Codable, Hashable, Identifiable {
    let code: String
    let title: String
    let access: String
    let payload: [String: PowerSyncPayloadValue]

    var id: String { code }

    private enum CodingKeys: String, CodingKey {
        case code
        case title
        case access
        case payload
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        code = try container.decodeIfPresent(String.self, forKey: .code) ?? UUID().uuidString
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? code
        access = try container.decodeIfPresent(String.self, forKey: .access) ?? "hidden"
        payload = try container.decodeIfPresent([String: PowerSyncPayloadValue].self, forKey: .payload) ?? [:]
    }

    var isVisible: Bool { access.lowercased() == "full" }

    func string(_ key: String) -> String? {
        guard case let .string(value) = payload[key] else { return nil }
        return value
    }

    func int(_ key: String) -> Int? {
        switch payload[key] {
        case let .int(value): return value
        case let .double(value): return Int(value)
        default: return nil
        }
    }

    func bool(_ key: String) -> Bool? {
        guard case let .bool(value) = payload[key] else { return nil }
        return value
    }

    func stringArray(_ key: String) -> [String] {
        guard case let .array(values) = payload[key] else { return [] }
        return values.compactMap { item in
            if case let .string(value) = item { return value }
            return nil
        }
    }
}


struct WeeklyParentReportStats: Codable, Hashable {
    let weeklyActiveDays: Int
    let weeklyReviewCount: Int
    let weeklySavedCardCount: Int?
    let savedCardCount: Int
    let currentStreakDays: Int
    let reviewDueCount: Int?
    let todayCompletedCount: Int?
    let completedDailyTaskCount: Int?
    let newSavedCardCount: Int?
    let childCount: Int?

    var displaySavedCardCount: Int {
        weeklySavedCardCount ?? newSavedCardCount ?? savedCardCount
    }

    var displayCompletedTaskCount: Int? {
        completedDailyTaskCount ?? todayCompletedCount
    }

    var hasActivity: Bool {
        weeklyActiveDays > 0
            || weeklyReviewCount > 0
            || displaySavedCardCount > 0
            || currentStreakDays > 0
            || (reviewDueCount ?? 0) > 0
            || (displayCompletedTaskCount ?? 0) > 0
            || (childCount ?? 0) > 0
    }

    static let empty = WeeklyParentReportStats(
        weeklyActiveDays: 0,
        weeklyReviewCount: 0,
        savedCardCount: 0,
        currentStreakDays: 0
    )

    init(
        weeklyActiveDays: Int,
        weeklyReviewCount: Int,
        weeklySavedCardCount: Int? = nil,
        savedCardCount: Int,
        currentStreakDays: Int,
        reviewDueCount: Int? = nil,
        todayCompletedCount: Int? = nil,
        completedDailyTaskCount: Int? = nil,
        newSavedCardCount: Int? = nil,
        childCount: Int? = nil
    ) {
        self.weeklyActiveDays = weeklyActiveDays
        self.weeklyReviewCount = weeklyReviewCount
        self.weeklySavedCardCount = weeklySavedCardCount
        self.savedCardCount = savedCardCount
        self.currentStreakDays = currentStreakDays
        self.reviewDueCount = reviewDueCount
        self.todayCompletedCount = todayCompletedCount
        self.completedDailyTaskCount = completedDailyTaskCount
        self.newSavedCardCount = newSavedCardCount
        self.childCount = childCount
    }

    private enum CodingKeys: String, CodingKey {
        case weeklyActiveDays
        case weeklyReviewCount
        case weeklySavedCardCount
        case savedCardCount
        case currentStreakDays
        case reviewDueCount
        case todayCompletedCount
        case completedDailyTaskCount
        case newSavedCardCount
        case childCount
        case weekly_active_days
        case weekly_review_count
        case weekly_saved_card_count
        case saved_card_count
        case current_streak_days
        case review_due_count
        case today_completed_count
        case completed_daily_task_count
        case new_saved_card_count
        case child_count
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        weeklyActiveDays = try container.decodeIfPresent(Int.self, forKey: .weeklyActiveDays)
            ?? container.decodeIfPresent(Int.self, forKey: .weekly_active_days)
            ?? 0
        weeklyReviewCount = try container.decodeIfPresent(Int.self, forKey: .weeklyReviewCount)
            ?? container.decodeIfPresent(Int.self, forKey: .weekly_review_count)
            ?? 0
        weeklySavedCardCount = try container.decodeIfPresent(Int.self, forKey: .weeklySavedCardCount)
            ?? container.decodeIfPresent(Int.self, forKey: .weekly_saved_card_count)
            ?? container.decodeIfPresent(Int.self, forKey: .newSavedCardCount)
            ?? container.decodeIfPresent(Int.self, forKey: .new_saved_card_count)
        savedCardCount = try container.decodeIfPresent(Int.self, forKey: .savedCardCount)
            ?? container.decodeIfPresent(Int.self, forKey: .saved_card_count)
            ?? weeklySavedCardCount
            ?? 0
        currentStreakDays = try container.decodeIfPresent(Int.self, forKey: .currentStreakDays)
            ?? container.decodeIfPresent(Int.self, forKey: .current_streak_days)
            ?? 0
        reviewDueCount = try container.decodeIfPresent(Int.self, forKey: .reviewDueCount)
            ?? container.decodeIfPresent(Int.self, forKey: .review_due_count)
        todayCompletedCount = try container.decodeIfPresent(Int.self, forKey: .todayCompletedCount)
            ?? container.decodeIfPresent(Int.self, forKey: .today_completed_count)
        completedDailyTaskCount = try container.decodeIfPresent(Int.self, forKey: .completedDailyTaskCount)
            ?? container.decodeIfPresent(Int.self, forKey: .completed_daily_task_count)
        newSavedCardCount = try container.decodeIfPresent(Int.self, forKey: .newSavedCardCount)
            ?? container.decodeIfPresent(Int.self, forKey: .new_saved_card_count)
            ?? weeklySavedCardCount
        childCount = try container.decodeIfPresent(Int.self, forKey: .childCount)
            ?? container.decodeIfPresent(Int.self, forKey: .child_count)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(weeklyActiveDays, forKey: .weeklyActiveDays)
        try container.encode(weeklyReviewCount, forKey: .weeklyReviewCount)
        try container.encodeIfPresent(weeklySavedCardCount, forKey: .weeklySavedCardCount)
        try container.encode(savedCardCount, forKey: .savedCardCount)
        try container.encode(currentStreakDays, forKey: .currentStreakDays)
        try container.encodeIfPresent(reviewDueCount, forKey: .reviewDueCount)
        try container.encodeIfPresent(todayCompletedCount, forKey: .todayCompletedCount)
        try container.encodeIfPresent(completedDailyTaskCount, forKey: .completedDailyTaskCount)
        try container.encodeIfPresent(newSavedCardCount, forKey: .newSavedCardCount)
        try container.encodeIfPresent(childCount, forKey: .childCount)
    }
}

struct WeeklyChildReportSummary: Codable, Hashable, Identifiable {
    let childId: String
    let childName: String
    let stats: WeeklyParentReportStats
    let highlight: String
    let nextWeekSuggestion: String

    var id: String { childId }

    private enum CodingKeys: String, CodingKey {
        case childId
        case child_id
        case childName
        case child_name
        case stats
        case highlight
        case nextWeekSuggestion
        case next_week_suggestion
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        childId = try container.decodeIfPresent(String.self, forKey: .childId)
            ?? container.decodeIfPresent(String.self, forKey: .child_id)
            ?? UUID().uuidString
        childName = try container.decodeIfPresent(String.self, forKey: .childName)
            ?? container.decodeIfPresent(String.self, forKey: .child_name)
            ?? "孩子"
        stats = try container.decodeIfPresent(WeeklyParentReportStats.self, forKey: .stats) ?? .empty
        highlight = try container.decodeIfPresent(String.self, forKey: .highlight) ?? ""
        nextWeekSuggestion = try container.decodeIfPresent(String.self, forKey: .nextWeekSuggestion)
            ?? container.decodeIfPresent(String.self, forKey: .next_week_suggestion)
            ?? ""
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(childId, forKey: .childId)
        try container.encode(childName, forKey: .childName)
        try container.encode(stats, forKey: .stats)
        try container.encode(highlight, forKey: .highlight)
        try container.encode(nextWeekSuggestion, forKey: .nextWeekSuggestion)
    }
}

struct WeeklyParentReportHistory: Codable, Hashable {
    let scope: String
    let childId: String?
    let isPremiumPlan: Bool
    let historyWeeks: Int?
    let availableHistoryWeeks: Int?
    let reports: [WeeklyParentReport]
    let offlineHistoryPreviewEnabled: Bool

    init(
        scope: String = "child",
        childId: String? = nil,
        isPremiumPlan: Bool = false,
        historyWeeks: Int? = nil,
        availableHistoryWeeks: Int? = nil,
        reports: [WeeklyParentReport],
        offlineHistoryPreviewEnabled: Bool = false
    ) {
        self.scope = scope
        self.childId = childId
        self.isPremiumPlan = isPremiumPlan
        self.historyWeeks = historyWeeks
        self.availableHistoryWeeks = availableHistoryWeeks
        self.reports = reports
        self.offlineHistoryPreviewEnabled = offlineHistoryPreviewEnabled
    }

    private enum CodingKeys: String, CodingKey {
        case scope
        case childId
        case child_id
        case isPremiumPlan
        case historyWeeks
        case history_weeks
        case weeklyReportHistoryWeeks
        case availableHistoryWeeks
        case available_history_weeks
        case reports
        case items
        case weeks
        case offlineHistoryPreviewEnabled
        case offline_history_preview_enabled
    }

    init(from decoder: Decoder) throws {
        if var unkeyedContainer = try? decoder.unkeyedContainer() {
            var decodedReports: [WeeklyParentReport] = []
            while !unkeyedContainer.isAtEnd {
                decodedReports.append(try unkeyedContainer.decode(WeeklyParentReport.self))
            }
            scope = "child"
            childId = nil
            isPremiumPlan = false
            historyWeeks = nil
            availableHistoryWeeks = nil
            reports = decodedReports
            offlineHistoryPreviewEnabled = false
            return
        }

        let container = try decoder.container(keyedBy: CodingKeys.self)
        scope = try container.decodeIfPresent(String.self, forKey: .scope) ?? "child"
        childId = try container.decodeIfPresent(String.self, forKey: .childId)
            ?? container.decodeIfPresent(String.self, forKey: .child_id)
        isPremiumPlan = try container.decodeIfPresent(Bool.self, forKey: .isPremiumPlan) ?? false
        historyWeeks = try container.decodeIfPresent(Int.self, forKey: .historyWeeks)
            ?? container.decodeIfPresent(Int.self, forKey: .history_weeks)
            ?? container.decodeIfPresent(Int.self, forKey: .weeklyReportHistoryWeeks)
        availableHistoryWeeks = try container.decodeIfPresent(Int.self, forKey: .availableHistoryWeeks)
            ?? container.decodeIfPresent(Int.self, forKey: .available_history_weeks)
        reports = try container.decodeIfPresent([WeeklyParentReport].self, forKey: .reports)
            ?? container.decodeIfPresent([WeeklyParentReport].self, forKey: .items)
            ?? container.decodeIfPresent([WeeklyParentReport].self, forKey: .weeks)
            ?? []
        offlineHistoryPreviewEnabled = try container.decodeIfPresent(Bool.self, forKey: .offlineHistoryPreviewEnabled)
            ?? container.decodeIfPresent(Bool.self, forKey: .offline_history_preview_enabled)
            ?? false
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(scope, forKey: .scope)
        try container.encodeIfPresent(childId, forKey: .childId)
        try container.encode(isPremiumPlan, forKey: .isPremiumPlan)
        try container.encodeIfPresent(historyWeeks, forKey: .historyWeeks)
        try container.encodeIfPresent(availableHistoryWeeks, forKey: .availableHistoryWeeks)
        try container.encode(reports, forKey: .reports)
        try container.encode(offlineHistoryPreviewEnabled, forKey: .offlineHistoryPreviewEnabled)
    }
}

enum WeeklyReportLoadState: Equatable {
    case idle
    case loading
    case loaded(WeeklyParentReport)
    case empty(String)
    case locked(String)
    case failed(String, retryable: Bool)
}

enum WeeklyReportHistoryLoadState: Equatable {
    case idle
    case loading
    case loaded(WeeklyParentReportHistory)
    case empty(String)
    case locked(String)
    case failed(String, retryable: Bool)
}
