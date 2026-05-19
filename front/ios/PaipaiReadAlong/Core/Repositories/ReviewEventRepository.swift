import Foundation

struct LocalReviewEvent: Identifiable, Codable, Hashable {
    let id: String
    let childId: String
    let cardId: String
    let eventType: String
    let resultLevel: String
    let eventAt: String
}

final class ReviewEventRepository {
    private let database: LocalDatabase

    init(database: LocalDatabase) {
        self.database = database
    }

    func append(childId: String, cardId: String, eventType: String, resultLevel: String) async {
        let event = LocalReviewEvent(
            id: UUID().uuidString.lowercased(),
            childId: childId,
            cardId: cardId,
            eventType: eventType,
            resultLevel: resultLevel,
            eventAt: AppClock.nowString()
        )
        _ = try? await database.execute(
            sql: """
                INSERT OR REPLACE INTO \(ReadingLocalTableName.reviewEvent)
                (id, app_code, child_id, card_id, event_type, result_level, event_at, created_at, updated_at)
                VALUES (?, '\(AppIdentity.appCode)', ?, ?, ?, ?, ?, ?, ?)
                """,
            parameters: [
                event.id,
                event.childId,
                event.cardId,
                event.eventType,
                event.resultLevel,
                event.eventAt,
                event.eventAt,
                event.eventAt
            ]
        )
    }

    func clear() async {
        _ = try? await database.execute(sql: "DELETE FROM \(ReadingLocalTableName.reviewEvent)", parameters: [])
    }
}
