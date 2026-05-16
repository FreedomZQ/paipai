import Foundation

struct Plan: Codable, Hashable, Identifiable {
    var id: String { code }

    let code: String
    let displayName: String
    let childLimit: Int
    let dailyCaptureLimit: Int
    let localCardLimit: Int
    let cloudSyncEnabled: Bool
    let advancedVoiceEnabled: Bool
    let appStoreProductId: String?
    let highlight: Bool?
    let displayPrice: String?
    let originalPrice: String?
    let badgeText: String?
    let historyEnabled: Bool
    let supportedLocales: [String]
    let supportedLearningTrackCodes: [String]

    var name: String { displayName }
    var description: String {
        var highlights: [String] = []
        if childLimit > 1 { highlights.append("多孩子") }
        if historyEnabled { highlights.append("历史回顾") }
        if cloudSyncEnabled { highlights.append("云同步") }
        if advancedVoiceEnabled { highlights.append("高级朗读") }
        return highlights.isEmpty ? "基础功能免费使用" : highlights.joined(separator: "、")
    }
    var isPopular: Bool { highlight ?? false }

    static let defaultPlans: [Plan] = [
        Plan(code: "free", displayName: "免费版", childLimit: 1, dailyCaptureLimit: 3, localCardLimit: 20, cloudSyncEnabled: false, advancedVoiceEnabled: false, appStoreProductId: nil, highlight: false, displayPrice: "¥0", originalPrice: nil, badgeText: nil, historyEnabled: false, supportedLocales: AppLocaleCatalog.supportedInterfaceLocales, supportedLearningTrackCodes: ["zh_to_en", "en_to_zh"]),
        Plan(code: "family_multi_child_lifetime", displayName: "高级版", childLimit: 5, dailyCaptureLimit: 50, localCardLimit: 800, cloudSyncEnabled: true, advancedVoiceEnabled: true, appStoreProductId: "com.paipai.readalong.family.multi_child.lifetime", highlight: true, displayPrice: "¥68", originalPrice: "¥98", badgeText: "推荐", historyEnabled: true, supportedLocales: AppLocaleCatalog.supportedInterfaceLocales, supportedLearningTrackCodes: ["zh_to_en", "en_to_zh"])
    ]

    init(
        code: String,
        displayName: String,
        childLimit: Int,
        dailyCaptureLimit: Int,
        localCardLimit: Int,
        cloudSyncEnabled: Bool,
        advancedVoiceEnabled: Bool,
        appStoreProductId: String?,
        highlight: Bool?,
        displayPrice: String? = nil,
        originalPrice: String? = nil,
        badgeText: String? = nil,
        historyEnabled: Bool = false,
        supportedLocales: [String] = [],
        supportedLearningTrackCodes: [String] = []
    ) {
        self.code = code
        self.displayName = displayName
        self.childLimit = childLimit
        self.dailyCaptureLimit = dailyCaptureLimit
        self.localCardLimit = localCardLimit
        self.cloudSyncEnabled = cloudSyncEnabled
        self.advancedVoiceEnabled = advancedVoiceEnabled
        self.appStoreProductId = appStoreProductId
        self.highlight = highlight
        self.displayPrice = displayPrice
        self.originalPrice = originalPrice
        self.badgeText = badgeText
        self.historyEnabled = historyEnabled
        self.supportedLocales = supportedLocales
        self.supportedLearningTrackCodes = supportedLearningTrackCodes
    }

    private enum CodingKeys: String, CodingKey {
        case code
        case productCode
        case displayName
        case childLimit
        case dailyCaptureLimit
        case localCardLimit
        case cloudSyncEnabled
        case advancedVoiceEnabled
        case appStoreProductId
        case highlight
        case displayPrice
        case originalPrice
        case badgeText
        case historyEnabled
        case supportedLocales
        case supportedLearningTrackCodes
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        code = try container.decodeIfPresent(String.self, forKey: .code)
            ?? container.decodeIfPresent(String.self, forKey: .productCode)
            ?? "free"
        displayName = try container.decodeIfPresent(String.self, forKey: .displayName) ?? code
        childLimit = try container.decodeIfPresent(Int.self, forKey: .childLimit) ?? 1
        dailyCaptureLimit = try container.decodeIfPresent(Int.self, forKey: .dailyCaptureLimit) ?? 3
        localCardLimit = try container.decodeIfPresent(Int.self, forKey: .localCardLimit) ?? 20
        cloudSyncEnabled = try container.decodeIfPresent(Bool.self, forKey: .cloudSyncEnabled) ?? false
        advancedVoiceEnabled = try container.decodeIfPresent(Bool.self, forKey: .advancedVoiceEnabled) ?? false
        appStoreProductId = try container.decodeIfPresent(String.self, forKey: .appStoreProductId)
        highlight = try container.decodeIfPresent(Bool.self, forKey: .highlight)
        displayPrice = try container.decodeIfPresent(String.self, forKey: .displayPrice)
        originalPrice = try container.decodeIfPresent(String.self, forKey: .originalPrice)
        badgeText = try container.decodeIfPresent(String.self, forKey: .badgeText)
        historyEnabled = try container.decodeIfPresent(Bool.self, forKey: .historyEnabled) ?? false
        supportedLocales = try container.decodeIfPresent([String].self, forKey: .supportedLocales) ?? []
        supportedLearningTrackCodes = try container.decodeIfPresent([String].self, forKey: .supportedLearningTrackCodes) ?? []
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(code, forKey: .code)
        try container.encode(displayName, forKey: .displayName)
        try container.encode(childLimit, forKey: .childLimit)
        try container.encode(dailyCaptureLimit, forKey: .dailyCaptureLimit)
        try container.encode(localCardLimit, forKey: .localCardLimit)
        try container.encode(cloudSyncEnabled, forKey: .cloudSyncEnabled)
        try container.encode(advancedVoiceEnabled, forKey: .advancedVoiceEnabled)
        try container.encodeIfPresent(appStoreProductId, forKey: .appStoreProductId)
        try container.encodeIfPresent(highlight, forKey: .highlight)
        try container.encodeIfPresent(displayPrice, forKey: .displayPrice)
        try container.encodeIfPresent(originalPrice, forKey: .originalPrice)
        try container.encodeIfPresent(badgeText, forKey: .badgeText)
        try container.encode(historyEnabled, forKey: .historyEnabled)
        try container.encode(supportedLocales, forKey: .supportedLocales)
        try container.encode(supportedLearningTrackCodes, forKey: .supportedLearningTrackCodes)
    }
}

