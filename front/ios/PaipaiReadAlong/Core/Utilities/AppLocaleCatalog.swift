import Foundation

enum AppLocaleCatalog {
    static let supportedInterfaceLocales = ["zh-Hans", "en", "ja", "ko", "es"]

    static func mergedSupportedLocales(with remoteLocales: [String], currentLocale: String) -> [String] {
        var result: [String] = []
        for locale in supportedInterfaceLocales + remoteLocales + [currentLocale] {
            let normalized = normalize(locale)
            guard !normalized.isEmpty, !result.contains(where: { normalize($0) == normalized }) else { continue }
            result.append(locale)
        }
        return result
    }

    static func title(for locale: String) -> String {
        let normalized = normalize(locale)
        if normalized.hasPrefix("zh") { return "简体中文" }
        if normalized.hasPrefix("en") { return "English" }
        if normalized.hasPrefix("ja") { return "日本語" }
        if normalized.hasPrefix("ko") { return "한국어" }
        if normalized.hasPrefix("es") { return "Español" }
        return locale
    }

    static func subtitle(for locale: String) -> String {
        let normalized = normalize(locale)
        if normalized.hasPrefix("zh") { return "Simplified Chinese" }
        if normalized.hasPrefix("en") { return "English" }
        if normalized.hasPrefix("ja") { return "Japanese" }
        if normalized.hasPrefix("ko") { return "Korean" }
        if normalized.hasPrefix("es") { return "Spanish" }
        return locale
    }

    static func normalize(_ locale: String) -> String {
        locale.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "_", with: "-")
            .lowercased()
    }
}
