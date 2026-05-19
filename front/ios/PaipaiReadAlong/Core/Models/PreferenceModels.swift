import Foundation

struct UserPreference: Codable, Hashable {
    let userId: Int?
    let uiLocale: String
    let sourceLanguageCode: String
    let targetLanguageCode: String
    let readingTrackCode: String
    let ttsVoiceCode: String?
    let translationMode: String
    let updatedAt: String?
    let persisted: Bool

    static let `default` = UserPreference(
        userId: nil,
        uiLocale: "zh-Hans",
        sourceLanguageCode: "en",
        targetLanguageCode: "zh-Hans",
        readingTrackCode: "zh_to_en",
        ttsVoiceCode: nil,
        translationMode: "device_only",
        updatedAt: nil,
        persisted: false
    )
}
