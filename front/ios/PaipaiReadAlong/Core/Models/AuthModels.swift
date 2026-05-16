import Foundation

enum AuthMode: String {
    case signedOut
    case formalAccount
}

struct AuthSessionEnvelope: Codable {
    let session: SessionSummary?
    let account: AuthAccount
    let accountState: AccountState?
    let recentSessions: [RecentSession]
    let note: String?

    var persistedSession: StoredAuthSession? {
        guard let session, let accessToken = session.accessToken else { return nil }
        return StoredAuthSession(
            accessToken: accessToken,
            tokenType: session.tokenType,
            sessionId: session.sessionId,
            sessionType: session.sessionType,
            expiresAt: session.expiresAt,
            account: account
        )
    }
}

struct SessionSummary: Codable {
    let accessToken: String?
    let tokenType: String
    let sessionId: String
    let sessionType: String
    let expiresAt: String
}

struct AuthAccount: Codable, Hashable {
    let accountId: String
    let signInProvider: String
    let formalAccount: Bool
    let email: String?
    let identityVerificationState: String?
    let createdAt: String?

    init(
        accountId: String,
        signInProvider: String,
        formalAccount: Bool,
        email: String?,
        identityVerificationState: String?,
        createdAt: String? = nil
    ) {
        self.accountId = accountId
        self.signInProvider = signInProvider
        self.formalAccount = formalAccount
        self.email = email
        self.identityVerificationState = identityVerificationState
        self.createdAt = createdAt
    }

    private enum CodingKeys: String, CodingKey {
        case accountId
        case signInProvider
        case formalAccount
        case email
        case identityVerificationState
        case createdAt
        case createdAtSnake = "created_at"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        accountId = try container.decode(String.self, forKey: .accountId)
        signInProvider = try container.decode(String.self, forKey: .signInProvider)
        formalAccount = try container.decode(Bool.self, forKey: .formalAccount)
        email = try container.decodeIfPresent(String.self, forKey: .email)
        identityVerificationState = try container.decodeIfPresent(String.self, forKey: .identityVerificationState)
        createdAt = try container.decodeIfPresent(String.self, forKey: .createdAt)
            ?? container.decodeIfPresent(String.self, forKey: .createdAtSnake)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(accountId, forKey: .accountId)
        try container.encode(signInProvider, forKey: .signInProvider)
        try container.encode(formalAccount, forKey: .formalAccount)
        try container.encodeIfPresent(email, forKey: .email)
        try container.encodeIfPresent(identityVerificationState, forKey: .identityVerificationState)
        try container.encodeIfPresent(createdAt, forKey: .createdAt)
    }
}

struct RecentSession: Codable, Hashable {
    let sessionId: String
    let sessionType: String
    let sessionStatus: String
    let expiresAt: String
    let lastSeenAt: String?
}

struct StoredAuthSession: Codable, Hashable {
    let accessToken: String
    let tokenType: String
    let sessionId: String
    let sessionType: String
    let expiresAt: String
    let account: AuthAccount

    var authMode: AuthMode {
        account.formalAccount ? .formalAccount : .signedOut
    }

    func merging(accountEnvelope: AuthSessionEnvelope) -> StoredAuthSession {
        StoredAuthSession(
            accessToken: accessToken,
            tokenType: tokenType,
            sessionId: sessionId,
            sessionType: sessionType,
            expiresAt: accountEnvelope.session?.expiresAt ?? expiresAt,
            account: accountEnvelope.account
        )
    }
}

struct LogoutReceipt: Codable {
    let loggedOut: Bool
    let status: String
}

struct AppleExchangePreview: Codable {
    let sessionIssued: Bool
    let status: String
    let codeExchangeStatus: String
    let identityTokenStatus: String
    let stateAccepted: Bool
    let nonceAccepted: Bool
    let decodedSubject: String?
    let decodedNonce: String?
    let note: String
    let configuration: [String: Bool]
    let diagnostics: [String: String?]
    let session: AuthSessionEnvelope?
}
