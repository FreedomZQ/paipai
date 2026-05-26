import Foundation

struct AccountState: Codable {
    let accountId: String
    let signInProvider: String
    let entitlement: AccountEntitlement
    let quota: DailyQuota
}

struct CloudUsageState: Codable {
    let ocr: CloudQuotaState
    let tts: CloudQuotaState
}

struct CloudQuotaState: Codable {
    let serviceType: String
    let trialLimit: Int
    let trialUsed: Int
    let purchasedCredits: Int
    let purchasedUsed: Int
    let remainingCount: Int
    let updatedAt: String
}

struct DailyLoginGiftConfig: Codable, Hashable {
    let appCode: String
    let planCode: String
    let featureCode: String
    let dailyGiftCredits: Int
    let recordMode: String
    let fetchedAt: String
}

struct EntitlementUsageSummary: Codable, Hashable {
    let serviceType: String
    let totalCount: Int
    let usedCount: Int
    let remainingCount: Int

    static func empty(serviceType: String) -> EntitlementUsageSummary {
        EntitlementUsageSummary(serviceType: serviceType, totalCount: 0, usedCount: 0, remainingCount: 0)
    }
}

struct BackendAccessProof: Codable, Hashable {
    let appCode: String?
    let userId: String?
    let plan: String?
    let status: String?
    let serverTime: String?
    let policy: String?
    let allowed: Bool?
}

struct CompensationRedeemPayload: Codable {
    let compensationCode: String
}

struct CompensationRedeemReceipt: Codable {
    let compensationCode: String
    let status: String
    let benefitType: String
    let benefitSummary: String
    let planCode: String?
    let entitlementCode: String?
    let serviceType: String?
    let grantCount: Int?
    let validUntil: String?
    let redeemedAt: String?
    let message: String?
    let accountState: AccountState?
}

struct AccountEntitlement: Codable {
    let planCode: String
    let planName: String
    let dailyLocalOcrLimit: Int
    let dailyLocalTtsLimit: Int
    let childLimit: Int
    let localCardLimit: Int
    let childCount: Int
    let remainingChildSlots: Int
    let advancedVoiceEnabled: Bool
    let premiumActive: Bool
    let validUntil: String?
    let authoritative: Bool
    let multiChildEnabled: Bool
    let dailyPlanScope: String
    let weeklyReportScope: String
    let weeklyReportHistoryWeeks: Int
    let historyEnabled: Bool
    let serverVerified: Bool
    let verificationSource: String?
    let accessProof: BackendAccessProof?

    var backendVerifiedPremiumActive: Bool {
        premiumActive && serverVerified && verificationSource == "backend_sys_billing"
    }

    init(
        planCode: String,
        planName: String,
        dailyLocalOcrLimit: Int,
        dailyLocalTtsLimit: Int,
        childLimit: Int,
        localCardLimit: Int,
        childCount: Int,
        remainingChildSlots: Int,
        advancedVoiceEnabled: Bool,
        premiumActive: Bool,
        validUntil: String?,
        authoritative: Bool,
        multiChildEnabled: Bool,
        dailyPlanScope: String,
        weeklyReportScope: String,
        weeklyReportHistoryWeeks: Int,
        historyEnabled: Bool,
        serverVerified: Bool,
        verificationSource: String?,
        accessProof: BackendAccessProof?
    ) {
        self.planCode = planCode
        self.planName = planName
        self.dailyLocalOcrLimit = dailyLocalOcrLimit
        self.dailyLocalTtsLimit = dailyLocalTtsLimit
        self.childLimit = childLimit
        self.localCardLimit = localCardLimit
        self.childCount = childCount
        self.remainingChildSlots = remainingChildSlots
        self.advancedVoiceEnabled = advancedVoiceEnabled
        self.premiumActive = premiumActive
        self.validUntil = validUntil
        self.authoritative = authoritative
        self.multiChildEnabled = multiChildEnabled
        self.dailyPlanScope = dailyPlanScope
        self.weeklyReportScope = weeklyReportScope
        self.weeklyReportHistoryWeeks = weeklyReportHistoryWeeks
        self.historyEnabled = historyEnabled
        self.serverVerified = serverVerified
        self.verificationSource = verificationSource
        self.accessProof = accessProof
    }

