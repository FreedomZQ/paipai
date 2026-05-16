import SwiftUI

struct AnnouncementView: View {
    @EnvironmentObject var appState: AppState
    let announcements: [Announcement]
    @Binding var selectedAnnouncementID: String?
    let onDismiss: () -> Void
    let onAction: (Announcement) -> Void
    @State private var localizedAnnouncements: [Announcement] = []
    @State private var isLocalizationReady = false

    private var selectedAnnouncement: Announcement? {
        displayedAnnouncements.first { $0.id == selectedAnnouncementID } ?? displayedAnnouncements.first
    }

    private var displayedAnnouncements: [Announcement] {
        if isLocalizationReady { return localizedAnnouncements }
        if !shouldTranslate { return announcements }
        return []
    }

    private var shouldTranslate: Bool {
        !AppLocaleCatalog.normalize(appState.interfaceLocaleCode).hasPrefix("zh")
            && announcements.contains(where: {
                $0.localizedTitle?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty != false
                && $0.localizedContent?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty != false
                && $0.localizedActionText?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty != false
            })
    }

    private var displayCount: Int {
        isLocalizationReady || !shouldTranslate ? displayedAnnouncements.count : announcements.count
    }

    private var localizationInputKey: String {
        "\(appState.interfaceLocaleCode)|" + announcements.map { "\($0.id):\($0.title):\($0.content)" }.joined(separator: "|")
    }

    var body: some View {
        ZStack {
            AnnouncementLocalizationHost(
                announcements: announcements,
                targetLanguageCode: appState.interfaceLocaleCode,
                onLocalized: { localized in
                    localizedAnnouncements = localized
                    isLocalizationReady = true
                }
            )
            .frame(width: 0, height: 0)
            .allowsHitTesting(false)

            Color.black.opacity(0.5)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(appState.uiText("通知", "Notices"))
                            .font(AppTypography.title3)
                            .foregroundColor(AppColors.textPrimary)
                        Text(appState.uiText("本次共有 \(displayCount) 条新通知", "\(displayCount) new notices"))
                            .font(AppTypography.caption)
                            .foregroundColor(AppColors.textSecondary)
                    }
                    Spacer()
                }
                .padding()
                .background(AppColors.cardBackground)

                GeometryReader { proxy in
                    let isCompact = proxy.size.width < 520
                    Group {
                        if shouldTranslate && !isLocalizationReady {
                            loadingPane
                        } else if isCompact {
                            VStack(spacing: 0) {
                                titleStrip
                                Divider()
                                contentPane
                            }
                        } else {
                            HStack(spacing: 0) {
                                titleSidebar
                                    .frame(width: min(190, max(150, proxy.size.width * 0.34)))
                                Divider()
                                contentPane
                            }
                        }
                    }
                }
                .frame(height: displayedAnnouncements.count > 1 ? 390 : 340)

