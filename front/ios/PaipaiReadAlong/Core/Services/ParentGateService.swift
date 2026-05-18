import CommonCrypto
import Foundation
import LocalAuthentication
import os
import Security

enum ParentAccessMethod: Equatable {
    case offlinePassword
    case deviceOwnerAuthentication
    case createOfflinePassword
}

struct ParentRecoveryQuestion: Identifiable, Codable, Equatable {
    let id: String
    let zhHans: String
    let english: String
    let japanese: String
    let korean: String
    let spanish: String
}

struct ParentPasswordSetupPayload {
    let password: String
    let answersByQuestionId: [String: String]

    init(password: String, answersByQuestionId: [String: String] = [:]) {
        self.password = password
        self.answersByQuestionId = answersByQuestionId
    }
}

enum ParentGateServiceError: Error, Equatable {
    case deviceAuthenticationUnavailable
    case deviceAuthenticationCancelled
    case deviceAuthenticationFailed
    case passwordTooShort
    case passwordNeedsLettersAndNumbers
    case weakPassword
    case recoveryAnswersIncomplete
    case offlinePasswordMissing
    case invalidPassword(remainingAttempts: Int)
    case locked(until: Date)
    case recoveryLocked(until: Date)
    case recoveryAnswerMismatch
    case keychainFailure
}

final class ParentGateService {
    static let shared = ParentGateService()

    static let recoveryQuestions: [ParentRecoveryQuestion] = [
        ParentRecoveryQuestion(
            id: "first_school_or_teacher",
            zhHans: "你小时候印象最深的一位老师或学校叫什么？",
            english: "What was the name of a memorable teacher or school from your childhood?",
            japanese: "子どもの頃に印象に残っている先生または学校の名前は何ですか？",
            korean: "어릴 때 가장 기억에 남는 선생님이나 학교 이름은 무엇인가요?",
            spanish: "¿Cómo se llamaba un profesor o una escuela que recuerdas de tu infancia?"
        ),
        ParentRecoveryQuestion(
            id: "family_trip_place",
            zhHans: "你和孩子最难忘的一次出行地点是哪里？",
            english: "Where was a memorable trip you took with your child?",
            japanese: "お子さまと行った忘れられない旅行先はどこですか？",
            korean: "아이와 함께했던 가장 기억에 남는 여행지는 어디인가요?",
            spanish: "¿Cuál fue un viaje memorable que hiciste con tu hijo?"
        ),
        ParentRecoveryQuestion(
            id: "favorite_family_book",
            zhHans: "家里最常一起读的一本书叫什么？",
            english: "What is the title of a book your family often reads together?",
            japanese: "家族でよく一緒に読む本の題名は何ですか？",
            korean: "가족이 함께 자주 읽는 책 제목은 무엇인가요?",
            spanish: "¿Cuál es el título de un libro que tu familia lee junta con frecuencia?"
        )
    ]

    private static let defaultPasswordMaxAttempts = 5
    private static let defaultRecoveryMaxAttempts = 5
    private static let defaultLockDuration: TimeInterval = 5 * 60
    private static let defaultRecoveryLockDuration: TimeInterval = 10 * 60
    private static let defaultSaltByteCount = 16
    private static let hashByteCount = 32
    private static let defaultPBKDF2Iterations = 210_000

    private let service: String
    private let account: String
    private let passwordMaxAttempts: Int
    private let recoveryMaxAttempts: Int
    private let lockDuration: TimeInterval
    private let recoveryLockDuration: TimeInterval
    private let saltByteCount: Int
    private let pbkdf2Iterations: Int
    private let logger = Logger(subsystem: AppIdentity.bundleIdentifier, category: "ParentGate")

    init(
        appCode: String = AppIdentity.appCode,
        bundleIdentifier: String = AppIdentity.bundleIdentifier,
        pbkdf2Iterations: Int = ParentGateService.defaultPBKDF2Iterations,
        passwordMaxAttempts: Int = ParentGateService.defaultPasswordMaxAttempts,
        recoveryMaxAttempts: Int = ParentGateService.defaultRecoveryMaxAttempts,
        lockDuration: TimeInterval = ParentGateService.defaultLockDuration,
        recoveryLockDuration: TimeInterval = ParentGateService.defaultRecoveryLockDuration,
        saltByteCount: Int = ParentGateService.defaultSaltByteCount
    ) {
        self.service = "\(bundleIdentifier).\(appCode).parent-gate"
        self.account = "\(appCode).offline-parent-password.v1"
        self.pbkdf2Iterations = pbkdf2Iterations
        self.passwordMaxAttempts = passwordMaxAttempts
        self.recoveryMaxAttempts = recoveryMaxAttempts
        self.lockDuration = lockDuration
        self.recoveryLockDuration = recoveryLockDuration
        self.saltByteCount = saltByteCount
    }

