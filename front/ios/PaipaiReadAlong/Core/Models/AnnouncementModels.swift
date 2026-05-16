import Foundation

// MARK: - Announcement
struct Announcement: Codable, Identifiable {
    let id: String
    let title: String
    let content: String
    let type: AnnouncementType
    let priority: Int
    let publishedAt: Date
    let startDate: Date
    let endDate: Date?
    let actionUrl: String?
    let actionText: String?
    let isDismissible: Bool
    let localizedTitle: String?
    let localizedContent: String?
    let localizedActionText: String?

    static let `default` = Announcement(
        id: "welcome",
        title: "欢迎使用拍拍伴读",
        content: "拍一句，听一句，慢慢会读。开始你的学习之旅吧！",
        type: .info,
        priority: 0,
        publishedAt: Date(),
        startDate: Date(),
        endDate: nil,
        actionUrl: nil,
        actionText: nil,
        isDismissible: true,
        localizedTitle: nil,
        localizedContent: nil,
        localizedActionText: nil
    )

    var displayTitle: String {
        localizedTitle?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? localizedTitle! : title
    }

    var displayContent: String {
        localizedContent?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? localizedContent! : content
    }

    var displayActionText: String? {
        let candidate = localizedActionText?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? localizedActionText : actionText
        return candidate?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? candidate : nil
    }
}

// MARK: - Announcement Type
enum AnnouncementType: String, Codable {
    case info = "info"
    case update = "update"
    case activity = "activity"
    case system = "system"
    
    var displayName: String {
        switch self {
        case .info: return "信息"
        case .update: return "更新"
        case .activity: return "活动"
        case .system: return "系统"
        }
    }

    func displayName(for localeCode: String) -> String {
        let isEnglishFallback = !AppLocaleCatalog.normalize(localeCode).hasPrefix("zh")
        switch self {
        case .info: return isEnglishFallback ? "Info" : "信息"
        case .update: return isEnglishFallback ? "Update" : "更新"
        case .activity: return isEnglishFallback ? "Activity" : "活动"
        case .system: return isEnglishFallback ? "System" : "系统"
        }
    }
    
    var color: String {
        switch self {
        case .info: return "#118AB2"
        case .update: return "#FF9800"
        case .activity: return "#9C27B0"
        case .system: return "#F44336"
        }
    }
    
    var backgroundColor: String {
        switch self {
        case .info: return "#E8F5FF"
        case .update: return "#FFF3E0"
        case .activity: return "#F3E5F5"
        case .system: return "#FFEBEE"
        }
    }
}

// MARK: - Announcement Display Config
struct AnnouncementDisplayConfig {
    let maxPerDay: Int
    let minInterval: TimeInterval
    let triggerOnLaunch: Bool
    let triggerOnTimer: Bool
    let timerInterval: TimeInterval
    
    static let `default` = AnnouncementDisplayConfig(
        maxPerDay: 3,
        minInterval: 30 * 60, // 30 minutes
        triggerOnLaunch: true,
        triggerOnTimer: false,
        timerInterval: 60 * 60 // 1 hour
    )
}
