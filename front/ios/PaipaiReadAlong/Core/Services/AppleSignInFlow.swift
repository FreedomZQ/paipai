import CryptoKit
import Foundation
import Security

struct AppleSignInRequestContext {
    let state: String
    let rawNonce: String
    let requestNonce: String

    var backendNonce: String {
        // Apple identityToken 里的 nonce 会回显到请求里设置的哈希值；
        // 当前后端也按这个值做严格比对，所以这里回传 requestNonce。
        requestNonce
    }

    static func make(length: Int = 32) -> AppleSignInRequestContext {
        let rawNonce = randomNonce(length: length)
        return AppleSignInRequestContext(
            state: UUID().uuidString,
            rawNonce: rawNonce,
            requestNonce: sha256(rawNonce)
        )
    }

    private static func randomNonce(length: Int) -> String {
        precondition(length > 0)

        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        result.reserveCapacity(length)

        while result.count < length {
            var random: UInt8 = 0
            let status = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
            if status != errSecSuccess {
                return UUID().uuidString.replacingOccurrences(of: "-", with: "")
            }
            if Int(random) < charset.count {
                result.append(charset[Int(random)])
            }
        }

        return result
    }

    private static func sha256(_ value: String) -> String {
        let digest = SHA256.hash(data: Data(value.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}

extension PersonNameComponents {
    var normalizedGivenName: String? {
        givenName?.trimmedNilIfEmpty
    }

    var normalizedFamilyName: String? {
        familyName?.trimmedNilIfEmpty
    }
}

private extension String {
    var trimmedNilIfEmpty: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
