import Foundation

struct ChildAgeBand: Hashable, Identifiable {
    let code: String
    let title: String

    var id: String { code }

    static let options: [ChildAgeBand] = [
        ChildAgeBand(code: "3_4", title: "3-4"),
        ChildAgeBand(code: "5_6", title: "5-6"),
        ChildAgeBand(code: "7_8", title: "7-8")
    ]

    static let defaultCode = "5_6"

    static func normalizedCode(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return defaultCode }
        if trimmed == "9+" || trimmed == "9＋" {
            return "9_plus"
        }
        return trimmed.replacingOccurrences(of: "-", with: "_")
    }

    static func displayTitle(for code: String) -> String {
        let normalized = normalizedCode(code)
        if normalized == "9_plus" {
            return "9+"
        }
        return options.first(where: { $0.code == normalized })?.title
            ?? normalized.replacingOccurrences(of: "_", with: "-")
    }
}
