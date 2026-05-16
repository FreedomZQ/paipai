import Foundation
import os

struct TranslationRequestPlan: Equatable {
    let sourceLanguageCode: String?
    let targetLanguageCode: String
    let sourceMode: SourceMode
    let sourceTextLength: Int
    let sourceTextFingerprint: String

    enum SourceMode: String {
        case automatic
        case explicit
    }
}

enum TranslationTextProcessor {
    static func preprocess(_ text: String) -> String {
        text
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .replacingOccurrences(of: "\u{00A0}", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func postprocess(_ text: String) -> String {
        text
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

enum TranslationPipelinePlanner {
    static func makePlan(
        text: String,
        sourceLanguageOverride: String?,
        targetLanguageOverride: String?,
        fallbackSourceLanguageCode: String,
        fallbackTargetLanguageCode: String
    ) -> TranslationRequestPlan {
        let preparedText = TranslationTextProcessor.preprocess(text)
        let targetLanguageCode = normalizeLanguageCode(targetLanguageOverride ?? fallbackTargetLanguageCode)
        let normalizedSourceOverride = sourceLanguageOverride.map(normalizeLanguageCode)
        let sourceLanguageCode = normalizedSourceOverride.flatMap { source in
            isSameLanguageFamily(source, targetLanguageCode) ? nil : source
        }

        return TranslationRequestPlan(
            sourceLanguageCode: sourceLanguageCode,
            targetLanguageCode: targetLanguageCode,
            sourceMode: sourceLanguageCode == nil ? .automatic : .explicit,
            sourceTextLength: preparedText.count,
            sourceTextFingerprint: fingerprint(preparedText)
        )
    }

    static func normalizeLanguageCode(_ languageCode: String) -> String {
        let lowered = languageCode
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "_", with: "-")
            .lowercased()
        if lowered == "zh" || lowered.hasPrefix("zh-hans") || lowered.hasPrefix("zh-cn") {
            return "zh-Hans"
        }
        if lowered.hasPrefix("zh-hant") || lowered.hasPrefix("zh-tw") || lowered.hasPrefix("zh-hk") {
            return "zh-Hant"
        }
        if let primary = lowered.split(separator: "-").first {
            return String(primary)
        }
        return lowered
    }

    static func isSameLanguageFamily(_ lhs: String, _ rhs: String) -> Bool {
        languageFamily(lhs) == languageFamily(rhs)
    }

    static func fingerprint(_ text: String) -> String {
        var hash: UInt64 = 14_695_981_039_346_656_037
        for byte in text.utf8 {
            hash ^= UInt64(byte)
            hash &*= 1_099_511_628_211
        }
        return String(format: "%016llx", hash)
    }

    private static func languageFamily(_ code: String) -> String {
        let normalized = normalizeLanguageCode(code).lowercased()
        if normalized.hasPrefix("zh") { return "zh" }
        if let primary = normalized.split(separator: "-").first {
            return String(primary)
        }
        return normalized
    }
}

enum TranslationDiagnostics {
    static let logger = Logger(subsystem: "com.paipai.readalong", category: "TranslationPipeline")
}
