import Foundation

struct OcrExtractReceipt: Codable {
    let traceId: String?
    let provider: String
    let model: String
    let text: String
    let prompt: String
    let minPixels: Int
    let maxPixels: Int
    let allowed: Bool?
    let serviceStatus: String?
    let remainingTrialCount: Int?
    let upgradeTitle: String?
    let upgradeMessage: String?
    let unlockOptions: [String]?
}

struct OcrFeedback {
    enum Status {
        case success
        case empty
        case failed
    }

    let status: Status
    let title: String
    let message: String
    let guidance: String?
    let text: String
    let traceId: String?
    let provider: String?
    let model: String?
    let isRetryable: Bool
    let alertTitle: String?
    let alertMessage: String?
}

struct CloudSpeechReceipt: Codable {
    let allowed: Bool?
    let serviceStatus: String?
    let remainingTrialCount: Int?
    let provider: String?
    let model: String?
    let audioBase64: String?
    let mimeType: String?
    let text: String?
    let languageCode: String?
    let rate: Float?
    let upgradeTitle: String?
    let upgradeMessage: String?
    let unlockOptions: [String]?
}
