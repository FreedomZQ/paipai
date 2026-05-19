import Foundation

final class AnnouncementStore {
    private let defaults = AppScopedDefaults()
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    private struct PresentationState: Codable {
        var shownCount: Int
        var lastShownAt: Date?
    }

    private struct DailyPresentationState: Codable {
        var userId: String
        var date: String
        var announcementIds: [String]
        var shownAt: Date
    }

    func loadRecent() -> [AppAnnouncement] {
        guard let data = defaults.data(forKey: AppDefaultKey.announcementsCache),
              let items = try? decoder.decode([AppAnnouncement].self, from: data) else {
            return []
        }
        return prune(items)
    }

    func save(_ items: [AppAnnouncement]) {
        let pruned = prune(items)
        if let data = try? encoder.encode(pruned) {
            defaults.set(data, forKey: AppDefaultKey.announcementsCache)
        }
    }

    func mergeAndSave(_ newItems: [AppAnnouncement]) -> [AppAnnouncement] {
        var merged: [String: AppAnnouncement] = Dictionary(uniqueKeysWithValues: loadRecent().map { ($0.announcementUuid, $0) })
        for item in newItems {
            merged[item.announcementUuid] = item
        }
        let result = sortedAnnouncements(from: prune(Array(merged.values)))
        save(result)
        return result
    }

    func dismiss(_ uuid: String) {
        var set = dismissedIds()
        set.insert(uuid)
        defaults.set(Array(set), forKey: AppDefaultKey.announcementsDismissed)
    }

    func isDismissed(_ uuid: String) -> Bool {
        dismissedIds().contains(uuid)
    }

    func canPresent(_ announcement: AppAnnouncement, at now: Date = Date()) -> Bool {
        guard !isDismissed(announcement.announcementUuid) else { return false }
        guard isWithinVisibleWindow(announcement, at: now) else { return false }
        let state = presentationStates()[announcement.announcementUuid] ?? PresentationState(shownCount: 0, lastShownAt: nil)
        let maxDisplayCount = max(announcement.maxDisplayCount ?? 1, 1)
        if state.shownCount >= maxDisplayCount {
            return false
        }
        let minInterval = TimeInterval(max(announcement.minIntervalSeconds ?? 0, 0))
        if let lastShownAt = state.lastShownAt, now.timeIntervalSince(lastShownAt) < minInterval {
            return false
        }
        return true
    }

    func activeAnnouncements(from items: [AppAnnouncement], at now: Date = Date()) -> [AppAnnouncement] {
        sortedAnnouncements(from: items).filter { $0.active && isWithinVisibleWindow($0, at: now) }
    }

    func canPresentDaily(for userId: String?, at now: Date = Date()) -> Bool {
        guard let userId = normalizedUserId(userId) else { return false }
        let key = dailyKey(userId: userId, date: dateKey(from: now))
        return dailyPresentationStates()[key] == nil
    }

    func recordDailyPresentation(for userId: String?, announcements: [AppAnnouncement], at now: Date = Date()) {
        guard let userId = normalizedUserId(userId), !announcements.isEmpty else { return }
        var states = dailyPresentationStates()
        let date = dateKey(from: now)
        states[dailyKey(userId: userId, date: date)] = DailyPresentationState(
            userId: userId,
            date: date,
            announcementIds: announcements.map(\.announcementUuid),
            shownAt: now
        )
        saveDailyPresentationStates(pruneDailyPresentationStates(states, keepingFrom: now))
    }

    func recordPresentation(for announcement: AppAnnouncement, at now: Date = Date()) {
        var states = presentationStates()
        var state = states[announcement.announcementUuid] ?? PresentationState(shownCount: 0, lastShownAt: nil)
        state.shownCount += 1
        state.lastShownAt = now
        states[announcement.announcementUuid] = state
        savePresentationStates(states)
    }

    func nextPresentableActiveAnnouncement(from items: [AppAnnouncement], at now: Date = Date()) -> AppAnnouncement? {
        sortedAnnouncements(from: items).first(where: { $0.active && canPresent($0, at: now) })
    }

    func presentableActiveAnnouncements(from items: [AppAnnouncement], at now: Date = Date()) -> [AppAnnouncement] {
        sortedAnnouncements(from: items).filter { $0.active && canPresent($0, at: now) }
    }

    private func dismissedIds() -> Set<String> {
        Set(defaults.stringArray(forKey: AppDefaultKey.announcementsDismissed) ?? [])
    }

    private func presentationStates() -> [String: PresentationState] {
        guard let data = defaults.data(forKey: AppDefaultKey.announcementsPresentationState),
              let states = try? decoder.decode([String: PresentationState].self, from: data) else {
            return [:]
        }
        return states
    }

    private func savePresentationStates(_ states: [String: PresentationState]) {
        if let data = try? encoder.encode(states) {
            defaults.set(data, forKey: AppDefaultKey.announcementsPresentationState)
        }
    }

    private func dailyPresentationStates() -> [String: DailyPresentationState] {
        guard let data = defaults.data(forKey: AppDefaultKey.announcementsDailyPresentationState),
              let states = try? decoder.decode([String: DailyPresentationState].self, from: data) else {
            return [:]
        }
        return pruneDailyPresentationStates(states, keepingFrom: Date())
    }

    private func saveDailyPresentationStates(_ states: [String: DailyPresentationState]) {
        if let data = try? encoder.encode(states) {
            defaults.set(data, forKey: AppDefaultKey.announcementsDailyPresentationState)
        }
    }

    private func pruneDailyPresentationStates(_ states: [String: DailyPresentationState], keepingFrom now: Date) -> [String: DailyPresentationState] {
        guard let cutoff = Calendar(identifier: .gregorian).date(byAdding: .day, value: -31, to: now) else { return states }
        let cutoffKey = dateKey(from: cutoff)
        return states.filter { $0.value.date >= cutoffKey }
    }

    func sortedAnnouncements(from items: [AppAnnouncement]) -> [AppAnnouncement] {
        items.sorted { lhs, rhs in
            let leftPriority = lhs.priority ?? 0
            let rightPriority = rhs.priority ?? 0
            if leftPriority != rightPriority {
                return leftPriority > rightPriority
            }
            return (lhs.createdAt ?? lhs.visibleStartAt ?? lhs.updatedAt ?? "") > (rhs.createdAt ?? rhs.visibleStartAt ?? rhs.updatedAt ?? "")
        }
    }

    private func prune(_ items: [AppAnnouncement]) -> [AppAnnouncement] {
        let cutoff = Date().addingTimeInterval(-30 * 24 * 3600)
        return items.filter { announcement in
            let reference = parseDate(announcement.visibleEndAt)
                ?? parseDate(announcement.visibleStartAt)
                ?? parseDate(announcement.updatedAt)
            guard let reference else { return true }
            return reference >= cutoff
        }
    }

    private func parseDate(_ raw: String?) -> Date? {
        guard let raw else { return nil }
        return ISO8601DateFormatter().date(from: raw)
    }

    private func isWithinVisibleWindow(_ announcement: AppAnnouncement, at now: Date) -> Bool {
        if let startAt = parseDate(announcement.visibleStartAt), startAt > now {
            return false
        }
        if let endAt = parseDate(announcement.visibleEndAt), endAt < now {
            return false
        }
        return true
    }

    private func normalizedUserId(_ raw: String?) -> String? {
        guard let raw = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else { return nil }
        return raw
    }

    private func dateKey(from date: Date) -> String {
        AppClock.dateOnly(from: date)
    }

    private func dailyKey(userId: String, date: String) -> String {
        "\(userId)#\(date)"
    }
}
