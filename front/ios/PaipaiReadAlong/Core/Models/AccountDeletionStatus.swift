import Foundation

struct AccountDeletionReceipt: Codable, Hashable {
    let requestId: String
    let status: String
    let executionStatus: String
    let requestedAt: String?
    let startedAt: String?
    let completedAt: String?
    let failedAt: String?
    let provider: String
    let executeSynchronously: Bool
    let idempotentReplay: Bool
    let appleRevokeStatus: String
    let appleRevokeNote: String?
    let sessionsRevoked: Int
    let identityLinksRevoked: Int
    let childrenScrubbed: Int
    let reviewCardsDeleted: Int
    let reviewEventsDeleted: Int
    let lastErrorCode: String?
    let lastErrorMessage: String?
    let note: String
}
