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
    let dailyCaptureLimit: Int
    let dailySpeechLimit: Int
    let childLimit: Int
    let localCardLimit: Int
    let childCount: Int
    let remainingChildSlots: Int
    let cloudSyncEnabled: Bool
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
        dailyCaptureLimit: Int,
        dailySpeechLimit: Int,
        childLimit: Int,
        localCardLimit: Int,
        childCount: Int,
        remainingChildSlots: Int,
        cloudSyncEnabled: Bool,
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
        self.dailyCaptureLimit = dailyCaptureLimit
        self.dailySpeechLimit = dailySpeechLimit
        self.childLimit = childLimit
        self.localCardLimit = localCardLimit
        self.childCount = childCount
        self.remainingChildSlots = remainingChildSlots
        self.cloudSyncEnabled = cloudSyncEnabled
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
        case dailyCaptureLimit
        case dailySpeechLimit
        case childLimit
        case localCardLimit
        case childCount
        case remainingChildSlots
        case cloudSyncEnabled
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
        dailyCaptureLimit = try container.decodeIfPresent(Int.self, forKey: .dailyCaptureLimit) ?? 3
        dailySpeechLimit = try container.decodeIfPresent(Int.self, forKey: .dailySpeechLimit) ?? 10
        childLimit = try container.decodeIfPresent(Int.self, forKey: .childLimit) ?? 1
        localCardLimit = try container.decodeIfPresent(Int.self, forKey: .localCardLimit) ?? 20
        childCount = try container.decodeIfPresent(Int.self, forKey: .childCount) ?? 0
        remainingChildSlots = try container.decodeIfPresent(Int.self, forKey: .remainingChildSlots) ?? max(childLimit - childCount, 0)
        cloudSyncEnabled = try container.decodeIfPresent(Bool.self, forKey: .cloudSyncEnabled) ?? false
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
}

struct DailyQuota: Codable {
    let quotaDate: String
    let captureLimit: Int
    let captureUsed: Int
    let captureRemaining: Int
    let speechLimit: Int
    let speechUsed: Int
    let speechRemaining: Int

    init(
        quotaDate: String,
        captureLimit: Int,
        captureUsed: Int,
        captureRemaining: Int,
        speechLimit: Int,
        speechUsed: Int,
        speechRemaining: Int
    ) {
        self.quotaDate = quotaDate
        self.captureLimit = captureLimit
        self.captureUsed = captureUsed
        self.captureRemaining = captureRemaining
        self.speechLimit = speechLimit
        self.speechUsed = speechUsed
        self.speechRemaining = speechRemaining
    }

    private enum CodingKeys: String, CodingKey {
        case quotaDate
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
        captureLimit = try container.decodeIfPresent(Int.self, forKey: .captureLimit) ?? 0
        captureUsed = try container.decodeIfPresent(Int.self, forKey: .captureUsed) ?? 0
        captureRemaining = try container.decodeIfPresent(Int.self, forKey: .captureRemaining) ?? max(captureLimit - captureUsed, 0)
        speechLimit = try container.decodeIfPresent(Int.self, forKey: .speechLimit)
            ?? container.decodeIfPresent(Int.self, forKey: .ttsLimit)
            ?? container.decodeIfPresent(Int.self, forKey: .readAloudLimit)
            ?? captureLimit
        speechUsed = try container.decodeIfPresent(Int.self, forKey: .speechUsed)
            ?? container.decodeIfPresent(Int.self, forKey: .ttsUsed)
            ?? container.decodeIfPresent(Int.self, forKey: .readAloudUsed)
            ?? 0
        speechRemaining = try container.decodeIfPresent(Int.self, forKey: .speechRemaining)
            ?? container.decodeIfPresent(Int.self, forKey: .ttsRemaining)
            ?? container.decodeIfPresent(Int.self, forKey: .readAloudRemaining)
            ?? max(speechLimit - speechUsed, 0)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(quotaDate, forKey: .quotaDate)
        try container.encode(captureLimit, forKey: .captureLimit)
        try container.encode(captureUsed, forKey: .captureUsed)
        try container.encode(captureRemaining, forKey: .captureRemaining)
        try container.encode(speechLimit, forKey: .speechLimit)
        try container.encode(speechUsed, forKey: .speechUsed)
        try container.encode(speechRemaining, forKey: .speechRemaining)
    }
}

struct CaptureQuotaValidation: Hashable {
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
    let storageMode: String?
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
