import SwiftUI
#if os(iOS) && canImport(Translation)
import Translation
#endif

struct LanguagePreferenceView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedLocale = ""
    @State private var selectedTrackCode = ""
    @State private var isSaving = false
    @State private var isPreparingLanguagePack = false
    @State private var languagePackMessage: String?
    @State private var languagePackStatus: TranslationLanguagePackStatus?
    #if os(iOS) && canImport(Translation)
    @State private var translationSessionConfiguration: TranslationSession.Configuration?
    #endif

    var body: some View {
        applyTranslationPreparationTask(
            to: Form {
                Section(appState.uiText("界面展示语种", "Interface language")) {
                    Picker(appState.uiText("显示语种", "Display language"), selection: $selectedLocale) {
                        ForEach(availableLocales, id: \.self) { locale in
                            Text(localeTitle(locale)).tag(locale)
                        }
                    }
                }

                Section(appState.uiText("默认学习方向", "Default learning track")) {
                    Picker(appState.uiText("学习方向", "Learning track"), selection: $selectedTrackCode) {
                        ForEach(appState.bootstrap.learningTracks) { track in
                            Text(track.label).tag(track.code)
                        }
                    }
                    Text(appState.uiText("这里修改的是默认语言偏好；每个孩子的学习方向仍可在孩子档案页单独调整。", "This updates the default language preference. Each child can still have an individual learning track in the child profile page."))
                        .font(AppTypography.footnote)
                        .foregroundStyle(.secondary)

                    if let languagePackMessage {
                        Text(languagePackMessage)
                            .font(AppTypography.footnote)
                            .foregroundStyle(languagePackStatus?.isSupported == false ? AppColors.error : AppColors.warning)
                    }

                    if let status = languagePackStatus, status.isSupported, !status.isReady, canInteractivelyPrepareLanguagePack {
                        Button {
                            Task { await prepareLanguagePackIfNeeded() }
                        } label: {
                            HStack(spacing: 8) {
                                if isPreparingLanguagePack {
                                    ProgressView()
                                        .controlSize(.small)
                                } else {
                                    Image(systemName: "arrow.down.circle")
                                }
                                Text(isPreparingLanguagePack
                                     ? appState.uiText("准备中...", "Preparing...")
                                     : appState.uiText("准备翻译语言包", "Prepare translation pack"))
                            }
                            .font(AppTypography.footnote.weight(.medium))
                            .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .disabled(isPreparingLanguagePack)
                    }

                    if let status = languagePackStatus, status.isReady {
                        Label(appState.uiText("翻译语言包已就绪", "Translation pack is ready"), systemImage: "checkmark.circle.fill")
                            .font(AppTypography.footnote)
                            .foregroundStyle(AppColors.success)
                    }
                }

                Section {
                    PrimaryButton(title: isSaving ? appState.uiText("保存中...", "Saving...") : appState.uiText("保存语言偏好", "Save language preferences"), isLoading: isSaving) {
                        Task { await save() }
                    }
                }
            }
        )
        .scrollContentBackground(.hidden)
        .background(AppColors.background)
        .navigationTitle(appState.uiText("语言偏好", "Language Preferences"))
        .task {
            selectedLocale = appState.interfaceLocaleCode
            selectedTrackCode = appState.userPreference?.readingTrackCode ?? appState.effectiveLearningTrackCode
            await refreshLanguagePackStatus()
        }
        .onChange(of: selectedTrackCode) { _, _ in
            Task { await refreshLanguagePackStatus() }
        }
    }

    private var availableLocales: [String] {
        AppLocaleCatalog.mergedSupportedLocales(
            with: appState.bootstrap.supportedLocales,
            currentLocale: appState.interfaceLocaleCode
        )
    }

    private var canInteractivelyPrepareLanguagePack: Bool {
        #if os(iOS) && canImport(Translation)
        if #available(iOS 18.0, *) {
            return true
        }
        #endif
        return false
    }

    private func localeTitle(_ locale: String) -> String {
        AppLocaleCatalog.title(for: locale)
    }

    private func save() async {
        isSaving = true
        await appState.updateLanguagePreferences(interfaceLocale: selectedLocale, readingTrackCode: selectedTrackCode)
        await refreshLanguagePackStatus()
        isSaving = false
    }

    private func refreshLanguagePackStatus() async {
        let pair = languagePair(for: selectedTrackCode)
        let status = await appState.translationService.checkLanguagePackAvailability(source: pair.source, target: pair.target)
        languagePackStatus = status
        if status.isReady {
            languagePackMessage = appState.uiText(
                "当前学习方向的设备端翻译语言包已准备完成。",
                "The on-device translation pack for the selected learning track is ready."
            )
        } else {
            languagePackMessage = status.message
        }
    }

    private func prepareLanguagePackIfNeeded() async {
        let pair = languagePair(for: selectedTrackCode)
        let readiness = await appState.translationService.downloadLanguagePackIfNeeded(source: pair.source, target: pair.target)
        languagePackStatus = readiness
        guard readiness.isSupported else {
            languagePackMessage = readiness.message
            return
        }
        guard !readiness.isReady else {
            languagePackMessage = appState.uiText(
                "当前学习方向的设备端翻译语言包已准备完成。",
                "The on-device translation pack for the selected learning track is ready."
            )
            return
        }
        #if os(iOS) && canImport(Translation)
        if #available(iOS 18.0, *) {
            isPreparingLanguagePack = true
            languagePackMessage = readiness.message ?? appState.uiText("系统可能会提示下载翻译语言包。", "The system may prompt you to download the translation pack.")
            translationSessionConfiguration = TranslationSession.Configuration(
                source: Locale.Language(identifier: pair.source),
                target: Locale.Language(identifier: pair.target)
            )
            return
        }
        #endif
        languagePackMessage = readiness.message
    }

    @ViewBuilder
    private func applyTranslationPreparationTask<Content: View>(to content: Content) -> some View {
        #if os(iOS) && canImport(Translation)
        if #available(iOS 18.0, *) {
            content.translationTask(translationSessionConfiguration) { session in
                do {
                    languagePackMessage = appState.uiText("正在准备翻译语言包...", "Preparing translation pack...")
                    try await session.prepareTranslation()
                    languagePackStatus = .ready
                    languagePackMessage = appState.uiText(
                        "翻译语言包已准备完成，之后翻译会直接使用设备端能力。",
                        "The translation pack is ready. Future translations will use the on-device capability directly."
                    )
                } catch {
                    languagePackMessage = error.localizedDescription
                }
                translationSessionConfiguration = nil
                isPreparingLanguagePack = false
            }
        } else {
            content
        }
        #else
        content
        #endif
    }

    private func languagePair(for learningTrackCode: String) -> (source: String, target: String) {
        switch learningTrackCode {
        case "en_to_zh", "bilingual":
            return (source: "en", target: "zh-Hans")
        default:
            return (source: "zh-Hans", target: "en")
        }
    }
}