    private enum CodingKeys: String, CodingKey {
        case planCode
        case planName
        case dailyLocalOcrLimit
        case dailyLocalTtsLimit
        case dailyCaptureLimit
        case dailySpeechLimit
        case childLimit
        case localCardLimit
        case childCount
        case remainingChildSlots
        case advancedVoiceEnabled
        case premiumActive
        case validUntil
        case authoritative
        case multiChildEnabled
        case dailyPlanScope
        case weeklyReportScope
        case weeklyReportHistoryWeeks
        case historyEnabled
        case serverVerified
        case verificationSource
        case accessProof
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        planCode = try container.decodeIfPresent(String.self, forKey: .planCode) ?? "free"
        planName = try container.decodeIfPresent(String.self, forKey: .planName) ?? "免费版"
        dailyLocalOcrLimit = try container.decodeIfPresent(Int.self, forKey: .dailyLocalOcrLimit)
            ?? container.decodeIfPresent(Int.self, forKey: .dailyCaptureLimit)
            ?? 3
        dailyLocalTtsLimit = try container.decodeIfPresent(Int.self, forKey: .dailyLocalTtsLimit)
            ?? container.decodeIfPresent(Int.self, forKey: .dailySpeechLimit)
            ?? 10
        childLimit = try container.decodeIfPresent(Int.self, forKey: .childLimit) ?? 1
        localCardLimit = try container.decodeIfPresent(Int.self, forKey: .localCardLimit) ?? 20
        childCount = try container.decodeIfPresent(Int.self, forKey: .childCount) ?? 0
        remainingChildSlots = try container.decodeIfPresent(Int.self, forKey: .remainingChildSlots) ?? max(childLimit - childCount, 0)
        advancedVoiceEnabled = try container.decodeIfPresent(Bool.self, forKey: .advancedVoiceEnabled) ?? false
        premiumActive = try container.decodeIfPresent(Bool.self, forKey: .premiumActive) ?? false
        validUntil = try container.decodeIfPresent(String.self, forKey: .validUntil)
        authoritative = try container.decodeIfPresent(Bool.self, forKey: .authoritative) ?? false
        let derivedMultiChildEnabled = childLimit > 1
        multiChildEnabled = try container.decodeIfPresent(Bool.self, forKey: .multiChildEnabled) ?? derivedMultiChildEnabled
        dailyPlanScope = try container.decodeIfPresent(String.self, forKey: .dailyPlanScope) ?? (multiChildEnabled ? "per_child" : "single_child")
        weeklyReportScope = try container.decodeIfPresent(String.self, forKey: .weeklyReportScope) ?? (multiChildEnabled ? "family" : "child")
        weeklyReportHistoryWeeks = try container.decodeIfPresent(Int.self, forKey: .weeklyReportHistoryWeeks) ?? 0
        historyEnabled = try container.decodeIfPresent(Bool.self, forKey: .historyEnabled) ?? (weeklyReportHistoryWeeks > 0)
        serverVerified = try container.decodeIfPresent(Bool.self, forKey: .serverVerified) ?? false
        verificationSource = try container.decodeIfPresent(String.self, forKey: .verificationSource)
        accessProof = try container.decodeIfPresent(BackendAccessProof.self, forKey: .accessProof)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(planCode, forKey: .planCode)
        try container.encode(planName, forKey: .planName)
        try container.encode(dailyLocalOcrLimit, forKey: .dailyLocalOcrLimit)
        try container.encode(dailyLocalTtsLimit, forKey: .dailyLocalTtsLimit)
        try container.encode(childLimit, forKey: .childLimit)
        try container.encode(localCardLimit, forKey: .localCardLimit)
        try container.encode(childCount, forKey: .childCount)
        try container.encode(remainingChildSlots, forKey: .remainingChildSlots)
        try container.encode(advancedVoiceEnabled, forKey: .advancedVoiceEnabled)
        try container.encode(premiumActive, forKey: .premiumActive)
        try container.encodeIfPresent(validUntil, forKey: .validUntil)
        try container.encode(authoritative, forKey: .authoritative)
        try container.encode(multiChildEnabled, forKey: .multiChildEnabled)
        try container.encode(dailyPlanScope, forKey: .dailyPlanScope)
        try container.encode(weeklyReportScope, forKey: .weeklyReportScope)
        try container.encode(weeklyReportHistoryWeeks, forKey: .weeklyReportHistoryWeeks)
        try container.encode(historyEnabled, forKey: .historyEnabled)
        try container.encode(serverVerified, forKey: .serverVerified)
        try container.encodeIfPresent(verificationSource, forKey: .verificationSource)
        try container.encodeIfPresent(accessProof, forKey: .accessProof)
    }
}

