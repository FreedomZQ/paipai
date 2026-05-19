import Foundation

struct DailyLearningTaskFeed: Codable, Hashable {
    let taskDate: String
    let scope: String
    let isPremiumPlan: Bool
    let currentChildId: String?
    let tasks: [DailyLearningTask]

    init(
        taskDate: String,
        scope: String,
        isPremiumPlan: Bool,
        currentChildId: String?,
        tasks: [DailyLearningTask]
    ) {
        self.taskDate = taskDate
        self.scope = scope
        self.isPremiumPlan = isPremiumPlan
        self.currentChildId = currentChildId
        self.tasks = tasks
    }

    private enum CodingKeys: String, CodingKey {
        case taskDate
        case scope
        case isPremiumPlan
        case currentChildId
        case tasks
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        taskDate = try container.decodeIfPresent(String.self, forKey: .taskDate) ?? ""
        scope = try container.decodeIfPresent(String.self, forKey: .scope) ?? "single_child"
        isPremiumPlan = try container.decodeIfPresent(Bool.self, forKey: .isPremiumPlan) ?? false
        currentChildId = try container.decodeIfPresent(String.self, forKey: .currentChildId)
        tasks = try container.decodeIfPresent([DailyLearningTask].self, forKey: .tasks) ?? []
    }
}

struct DailyLearningTask: Codable, Hashable, Identifiable {
    let taskId: String
    let childId: String?
    let taskType: String
    let title: String
    let reason: String
    let estimatedMinutes: Int
    let status: String
    let ctaType: String
    let completionMessage: String?

    var id: String { taskId }
    var isCompleted: Bool { status == "completed" }

    init(
        taskId: String = UUID().uuidString,
        childId: String? = nil,
        taskType: String,
        title: String,
        reason: String,
        estimatedMinutes: Int = 3,
        status: String = "generated",
        ctaType: String,
        completionMessage: String? = nil
    ) {
        self.taskId = taskId
        self.childId = childId
        self.taskType = taskType
        self.title = title
        self.reason = reason
        self.estimatedMinutes = estimatedMinutes
        self.status = status
        self.ctaType = ctaType
        self.completionMessage = completionMessage
    }

    private enum CodingKeys: String, CodingKey {
        case taskId
        case childId
        case taskType
        case title
        case reason
        case estimatedMinutes
        case status
        case ctaType
        case completionMessage
    }

    /// Decoding-only aliases: backend may send `id` for taskId, `message` for completionMessage.
    private enum DecodeAliasKeys: String, CodingKey {
        case id
        case message
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let aliases = try decoder.container(keyedBy: DecodeAliasKeys.self)
        taskId = try container.decodeIfPresent(String.self, forKey: .taskId)
            ?? aliases.decodeIfPresent(String.self, forKey: .id)
            ?? UUID().uuidString
        childId = try container.decodeIfPresent(String.self, forKey: .childId)
        taskType = try container.decodeIfPresent(String.self, forKey: .taskType) ?? "local_ocr_one"
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? "今天拍 1 句"
        reason = try container.decodeIfPresent(String.self, forKey: .reason) ?? "先拍一句，给明天的复习留一个起点。"
        estimatedMinutes = try container.decodeIfPresent(Int.self, forKey: .estimatedMinutes) ?? 3
        status = try container.decodeIfPresent(String.self, forKey: .status) ?? "generated"
        ctaType = try container.decodeIfPresent(String.self, forKey: .ctaType) ?? Self.defaultCtaType(for: taskType)
        completionMessage = try container.decodeIfPresent(String.self, forKey: .completionMessage)
            ?? aliases.decodeIfPresent(String.self, forKey: .message)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(taskId, forKey: .taskId)
        try container.encodeIfPresent(childId, forKey: .childId)
        try container.encode(taskType, forKey: .taskType)
        try container.encode(title, forKey: .title)
        try container.encode(reason, forKey: .reason)
        try container.encode(estimatedMinutes, forKey: .estimatedMinutes)
        try container.encode(status, forKey: .status)
        try container.encode(ctaType, forKey: .ctaType)
        try container.encodeIfPresent(completionMessage, forKey: .completionMessage)
    }

    private static func defaultCtaType(for taskType: String) -> String {
        switch taskType {
        case "review_one", "keep_streak":
            return "go_review"
        default:
            return "go_local_ocr"
        }
    }
}

struct DailyLearningTaskCompletion: Codable, Hashable {
    let taskId: String
    let status: String
    let completedAt: String?
    let streakDays: Int
    let weeklyActiveDays: Int
    let weeklyReviewCount: Int
    let todayCompletedCount: Int
    let message: String

    private enum CodingKeys: String, CodingKey {
        case taskId
        case status
        case completedAt
        case streakDays
        case weeklyActiveDays
        case weeklyReviewCount
        case todayCompletedCount
        case message
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        taskId = try container.decodeIfPresent(String.self, forKey: .taskId) ?? ""
        status = try container.decodeIfPresent(String.self, forKey: .status) ?? "completed"
        completedAt = try container.decodeIfPresent(String.self, forKey: .completedAt)
        streakDays = try container.decodeIfPresent(Int.self, forKey: .streakDays) ?? 0
        weeklyActiveDays = try container.decodeIfPresent(Int.self, forKey: .weeklyActiveDays) ?? 0
        weeklyReviewCount = try container.decodeIfPresent(Int.self, forKey: .weeklyReviewCount) ?? 0
        todayCompletedCount = try container.decodeIfPresent(Int.self, forKey: .todayCompletedCount) ?? 0
        message = try container.decodeIfPresent(String.self, forKey: .message) ?? "今天完成了，继续保持就好。"
    }
}
