import SwiftUI
#if os(iOS) && canImport(Translation)
import Translation
#endif

struct AnnouncementLocalizationHost: View {
    let announcements: [Announcement]
    let targetLanguageCode: String
    let onLocalized: ([Announcement]) -> Void

    @State private var translationSessionConfiguration: TranslationSession.Configuration?

    private var shouldTranslate: Bool {
        !targetLanguageCode.lowercased().hasPrefix("zh")
            && announcements.contains(where: {
                $0.localizedTitle?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty != false
                && $0.localizedContent?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty != false
                && $0.localizedActionText?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty != false
            })
    }

    private var taskKey: String {
        let payload = announcements.map { announcement in
            [
                announcement.id,
                announcement.title,
                announcement.content,
                announcement.actionText ?? "",
                announcement.localizedTitle ?? "",
                announcement.localizedContent ?? "",
                announcement.localizedActionText ?? ""
            ].joined(separator: "\u{1f}")
        }.joined(separator: "\u{1e}")
        return "\(targetLanguageCode)|\(payload)"
    }

    var body: some View {
        Color.clear
            .frame(width: 0, height: 0)
            .task(id: taskKey) {
                await prepareTranslation()
            }
            .translationTask(translationSessionConfiguration) { session in
                guard shouldTranslate else { return }
                do {
                    try await session.prepareTranslation()
                    let localized = try await translateAnnouncements(using: session)
                    await MainActor.run {
                        onLocalized(localized)
                    }
                } catch {
                    await MainActor.run {
                        onLocalized(announcements)
                    }
                }
                translationSessionConfiguration = nil
            }
    }

    @MainActor
    private func prepareTranslation() async {
        guard shouldTranslate else {
            onLocalized(announcements)
            translationSessionConfiguration = nil
            return
        }
        #if os(iOS) && canImport(Translation)
        if #available(iOS 18.0, *) {
            translationSessionConfiguration = TranslationSession.Configuration(
                source: Locale.Language(identifier: "zh-Hans"),
                target: Locale.Language(identifier: targetLanguageCode)
            )
            return
        }
        #endif
        onLocalized(announcements)
        translationSessionConfiguration = nil
    }

    #if os(iOS) && canImport(Translation)
    @available(iOS 18.0, *)
    private func translateAnnouncements(using session: TranslationSession) async throws -> [Announcement] {
        let requests = announcements.flatMap { announcement -> [TranslationSession.Request] in
            var items: [TranslationSession.Request] = [
                TranslationSession.Request(sourceText: announcement.title, clientIdentifier: "\(announcement.id)|title"),
                TranslationSession.Request(sourceText: announcement.content, clientIdentifier: "\(announcement.id)|content")
            ]
            if let actionText = announcement.actionText, !actionText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                items.append(TranslationSession.Request(sourceText: actionText, clientIdentifier: "\(announcement.id)|action"))
            }
            return items
        }
        let responses = try await session.translations(from: requests)
        let translatedTexts = Dictionary(uniqueKeysWithValues: responses.compactMap { response in
            response.clientIdentifier.map { ($0, response.targetText) }
        })
        return announcements.map { announcement in
            Announcement(
                id: announcement.id,
                title: announcement.title,
                content: announcement.content,
                type: announcement.type,
                priority: announcement.priority,
                publishedAt: announcement.publishedAt,
                startDate: announcement.startDate,
                endDate: announcement.endDate,
                actionUrl: announcement.actionUrl,
                actionText: announcement.actionText,
                isDismissible: announcement.isDismissible,
                localizedTitle: translatedTexts["\(announcement.id)|title"],
                localizedContent: translatedTexts["\(announcement.id)|content"],
                localizedActionText: translatedTexts["\(announcement.id)|action"]
            )
        }
    }
    #endif
}
