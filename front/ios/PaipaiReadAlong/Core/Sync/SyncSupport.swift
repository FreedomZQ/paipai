import Foundation

enum SyncClock {
    private static let formatterWithFractional: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let formatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    static func nowString() -> String {
        formatterWithFractional.string(from: Date())
    }

    static func date(from value: String?) -> Date? {
        guard let value, !value.isEmpty else { return nil }
        return formatterWithFractional.date(from: value) ?? formatter.date(from: value)
    }

    static func string(from date: Date) -> String {
        formatterWithFractional.string(from: date)
    }

    static func dateOnly(from date: Date) -> String {
        let calendar = Calendar(identifier: .gregorian)
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", components.year ?? 1970, components.month ?? 1, components.day ?? 1)
    }
}

final class ScopedJSONStore {
    private let defaults: AppScopedDefaults

    init(namespace: String) {
        self.defaults = AppScopedDefaults(namespace: namespace)
    }

    func load<T: Decodable>(_ type: T.Type, scope: String, fallback: T) -> T {
        guard let data = defaults.data(forKey: scope) else { return fallback }
        return (try? JSONDecoder().decode(T.self, from: data)) ?? fallback
    }

    func save<T: Encodable>(_ value: T, scope: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        defaults.set(data, forKey: scope)
    }

    func clear(scope: String) {
        defaults.removeObject(forKey: scope)
    }
}
