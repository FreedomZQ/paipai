import Foundation

struct ReviewCard: Identifiable, Hashable, Codable {
    let id: String
    var deviceId: String?
    var childId: String?
    var learningTrackCode: String?
    var learningLanguageCode: String?
    var text: String
    var supportHint: String
    var proficiency: Int
    var nextReviewAt: String?
    var syncEnabled: Bool?
    var storageMode: String?
    var sourceLanguageCode: String?
    var targetLanguageCode: String?
    var sourceType: String?
    var lastReviewedAt: String?
    var isReviewCompleted: Bool
    var reviewCount: Int
    var deletedAt: String?
    var recordVersion: Int
    var createdAt: String?
    var updatedAt: String?

    var translation: String? {
        supportHint.isEmpty ? nil : supportHint
    }

    init(
        id: String,
        deviceId: String? = nil,
        childId: String? = nil,
        learningTrackCode: String? = nil,
        learningLanguageCode: String? = nil,
        text: String,
        supportHint: String,
        proficiency: Int,
        nextReviewAt: String? = nil,
        syncEnabled: Bool? = nil,
        storageMode: String? = nil,
        sourceLanguageCode: String? = nil,
        targetLanguageCode: String? = nil,
        sourceType: String? = nil,
        lastReviewedAt: String? = nil,
        isReviewCompleted: Bool = false,
        reviewCount: Int = 0,
        deletedAt: String? = nil,
        recordVersion: Int = 1,
        createdAt: String? = nil,
        updatedAt: String? = nil
    ) {
        self.id = id
        self.deviceId = deviceId
        self.childId = childId
        self.learningTrackCode = learningTrackCode
        self.learningLanguageCode = learningLanguageCode
        self.text = text
        self.supportHint = supportHint
        self.proficiency = proficiency
        self.nextReviewAt = nextReviewAt
        self.syncEnabled = syncEnabled
        self.storageMode = storageMode
        self.sourceLanguageCode = sourceLanguageCode
        self.targetLanguageCode = targetLanguageCode
        self.sourceType = sourceType
        self.lastReviewedAt = lastReviewedAt
        self.isReviewCompleted = isReviewCompleted
        self.reviewCount = reviewCount
        self.deletedAt = deletedAt
        self.recordVersion = recordVersion
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(String.self, forKey: .id)
            ?? container.decode(String.self, forKey: .cardId)
        childId = try container.decodeIfPresent(String.self, forKey: .childId)
        deviceId = try container.decodeIfPresent(String.self, forKey: .deviceId)
        learningTrackCode = try container.decodeIfPresent(String.self, forKey: .learningTrackCode)
        learningLanguageCode = try container.decodeIfPresent(String.self, forKey: .learningLanguageCode)
        text = try container.decodeIfPresent(String.self, forKey: .text)
            ?? container.decodeIfPresent(String.self, forKey: .textPreview)
            ?? container.decodeIfPresent(String.self, forKey: .sourceText)
            ?? "已保存句卡"
        supportHint = try container.decodeIfPresent(String.self, forKey: .supportHint)
            ?? container.decodeIfPresent(String.self, forKey: .translatedText)
            ?? ""
        proficiency = try container.decodeIfPresent(Int.self, forKey: .proficiency) ?? 0
        nextReviewAt = try container.decodeIfPresent(String.self, forKey: .nextReviewAt)
        syncEnabled = try container.decodeIfPresent(Bool.self, forKey: .syncEnabled)
        storageMode = try container.decodeIfPresent(String.self, forKey: .storageMode)
        sourceLanguageCode = try container.decodeIfPresent(String.self, forKey: .sourceLanguageCode)
        targetLanguageCode = try container.decodeIfPresent(String.self, forKey: .targetLanguageCode)
        sourceType = try container.decodeIfPresent(String.self, forKey: .sourceType)
        lastReviewedAt = try container.decodeIfPresent(String.self, forKey: .lastReviewedAt)
        isReviewCompleted = try container.decodeIfPresent(Bool.self, forKey: .isReviewCompleted) ?? false
        reviewCount = try container.decodeIfPresent(Int.self, forKey: .reviewCount) ?? 0
        deletedAt = try container.decodeIfPresent(String.self, forKey: .deletedAt)
        recordVersion = try container.decodeIfPresent(Int.self, forKey: .recordVersion) ?? 1
        createdAt = try container.decodeIfPresent(String.self, forKey: .createdAt)
        updatedAt = try container.decodeIfPresent(String.self, forKey: .updatedAt)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encodeIfPresent(deviceId, forKey: .deviceId)
        try container.encodeIfPresent(childId, forKey: .childId)
        try container.encodeIfPresent(learningTrackCode, forKey: .learningTrackCode)
        try container.encodeIfPresent(learningLanguageCode, forKey: .learningLanguageCode)
        try container.encode(text, forKey: .text)
        try container.encode(supportHint, forKey: .supportHint)
        try container.encode(proficiency, forKey: .proficiency)
        try container.encodeIfPresent(nextReviewAt, forKey: .nextReviewAt)
        try container.encodeIfPresent(syncEnabled, forKey: .syncEnabled)
        try container.encodeIfPresent(storageMode, forKey: .storageMode)
        try container.encodeIfPresent(sourceLanguageCode, forKey: .sourceLanguageCode)
        try container.encodeIfPresent(targetLanguageCode, forKey: .targetLanguageCode)
        try container.encodeIfPresent(sourceType, forKey: .sourceType)
        try container.encodeIfPresent(lastReviewedAt, forKey: .lastReviewedAt)
        try container.encode(isReviewCompleted, forKey: .isReviewCompleted)
        try container.encode(reviewCount, forKey: .reviewCount)
        try container.encodeIfPresent(deletedAt, forKey: .deletedAt)
        try container.encode(recordVersion, forKey: .recordVersion)
        try container.encodeIfPresent(createdAt, forKey: .createdAt)
        try container.encodeIfPresent(updatedAt, forKey: .updatedAt)
    }

    var isDeleted: Bool {
        deletedAt != nil
    }

    private enum CodingKeys: String, CodingKey {
        case id
        case deviceId
        case cardId
        case childId
        case learningTrackCode
        case learningLanguageCode
        case text
        case textPreview
        case sourceText
        case supportHint
        case translatedText
        case proficiency
        case nextReviewAt
        case syncEnabled
        case storageMode
        case sourceLanguageCode
        case targetLanguageCode
        case sourceType
        case lastReviewedAt
        case isReviewCompleted
        case reviewCount
        case deletedAt
        case recordVersion
        case createdAt
        case updatedAt
    }

    static let sampleCards: [ReviewCard] = [
        ReviewCard(id: UUID().uuidString, text: "Good night, little bear.", supportHint: "晚安，小熊。", proficiency: 2),
        ReviewCard(id: UUID().uuidString, text: "The moon is bright tonight.", supportHint: "今晚月亮很亮。", proficiency: 1),
        ReviewCard(id: UUID().uuidString, text: "Let’s read one more page.", supportHint: "我们再读一页吧。", proficiency: 3)
    ]
}
