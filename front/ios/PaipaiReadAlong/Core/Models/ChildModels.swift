import Foundation

struct ChildProfile: Identifiable, Hashable, Codable {
    let id: String
    var nickname: String
    var ageBand: String
    var learningTrackCode: String
    var avatarEmoji: String
    var profileStatus: String?
    var deletedAt: String?
    var recordVersion: Int
    var updatedAt: String?

    init(
        id: String,
        nickname: String,
        ageBand: String,
        learningTrackCode: String,
        avatarEmoji: String = "🧸",
        profileStatus: String? = "active",
        deletedAt: String? = nil,
        recordVersion: Int = 1,
        updatedAt: String? = nil
    ) {
        self.id = id
        self.nickname = nickname
        self.ageBand = ChildAgeBand.normalizedCode(ageBand)
        self.learningTrackCode = learningTrackCode
        self.avatarEmoji = avatarEmoji
        self.profileStatus = profileStatus
        self.deletedAt = deletedAt
        self.recordVersion = recordVersion
        self.updatedAt = updatedAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(String.self, forKey: .id)
            ?? container.decode(String.self, forKey: .childId)
        nickname = try container.decode(String.self, forKey: .nickname)
        ageBand = ChildAgeBand.normalizedCode(try container.decode(String.self, forKey: .ageBand))
        learningTrackCode = try container.decode(String.self, forKey: .learningTrackCode)
        avatarEmoji = try container.decodeIfPresent(String.self, forKey: .avatarEmoji) ?? "🧸"
        profileStatus = try container.decodeIfPresent(String.self, forKey: .profileStatus)
        deletedAt = try container.decodeIfPresent(String.self, forKey: .deletedAt)
        recordVersion = try container.decodeIfPresent(Int.self, forKey: .recordVersion) ?? 1
        updatedAt = try container.decodeIfPresent(String.self, forKey: .updatedAt)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(nickname, forKey: .nickname)
        try container.encode(ageBand, forKey: .ageBand)
        try container.encode(learningTrackCode, forKey: .learningTrackCode)
        try container.encode(avatarEmoji, forKey: .avatarEmoji)
        try container.encodeIfPresent(profileStatus, forKey: .profileStatus)
        try container.encodeIfPresent(deletedAt, forKey: .deletedAt)
        try container.encode(recordVersion, forKey: .recordVersion)
        try container.encodeIfPresent(updatedAt, forKey: .updatedAt)
    }

    var isDeleted: Bool {
        deletedAt != nil || profileStatus == "deleted"
    }

    private enum CodingKeys: String, CodingKey {
        case id
        case childId
        case nickname
        case ageBand
        case learningTrackCode
        case avatarEmoji
        case profileStatus
        case deletedAt
        case recordVersion
        case updatedAt
    }

    static let placeholder = ChildProfile(
        id: UUID().uuidString,
        nickname: "宝贝 1",
        ageBand: ChildAgeBand.defaultCode,
        learningTrackCode: "zh_to_en",
        avatarEmoji: "🧸"
    )
    static let `default` = placeholder
}

enum AgeBand: String, CaseIterable {
    case age3_4 = "3_4"
    case age5_6 = "5_6"
    case age7_8 = "7_8"
    case age9Plus = "9_plus"

    var displayName: String {
        switch self {
        case .age3_4: return "3-4岁"
        case .age5_6: return "5-6岁"
        case .age7_8: return "7-8岁"
        case .age9Plus: return "9岁以上"
        }
    }
}

enum LearningTrack: String, CaseIterable {
    case zhToEn = "zh_to_en"
    case enToZh = "en_to_zh"
    case bilingual = "bilingual"

    var displayName: String {
        switch self {
        case .zhToEn: return "中文家庭学英语"
        case .enToZh: return "English families learn Chinese"
        case .bilingual: return "双语"
        }
    }

    var icon: String {
        switch self {
        case .zhToEn: return "🇬🇧"
        case .enToZh: return "🇨🇳"
        case .bilingual: return "🌏"
        }
    }
}
