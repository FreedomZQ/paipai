import Foundation

struct FeedbackSubmissionReceipt: Codable {
    let ticketNo: String
    let category: String
    let submittedAt: String
    let supportEmail: String
}
