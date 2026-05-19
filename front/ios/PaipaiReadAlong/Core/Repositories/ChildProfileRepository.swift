import Foundation

final class ChildProfileRepository {
    private let database: LocalDatabase

    init(database: LocalDatabase) {
        self.database = database
    }

    func loadAll() async -> [ChildProfile] {
        (try? await database.getAll(
            sql: "SELECT id, nickname, age_band, learning_track_code, avatar_emoji, profile_status, deleted_at, record_version, updated_at FROM \(ReadingLocalTableName.childProfile) ORDER BY COALESCE(updated_at, created_at, '') DESC",
            parameters: []
        ) { cursor in
            ChildProfile(
                id: try cursor.getString(name: "id"),
                nickname: try cursor.getString(name: "nickname"),
                ageBand: try cursor.getString(name: "age_band"),
                learningTrackCode: try cursor.getString(name: "learning_track_code"),
                avatarEmoji: try cursor.getStringOptional(name: "avatar_emoji") ?? "🧸",
                profileStatus: try cursor.getStringOptional(name: "profile_status") ?? "active",
                deletedAt: try cursor.getStringOptional(name: "deleted_at"),
                recordVersion: try cursor.getIntOptional(name: "record_version") ?? 1,
                updatedAt: try cursor.getStringOptional(name: "updated_at")
            )
        }) ?? []
    }

    func loadActive() async -> [ChildProfile] {
        let all = await loadAll()
        return all.filter { !$0.isDeleted }
    }

    @discardableResult
    func upsertLocal(id: String? = nil, nickname: String, ageBand: String, learningTrackCode: String, avatarEmoji: String = "🧸") async -> ChildProfile {
        let all = await loadAll()
        let existing = all.first { $0.id == id }
        let now = AppClock.nowString()
        let child = ChildProfile(
            id: id ?? UUID().uuidString.lowercased(),
            nickname: nickname,
            ageBand: ageBand,
            learningTrackCode: learningTrackCode,
            avatarEmoji: avatarEmoji,
            profileStatus: "active",
            deletedAt: nil,
            recordVersion: (existing?.recordVersion ?? 0) + 1,
            updatedAt: now
        )
        _ = try? await database.execute(
            sql: """
                INSERT OR REPLACE INTO \(ReadingLocalTableName.childProfile)
                (id, app_code, nickname, age_band, learning_track_code, avatar_emoji, profile_status, deleted_at, record_version, created_at, updated_at)
                VALUES (?, '\(AppIdentity.appCode)', ?, ?, ?, ?, ?, ?, ?, COALESCE((SELECT created_at FROM \(ReadingLocalTableName.childProfile) WHERE id = ?), ?), ?)
                """,
            parameters: [
                child.id,
                child.nickname,
                child.ageBand,
                child.learningTrackCode,
                child.avatarEmoji,
                child.profileStatus,
                child.deletedAt,
                child.recordVersion,
                child.id,
                now,
                child.updatedAt
            ]
        )
        return child
    }

    @discardableResult
    func softDeleteLocal(id: String) async -> ChildProfile? {
        let all = await loadAll()
        guard let existing = all.first(where: { $0.id == id }) else { return nil }
        let now = AppClock.nowString()
        let child = ChildProfile(
            id: existing.id,
            nickname: existing.nickname,
            ageBand: existing.ageBand,
            learningTrackCode: existing.learningTrackCode,
            avatarEmoji: existing.avatarEmoji,
            profileStatus: "deleted",
            deletedAt: now,
            recordVersion: existing.recordVersion + 1,
            updatedAt: now
        )
        _ = try? await database.execute(
            sql: """
                INSERT OR REPLACE INTO \(ReadingLocalTableName.childProfile)
                (id, app_code, nickname, age_band, learning_track_code, avatar_emoji, profile_status, deleted_at, record_version, created_at, updated_at)
                VALUES (?, '\(AppIdentity.appCode)', ?, ?, ?, ?, ?, ?, ?, COALESCE((SELECT created_at FROM \(ReadingLocalTableName.childProfile) WHERE id = ?), ?), ?)
                """,
            parameters: [
                child.id,
                child.nickname,
                child.ageBand,
                child.learningTrackCode,
                child.avatarEmoji,
                child.profileStatus,
                child.deletedAt,
                child.recordVersion,
                child.id,
                now,
                child.updatedAt
            ]
        )
        return child
    }

    func clear() async {
        _ = try? await database.execute(sql: "DELETE FROM \(ReadingLocalTableName.childProfile)", parameters: [])
    }
}
