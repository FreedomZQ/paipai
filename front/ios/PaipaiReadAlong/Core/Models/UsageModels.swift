import Foundation

struct UsageSessionStartReceipt: Codable {
    let sessionUuid: String
    let childId: String
    let startedAt: String
    let status: String
}

struct UsageSessionEndReceipt: Codable {
    let sessionUuid: String
    let childId: String
    let startedAt: String
    let endedAt: String
    let durationSeconds: Int
    let status: String
}

struct UsageSessionRecord: Identifiable, Codable, Hashable {
    let id: String
    var childId: String
    var sourcePage: String
    var startedAt: String
    var endedAt: String?
    var durationSeconds: Int
    var clientPlatform: String?
    var deviceModel: String?
    var deletedAt: String?
    var updatedAt: String?

    var isDeleted: Bool { deletedAt != nil }
}

struct DailyUsagePoint: Codable, Hashable, Identifiable {
    var id: String { usageDate }
    let usageDate: String
    let durationSeconds: Int
}

struct ChildUsageSummary: Codable {
    let childId: String
    let childName: String
    let usageDate: String
    let todayDurationSeconds: Int
    let totalDurationSeconds: Int
    let weeklyDurationSeconds: Int
    let todaySessionCount: Int
    let lastUsedAt: String?
    let recentDailyUsage: [DailyUsagePoint]
    let retentionDays: Int?
    let recentSummaryDays: Int?
    let dayBoundary: String?
}

struct FamilyUsageSummary: Codable {
    let usageDate: String
    let todayDurationSeconds: Int
    let totalDurationSeconds: Int
    let weeklyDurationSeconds: Int
    let todaySessionCount: Int
    let childCount: Int
    let lastUsedAt: String?
    let recentDailyUsage: [DailyUsagePoint]
    let retentionDays: Int?
    let recentSummaryDays: Int?
    let dayBoundary: String?
}

struct ReadingAchievementActivity: Codable, Hashable {
    let effectiveSessionCount: Int
    let activeDayCount: Int
    let currentStreakDays: Int

    static let empty = ReadingAchievementActivity(
        effectiveSessionCount: 0,
        activeDayCount: 0,
        currentStreakDays: 0
    )
}

struct ReadingAchievementStats: Codable, Hashable {
    let childId: String
    let learningEventCount: Int
    let savedCardCount: Int
    let masteredCardCount: Int
    let effectiveSessionCount: Int
    let activeDayCount: Int
    let currentStreakDays: Int

    static let empty = ReadingAchievementStats(
        childId: "",
        learningEventCount: 0,
        savedCardCount: 0,
        masteredCardCount: 0,
        effectiveSessionCount: 0,
        activeDayCount: 0,
        currentStreakDays: 0
    )

    var hasCompletedFirstLearningCard: Bool {
        learningEventCount > 0 || masteredCardCount > 0
    }

    var hasSavedFiveCards: Bool {
        savedCardCount >= 5
    }

    var hasThreeDayStreak: Bool {
        currentStreakDays >= 3
    }

    var hasTenMasteredCards: Bool {
        masteredCardCount >= 10
    }

    var hasThirtyEffectiveSessions: Bool {
        effectiveSessionCount >= 30
    }

    var hasStableLongTermRhythm: Bool {
        currentStreakDays >= 7 && activeDayCount >= 7 && effectiveSessionCount >= 30
    }
}

/// 按日统计 OCR 与语音朗读的使用次数。
///
/// 用于周报等场景回放最近几天每天的拍照识图 / 语音朗读次数。
/// 该记录完全由客户端本地写入，不依赖后端服务，后端下线时也能累加。
struct DailyQuotaUsageRecord: Codable, Hashable, Identifiable {
    var id: String { usageDate }
    /// YYYY-MM-DD 本地时区日期
    let usageDate: String
    /// 当日设备端（离线）OCR 次数
    var localOcrCount: Int
    /// 当日云端 OCR 次数
    var cloudOcrCount: Int
    /// 当日设备端（离线）语音朗读次数
    var localTtsCount: Int
    /// 当日云端语音朗读次数
    var cloudTtsCount: Int

    /// 当日 OCR 总次数（设备端 + 云端）
    var localOcrTotalCount: Int { localOcrCount + cloudOcrCount }
    /// 当日语音朗读总次数（设备端 + 云端）
    var localTtsTotalCount: Int { localTtsCount + cloudTtsCount }

    init(
        usageDate: String,
        localOcrCount: Int = 0,
        cloudOcrCount: Int = 0,
        localTtsCount: Int = 0,
        cloudTtsCount: Int = 0
    ) {
        self.usageDate = usageDate
        self.localOcrCount = localOcrCount
        self.cloudOcrCount = cloudOcrCount
        self.localTtsCount = localTtsCount
        self.cloudTtsCount = cloudTtsCount
    }
}
