import Foundation

struct EmailVerificationTicketReceipt: Codable {
    let maskedEmail: String
    let sceneCode: String
    let expiresAt: String
    let deliveryStatus: String
    let debugCode: String?
    let note: String
}