struct DailyQuota: Codable {
    let quotaDate: String
    let localOcrLimit: Int
    let localOcrUsed: Int
    let localOcrRemaining: Int
    let localTtsLimit: Int
    let localTtsUsed: Int
    let localTtsRemaining: Int
    let dailyLoginGiftLimit: Int
    let dailyLoginGiftUsed: Int
    let dailyLoginGiftRemaining: Int

    init(
        quotaDate: String,
        localOcrLimit: Int,
        localOcrUsed: Int,
        localOcrRemaining: Int,
        localTtsLimit: Int,
        localTtsUsed: Int,
        localTtsRemaining: Int,
        dailyLoginGiftLimit: Int? = nil,
        dailyLoginGiftUsed: Int? = nil,
        dailyLoginGiftRemaining: Int? = nil
    ) {
        self.quotaDate = quotaDate
        self.localOcrLimit = localOcrLimit
        self.localOcrUsed = localOcrUsed
        self.localOcrRemaining = localOcrRemaining
        self.localTtsLimit = localTtsLimit
        self.localTtsUsed = localTtsUsed
        self.localTtsRemaining = localTtsRemaining
        let fallbackLimit = max(localOcrLimit, localTtsLimit)
        let fallbackUsed = min(max(localOcrUsed + localTtsUsed, 0), fallbackLimit)
        self.dailyLoginGiftLimit = dailyLoginGiftLimit ?? fallbackLimit
        self.dailyLoginGiftUsed = dailyLoginGiftUsed ?? fallbackUsed
        self.dailyLoginGiftRemaining = dailyLoginGiftRemaining ?? max(self.dailyLoginGiftLimit - self.dailyLoginGiftUsed, 0)
    }

    private enum CodingKeys: String, CodingKey {
        case quotaDate
        case localOcrLimit
        case localOcrUsed
        case localOcrRemaining
        case localTtsLimit
        case localTtsUsed
        case localTtsRemaining
        case dailyLoginGiftLimit
        case dailyLoginGiftUsed
        case dailyLoginGiftRemaining
        case captureLimit
        case captureUsed
        case captureRemaining
        case speechLimit
        case speechUsed
        case speechRemaining
        case ttsLimit
        case ttsUsed
        case ttsRemaining
        case readAloudLimit
        case readAloudUsed
        case readAloudRemaining
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        quotaDate = try container.decodeIfPresent(String.self, forKey: .quotaDate) ?? ""
        localOcrLimit = try container.decodeIfPresent(Int.self, forKey: .localOcrLimit)
            ?? container.decodeIfPresent(Int.self, forKey: .captureLimit)
            ?? 0
        localOcrUsed = try container.decodeIfPresent(Int.self, forKey: .localOcrUsed)
            ?? container.decodeIfPresent(Int.self, forKey: .captureUsed)
            ?? 0
        localOcrRemaining = try container.decodeIfPresent(Int.self, forKey: .localOcrRemaining)
            ?? container.decodeIfPresent(Int.self, forKey: .captureRemaining)
            ?? max(localOcrLimit - localOcrUsed, 0)
        localTtsLimit = try container.decodeIfPresent(Int.self, forKey: .localTtsLimit)
            ?? container.decodeIfPresent(Int.self, forKey: .speechLimit)
            ?? container.decodeIfPresent(Int.self, forKey: .ttsLimit)
            ?? container.decodeIfPresent(Int.self, forKey: .readAloudLimit)
            ?? localOcrLimit
        localTtsUsed = try container.decodeIfPresent(Int.self, forKey: .localTtsUsed)
            ?? container.decodeIfPresent(Int.self, forKey: .speechUsed)
            ?? container.decodeIfPresent(Int.self, forKey: .ttsUsed)
            ?? container.decodeIfPresent(Int.self, forKey: .readAloudUsed)
            ?? 0
        localTtsRemaining = try container.decodeIfPresent(Int.self, forKey: .localTtsRemaining)
            ?? container.decodeIfPresent(Int.self, forKey: .speechRemaining)
            ?? container.decodeIfPresent(Int.self, forKey: .ttsRemaining)
            ?? container.decodeIfPresent(Int.self, forKey: .readAloudRemaining)
            ?? max(localTtsLimit - localTtsUsed, 0)
        // 中文说明：新版后端返回统一每日登录赠送积分；旧后端缺字段时用旧 OCR/TTS 字段合成，保证降级可用。
        let fallbackGiftLimit = max(localOcrLimit, localTtsLimit)
        let fallbackGiftUsed = min(max(localOcrUsed + localTtsUsed, 0), fallbackGiftLimit)
        dailyLoginGiftLimit = try container.decodeIfPresent(Int.self, forKey: .dailyLoginGiftLimit) ?? fallbackGiftLimit
        dailyLoginGiftUsed = try container.decodeIfPresent(Int.self, forKey: .dailyLoginGiftUsed) ?? fallbackGiftUsed
        dailyLoginGiftRemaining = try container.decodeIfPresent(Int.self, forKey: .dailyLoginGiftRemaining) ?? max(dailyLoginGiftLimit - dailyLoginGiftUsed, 0)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(quotaDate, forKey: .quotaDate)
        try container.encode(localOcrLimit, forKey: .localOcrLimit)
        try container.encode(localOcrUsed, forKey: .localOcrUsed)
        try container.encode(localOcrRemaining, forKey: .localOcrRemaining)
        try container.encode(localTtsLimit, forKey: .localTtsLimit)
        try container.encode(localTtsUsed, forKey: .localTtsUsed)
        try container.encode(localTtsRemaining, forKey: .localTtsRemaining)
        try container.encode(dailyLoginGiftLimit, forKey: .dailyLoginGiftLimit)
        try container.encode(dailyLoginGiftUsed, forKey: .dailyLoginGiftUsed)
        try container.encode(dailyLoginGiftRemaining, forKey: .dailyLoginGiftRemaining)
    }
}

