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
        kidsCategoryEnabled: true,
        captureCharLimit: 120,
        defaultLocale: "zh-Hans",
        supportedLocales: AppLocaleCatalog.supportedInterfaceLocales,
        learningTracks: [
            AppLearningTrack(code: "zh_to_en", label: "中文家庭学英语"),
            AppLearningTrack(code: "en_to_zh", label: "English families learn Chinese")
        ],
        paywall: PaywallConfig(
            defaultHighlight: "local_ocr_100",
            trialEnabled: false,
            headline: "本机积分",
            subtitle: "用于当前设备的本地识字和朗读。购买由 Apple 确认，余额只保存在本机 Keychain。",
            trustBullets: [
                "购买或赠送的积分不按日期过期，使用后按页面显示的消耗值扣减。",
                "学习内容和本机积分默认只保存在当前设备，不上传到开发者服务器。",
                "消耗型本机积分不支持跨设备自动恢复。"
            ],
            legalNotice: "价格与扣款以 Apple 确认弹窗为准；换机、抹掉设备或重置本机钱包后余额可能无法恢复。"
        ),
        usagePolicy: .default,
        supportEmail: "support@paipai.app",
        supportUrl: "https://www.paipai.app/support",
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
        headline: String = "本机积分",
        subtitle: String = "用于当前设备的本地识字和朗读。购买由 Apple 确认，余额只保存在本机 Keychain。",
        trustBullets: [String] = [
            "购买或赠送的积分不按日期过期，使用后按页面显示的消耗值扣减。",
            "学习内容和本机积分默认只保存在当前设备，不上传到开发者服务器。",
            "消耗型本机积分不支持跨设备自动恢复。"
        ],
        legalNotice: String = "价格与扣款以 Apple 确认弹窗为准；换机、抹掉设备或重置本机钱包后余额可能无法恢复。"
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
        defaultHighlight = try container.decodeIfPresent(String.self, forKey: .defaultHighlight) ?? "local_ocr_100"
        trialEnabled = try container.decodeIfPresent(Bool.self, forKey: .trialEnabled) ?? false
        headline = try container.decodeIfPresent(String.self, forKey: .headline) ?? "本机积分"
        subtitle = try container.decodeIfPresent(String.self, forKey: .subtitle) ?? "用于当前设备的本地识字和朗读。购买由 Apple 确认，余额只保存在本机 Keychain。"
        trustBullets = try container.decodeIfPresent([String].self, forKey: .trustBullets) ?? [
            "购买或赠送的积分不按日期过期，使用后按页面显示的消耗值扣减。",
            "学习内容和本机积分默认只保存在当前设备，不上传到开发者服务器。",
            "消耗型本机积分不支持跨设备自动恢复。"
        ]
        legalNotice = try container.decodeIfPresent(String.self, forKey: .legalNotice) ?? "价格与扣款以 Apple 确认弹窗为准；换机、抹掉设备或重置本机钱包后余额可能无法恢复。"
    }
}