                VStack(spacing: AppLayout.spacingM) {
                    if let selectedAnnouncement,
                       let actionText = selectedAnnouncement.displayActionText {
                        PrimaryButton(title: actionText) {
                            onAction(selectedAnnouncement)
                            onDismiss()
                        }
                    }
                    SecondaryButton(title: appState.uiText("确定", "OK")) { onDismiss() }
                }
                .padding()
                .background(Color.white)
            }
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous))
            .shadow(color: Color.black.opacity(0.14), radius: 24, x: 0, y: 10)
            .frame(maxWidth: 680)
            .padding(.horizontal, AppLayout.paddingScreen)
            .onAppear {
                resetLocalizationState()
            }
            .onChange(of: localizationInputKey) { _, _ in
                resetLocalizationState()
            }
        }
    }

    private func resetLocalizationState() {
        if shouldTranslate {
            isLocalizationReady = false
            localizedAnnouncements = []
        } else {
            isLocalizationReady = true
            localizedAnnouncements = announcements
        }
    }

    private var loadingPane: some View {
        VStack(spacing: AppLayout.spacingS) {
            ProgressView()
            Text(appState.uiText("正在准备公告内容...", "Preparing notices..."))
                .font(AppTypography.footnote)
                .foregroundColor(AppColors.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.white)
    }

    private var titleSidebar: some View {
        ScrollView {
            VStack(spacing: 8) {
                ForEach(displayedAnnouncements) { announcement in
                    titleButton(for: announcement, compact: false)
                }
            }
            .padding(12)
        }
        .background(Color(hex: "#FFFDF5"))
    }

    private var titleStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(displayedAnnouncements) { announcement in
                    titleButton(for: announcement, compact: true)
                }
            }
            .padding(12)
        }
        .background(Color(hex: "#FFFDF5"))
    }

    private func titleButton(for announcement: Announcement, compact: Bool) -> some View {
        let isSelected = announcement.id == selectedAnnouncement?.id
        return Button {
            selectedAnnouncementID = announcement.id
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                Text(appState.localizedAnnouncementTypeName(announcement.type))
                    .font(AppTypography.scaledFont(size: 11, weight: .medium))
                    .foregroundColor(Color(hex: announcement.type.color))
                Text(announcement.displayTitle)
                    .font(AppTypography.scaledFont(size: 14, weight: .semibold))
                    .foregroundColor(AppColors.textPrimary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
            .frame(minWidth: compact ? 148 : 0, maxWidth: compact ? 148 : .infinity, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(isSelected ? Color(hex: announcement.type.backgroundColor) : Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .stroke(isSelected ? Color(hex: announcement.type.color).opacity(0.55) : AppColors.border.opacity(0.6), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var contentPane: some View {
        ScrollView {
            if let announcement = selectedAnnouncement {
                VStack(spacing: AppLayout.spacingL) {
                    HStack {
                        Text(appState.localizedAnnouncementTypeName(announcement.type))
                            .font(AppTypography.scaledFont(size: 12, weight: .medium))
                            .foregroundColor(Color(hex: announcement.type.color))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Color(hex: announcement.type.backgroundColor))
                            .clipShape(Capsule())
                        Spacer()
                    }

                    Text(announcement.displayTitle)
                        .font(AppTypography.title2)
                        .foregroundColor(AppColors.textPrimary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    Text(announcement.displayContent)
                        .font(AppTypography.body)
                        .foregroundColor(AppColors.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    Text(formatDate(announcement.startDate))
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textTertiary)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
                .padding()
            }
        }
        .background(Color.white)
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.string(from: date)
    }

}

@MainActor
final class AnnouncementManager: ObservableObject {
    @Published var currentAnnouncements: [Announcement] = []
    @Published var selectedAnnouncementID: String?

    private let store = AnnouncementStore()
    private let translationService = TranslationService()
    private var pendingDailyUserId: String?
    private var pendingDailyAnnouncements: [AppAnnouncement] = []

    func dismissCurrentAnnouncement() {
        if !pendingDailyAnnouncements.isEmpty {
            store.recordDailyPresentation(for: pendingDailyUserId, announcements: pendingDailyAnnouncements)
            pendingDailyUserId = nil
            pendingDailyAnnouncements = []
        }
        publish([])
    }

    func presentRemoteAnnouncements(_ remote: [AppAnnouncement], targetLocale: String) async {
        let merged = store.mergeAndSave(remote)
        await presentLaunchAnnouncements(from: merged, targetLocale: targetLocale)
    }

    func presentCachedAnnouncements(targetLocale: String) async {
        let cached = store.loadRecent()
        await presentLaunchAnnouncements(from: cached, targetLocale: targetLocale)
    }

    func presentCachedDailyLaunchAnnouncements(userId: String?, targetLocale: String, translationService: TranslationService) async {
        await presentDailyLaunchAnnouncements(store.loadRecent(), userId: userId, targetLocale: targetLocale, translationService: translationService)
    }

    private func presentLaunchAnnouncements(from items: [AppAnnouncement], targetLocale: String) async {
        let presentable = store.sortedAnnouncements(from: items).filter { $0.active }
        guard !presentable.isEmpty else {
            publish([])
            return
        }
        let mapped = presentable.map(map(remote:))
        publish(await localizedAnnouncements(mapped, targetLocale: targetLocale))
    }

    func presentDailyLaunchAnnouncements(_ remote: [AppAnnouncement], userId: String?, targetLocale: String, translationService: TranslationService) async {
        let merged = store.mergeAndSave(remote)
        guard store.canPresentDaily(for: userId) else {
            publish([])
            return
        }
        let presentable = store.activeAnnouncements(from: merged)
            .filter { ($0.triggerScene ?? "app_launch") == "app_launch" }
        guard !presentable.isEmpty else {
            publish([])
            return
        }
        pendingDailyUserId = userId
        pendingDailyAnnouncements = presentable
        let mapped = presentable.map(map(remote:))
        publish(await translationService.localizeAnnouncements(mapped, targetLanguageCode: targetLocale))
    }

    func activeAnnouncementModels(from remote: [AppAnnouncement]) -> [Announcement] {
        store.activeAnnouncements(from: remote).map(map(remote:))
    }

    private func map(remote: AppAnnouncement) -> Announcement {
        let title = remote.title
        let content = remote.content
        let type = AnnouncementType(rawValue: remote.type ?? "info") ?? .info
        let priority = remote.priority ?? 0
        let publishedAt = parseDate(remote.createdAt) ?? parseDate(remote.visibleStartAt) ?? parseDate(remote.updatedAt) ?? Date()
        let startDate = parseDate(remote.visibleStartAt) ?? Date()
        let endDate = parseDate(remote.visibleEndAt)
        let actionUrl = remote.actionUrl
        let actionText = remote.actionText
        let isDismissible = remote.dismissible ?? true
        return Announcement(
            id: remote.announcementUuid,
            title: title,
            content: content,
            type: type,
            priority: priority,
            publishedAt: publishedAt,
            startDate: startDate,
            endDate: endDate,
            actionUrl: actionUrl,
            actionText: actionText,
            isDismissible: isDismissible,
            localizedTitle: remote.localizedTitle,
            localizedContent: remote.localizedContent,
            localizedActionText: remote.localizedActionText
        )
    }

    private func parseDate(_ raw: String?) -> Date? {
        guard let raw else { return nil }
        return ISO8601DateFormatter().date(from: raw)
    }

    private func publish(_ announcements: [Announcement]) {
        withTransaction(Transaction(animation: nil)) {
            currentAnnouncements = announcements
            selectedAnnouncementID = announcements.first?.id
        }
    }

    func setCurrentAnnouncements(_ announcements: [Announcement]) {
        publish(announcements)
    }

    private func localizedAnnouncements(_ announcements: [Announcement], targetLocale: String) async -> [Announcement] {
        await translationService.localizeAnnouncements(announcements, targetLanguageCode: targetLocale)
    }
}

#Preview {
    AnnouncementView(announcements: [Announcement.default], selectedAnnouncementID: .constant(Announcement.default.id), onDismiss: {}, onAction: { _ in })
        .environmentObject(AppState())
}