struct LocalOcrQuotaValidation: Hashable {
    let isAllowed: Bool
    let requiredAmount: Int
    let maxLimit: Int
    let usedAmount: Int
    let remainingAmount: Int
    let message: String?
}

struct CreateChildReceipt: Codable {
    let child: ChildProfile
    let accountState: AccountState
}

typealias UpdateChildReceipt = CreateChildReceipt

struct HomeSummary: Codable {
    let currentChild: HomeChild?
    let todayCompletedCount: Int
    let reviewDueCount: Int
    let recentCards: [RecentReviewCard]
    let quota: DailyQuota
    let entitlement: AccountEntitlement
    let growth: LearningGrowth
    let childSummaries: [ChildProgress]

    private enum CodingKeys: String, CodingKey {
        case currentChild
        case todayCompletedCount
        case reviewDueCount
        case recentCards
        case quota
        case entitlement
        case growth
        case childSummaries
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        currentChild = try container.decodeIfPresent(HomeChild.self, forKey: .currentChild)
        todayCompletedCount = try container.decodeIfPresent(Int.self, forKey: .todayCompletedCount) ?? 0
        reviewDueCount = try container.decodeIfPresent(Int.self, forKey: .reviewDueCount) ?? 0
        recentCards = try container.decodeIfPresent([RecentReviewCard].self, forKey: .recentCards) ?? []
        quota = try container.decode(DailyQuota.self, forKey: .quota)
        entitlement = try container.decode(AccountEntitlement.self, forKey: .entitlement)
        growth = try container.decodeIfPresent(LearningGrowth.self, forKey: .growth) ?? .empty
        childSummaries = try container.decodeIfPresent([ChildProgress].self, forKey: .childSummaries) ?? []
    }
}

struct HomeChild: Codable, Hashable {
    let childId: String
    let nickname: String
    let ageBand: String
    let avatarEmoji: String
}

struct RecentReviewCard: Codable, Hashable, Identifiable {
    let cardId: String
    let text: String
    let supportHint: String
    let proficiency: Int
    let nextReviewAt: String?
    let createdAt: String?

    var id: String { cardId }
}

struct LearningGrowth: Codable, Hashable {
    let currentStreakDays: Int
    let weeklyActiveDays: Int
    let weeklyReviewCount: Int
    let encouragement: String

    static let empty = LearningGrowth(
        currentStreakDays: 0,
        weeklyActiveDays: 0,
        weeklyReviewCount: 0,
        encouragement: "先从今天的一句开始，连续几天回来复习，就会慢慢看到陪读节奏稳定下来。"
    )
}

struct ChildProgress: Codable, Hashable, Identifiable {
    let childId: String
    let nickname: String
    let ageBand: String
    let avatarEmoji: String
    let reviewDueCount: Int
    let savedCardCount: Int
    let todayCompletedCount: Int

    var id: String { childId }
}