    func preferredAccessMethod() -> ParentAccessMethod {
        if loadRecord() != nil {
            return .offlinePassword
        }

        let context = LAContext()
        var error: NSError?
        if context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) {
            return .deviceOwnerAuthentication
        }

        if let error, LAError(_nsError: error).code == .passcodeNotSet {
            logger.info("Device passcode is not set; offline password setup required.")
        } else {
            logger.warning("Device owner authentication unavailable; offline password setup required.")
        }
        return .createOfflinePassword
    }

    func authenticateDevice(localizedReason: String) async throws {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            if let error, LAError(_nsError: error).code == .passcodeNotSet {
                throw ParentGateServiceError.deviceAuthenticationUnavailable
            }
            throw ParentGateServiceError.deviceAuthenticationUnavailable
        }

        do {
            let passed = try await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: localizedReason)
            if passed {
                logger.info("Parent gate device authentication passed.")
                return
            }
            throw ParentGateServiceError.deviceAuthenticationFailed
        } catch {
            if let laError = error as? LAError {
                switch laError.code {
                case .userCancel, .systemCancel, .appCancel:
                    logger.info("Parent gate device authentication was cancelled.")
                    throw ParentGateServiceError.deviceAuthenticationCancelled
                case .passcodeNotSet, .biometryNotAvailable, .notInteractive:
                    logger.warning("Parent gate device authentication unavailable.")
                    throw ParentGateServiceError.deviceAuthenticationUnavailable
                default:
                    break
                }
            }
            logger.warning("Parent gate device authentication failed.")
            throw ParentGateServiceError.deviceAuthenticationFailed
        }
    }

    func createOfflinePassword(_ payload: ParentPasswordSetupPayload) throws {
        try validatePassword(payload.password)

        let passwordSalt = try randomData(count: saltByteCount)
        let passwordHash = try pbkdf2(payload.password, salt: passwordSalt)
        let answerRecords = try Self.recoveryQuestions.compactMap { question -> RecoveryAnswerRecord? in
            let answer = normalizedRecoveryAnswer(payload.answersByQuestionId[question.id] ?? "")
            guard !answer.isEmpty else { return nil }
            let salt = try randomData(count: saltByteCount)
            return RecoveryAnswerRecord(
                questionId: question.id,
                salt: salt,
                hash: try pbkdf2(answer, salt: salt)
            )
        }
        let record = OfflineParentPasswordRecord(
            passwordSalt: passwordSalt,
            passwordHash: passwordHash,
            iterations: pbkdf2Iterations,
            answers: answerRecords,
            failedAttempts: 0,
            lockedUntil: nil,
            recoveryFailedAttempts: 0,
            recoveryLockedUntil: nil,
            createdAt: Date(),
            updatedAt: Date()
        )
        try saveRecord(record)
        logger.info("Offline parent password created in local Keychain.")
    }

    func verifyOfflinePassword(_ password: String) throws {
        var record = try requireRecord()
        let now = Date()
        if let lockedUntil = record.lockedUntil, lockedUntil > now {
            throw ParentGateServiceError.locked(until: lockedUntil)
        }

        let candidate = try pbkdf2(password, salt: record.passwordSalt, iterations: record.iterations)
        if constantTimeEquals(candidate, record.passwordHash) {
            record.failedAttempts = 0
            record.lockedUntil = nil
            record.updatedAt = now
            try saveRecord(record)
            logger.info("Offline parent password verification passed.")
            return
        }

        record.failedAttempts += 1
        let remaining = max(0, passwordMaxAttempts - record.failedAttempts)
        if remaining == 0 {
            record.failedAttempts = 0
            record.lockedUntil = now.addingTimeInterval(lockDuration)
            try saveRecord(record)
            logger.warning("Offline parent password locked after repeated failures.")
            throw ParentGateServiceError.locked(until: record.lockedUntil ?? now)
        }
        try saveRecord(record)
        logger.warning("Offline parent password verification failed.")
        throw ParentGateServiceError.invalidPassword(remainingAttempts: remaining)
    }

    func verifyRecoveryAnswer(_ answersByQuestionId: [String: String]) throws {
        var record = try requireRecord()
        let now = Date()
        if let lockedUntil = record.recoveryLockedUntil, lockedUntil > now {
            throw ParentGateServiceError.recoveryLocked(until: lockedUntil)
        }

        let isMatched = record.answers.contains { saved in
            let answer = normalizedRecoveryAnswer(answersByQuestionId[saved.questionId] ?? "")
            guard !answer.isEmpty, let candidate = try? pbkdf2(answer, salt: saved.salt, iterations: record.iterations) else { return false }
            return constantTimeEquals(candidate, saved.hash)
        }

        if isMatched {
            record.recoveryFailedAttempts = 0
            record.recoveryLockedUntil = nil
            record.updatedAt = now
            try saveRecord(record)
            logger.info("Parent password recovery question verification passed.")
            return
        }

        record.recoveryFailedAttempts += 1
        if record.recoveryFailedAttempts >= recoveryMaxAttempts {
            record.recoveryFailedAttempts = 0
            record.recoveryLockedUntil = now.addingTimeInterval(recoveryLockDuration)
            try saveRecord(record)
            logger.warning("Parent password recovery locked after repeated failures.")
            throw ParentGateServiceError.recoveryLocked(until: record.recoveryLockedUntil ?? now)
        }
        try saveRecord(record)
        logger.warning("Parent password recovery answer mismatch.")
        throw ParentGateServiceError.recoveryAnswerMismatch
    }

    func resetOfflinePassword(_ payload: ParentPasswordSetupPayload) throws {
        try createOfflinePassword(payload)
        logger.info("Offline parent password reset in local Keychain.")
    }

    func validatePassword(_ password: String) throws {
        let trimmed = password.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 4 else { throw ParentGateServiceError.passwordTooShort }
        let lowercased = trimmed.lowercased()
        let weakPasswords: Set<String> = [
            "0000", "1111", "1234", "123456", "12345678", "111111", "000000", "654321",
            "abcdef", "abc123", "a123456", "qwerty", "password", "password1", "paipai123"
        ]
        guard !weakPasswords.contains(lowercased) else { throw ParentGateServiceError.weakPassword }
    }

    private func requireRecord() throws -> OfflineParentPasswordRecord {
        guard let record = loadRecord() else { throw ParentGateServiceError.offlinePasswordMissing }
        return record
    }

    func clearOfflinePassword() {
        SecItemDelete(baseQuery as CFDictionary)
    }

    private func loadRecord() -> OfflineParentPasswordRecord? {
        var query = baseQuery
        query[kSecReturnData as String] = kCFBooleanTrue
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess,
              let data = item as? Data else {
            return nil
        }
        return try? JSONDecoder().decode(OfflineParentPasswordRecord.self, from: data)
    }

    private func saveRecord(_ record: OfflineParentPasswordRecord) throws {
        guard let data = try? JSONEncoder().encode(record) else {
            throw ParentGateServiceError.keychainFailure
        }

        let existing = SecItemCopyMatching(baseQuery as CFDictionary, nil)
        if existing == errSecSuccess {
            let status = SecItemUpdate(baseQuery as CFDictionary, [kSecValueData as String: data] as CFDictionary)
            guard status == errSecSuccess else { throw ParentGateServiceError.keychainFailure }
        } else {
            var query = baseQuery
            query[kSecValueData as String] = data
            query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            let status = SecItemAdd(query as CFDictionary, nil)
            guard status == errSecSuccess else { throw ParentGateServiceError.keychainFailure }
        }
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }

    private func randomData(count: Int) throws -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        let status = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        guard status == errSecSuccess else { throw ParentGateServiceError.keychainFailure }
        return Data(bytes)
    }

    private func pbkdf2(_ value: String, salt: Data, iterations: Int? = nil) throws -> Data {
        guard let passwordData = value.data(using: .utf8) else {
            throw ParentGateServiceError.keychainFailure
        }
        var derived = [UInt8](repeating: 0, count: Self.hashByteCount)
        let status = passwordData.withUnsafeBytes { passwordBuffer in
            salt.withUnsafeBytes { saltBuffer in
                CCKeyDerivationPBKDF(
                    CCPBKDFAlgorithm(kCCPBKDF2),
                    passwordBuffer.bindMemory(to: Int8.self).baseAddress,
                    passwordData.count,
                    saltBuffer.bindMemory(to: UInt8.self).baseAddress,
                    salt.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                    UInt32(iterations ?? pbkdf2Iterations),
                    &derived,
                    derived.count
                )
            }
        }
        guard status == kCCSuccess else { throw ParentGateServiceError.keychainFailure }
        return Data(derived)
    }

    private func normalizedRecoveryAnswer(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: .current)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .lowercased()
    }

    private func constantTimeEquals(_ lhs: Data, _ rhs: Data) -> Bool {
        guard lhs.count == rhs.count else { return false }
        return zip(lhs, rhs).reduce(UInt8(0)) { $0 | ($1.0 ^ $1.1) } == 0
    }
}

private struct OfflineParentPasswordRecord: Codable {
    var passwordSalt: Data
    var passwordHash: Data
    var iterations: Int
    var answers: [RecoveryAnswerRecord]
    var failedAttempts: Int
    var lockedUntil: Date?
    var recoveryFailedAttempts: Int
    var recoveryLockedUntil: Date?
    var createdAt: Date
    var updatedAt: Date
}

private struct RecoveryAnswerRecord: Codable {
    var questionId: String
    var salt: Data
    var hash: Data
}