struct SubscriptionStatus: Codable {
    let currentPlanCode: String
    let currentPlanName: String
    let authoritative: Bool
    let hasPendingVerification: Bool
    let verificationReadiness: SubscriptionVerificationReadiness
    let projections: [SubscriptionProjection]
    let recentIntakes: [SubscriptionIntake]
}

struct SubscriptionVerificationReadiness: Codable, Hashable {
    let bundleContextConfigured: Bool
    let serverApiCredentialsConfigured: Bool
    let cryptographicVerificationLive: Bool
    let bundleId: String?
    let environment: String?
    let note: String
}

struct SubscriptionProjection: Codable, Hashable {
    let originalTransactionId: String
    let status: String
    let verificationStatus: String
    let productId: String?
    let planCode: String?
    let validUntil: String?
    let authoritativeSource: String
    let lastVerifiedAt: String?
}

struct SubscriptionIntake: Codable, Hashable {
    let intakeId: String
    let sourceType: String
    let status: String
    let verificationStatus: String
    let productId: String?
    let planCode: String?
    let receivedAt: String
    let failureReason: String?
}

struct TransactionIntakeReceipt: Codable {
    let intakeId: String
    let sourceType: String
    let status: String
    let verificationStatus: String
    let productId: String?
    let planCode: String?
    let authoritativePlanCode: String
    let requiresServerVerification: Bool
    let note: String
    let accountState: AccountState
}

struct EntitlementRefreshReceipt: Codable {
    let refreshedAt: String
    let effectivePlanCode: String
    let effectivePlanName: String
    let activeProjectionCount: Int
    let source: String
}

struct BillingHealth: Codable, Hashable {
    let status: String
    let purchaseAvailable: Bool
    let unavailableMessage: String?
    let checkedAt: String
}

/// 购买权限实时校验结果。
/// appCode 区分统一后端中的不同应用，productCode 表示本次校验的具体购买项；
/// allowed 为 false 时，前端必须立即置灰付款按钮并展示 message。
struct PurchasePermissionDecision: Codable, Hashable {
    let appCode: String
    let productCode: String?
    let allowed: Bool
    let status: String
    let reasonCode: String?
    let messageKey: String?
    let messageTextMap: [String: String]?
    let message: String?
}

struct CreditProduct: Codable, Hashable, Identifiable {
    var id: String { productCode }

    let productCode: String
    let packageType: String?
    let serviceType: String
    let displayName: String
    let displayDescription: String?
    let amount: Int
    let quantityUnit: String?
    let displayPrice: String
    let currency: String?
    let priceAmountCents: Int?
    let validDays: Int
    let enabled: Bool
    let status: String?
    let sortOrder: Int?
    let disabledMessage: String?
    let messageKey: String?

    var localizedCategoryName: String {
        switch packageType ?? serviceType {
        case "voice_count_pack", "speech", "cloud_tts":
            return "语音朗读"
        case "image_recognition_pack", "capture", "cloud_ocr":
            return "图片识别文字"
        case "child_limit_extension", "child_profile":
            return "孩子数量上限"
        case "sentence_card_limit_extension", "local_card":
            return "句卡容量"
        default:
            return "资源包"
        }
    }
}

struct InternalPurchaseReceipt: Codable {
    let status: String
    let paymentMode: String
    let product: CreditProduct
    let remainingCount: Int
    let expiresAt: String
    let purchasedToday: Int
    let dailyPurchaseLimit: Int
    let accountState: AccountState
}

struct EntitlementRecordPage: Codable {
    let page: Int
    let pageSize: Int
    let hasMore: Bool
    let records: [EntitlementRecord]
}

struct EntitlementRecord: Codable, Hashable, Identifiable {
    let id: String
    let serviceType: String
    let grantType: String
    let acquireMethod: String
    let totalCount: Int
    let usedCount: Int
    let remainingCount: Int
    let acquiredAt: String
    let expiresAt: String
    let productCode: String?
}
