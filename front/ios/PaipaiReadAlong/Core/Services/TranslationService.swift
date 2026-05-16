import Foundation
#if os(iOS) && canImport(Translation)
import Translation
#endif

struct TranslationLanguagePackStatus {
    let isSupported: Bool
    let isReady: Bool
    let message: String?

    static let ready = TranslationLanguagePackStatus(isSupported: true, isReady: true, message: nil)
}

@MainActor
final class TranslationService {
    func checkLanguagePackAvailability(for targetLanguage: String) async -> Bool {
        let status = await checkLanguagePackAvailability(source: nil, target: targetLanguage)
        return status.isSupported
    }

    func checkLanguagePackAvailability(source sourceLanguage: String? = nil, target targetLanguage: String) async -> TranslationLanguagePackStatus {
        let target = normalizeLanguageCode(targetLanguage)
        let source = normalizeLanguageCode(sourceLanguage ?? "")
        guard isSupportedLanguage(target) else {
            return TranslationLanguagePackStatus(isSupported: false, isReady: false, message: "当前目标语言暂不支持翻译：\(targetLanguage)")
        }
        if !source.isEmpty, source == target {
            return TranslationLanguagePackStatus(isSupported: true, isReady: true, message: nil)
        }
        if !source.isEmpty, !isSupportedLanguage(source) {
            return TranslationLanguagePackStatus(isSupported: false, isReady: false, message: "当前源语言暂不支持翻译：\(sourceLanguage ?? "")")
        }
        #if os(iOS) && canImport(Translation)
        if #available(iOS 18.0, *) {
            guard !source.isEmpty else {
                return TranslationLanguagePackStatus(isSupported: true, isReady: false, message: languagePackPreparationMessage(source: nil, target: target))
            }
            let candidates = languageCandidates(source: source, target: target)
            for pair in candidates {
                let status = await LanguageAvailability().status(
                    from: Locale.Language(identifier: pair.source),
                    to: Locale.Language(identifier: pair.target)
                )
                switch status {
                case .installed:
                    return .ready
                case .supported:
                    return TranslationLanguagePackStatus(isSupported: true, isReady: false, message: languagePackPreparationMessage(source: pair.source, target: pair.target))
                case .unsupported:
                    continue
                @unknown default:
                    return TranslationLanguagePackStatus(isSupported: true, isReady: false, message: languagePackPreparationMessage(source: pair.source, target: pair.target))
                }
            }
            return TranslationLanguagePackStatus(isSupported: true, isReady: false, message: languagePackPreparationMessage(source: source, target: target))
        }
        #endif
        return TranslationLanguagePackStatus(isSupported: true, isReady: false, message: "当前系统版本不支持设备端翻译，请升级到 iOS 18 或更高版本。")
    }

    func downloadLanguagePackIfNeeded(for targetLanguage: String) async -> Bool {
        let status = await downloadLanguagePackIfNeeded(source: nil, target: targetLanguage)
        return status.isReady
    }

    func downloadLanguagePackIfNeeded(source sourceLanguage: String? = nil, target targetLanguage: String) async -> TranslationLanguagePackStatus {
        let status = await checkLanguagePackAvailability(source: sourceLanguage, target: targetLanguage)
        guard status.isSupported else { return status }
        #if os(iOS) && canImport(Translation)
        if #available(iOS 18.0, *) {
            // The actual Translation framework download prompt must be driven by a SwiftUI
            // translationTask session. Keep this service conservative: it validates support and
            // lets the view layer run the session-backed translation/download flow.
            return status
        }
        #endif
        return status
    }

    func languagePackPreparationMessage(source sourceLanguage: String? = nil, target targetLanguage: String) -> String {
        let source = sourceLanguage.map { normalizeLanguageCode($0) } ?? "auto"
        let target = normalizeLanguageCode(targetLanguage)
        return "首次使用 \(source) → \(target) 翻译时，系统可能会提示下载设备端语言包。"
    }

    func preferredTranslationLanguagePair(source sourceLanguage: String, target targetLanguage: String) async -> (source: String, target: String) {
        let source = normalizeLanguageCode(sourceLanguage)
        let target = normalizeLanguageCode(targetLanguage)
        #if os(iOS) && canImport(Translation)
        if #available(iOS 18.0, *) {
            let candidates = languageCandidates(source: source, target: target)
            for pair in candidates {
                let status = await LanguageAvailability().status(
                    from: Locale.Language(identifier: pair.source),
                    to: Locale.Language(identifier: pair.target)
                )
                if status == .installed || status == .supported {
                    return pair
                }
            }
        }
        #endif
        return (source: source, target: target)
    }

    func isTargetLanguagePackInstalled(target targetLanguage: String, preferredSource sourceLanguage: String? = nil) async -> Bool {
        let target = normalizeLanguageCode(targetLanguage)
        guard isSupportedLanguage(target) else { return false }
        #if os(iOS) && canImport(Translation)
        if #available(iOS 18.0, *) {
            // 系统 Translation 只暴露“源语种 -> 目标语种”的可用性查询。
            // 这里用一组预设源语种探测目标语种包是否已经安装，避免 OCR 源语种不适合直连时误弹下载提示。
            for source in targetLanguageProbeSources(preferredSource: sourceLanguage, target: target) {
                let status = await LanguageAvailability().status(
                    from: Locale.Language(identifier: source),
                    to: Locale.Language(identifier: target)
                )
                if status == .installed {
                    return true
                }
            }
        }
        #endif
        return false
    }

    func localizeAnnouncements(_ announcements: [Announcement], targetLanguageCode: String) async -> [Announcement] {
        return announcements
    }

    private func isSupportedLanguage(_ languageCode: String) -> Bool {
        let normalized = normalizeLanguageCode(languageCode)
        return supportedLanguagePrefixes.contains { normalized.hasPrefix($0) }
    }

    private func normalizeLanguageCode(_ languageCode: String) -> String {
        let trimmed = languageCode.trimmingCharacters(in: .whitespacesAndNewlines)
        let lowered = trimmed.replacingOccurrences(of: "_", with: "-").lowercased()
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

    private func languageCandidates(source: String, target: String) -> [(source: String, target: String)] {
        var candidates: [(source: String, target: String)] = [(source, target)]
        let fallbackSource = frameworkFallbackLanguageCode(source)
        let fallbackTarget = frameworkFallbackLanguageCode(target)
        let fallbackPair = (source: fallbackSource, target: fallbackTarget)
        if fallbackPair.source != source || fallbackPair.target != target {
            candidates.append(fallbackPair)
        }
        return candidates
    }

    private func frameworkFallbackLanguageCode(_ languageCode: String) -> String {
        let normalized = normalizeLanguageCode(languageCode)
        if normalized.hasPrefix("zh") { return "zh" }
        return normalized
    }

    private func targetLanguageProbeSources(preferredSource sourceLanguage: String?, target targetLanguage: String) -> [String] {
        var sources: [String] = []
        if let sourceLanguage {
            sources.append(normalizeLanguageCode(sourceLanguage))
        }
        sources.append(contentsOf: ["en", "zh-Hans", "ja", "ko", "es", "fr", "de", "it", "pt"])
        return sources
            .map(frameworkFallbackLanguageCode)
            .filter { !$0.isEmpty && !isSameLanguageFamily($0, targetLanguage) }
            .reduce(into: []) { result, source in
                if !result.contains(source) {
                    result.append(source)
                }
            }
    }

    private func isSameLanguageFamily(_ lhs: String, _ rhs: String) -> Bool {
        languageFamily(lhs) == languageFamily(rhs)
    }

    private func languageFamily(_ languageCode: String) -> String {
        let normalized = normalizeLanguageCode(languageCode).lowercased()
        if normalized.hasPrefix("zh") { return "zh" }
        if let primary = normalized.split(separator: "-").first {
            return String(primary)
        }
        return normalized
    }

    private var supportedLanguagePrefixes: Set<String> {
        [
            "ar", "ca", "cs", "da", "de", "el", "en", "es", "fi", "fr",
            "he", "hi", "id", "it", "ja", "ko", "ms", "nb", "nl", "pl",
            "pt", "ro", "ru", "sk", "sv", "th", "tr", "uk", "vi", "zh"
        ]
    }
}
