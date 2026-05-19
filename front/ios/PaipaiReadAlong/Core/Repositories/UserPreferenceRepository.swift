import Foundation

final class UserPreferenceRepository {
    private let database: LocalDatabase

    init(database: LocalDatabase) {
        self.database = database
    }

    func current() async -> UserPreference {
        (try? await database.getOptional(
            sql: "SELECT user_id, ui_locale, source_language_code, target_language_code, reading_track_code, tts_voice_code, translation_mode, updated_at FROM \(ReadingLocalTableName.userPreference) LIMIT 1",
            parameters: []
        ) { cursor in
            UserPreference(
                userId: try cursor.getInt64Optional(name: "user_id").map(Int.init),
                uiLocale: try cursor.getStringOptional(name: "ui_locale") ?? "zh-Hans",
                sourceLanguageCode: try cursor.getStringOptional(name: "source_language_code") ?? "en",
                targetLanguageCode: try cursor.getStringOptional(name: "target_language_code") ?? "zh-Hans",
                readingTrackCode: try cursor.getStringOptional(name: "reading_track_code") ?? "zh_to_en",
                ttsVoiceCode: try cursor.getStringOptional(name: "tts_voice_code"),
                translationMode: try cursor.getStringOptional(name: "translation_mode") ?? "device_only",
                updatedAt: try cursor.getStringOptional(name: "updated_at"),
                persisted: true
            )
        }) ?? .default
    }

    @discardableResult
    func updateLocal(
        uiLocale: String? = nil,
        sourceLanguageCode: String? = nil,
        targetLanguageCode: String? = nil,
        readingTrackCode: String? = nil,
        ttsVoiceCode: String? = nil,
        translationMode: String? = nil,
        persisted: Bool = true
    ) async -> UserPreference {
        let current = await self.current()
        let now = AppClock.nowString()
        let next = UserPreference(
            userId: current.userId,
            uiLocale: uiLocale ?? current.uiLocale,
            sourceLanguageCode: sourceLanguageCode ?? current.sourceLanguageCode,
            targetLanguageCode: targetLanguageCode ?? current.targetLanguageCode,
            readingTrackCode: readingTrackCode ?? current.readingTrackCode,
            ttsVoiceCode: ttsVoiceCode ?? current.ttsVoiceCode,
            translationMode: translationMode ?? current.translationMode,
            updatedAt: now,
            persisted: persisted
        )
        _ = try? await database.execute(
            sql: """
                INSERT OR REPLACE INTO \(ReadingLocalTableName.userPreference)
                (id, app_code, ui_locale, source_language_code, target_language_code, reading_track_code, tts_voice_code,
                 translation_mode, record_version, created_at, updated_at)
                VALUES ('me', '\(AppIdentity.appCode)', ?, ?, ?, ?, ?, ?, COALESCE((SELECT record_version + 1 FROM \(ReadingLocalTableName.userPreference) WHERE id = 'me'), 1), COALESCE((SELECT created_at FROM \(ReadingLocalTableName.userPreference) WHERE id = 'me'), ?), ?)
                """,
            parameters: [
                next.uiLocale,
                next.sourceLanguageCode,
                next.targetLanguageCode,
                next.readingTrackCode,
                next.ttsVoiceCode,
                next.translationMode,
                now,
                now
            ]
        )
        return next
    }

    func clear() async {
        _ = try? await database.execute(sql: "DELETE FROM \(ReadingLocalTableName.userPreference)", parameters: [])
    }
}
