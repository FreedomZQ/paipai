import Foundation
import Security

protocol SessionStoring {
    func load() -> StoredAuthSession?
    func save(_ session: StoredAuthSession)
    func clear()
}

final class SecureSessionStore: SessionStoring {
    private let service: String
    private let account: String

    init(
        appCode: String = AppIdentity.appCode,
        bundleIdentifier: String = AppIdentity.bundleIdentifier
    ) {
        self.service = "\(bundleIdentifier).\(appCode).auth"
        self.account = "\(appCode).current-bearer-session"
    }

    func load() -> StoredAuthSession? {
        var query = baseQuery
        query[kSecReturnData as String] = kCFBooleanTrue
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess,
              let data = item as? Data else {
            return nil
        }
        return try? JSONDecoder().decode(StoredAuthSession.self, from: data)
    }

    func save(_ session: StoredAuthSession) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        let status = SecItemCopyMatching(baseQuery as CFDictionary, nil)
        if status == errSecSuccess {
            SecItemUpdate(baseQuery as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        } else {
            var query = baseQuery
            query[kSecValueData as String] = data
            SecItemAdd(query as CFDictionary, nil)
        }
    }

    func clear() {
        SecItemDelete(baseQuery as CFDictionary)
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }
}
