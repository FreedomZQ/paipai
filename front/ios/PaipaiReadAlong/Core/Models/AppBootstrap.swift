import Foundation

struct AppBootstrap: Codable {
    let appName: String
    let kidsCategoryEnabled: Bool
    let captureCharLimit: Int
    let defaultLocale: String
    let supportedLocales: [String]
    let learningTracks: [AppLearningTrack]
    let paywall: PaywallConfig
    let usagePolicy: UsagePolicyConfig?
    let supportEmail: String?
    let supportUrl: String?
    let deleteAccountUrl: String?

    static let placeholder = AppBootstrap(
        appName: "拍拍伴读",
        kidsCategoryEnabled: false,
        captureCharLimit: 120,
        defaultLocale: "zh-Hans",
        supportedLocales: AppLocaleCatalog.supportedInterfaceLocales,
        learningTracks: [
            AppLearningTrack(code: "zh_to_en", label: "中文家庭学英语"),
            AppLearningTrack(code: "en_to_zh", label: "English families learn Chinese")
        ],
        paywall: PaywallConfig(
            defaultHighlight: "family_multi_child_lifetime",
            trialEnabled: false,
            headline: "解锁家庭伴读节奏",
            subtitle: "多孩子档案、更多拍读额度和周报历史，帮助家长长期看到孩子的进步。",
            trustBullets: [
                "一次开通当前高级版权益，具体扣款以 Apple 确认弹窗为准。",
                "学习内容默认优先保存在本机。",
                "账号删除、法务文档和客服入口均在 App 内可访问。"
            ],
            legalNotice: "权益以后端校验结果为准；价格与扣款以 Apple 确认弹窗为准。"
        ),
        usagePolicy: .default,
        supportEmail: nil,
        supportUrl: nil,
        deleteAccountUrl: nil
    )
}

struct UsagePolicyConfig: Codable, Hashable {
    let retentionDays: Int
    let recentSummaryDays: Int
    let dayBoundary: String
    let maxSessionHours: Int

    static let `default` = UsagePolicyConfig(
        retentionDays: 30,
        recentSummaryDays: 7,
        dayBoundary: "client_local",
        maxSessionHours: 24
    )

    var safeRetentionDays: Int { min(max(retentionDays, 1), 366) }
    var safeRecentSummaryDays: Int { min(max(recentSummaryDays, 1), 31) }
}

struct AppLearningTrack: Codable, Hashable, Identifiable {
    var id: String { code }
    let code: String
    let label: String
}

struct PaywallConfig: Codable {
    let defaultHighlight: String
    let trialEnabled: Bool
    let headline: String
    let subtitle: String
    let trustBullets: [String]
    let legalNotice: String

    init(
        defaultHighlight: String,
        trialEnabled: Bool,
        headline: String = "解锁家庭伴读节奏",
        subtitle: String = "多孩子档案、更多拍读额度和周报历史，帮助家长长期看到孩子的进步。",
        trustBullets: [String] = [
            "一次开通当前高级版权益，具体扣款以 Apple 确认弹窗为准。",
            "学习内容默认优先保存在本机。",
            "账号删除、法务文档和客服入口均在 App 内可访问。"
        ],
        legalNotice: String = "权益以后端校验结果为准；价格与扣款以 Apple 确认弹窗为准。"
    ) {
        self.defaultHighlight = defaultHighlight
        self.trialEnabled = trialEnabled
        self.headline = headline
        self.subtitle = subtitle
        self.trustBullets = trustBullets
        self.legalNotice = legalNotice
    }

    enum CodingKeys: String, CodingKey {
        case defaultHighlight
        case trialEnabled
        case headline
        case subtitle
        case trustBullets
        case legalNotice
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        defaultHighlight = try container.decodeIfPresent(String.self, forKey: .defaultHighlight) ?? "family_multi_child_lifetime"
        trialEnabled = try container.decodeIfPresent(Bool.self, forKey: .trialEnabled) ?? false
        headline = try container.decodeIfPresent(String.self, forKey: .headline) ?? "解锁高级版伴读节奏"
        subtitle = try container.decodeIfPresent(String.self, forKey: .subtitle) ?? "多孩子档案、更多拍读额度和周报历史，帮助家长长期看到孩子的进步。"
        trustBullets = try container.decodeIfPresent([String].self, forKey: .trustBullets) ?? [
            "一次开通当前高级版权益，具体扣款以 Apple 确认弹窗为准。",
            "学习内容默认优先保存在本机。",
            "账号删除、法务文档和客服入口均在 App 内可访问。"
        ]
        legalNotice = try container.decodeIfPresent(String.self, forKey: .legalNotice) ?? "权益以后端校验结果为准；价格与扣款以 Apple 确认弹窗为准。"
    }
}
