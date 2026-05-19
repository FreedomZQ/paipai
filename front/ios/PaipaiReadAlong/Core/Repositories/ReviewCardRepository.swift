import Foundation

final class ReviewCardRepository {
    private let database: LocalDatabase
    private let textCrypto: LocalTextCryptoService

    init(database: LocalDatabase, textCrypto: LocalTextCryptoService = .shared) {
        self.database = database
        self.textCrypto = textCrypto
    }

    func loadAll() async -> [ReviewCard] {
        (try? await database.getAll(
            sql: """
                SELECT id, device_id, child_id, learning_track_code, learning_language_code, encrypted_text, source_text, text_preview, support_hint, translated_text,
                       proficiency, next_review_at, source_language_code,
                       target_language_code, source_type, last_reviewed_at, is_review_completed, review_count,
                       deleted_at, record_version, created_at, updated_at
                FROM \(ReadingLocalTableName.reviewCard)
                ORDER BY COALESCE(updated_at, created_at, '') DESC
                """,
            parameters: []
        ) { cursor in
            let encryptedText = try cursor.getStringOptional(name: "encrypted_text")
            let sourceText = try cursor.getStringOptional(name: "source_text")
            let textPreview = try cursor.getStringOptional(name: "text_preview")
            let supportHintValue = try cursor.getStringOptional(name: "support_hint")
            let translatedText = try cursor.getStringOptional(name: "translated_text")
            return ReviewCard(
                id: try cursor.getString(name: "id"),
                deviceId: try cursor.getStringOptional(name: "device_id"),
                childId: try cursor.getStringOptional(name: "child_id"),
                learningTrackCode: try cursor.getStringOptional(name: "learning_track_code"),
                learningLanguageCode: try cursor.getStringOptional(name: "learning_language_code"),
                text: self.textCrypto.decrypt(encryptedText)
                    ?? sourceText
                    ?? textPreview
                    ?? LocalTextCryptoService.redactedPreview,
                supportHint: supportHintValue ?? translatedText ?? "",
                proficiency: try cursor.getIntOptional(name: "proficiency") ?? 0,
                nextReviewAt: try cursor.getStringOptional(name: "next_review_at"),
                sourceLanguageCode: try cursor.getStringOptional(name: "source_language_code"),
                targetLanguageCode: try cursor.getStringOptional(name: "target_language_code"),
                sourceType: try cursor.getStringOptional(name: "source_type"),
                lastReviewedAt: try cursor.getStringOptional(name: "last_reviewed_at"),
                isReviewCompleted: (try cursor.getIntOptional(name: "is_review_completed") ?? 0) == 1,
                reviewCount: try cursor.getIntOptional(name: "review_count") ?? 0,
                deletedAt: try cursor.getStringOptional(name: "deleted_at"),
                recordVersion: try cursor.getIntOptional(name: "record_version") ?? 1,
                createdAt: try cursor.getStringOptional(name: "created_at"),
                updatedAt: try cursor.getStringOptional(name: "updated_at")
            )
        }) ?? []
    }

    func loadRecentCards(childId: String, limit: Int = 3) async -> [ReviewCard] {
        await loadAll()
            .filter { !$0.isDeleted && $0.childId == childId }
            .prefix(max(limit, 0))
            .map { $0 }
    }

    func loadReviewCards(childId: String? = nil) async -> [ReviewCard] {
        await loadAll()
            .filter { !$0.isDeleted }
            .filter { childId == nil || $0.childId == childId }
    }

    func loadDueCards(childId: String? = nil) async -> [ReviewCard] {
        let now = Date()
        return await loadAll()
            .filter { !$0.isDeleted }
            .filter { childId == nil || $0.childId == childId }
            .filter { $0.proficiency < 3 }
            .filter { card in
                guard let nextReviewAt = card.nextReviewAt else { return true }
                return (AppClock.date(from: nextReviewAt) ?? now) <= now
            }
            .sorted { lhs, rhs in
                (AppClock.date(from: lhs.nextReviewAt) ?? .distantPast) < (AppClock.date(from: rhs.nextReviewAt) ?? .distantPast)
            }
    }

