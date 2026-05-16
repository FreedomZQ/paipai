import Foundation

struct AppAnnouncement: Codable, Identifiable, Hashable {
    let announcementUuid: String
    let title: String
    let content: String
    let visibleStartAt: String?
    let visibleEndAt: String?
    let active: Bool
    let createdAt: String?
    let updatedAt: String?
    let type: String?
    let priority: Int?
    let actionUrl: String?
    let actionText: String?
    let localizedTitle: String?
    let localizedContent: String?
    let localizedActionText: String?
    let dismissible: Bool?
    let maxDisplayCount: Int?
    let minIntervalSeconds: Int?
    let triggerScene: String?

    var id: String { announcementUuid }
}
