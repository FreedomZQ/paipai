import Foundation
import CryptoKit
import Security

/// 句卡正文的本机加密服务。
///
/// 设计取舍：首发版本优先降低儿童内容上传明文带来的法律与审核风险，因此完整句子只写入
/// `encrypted_text`；`text_preview` 保持非敏感通用占位。密钥保存在当前 App + appCode 隔离的
/// Keychain 中，不上传到后端。这样个人开发者无需维护密钥托管服务，运维成本最低。
///
/// 注意：该方案保护“本机/同步 payload 中的完整正文不明文外泄”，但不是跨设备端到端同步密钥方案。
/// 如果用户换机且没有同一 Keychain 密钥，只能看到通用预览；后续如要多设备可读，应另行设计家长
/// 授权的密钥迁移或账号级密钥托管，不能把本地密钥直接上传到后端。
final class LocalTextCryptoService {
    static let shared = LocalTextCryptoService()

    static let envelopePrefix = "enc:v1:aesgcm:keychain:"
    static let encryptionVersion = "aesgcm_keychain_v1"
    static let keyId = "local_device_key_v1"
    static let redactedPreview = "已保存句卡"

    private let service: String
    private let account: String

    init(
        appCode: String = AppIdentity.appCode,
        bundleIdentifier: String = AppIdentity.bundleIdentifier
    ) {
        self.service = "\(bundleIdentifier).\(appCode).review-card-crypto"
        self.account = "\(appCode).\(Self.keyId)"
    }

    func encrypt(_ plainText: String) -> String {
        let normalized = plainText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty,
              let data = normalized.data(using: .utf8),
              let key = loadOrCreateKey(),
              let sealedBox = try? AES.GCM.seal(data, using: key),
              let combined = sealedBox.combined else {
            // 极端情况下 Keychain/CryptoKit 不可用时，仍避免把完整原文伪装成“加密”。
            // 用不可逆摘要作为兜底，后端与云端只能看到摘要，不再看到明文正文。
            return "hash:v1:sha256:\(SHA256.hash(data: Data(normalized.utf8)).compactMap { String(format: "%02x", $0) }.joined())"
        }
        return Self.envelopePrefix + combined.base64EncodedString()
    }

    func decrypt(_ envelope: String?) -> String? {
        guard let envelope, !envelope.isBlank else { return nil }
        guard envelope.hasPrefix(Self.envelopePrefix) else { return legacyBase64Decode(envelope) }
        let encoded = String(envelope.dropFirst(Self.envelopePrefix.count))
        guard let data = Data(base64Encoded: encoded),
              let sealedBox = try? AES.GCM.SealedBox(combined: data),
              let key = loadKey(),
              let plain = try? AES.GCM.open(sealedBox, using: key),
              let text = String(data: plain, encoding: .utf8),
              !text.isBlank else {
            return nil
        }
        return text
    }

    func safePreview(for plainText: String) -> String {
        // 不把儿童阅读内容放入可同步的明文 preview。UI 展示时优先解密 encrypted_text。
        Self.redactedPreview
    }

    private func legacyBase64Decode(_ value: String) -> String? {
        guard let data = Data(base64Encoded: value),
              let decoded = String(data: data, encoding: .utf8),
              !decoded.isBlank else { return nil }
        return decoded
    }

    private func loadOrCreateKey() -> SymmetricKey? {
        if let key = loadKey() { return key }
        let key = SymmetricKey(size: .bits256)
        let raw = key.withUnsafeBytes { Data($0) }
        saveKey(raw)
        return key
    }

    private func loadKey() -> SymmetricKey? {
        var query = baseQuery
        query[kSecReturnData as String] = kCFBooleanTrue
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return SymmetricKey(data: data)
    }

    private func saveKey(_ data: Data) {
        let status = SecItemCopyMatching(baseQuery as CFDictionary, nil)
        if status == errSecSuccess {
            SecItemUpdate(baseQuery as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        } else {
            var query = baseQuery
            query[kSecValueData as String] = data
            query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(query as CFDictionary, nil)
        }
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }
}

private extension String {
    var isBlank: Bool {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