    @discardableResult
    func createLocalCard(
        deviceId: String,
        childId: String,
        learningTrackCode: String,
        learningLanguageCode: String,
        text: String,
        supportHint: String?,
        sourceLanguageCode: String,
        targetLanguageCode: String
    ) async -> ReviewCard? {
        let normalizedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedHint = supportHint?.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedText.isEmpty else { return nil }
        let now = Date()
        let nowString = AppClock.string(from: now)
        let nextReviewAt = AppClock.string(from: Calendar.current.date(byAdding: .day, value: 1, to: now) ?? now)
        let card = ReviewCard(
            id: UUID().uuidString.lowercased(),
            deviceId: deviceId,
            childId: childId,
            learningTrackCode: learningTrackCode,
            learningLanguageCode: learningLanguageCode,
            text: normalizedText,
            supportHint: normalizedHint ?? "",
            proficiency: 0,
            nextReviewAt: nextReviewAt,
            sourceLanguageCode: sourceLanguageCode,
            targetLanguageCode: targetLanguageCode,
            sourceType: "learning_page",
            lastReviewedAt: nil,
            isReviewCompleted: false,
            reviewCount: 0,
            deletedAt: nil,
            recordVersion: 1,
            createdAt: nowString,
            updatedAt: nowString
        )
        let encryptedText = textCrypto.encrypt(card.text)
        let safePreview = textCrypto.safePreview(for: card.text)
        do {
            try await database.execute(
                sql: """
                    INSERT OR REPLACE INTO \(ReadingLocalTableName.reviewCard)
                    (id, app_code, device_id, child_id, learning_track_code, learning_language_code, encrypted_text, text_preview, support_hint, proficiency, next_review_at,
                     card_status, source_text, translated_text, source_language_code, target_language_code,
                     source_type, content_encryption_version, content_key_id, last_reviewed_at, is_review_completed, review_count,
                     deleted_at, record_version, created_at, updated_at)
                    VALUES (?, '\(AppIdentity.appCode)', ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                parameters: [
                    card.id,
                    card.deviceId,
                    card.childId,
                    card.learningTrackCode,
                    card.learningLanguageCode,
                    encryptedText,
                    safePreview,
                    card.supportHint,
                    card.proficiency,
                    card.nextReviewAt,
                    nil,
                    card.supportHint,
                    card.sourceLanguageCode,
                    card.targetLanguageCode,
                    card.sourceType,
                    LocalTextCryptoService.encryptionVersion,
                    LocalTextCryptoService.keyId,
                    card.lastReviewedAt,
                    card.isReviewCompleted ? 1 : 0,
                    card.reviewCount,
                    card.deletedAt,
                    card.recordVersion,
                    nowString,
                    card.updatedAt
                ]
            )
            return card
        } catch {
            return nil
        }
    }

    @discardableResult
    func applyReviewResult(cardId: String, resultLevel: String) async -> ReviewCard? {
        let cards = await loadAll()
        guard let current = cards.first(where: { $0.id == cardId }) else { return nil }
        let nextProficiency: Int
        switch resultLevel {
        case "remembered":
            nextProficiency = 3
        default:
            nextProficiency = min(current.proficiency, 2)
        }
        let updatedAt = AppClock.nowString()
        let nextReviewAt = AppClock.string(from: Calendar.current.date(byAdding: .day, value: nextProficiency >= 3 ? 7 : 1, to: Date()) ?? Date())
        do {
            try await database.execute(
                sql: """
                    UPDATE \(ReadingLocalTableName.reviewCard)
                    SET proficiency = ?,
                        last_reviewed_at = ?,
                        next_review_at = ?,
                        is_review_completed = ?,
                        review_count = ?,
                        record_version = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                parameters: [
                    nextProficiency,
                    updatedAt,
                    nextReviewAt,
                    1,
                    current.reviewCount + 1,
                    current.recordVersion + 1,
                    updatedAt,
                    cardId
                ]
            )
        } catch {
            return nil
        }
        let refreshed = await loadAll()
        return refreshed.first(where: { $0.id == cardId })
    }

    /// 将单张句卡标记为已删除，保留本地记录用于本机历史统计和撤销判断。
    @discardableResult
    func softDeleteLocal(cardId: String) async -> Bool {
        let cards = await loadAll()
        guard let current = cards.first(where: { $0.id == cardId && !$0.isDeleted }) else { return false }
        let now = AppClock.nowString()
        do {
            try await database.execute(
                sql: """
                    UPDATE \(ReadingLocalTableName.reviewCard)
                    SET card_status = ?,
                        deleted_at = ?,
                        record_version = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                parameters: [
                    "deleted",
                    now,
                    current.recordVersion + 1,
                    now,
                    cardId
                ]
            )
            return true
        } catch {
            return false
        }
    }

    func clear() async {
        _ = try? await database.execute(sql: "DELETE FROM \(ReadingLocalTableName.reviewCard)", parameters: [])
    }
}
