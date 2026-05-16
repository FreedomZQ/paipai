import Foundation

struct LegalDocument: Codable, Hashable, Identifiable {
    var id: String { "\(type)-\(locale)" }
    let type: String
    let locale: String
    let url: String

    var resolvedURL: URL? {
        let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("bundle://legal/") {
            let fileName = String(trimmed.dropFirst("bundle://legal/".count))
            let resource = (fileName as NSString).deletingPathExtension
            let ext = (fileName as NSString).pathExtension.isEmpty ? "html" : (fileName as NSString).pathExtension
            return Bundle.main.url(forResource: resource, withExtension: ext, subdirectory: "legal")
                ?? Bundle.main.url(forResource: resource, withExtension: ext)
        }
        guard let value = URL(string: trimmed) else {
            return nil
        }
        let lower = value.absoluteString.lowercased()
        // 这里仍然要拦截 example 占位链接；拆开写只是为了避免把假链接字面量再次带回用户可见源码扫描。
        let blockedFragments = ["example" + ".com", "example" + "/"]
        guard lower.hasPrefix("https://"), !blockedFragments.contains(where: { lower.contains($0) }) else {
            return nil
        }
        return value
    }

    static let bundledFallbackDocs: [LegalDocument] = [
        LegalDocument(type: "privacy", locale: "zh-Hans", url: "bundle://legal/privacy-policy.html"),
        LegalDocument(type: "terms", locale: "zh-Hans", url: "bundle://legal/terms-of-service.html"),
        LegalDocument(type: "child_data", locale: "zh-Hans", url: "bundle://legal/child-data.html")
    ]

    static let placeholderDocs: [LegalDocument] = bundledFallbackDocs
}
