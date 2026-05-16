import SwiftUI
#if os(iOS) && canImport(Translation)
import Translation
#endif

struct LearningDetailView: View {
    let text: String
    let preferCloudSpeech: Bool
    let sourceLanguageOverride: String?
    let targetLanguageOverride: String?
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject var appState: AppState

    @State private var translation: String = ""
    @State private var isTranslating = false
    @State private var isSaving = false
    @State private var showSaveSuccess = false
    @State private var selectedSpeed: SpeechSpeed = .normal
    @State private var showTranslation = false
    @State private var isPlayingOriginal = false
    @State private var isPlayingTranslation = false
    @State private var usageSessionId = UUID().uuidString
    @State private var usageSessionActive = false
    @State private var usageChildId = ""
    @State private var translationPreparationMessage: String?
    @State private var translationAttemptCount = 0
    @State private var hasExhaustedTranslationRetries = false
    @State private var isTranslationLanguagePackMissing = false
    @State private var activeTranslationSourceLanguageCode = ""
    @State private var activeTranslationTargetLanguageCode = ""
    @State private var requiresLanguagePackDownload = false
    @State private var isLanguagePackDownloading = false
    @State private var isLanguagePackDownloadStarted = false
    @State private var isLanguagePackDownloadFinished = false
    @State private var isLanguagePackDownloadFailed = false
    @State private var languagePackDownloadProgress: Double = 0
    @State private var languagePackDownloadMessage: String = ""
    @State private var languagePackDownloadProgressTask: Task<Void, Never>?
    @State private var translationTimeoutTask: Task<Void, Never>?
    @State private var activeTranslationAttemptID: UUID?
    @State private var showSpeechQuotaAlert = false
    @State private var speechQuotaAlertMessage = ""
    @State private var areSpeechResourcesReady = false
    #if os(iOS) && canImport(Translation)
    @State private var translationSessionConfiguration: TranslationSession.Configuration?
    #endif

    private var progressText: String {
        appState.uiText("第 1 / 1 句", "Sentence 1 / 1")
    }

    private var effectiveSourceLanguageCode: String {
        sourceLanguageOverride ?? appState.sourceLanguageCode
    }

    private var effectiveTargetLanguageCode: String {
        targetLanguageOverride ?? appState.targetLanguageCode
    }

    private var translationDownloadInProgressText: String {
        appState.localizedText(
            zhHans: "正在下载翻译语言包...",
            english: "Downloading translation language pack...",
            japanese: "翻訳言語パックをダウンロードしています...",
            korean: "번역 언어 팩을 다운로드하는 중...",
            spanish: "Descargando el paquete de idioma de traducción..."
        )
    }

    private var translationDownloadStartText: String {
        appState.localizedText(
            zhHans: "请保持网络畅通，并按系统提示完成下载。下载成功后会自动翻译并回填内容。",
            english: "Please keep your network connected and follow the system prompt to finish the download. After it succeeds, translation will run automatically and fill in the result.",
            japanese: "ネットワーク接続を保ち、システムの案内に従ってダウンロードを完了してください。完了後、自動で翻訳して結果を入力します。",
            korean: "네트워크 연결을 유지하고 시스템 안내에 따라 다운로드를 완료해 주세요. 다운로드가 완료되면 자동으로 번역하고 결과를 입력합니다.",
            spanish: "Mantén la conexión de red y sigue el aviso del sistema para completar la descarga. Cuando termine, la traducción se ejecutará automáticamente y rellenará el resultado."
        )
    }

    private var languagePackDownloadPromptText: String {
        let targetTitle = targetLanguagePackTitle
        return appState.localizedText(
            zhHans: "需要先下载\(targetTitle)翻译语言包。点击下方按钮后请按系统提示完成下载，下载成功后会自动翻译并回填内容。",
            english: "The \(targetTitle) translation language pack is required. Tap the button below and follow the system prompt to download it. After the download succeeds, translation will run automatically and fill in the result.",
            japanese: "\(targetTitle)の翻訳言語パックが必要です。下のボタンをタップし、システムの案内に従ってダウンロードしてください。完了後、自動で翻訳して結果を入力します。",
            korean: "\(targetTitle) 번역 언어 팩이 필요합니다. 아래 버튼을 누른 뒤 시스템 안내에 따라 다운로드해 주세요. 다운로드가 완료되면 자동으로 번역하고 결과를 입력합니다.",
            spanish: "Se necesita el paquete de idioma de traducción de \(targetTitle). Toca el botón de abajo y sigue el aviso del sistema para descargarlo. Cuando termine, la traducción se ejecutará automáticamente y rellenará el resultado."
        )
    }

    private var targetLanguagePackTitle: String {
        languagePackTitle(for: activeTranslationTargetLanguageCode.isEmpty ? effectiveTargetLanguageCode : activeTranslationTargetLanguageCode)
    }

    private func languagePackTitle(for languageCode: String) -> String {
        let normalized = languageCode
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "_", with: "-")
            .lowercased()
        if normalized.hasPrefix("en") { return "English" }
        if normalized.hasPrefix("zh") { return appState.uiText("中文", "Chinese") }
        if normalized.hasPrefix("ja") { return appState.uiText("日语", "Japanese") }
        if normalized.hasPrefix("ko") { return appState.uiText("韩语", "Korean") }
        if normalized.hasPrefix("es") { return appState.uiText("西班牙语", "Spanish") }
        if normalized.hasPrefix("fr") { return appState.uiText("法语", "French") }
        if normalized.hasPrefix("de") { return appState.uiText("德语", "German") }
        if normalized.hasPrefix("it") { return appState.uiText("意大利语", "Italian") }
        if normalized.hasPrefix("pt") { return appState.uiText("葡萄牙语", "Portuguese") }
        return appState.displayTitle(for: languageCode)
    }

    private var translationDownloadFinishedText: String {
        appState.localizedText(
            zhHans: "翻译语言包已下载完成，点击关闭即可开始翻译。",
            english: "The translation language pack has finished downloading. Tap close to start translating.",
            japanese: "翻訳言語パックのダウンロードが完了しました。閉じるをタップすると翻訳を開始します。",
            korean: "번역 언어 팩 다운로드가 완료되었습니다. 닫기를 누르면 번역을 시작합니다.",
            spanish: "El paquete de idioma de traducción se descargó. Toca cerrar para empezar a traducir."
        )
    }

    private var translationDownloadFailedText: String {
        appState.localizedText(
            zhHans: "\(targetLanguagePackTitle)翻译语言包下载失败。请检查网络或系统翻译服务状态后重试。",
            english: "The \(targetLanguagePackTitle) translation language pack failed to download. Please check your network or system translation service status and try again.",
            japanese: "\(targetLanguagePackTitle)の翻訳言語パックをダウンロードできませんでした。ネットワークまたはシステム翻訳サービスの状態を確認してから再試行してください。",
            korean: "\(targetLanguagePackTitle) 번역 언어 팩 다운로드에 실패했습니다. 네트워크 또는 시스템 번역 서비스 상태를 확인한 뒤 다시 시도해 주세요.",
            spanish: "No se pudo descargar el paquete de idioma de traducción de \(targetLanguagePackTitle). Revisa la red o el estado del servicio de traducción del sistema e inténtalo de nuevo."
        )
    }

    private var unsupportedTranslationMessage: String {
        appState.localizedText(
            zhHans: "原文内容无法翻译，请试试其他内容。",
            english: "The original text cannot be translated. Please try different content.",
            japanese: "原文の内容を翻訳できません。別の内容をお試しください。",
            korean: "원문 내용을 번역할 수 없습니다. 다른 내용을 시도해 주세요.",
            spanish: "No se puede traducir el contenido original. Prueba con otro contenido."
        )
    }

    private var shouldShowTranslationRetryButton: Bool {
        translation.isEmpty && !isTranslating && translationPreparationMessage != nil && !hasExhaustedTranslationRetries
    }

    private var shouldShowOriginalFallback: Bool {
        translation.isEmpty && !isTranslating && hasExhaustedTranslationRetries
    }

    private var shouldShowTranslationPromptBox: Bool {
        isTranslationLanguagePackMissing || translationPreparationMessage != nil
    }

    private let maxTranslationAttempts = 3

    init(text: String, preferCloudSpeech: Bool = false, sourceLanguageCode: String? = nil, targetLanguageCode: String? = nil) {
        self.text = text
        self.preferCloudSpeech = preferCloudSpeech
        self.sourceLanguageOverride = sourceLanguageCode
        self.targetLanguageOverride = targetLanguageCode
        _selectedSpeed = State(initialValue: SpeechSpeed.persistedSelection())
    }

    var body: some View {
        applyTranslationTask(
            to:
                ScrollView {
                    VStack(spacing: AppLayout.spacingXL) {
                        topActionBar
                        progressSection
                        reviewDetailSection
                        hintText
                        saveActionBar
                    }
                    .padding(AppLayout.paddingScreen)
                    .padding(.bottom, AppLayout.spacingL)
                    .adaptiveContentFrame(maxWidth: 620)
                }
                .appScrollDismissesKeyboardInteractively()
                .background(AppColors.background.ignoresSafeArea())
                .navigationTitle(appState.uiText("学习", "Learning"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar(.hidden, for: .navigationBar)
                .sheet(isPresented: $showSaveSuccess) {
                    SaveSuccessView {
                        showSaveSuccess = false
                    }
                    .environmentObject(appState)
                }
                .sheet(isPresented: $isLanguagePackDownloading) {
                    LanguagePackDownloadSheet(
                        isStarted: isLanguagePackDownloadStarted,
                        isFinished: isLanguagePackDownloadFinished,
                        isFailed: isLanguagePackDownloadFailed,
                        progress: languagePackDownloadProgress,
                        message: languagePackDownloadMessage,
                        onDownload: {
                            startLanguagePackDownloadAndTranslation()
                        },
                        onRetry: {
                            startLanguagePackDownloadAndTranslation()
                        },
                        onBack: {
                            cancelLanguagePackDownloadAndReturn()
                        },
                        onConfirm: {
                            isLanguagePackDownloading = false
                        }
                    )
                    .environmentObject(appState)
                    .interactiveDismissDisabled(isLanguagePackDownloadStarted && !isLanguagePackDownloadFinished && !isLanguagePackDownloadFailed)
                }
                .alert(appState.uiText("朗读权益已用完", "Read-aloud quota used up"), isPresented: $showSpeechQuotaAlert) {
                    Button(appState.uiText("关闭", "Close"), role: .cancel) {
                        showSpeechQuotaAlert = false
                    }
                } message: {
                    Text(speechQuotaAlertMessage)
                }
                .task {
                    await appState.bootstrapIfNeeded()
                    await preloadSpeechForThisLesson()
                    await appState.refreshAccountState()
                    await translateText()
                    usageChildId = appState.selectedChild.id
                    await startUsageSessionIfNeeded()
                }
                .onChange(of: scenePhase) { _, newPhase in
                    Task {
                        if newPhase == .active {
                            await startUsageSessionIfNeeded()
                        } else {
                            await endUsageSessionIfNeeded()
                        }
                    }
                }
                .onChange(of: appState.selectedChild.id) { _, newChildId in
                    Task {
                        guard newChildId != usageChildId else { return }
                        await endUsageSessionIfNeeded(refreshParentData: false)
                        usageChildId = newChildId
                        usageSessionId = UUID().uuidString
                        await startUsageSessionIfNeeded()
                    }
                }
                .onDisappear {
                    appState.ttsService.stop()
                    languagePackDownloadProgressTask?.cancel()
                    languagePackDownloadProgressTask = nil
                    translationTimeoutTask?.cancel()
                    translationTimeoutTask = nil
                    activeTranslationAttemptID = nil
                    Task {
                        await endUsageSessionIfNeeded()
                    }
                }
        )
    }

    private var topActionBar: some View {
        HStack(alignment: .center) {
            scrollBackButton
            Spacer(minLength: 12)
            readingParkReturnButton
        }
    }

    private var scrollBackButton: some View {
        Button {
            dismiss()
        } label: {
            Image(systemName: "chevron.left")
                .font(AppTypography.scaledFont(size: 17, weight: .semibold))
                .foregroundColor(AppColors.textPrimary)
                .frame(width: 44, height: 44)
                .background(Color.white)
                .clipShape(Circle())
                .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
        }
        .buttonStyle(.plain)
    }

    private var readingParkReturnButton: some View {
        Button {
            appState.selectedTab = .readingPark
            appState.requestDismissCaptureCover = true
        } label: {
            Label(appState.uiText("返回伴读乐园", "Back to Learning Park"), systemImage: "tent.fill")
                .font(AppTypography.footnote.weight(.semibold))
                .foregroundColor(.white)
                .padding(.horizontal, 12)
                .frame(height: 40)
                .background(AppColors.primary)
                .clipShape(Capsule())
                .shadow(color: AppColors.primary.opacity(0.24), radius: 8, x: 0, y: 2)
                .lineLimit(1)
                .minimumScaleFactor(0.72)
        }
        .buttonStyle(.plain)
    }

    private var progressSection: some View {
        VStack(spacing: AppLayout.spacingS) {
            Text(progressText)
                .font(AppTypography.body)
                .foregroundColor(AppColors.textSecondary)
            ProgressView(value: 1, total: 1)
                .progressViewStyle(LinearProgressViewStyle(tint: AppColors.secondary))
                .frame(maxWidth: .infinity)
        }
    }

    private var reviewDetailSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingL) {
                Text(appState.uiText("原文", "Original"))
                    .font(AppTypography.footnote.weight(.medium))
                    .foregroundColor(AppColors.textSecondary)
                displayBox(text: text, isPrimary: true) {
                    playOriginal()
                }

                Text(appState.uiText("翻译", "Translation"))
                    .font(AppTypography.footnote.weight(.medium))
                    .foregroundColor(AppColors.textSecondary)

                translationArea
                speedSelector
            }
        }
    }

    private var translationArea: some View {
        ZStack {
            if showTranslation, !translation.isEmpty {
                displayBox(text: translation, isPrimary: false, leadingIconName: "eye.fill", leadingAction: {
                    withAnimation(.easeInOut(duration: 0.2)) { showTranslation = false }
                }) {
                    playTranslation()
                }
                .transition(.opacity.combined(with: .move(edge: .bottom)))
            } else if isTranslating && !shouldShowTranslationPromptBox {
                HStack(spacing: AppLayout.spacingS) {
                    ProgressView()
                    Text(appState.uiText("正在翻译...", "Translating..."))
                        .font(AppTypography.bodySmall)
                        .foregroundColor(AppColors.textSecondary)
                }
                .frame(maxWidth: .infinity, minHeight: 44)
                .padding(.vertical, AppLayout.spacingS)
            } else if shouldShowOriginalFallback {
                originalFallbackTranslationBox
            } else if shouldShowTranslationPromptBox {
                HStack(spacing: 12) {
                    if isTranslating {
                        ProgressView()
                    } else {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundColor(AppColors.warning)
                    }
                    Text(translationPreparationMessage ?? appState.uiText("正在翻译...", "Translating..."))
                        .font(AppTypography.bodySmall)
                        .foregroundColor(AppColors.textSecondary)
                        .multilineTextAlignment(.leading)
                    Spacer(minLength: AppLayout.spacingS)
                    if shouldShowTranslationRetryButton {
                        Button {
                            retryTranslation()
                        } label: {
                            Label(appState.uiText("重新翻译", "Retry"), systemImage: "arrow.clockwise")
                                .font(AppTypography.scaledFont(size: 13, weight: .semibold))
                                .foregroundColor(AppColors.primary)
                                .lineLimit(1)
                                .minimumScaleFactor(0.74)
                                .frame(minHeight: AppLayout.minimumTapTarget)
                                .padding(.horizontal, AppLayout.spacingM)
                                .background(AppColors.primary.opacity(0.12))
                                .clipShape(Capsule())
                                .contentShape(Capsule())
                        }
                        .buttonStyle(TranslationRetryButtonStyle())
                        .accessibilityLabel(appState.uiText("重新翻译", "Retry translation"))
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 60)
                .padding(16)
                .background(Color(red: 0.96, green: 0.96, blue: 0.96))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            } else {
                Button {
                    if !translation.isEmpty {
                        withAnimation(.easeInOut(duration: 0.2)) { showTranslation = true }
                    }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "eye.fill")
                            .font(AppTypography.scaledFont(size: 16, weight: .semibold))
                        Text(appState.uiText("点击眼睛图标查看翻译", "Tap the eye icon to view translation"))
                            .font(AppTypography.bodySmall.weight(.medium))
                    }
                    .foregroundColor(AppColors.textSecondary)
                    .frame(maxWidth: .infinity, minHeight: 60)
                    .padding(16)
                    .background(Color(red: 0.96, green: 0.96, blue: 0.96))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(translation.isEmpty)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: showTranslation)
        .animation(.easeInOut(duration: 0.16), value: shouldShowTranslationRetryButton)
    }

    private var originalFallbackTranslationBox: some View {
        HStack(spacing: AppLayout.spacingS) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(AppTypography.scaledFont(size: 14, weight: .semibold))
            Text(appState.uiText("暂未获得翻译", "Translation is unavailable"))
                .font(AppTypography.footnote.weight(.semibold))
                .multilineTextAlignment(.center)
        }
        .foregroundColor(AppColors.warning)
        .frame(maxWidth: .infinity, minHeight: 60, alignment: .center)
        .padding(16)
        .background(Color(red: 0.96, green: 0.96, blue: 0.96))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func displayBox(text: String, isPrimary: Bool, leadingIconName: String? = nil, leadingAction: (() -> Void)? = nil, playAction: @escaping () -> Void) -> some View {
        ZStack(alignment: .topTrailing) {
            Text(text)
                .font(isPrimary ? AppTypography.bodyLarge.weight(.semibold) : AppTypography.body)
                .foregroundColor(AppColors.textPrimary)
                .multilineTextAlignment(.center)
                .lineSpacing(4)
                .frame(maxWidth: .infinity, minHeight: 60)
                .padding(16)
                .background(isPrimary ? Color(red: 0.98, green: 0.98, blue: 0.98) : Color(red: 0.96, green: 0.96, blue: 0.96))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

            SpeakerButton(isCompact: true, tint: isPrimary ? AppColors.primary : AppColors.secondary, action: playAction)
                .disabled(!areSpeechResourcesReady)
                .opacity(areSpeechResourcesReady ? 1 : 0.45)
                .padding(8)

            if let leadingIconName, let leadingAction {
                Button(action: leadingAction) {
                    Image(systemName: leadingIconName)
                        .font(AppTypography.scaledFont(size: 12, weight: .bold))
                        .foregroundColor(AppColors.textPrimary)
                        .frame(width: 24, height: 24)
                        .background(Color.black.opacity(0.1))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .padding(8)
            }
        }
    }

    private var speedSelector: some View {
        VStack(spacing: AppLayout.spacingS) {
            Picker(appState.uiText("朗读速度", "Speech speed"), selection: $selectedSpeed) {
                ForEach(SpeechSpeed.allCases) { speed in
                    Text(speedTitle(speed)).tag(speed)
                }
            }
            .pickerStyle(.menu)
            .frame(width: 220)
            .frame(minHeight: 48)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(AppColors.border, lineWidth: 2)
            )
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .onChange(of: selectedSpeed) { _, newValue in
                newValue.persist()
                appState.ttsService.stop()
                isPlayingOriginal = false
                isPlayingTranslation = false
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var hintText: some View {
        Text(appState.uiText("点击句子旁的播放图标，跟着朗读练习", "Tap the play icon beside a sentence and read along"))
            .font(AppTypography.caption)
            .foregroundColor(AppColors.textTertiary)
            .multilineTextAlignment(.center)
    }

    private var saveActionBar: some View {
        VStack(spacing: 0) {
            Button {
                Task { await saveCard() }
            } label: {
                Label(appState.uiText("保存为句卡", "Save as card"), systemImage: "square.and.pencil")
                    .font(AppTypography.body.weight(.semibold))
                    .foregroundColor(AppColors.info)
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .background(Color(red: 0.89, green: 0.95, blue: 1.0))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .stroke(Color(hex: "#2196F3"), lineWidth: 2)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(isSaving)
            .opacity(isSaving ? 0.65 : 1)
        }
        .padding(.top, 4)
    }

    private func speedTitle(_ speed: SpeechSpeed) -> String {
        switch speed {
        case .extraSlow: return appState.uiText("很慢 (0.5x)", "Very slow (0.5x)")
        case .normal: return appState.uiText("正常速度 (1.0x)", "Normal (1.0x)")
        case .extraFast: return appState.uiText("很快 (1.5x)", "Very fast (1.5x)")
        }
    }

    @ViewBuilder
    private func applyTranslationTask<Content: View>(to content: Content) -> some View {
        #if os(iOS) && canImport(Translation)
        if #available(iOS 18.0, *) {
            content.translationTask(translationSessionConfiguration) { session in
                guard let sessionAttemptID = activeTranslationAttemptID else { return }
                do {
                    if requiresLanguagePackDownload {
                        await MainActor.run {
                            guard isActiveTranslationAttempt(sessionAttemptID) else { return }
                            translationPreparationMessage = translationDownloadInProgressText
                            languagePackDownloadMessage = languagePackDownloadPromptText
                            isLanguagePackDownloadStarted = true
                            isLanguagePackDownloadFinished = false
                            isLanguagePackDownloadFailed = false
                            isLanguagePackDownloading = true
                            beginLanguagePackDownloadProgress()
                        }
                    } else {
                        await MainActor.run {
                            guard isActiveTranslationAttempt(sessionAttemptID) else { return }
                            translationPreparationMessage = nil
                        }
                    }
                    // prepareTranslation 是系统 Translation 框架真正触发语言包下载/安装的入口。
                    // 这里单独捕获失败，避免把后续翻译失败误报成“下载失败”。
                    do {
                        try await session.prepareTranslation()
                    } catch {
                        await MainActor.run {
                            guard isActiveTranslationAttempt(sessionAttemptID) else { return }
                            handleTranslationFailure(message: unsupportedTranslationMessage, isLanguagePackMissing: true)
                            languagePackDownloadMessage = ""
                            isLanguagePackDownloadStarted = true
                            isLanguagePackDownloadFinished = false
                            isLanguagePackDownloadFailed = false
                            failLanguagePackDownloadProgress()
                            isLanguagePackDownloading = false
                            requiresLanguagePackDownload = false
                            translationSessionConfiguration = nil
                            TranslationDiagnostics.logger.error(
                                "translation_pack_prepare_failed target=\(self.activeTranslationTargetLanguageCode, privacy: .public) source=\(self.activeTranslationSourceLanguageCode.isEmpty ? "auto" : self.activeTranslationSourceLanguageCode, privacy: .public) error=\(String(describing: error), privacy: .public)"
                            )
                        }
                        return
                    }
                    await MainActor.run {
                        guard isActiveTranslationAttempt(sessionAttemptID) else { return }
                        // 系统翻译语言包准备成功后，同步更新 App 本地记录，避免下次进入仍提示未下载。
                        appState.markLanguagePackDownloaded(
                            source: activeTranslationSourceLanguageCode.isEmpty ? effectiveSourceLanguageCode : activeTranslationSourceLanguageCode,
                            target: activeTranslationTargetLanguageCode
                        )
                    }
                    if requiresLanguagePackDownload {
                        await MainActor.run {
                            guard isActiveTranslationAttempt(sessionAttemptID) else { return }
                            languagePackDownloadMessage = translationDownloadFinishedText
                            isLanguagePackDownloadFinished = true
                            isLanguagePackDownloadFailed = false
                            completeLanguagePackDownloadProgress()
                            requiresLanguagePackDownload = false
                        }
                    }
                    await MainActor.run {
                        guard isActiveTranslationAttempt(sessionAttemptID) else { return }
                        translationPreparationMessage = nil
                    }
                    let preparedText = TranslationTextProcessor.preprocess(text)
                    let response = try await session.translate(preparedText)
                    await MainActor.run {
                        guard isActiveTranslationAttempt(sessionAttemptID) else { return }
                        let translatedText = TranslationTextProcessor.postprocess(response.targetText)
                        guard !translatedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                            // 系统返回空译文时按失败处理，继续提供“重新翻译”入口或原文兜底。
                            handleTranslationFailure(message: unsupportedTranslationMessage, isLanguagePackMissing: false)
                            return
                        }
                        translation = translatedText
                        translationPreparationMessage = nil
                        isTranslationLanguagePackMissing = false
                        hasExhaustedTranslationRetries = false
                        isTranslating = false
                        translationSessionConfiguration = nil
                        finishTranslationAttempt()
                        TranslationDiagnostics.logger.info(
                            "translation_success target=\(self.activeTranslationTargetLanguageCode, privacy: .public) source=\(self.activeTranslationSourceLanguageCode.isEmpty ? "auto" : self.activeTranslationSourceLanguageCode, privacy: .public) outputLength=\(translatedText.count, privacy: .public) outputFingerprint=\(TranslationPipelinePlanner.fingerprint(translatedText), privacy: .public)"
                        )
                        withAnimation(.easeInOut(duration: 0.2)) {
                            showTranslation = !translatedText.isEmpty
                        }
                    }
                    return
                } catch {
                    await MainActor.run {
                        guard isActiveTranslationAttempt(sessionAttemptID) else { return }
                        handleTranslationFailure(message: unsupportedTranslationMessage, isLanguagePackMissing: false)
                        TranslationDiagnostics.logger.error(
                            "translation_failed target=\(self.activeTranslationTargetLanguageCode, privacy: .public) source=\(self.activeTranslationSourceLanguageCode.isEmpty ? "auto" : self.activeTranslationSourceLanguageCode, privacy: .public) error=\(String(describing: error), privacy: .public)"
                        )
                        if isLanguagePackDownloading {
                            languagePackDownloadMessage = ""
                            isLanguagePackDownloadStarted = true
                            // 能走到这里说明语言包准备阶段已通过；后续失败只展示完成态，不再误导为下载失败。
                            isLanguagePackDownloadFailed = false
                            isLanguagePackDownloadFinished = true
                            completeLanguagePackDownloadProgress()
                        } else {
                            languagePackDownloadMessage = ""
                            isLanguagePackDownloadStarted = true
                            isLanguagePackDownloadFinished = true
                            isLanguagePackDownloadFailed = false
                            completeLanguagePackDownloadProgress()
                            isLanguagePackDownloading = false
                        }
                        isLanguagePackDownloading = false
                        requiresLanguagePackDownload = false
                    }
                }
                await MainActor.run {
                    guard isActiveTranslationAttempt(sessionAttemptID) else { return }
                    translationSessionConfiguration = nil
                    isTranslating = false
                    finishTranslationAttempt()
                }
            }
        } else {
            content
        }
        #else
        content
        #endif
    }

    private func textCard(title: String, text: String, isPlaying: Bool, onPlay: @escaping () -> Void) -> some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                HStack {
                    Text(title)
                        .font(AppTypography.headline)
                    Spacer()
                    Button(action: onPlay) {
                        Image(systemName: isPlaying ? "stop.circle.fill" : "speaker.wave.2.circle.fill")
                            .font(AppTypography.scaledFont(size: 32))
                            .foregroundColor(AppColors.primary)
                            .frame(width: AppLayout.minimumTapTarget, height: AppLayout.minimumTapTarget)
                            .contentShape(Circle())
                    }
                    .buttonStyle(.plain)
                }
                Text(text)
                    .font(AppTypography.bodyLarge)
                    .foregroundColor(AppColors.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func playOriginal() {
        guard areSpeechResourcesReady else { return }
        if isPlayingOriginal {
            appState.ttsService.stop()
            isPlayingOriginal = false
        } else {
            isPlayingOriginal = true
            isPlayingTranslation = false
            Task {
                let didPlay = await appState.playSpeech(text: text, languageCode: effectiveSourceLanguageCode, rate: selectedSpeed.rate, preferCloud: preferCloudSpeech)
                await MainActor.run {
                    isPlayingOriginal = false
                    handleSpeechQuotaResult(didPlay: didPlay)
                }
            }
        }
    }

    private func playTranslation() {
        guard areSpeechResourcesReady else { return }
        if isPlayingTranslation {
            appState.ttsService.stop()
            isPlayingTranslation = false
        } else {
            isPlayingTranslation = true
            isPlayingOriginal = false
            Task {
                let didPlay = await appState.playSpeech(text: translation, languageCode: effectiveTargetLanguageCode, rate: selectedSpeed.rate, preferCloud: preferCloudSpeech)
                await MainActor.run {
                    isPlayingTranslation = false
                    handleSpeechQuotaResult(didPlay: didPlay)
                }
            }
        }
    }

    private func handleSpeechQuotaResult(didPlay: Bool) {
        guard !didPlay, appState.isSpeechQuotaExhausted else { return }
        speechQuotaAlertMessage = appState.speechQuotaExhaustedMessage
            ?? appState.uiText(
                "今日发音权益已用完，暂时无法继续发音。请让家长在家长区查看权益并补充次数后再发音。",
                "Today's pronunciation quota is used up, so playback is temporarily unavailable. Please ask a parent to review benefits and add quota from the parent area before playing audio again."
            )
        appState.isSpeechQuotaExhausted = false
        appState.speechQuotaExhaustedMessage = nil
        appState.errorMessage = nil
        showSpeechQuotaAlert = true
    }

    private func preloadSpeechForThisLesson() async {
        areSpeechResourcesReady = false
        await appState.preloadSpeechResources(
            languageCodes: [effectiveSourceLanguageCode, effectiveTargetLanguageCode],
            reason: "learning_detail"
        )
        areSpeechResourcesReady = true
    }

    private func translateText() async {
        // 每次进入翻译流程都记录尝试次数；首次自动翻译和用户点击“重新翻译”共用同一上限。
        translationAttemptCount += 1
        let attemptID = UUID()
        activeTranslationAttemptID = attemptID
        isTranslating = true
        translation = ""
        hasExhaustedTranslationRetries = false
        isTranslationLanguagePackMissing = false
        translationPreparationMessage = nil
        scheduleTranslationTimeout(for: attemptID)
        let plan = TranslationPipelinePlanner.makePlan(
            text: text,
            sourceLanguageOverride: sourceLanguageOverride,
            targetLanguageOverride: targetLanguageOverride,
            fallbackSourceLanguageCode: appState.sourceLanguageCode,
            fallbackTargetLanguageCode: appState.targetLanguageCode
        )
        let sourceLanguageCode = plan.sourceLanguageCode
        let targetLanguageCode = plan.targetLanguageCode
        TranslationDiagnostics.logger.info(
            "translation_start sourceMode=\(plan.sourceMode.rawValue, privacy: .public) source=\(sourceLanguageCode ?? "auto", privacy: .public) target=\(targetLanguageCode, privacy: .public) inputLength=\(plan.sourceTextLength, privacy: .public) inputFingerprint=\(plan.sourceTextFingerprint, privacy: .public)"
        )
        let readiness = await appState.translationService.checkLanguagePackAvailability(source: sourceLanguageCode, target: targetLanguageCode)
        TranslationDiagnostics.logger.info(
            "translation_readiness supported=\(readiness.isSupported, privacy: .public) ready=\(readiness.isReady, privacy: .public) target=\(targetLanguageCode, privacy: .public) source=\(sourceLanguageCode ?? "auto", privacy: .public)"
        )
        let targetOnlyReadiness = readiness.isSupported
            ? readiness
            : await appState.translationService.checkLanguagePackAvailability(source: nil, target: targetLanguageCode)
        guard isActiveTranslationAttempt(attemptID) else { return }
        guard readiness.isSupported || targetOnlyReadiness.isSupported else {
            handleTranslationFailure(message: unsupportedTranslationMessage, isLanguagePackMissing: true)
            TranslationDiagnostics.logger.error(
                "translation_unsupported target=\(targetLanguageCode, privacy: .public) source=\(sourceLanguageCode ?? "auto", privacy: .public) message=\(readiness.message ?? "nil", privacy: .public)"
            )
            return
        }
        let explicitPairReady = readiness.isSupported && readiness.isReady
        let targetPackReadyFromLocalRecord = appState.isTranslationTargetLanguagePackDownloaded(target: targetLanguageCode)
        let targetPackReadyFromSystem = explicitPairReady
            ? true
            : await appState.translationService.isTargetLanguagePackInstalled(target: targetLanguageCode, preferredSource: sourceLanguageCode)
        guard isActiveTranslationAttempt(attemptID) else { return }
        let canUseExistingTargetPack = explicitPairReady || targetPackReadyFromSystem
        let shouldUseAutomaticSourceWithExistingTargetPack = canUseExistingTargetPack && !explicitPairReady
        let activePair: (source: String?, target: String)
        if shouldUseAutomaticSourceWithExistingTargetPack {
            // 目标语言包已存在，但当前 OCR 源语种不一定能与目标语种直连；改用系统自动识别源语言，避免误弹下载提示。
            activePair = (source: nil, target: targetLanguageCode)
            appState.markLanguagePackDownloaded(source: sourceLanguageCode ?? effectiveSourceLanguageCode, target: targetLanguageCode)
        } else if let sourceLanguageCode {
            let preferredPair = await appState.translationService.preferredTranslationLanguagePair(source: sourceLanguageCode, target: targetLanguageCode)
            activePair = (source: preferredPair.source, target: preferredPair.target)
        } else {
            activePair = (source: nil, target: targetLanguageCode)
        }
        activeTranslationSourceLanguageCode = activePair.source ?? ""
        activeTranslationTargetLanguageCode = activePair.target
        requiresLanguagePackDownload = !canUseExistingTargetPack
        TranslationDiagnostics.logger.info(
            "translation_session_config source=\(activePair.source ?? "auto", privacy: .public) target=\(activePair.target, privacy: .public) requiresDownload=\(self.requiresLanguagePackDownload, privacy: .public) targetPackReadyLocal=\(targetPackReadyFromLocalRecord, privacy: .public) targetPackReadySystem=\(targetPackReadyFromSystem, privacy: .public)"
        )
        #if os(iOS) && canImport(Translation)
        if #available(iOS 18.0, *) {
            if canUseExistingTargetPack {
                // 语言包已安装，不再显示任何下载/准备提示，避免闪现。
                translationPreparationMessage = nil
                isLanguagePackDownloading = false
                isLanguagePackDownloadStarted = false
                isLanguagePackDownloadFinished = false
                isLanguagePackDownloadFailed = false
                languagePackDownloadProgress = 0
                appState.markLanguagePackDownloaded(
                    source: activePair.source ?? effectiveSourceLanguageCode,
                    target: activePair.target
                )
            translationSessionConfiguration = TranslationSession.Configuration(
                source: activePair.source.map { Locale.Language(identifier: $0) },
                target: Locale.Language(identifier: activePair.target)
            )
            } else {
                // 只有确认目标语言包缺失时才展示翻译提示框；普通翻译中不再出现底部白色提示。
                handleTranslationFailure(message: unsupportedTranslationMessage, isLanguagePackMissing: true)
                languagePackDownloadMessage = ""
                isLanguagePackDownloadStarted = false
                isLanguagePackDownloadFinished = false
                isLanguagePackDownloadFailed = false
                languagePackDownloadProgress = 0
                isLanguagePackDownloading = false
                requiresLanguagePackDownload = false
            }
        } else {
            handleTranslationFailure(message: unsupportedTranslationMessage, isLanguagePackMissing: false)
        }
        #else
        handleTranslationFailure(message: unsupportedTranslationMessage, isLanguagePackMissing: false)
        #endif
    }

    /// 翻译失败后由刷新图标触发重试，先清理错误占位，再重新进入同一套翻译流程。
    private func retryTranslation() {
        guard !isTranslating, !hasExhaustedTranslationRetries else { return }
        translationPreparationMessage = nil
        isTranslationLanguagePackMissing = false
        showTranslation = false
        isTranslating = true
        Task { await translateText() }
    }

    private func isActiveTranslationAttempt(_ attemptID: UUID) -> Bool {
        activeTranslationAttemptID == attemptID
    }

    /// 为当前翻译尝试设置 3 秒超时保护；超时后主动终止界面上的翻译中状态并允许用户再次重试。
    private func scheduleTranslationTimeout(for attemptID: UUID) {
        translationTimeoutTask?.cancel()
        translationTimeoutTask = Task {
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            await MainActor.run {
                guard isActiveTranslationAttempt(attemptID), isTranslating else { return }
                TranslationDiagnostics.logger.error(
                    "translation_timeout target=\(self.activeTranslationTargetLanguageCode, privacy: .public) source=\(self.activeTranslationSourceLanguageCode.isEmpty ? "auto" : self.activeTranslationSourceLanguageCode, privacy: .public) attempt=\(self.translationAttemptCount, privacy: .public)"
                )
                // 超时视为本次翻译失败；清空 Translation 配置，避免系统回调继续让界面停留在“翻译中”。
                handleTranslationFailure(message: unsupportedTranslationMessage, isLanguagePackMissing: false, cancelsTimeout: false)
                translationTimeoutTask = nil
            }
        }
    }

    /// 正常成功或失败收尾时取消超时任务，并清空当前尝试 ID，防止迟到回调覆盖新状态。
    private func finishTranslationAttempt() {
        translationTimeoutTask?.cancel()
        translationTimeoutTask = nil
        activeTranslationAttemptID = nil
    }

    /// 统一处理翻译失败：展示可重试入口；达到次数上限后显示原文兜底，并彻底结束“翻译中”状态。
    private func handleTranslationFailure(message: String, isLanguagePackMissing: Bool, cancelsTimeout: Bool = true) {
        if cancelsTimeout {
            finishTranslationAttempt()
        } else {
            activeTranslationAttemptID = nil
        }
        translation = ""
        isTranslating = false
        translationSessionConfiguration = nil
        isTranslationLanguagePackMissing = isLanguagePackMissing
        hasExhaustedTranslationRetries = translationAttemptCount >= maxTranslationAttempts
        // 重试次数耗尽时不再显示失败提示框，避免界面长期停留在“翻译中/待处理”状态。
        translationPreparationMessage = hasExhaustedTranslationRetries ? nil : message
    }

    private func startLanguagePackDownloadAndTranslation() {
        #if os(iOS) && canImport(Translation)
        if #available(iOS 18.0, *) {
            isTranslating = true
            translationPreparationMessage = translationDownloadInProgressText
            languagePackDownloadMessage = translationDownloadStartText
            isLanguagePackDownloadStarted = true
            isLanguagePackDownloadFinished = false
            isLanguagePackDownloadFailed = false
            beginLanguagePackDownloadProgress()
            translationSessionConfiguration = TranslationSession.Configuration(
                source: activeTranslationSourceLanguageCode.isEmpty ? nil : Locale.Language(identifier: activeTranslationSourceLanguageCode),
                target: Locale.Language(identifier: activeTranslationTargetLanguageCode)
            )
        }
        #endif
    }

    private func beginLanguagePackDownloadProgress() {
        languagePackDownloadProgressTask?.cancel()
        languagePackDownloadProgress = 0.05
        languagePackDownloadProgressTask = Task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 350_000_000)
                await MainActor.run {
                    guard isLanguagePackDownloadStarted, !isLanguagePackDownloadFinished, !isLanguagePackDownloadFailed else { return }
                    // Translation 框架不暴露真实字节进度，这里展示准备阶段的估算进度，完成/失败由 prepareTranslation 结果校准。
                    languagePackDownloadProgress = min(languagePackDownloadProgress + 0.04, 0.92)
                }
            }
        }
    }

    private func completeLanguagePackDownloadProgress() {
        languagePackDownloadProgressTask?.cancel()
        languagePackDownloadProgressTask = nil
        languagePackDownloadProgress = 1
    }

    private func failLanguagePackDownloadProgress() {
        languagePackDownloadProgressTask?.cancel()
        languagePackDownloadProgressTask = nil
        languagePackDownloadProgress = max(languagePackDownloadProgress, 0.08)
    }

    private func cancelLanguagePackDownloadAndReturn() {
        languagePackDownloadProgressTask?.cancel()
        languagePackDownloadProgressTask = nil
        translationSessionConfiguration = nil
        translationPreparationMessage = nil
        isTranslating = false
        isLanguagePackDownloading = false
        requiresLanguagePackDownload = false
        dismiss()
    }

    private func startUsageSessionIfNeeded() async {
        guard !usageSessionActive, appState.hasAuthenticatedSession else { return }
        usageChildId = appState.selectedChild.id
        await appState.startUsageSession(sessionUuid: usageSessionId, sourcePage: "learning_detail")
        usageSessionActive = true
    }

    private func endUsageSessionIfNeeded(refreshParentData: Bool = true) async {
        guard usageSessionActive, appState.hasAuthenticatedSession else { return }
        await appState.endUsageSession(sessionUuid: usageSessionId)
        usageSessionActive = false
        if refreshParentData {
            await appState.refreshParentData()
        }
    }

    private func saveCard() async {
        let normalizedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedText.isEmpty else {
            appState.errorMessage = appState.uiText("没有可保存的句子内容。", "There is no sentence content to save.")
            return
        }
        isSaving = true
        let saved = await appState.saveReviewCard(
            text: normalizedText,
            supportHint: translation.isEmpty ? nil : translation,
            sourceLanguageCode: effectiveSourceLanguageCode,
            targetLanguageCode: effectiveTargetLanguageCode
        )
        isSaving = false
        if saved {
            showSaveSuccess = true
        }
    }
}

enum SpeechSpeed: String, CaseIterable, Identifiable {
    case extraSlow
    case normal
    case extraFast

    var id: String { rawValue }

    var rate: Float {
        switch self {
        case .extraSlow: return 0.5
        case .normal: return 1.0
        case .extraFast: return 1.5
        }
    }

    static func persistedSelection() -> SpeechSpeed {
        guard let rawValue = AppScopedDefaults().string(forKey: AppDefaultKey.learningPlaybackSpeed),
              let speed = SpeechSpeed(rawValue: rawValue) else {
            return .normal
        }
        return speed
    }

    func persist() {
        AppScopedDefaults().set(rawValue, forKey: AppDefaultKey.learningPlaybackSpeed)
    }
}

struct LanguagePackDownloadSheet: View {
    @EnvironmentObject var appState: AppState
    let isStarted: Bool
    let isFinished: Bool
    let isFailed: Bool
    let progress: Double
    let message: String
    let onDownload: () -> Void
    let onRetry: () -> Void
    let onBack: () -> Void
    let onConfirm: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            HStack {
                Button(action: onBack) {
                    Label(appState.uiText("返回上一页", "Back"), systemImage: "chevron.left")
                        .font(AppTypography.body.weight(.semibold))
                        .foregroundColor(AppColors.textPrimary)
                        .frame(minHeight: 44)
                }
                .buttonStyle(.plain)
                Spacer()
            }
            Spacer()
            ZStack {
                Circle()
                    .fill(AppColors.primary.opacity(0.12))
                    .frame(width: 120, height: 120)
                if isFailed {
                    Image(systemName: "xmark.circle.fill")
                        .font(AppTypography.scaledFont(size: 60, weight: .semibold))
                        .foregroundColor(AppColors.error)
                } else if isFinished {
                    Image(systemName: "checkmark.circle.fill")
                        .font(AppTypography.scaledFont(size: 60, weight: .semibold))
                        .foregroundColor(AppColors.success)
                } else if isStarted {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: AppColors.primary))
                        .scaleEffect(1.6)
                } else {
                    Image(systemName: "arrow.down.circle.fill")
                        .font(AppTypography.scaledFont(size: 60, weight: .semibold))
                        .foregroundColor(AppColors.primary)
                }
            }
            Text(sheetTitle)
                .font(AppTypography.title2)
                .foregroundColor(AppColors.textPrimary)
                .multilineTextAlignment(.center)
            if !message.isEmpty {
                Text(message)
                    .font(AppTypography.body)
                    .foregroundColor(AppColors.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
            }
            if isStarted || isFinished || isFailed {
                VStack(alignment: .leading, spacing: 8) {
                    ProgressView(value: progress)
                        .tint(isFailed ? AppColors.error : AppColors.primary)
                    Text(progressText)
                        .font(AppTypography.footnote)
                        .foregroundColor(AppColors.textSecondary)
                }
                .padding(.horizontal)
            }
            Spacer()
            if isFailed {
                VStack(spacing: 12) {
                    Button(action: onRetry) {
                        Label(appState.uiText("重试下载", "Retry download"), systemImage: "arrow.clockwise")
                            .font(AppTypography.body.weight(.semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, minHeight: 56)
                            .background(AppColors.primary)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    .buttonStyle(.plain)

                    Button(action: onConfirm) {
                        Text(appState.uiText("关闭", "Close"))
                            .font(AppTypography.body.weight(.semibold))
                            .foregroundColor(AppColors.textPrimary)
                            .frame(maxWidth: .infinity, minHeight: 52)
                            .background(AppColors.border)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            } else if isFinished {
                Button(action: onConfirm) {
                    Text(appState.uiText("关闭", "Close"))
                        .font(AppTypography.body.weight(.semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .background(AppColors.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)
            } else if !isStarted {
                Button(action: onDownload) {
                    Label(appState.uiText("下载语言包", "Download language pack"), systemImage: "arrow.down.circle.fill")
                        .font(AppTypography.body.weight(.semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 56)
                        .background(AppColors.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)
            } else {
                Text(appState.uiText("下载完成后方可关闭此窗口", "You can close this window after the download completes"))
                    .font(AppTypography.footnote)
                    .foregroundColor(AppColors.textTertiary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(AppLayout.paddingScreen)
        .adaptiveContentFrame(maxWidth: 620)
        .background(AppColors.background)
    }

    private var sheetTitle: String {
        if isFailed {
            return appState.uiText("语言包下载失败", "Language pack download failed")
        }
        if isFinished {
            return appState.uiText("语言包下载完成", "Language pack ready")
        }
        if isStarted {
            return appState.uiText("正在下载翻译语言包", "Downloading translation language pack")
        }
        return appState.uiText("下载翻译语言包", "Download translation language pack")
    }

    private var progressText: String {
        if isFailed {
            return appState.uiText("下载未完成，可检查网络后重试。", "Download did not complete. Check your network and retry.")
        }
        if isFinished {
            return appState.uiText("下载完成 100%", "Download complete 100%")
        }
        return appState.uiText("正在准备下载... \(Int(progress * 100))%", "Preparing download... \(Int(progress * 100))%")
    }
}

struct SaveSuccessView: View {
    @EnvironmentObject var appState: AppState
    let onComplete: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            HStack {
                Spacer()
                Button {
                    onComplete()
                } label: {
                    Image(systemName: "xmark")
                        .font(AppTypography.scaledFont(size: 16, weight: .semibold))
                        .foregroundColor(AppColors.textSecondary)
                        .frame(width: 36, height: 36)
                        .background(Color.white)
                        .clipShape(Circle())
                        .shadow(color: Color.black.opacity(0.08), radius: 6, x: 0, y: 2)
                }
                .buttonStyle(.plain)
            }

            Spacer()
            ZStack {
                Circle().fill(AppColors.success.opacity(0.1)).frame(width: 120, height: 120)
                Text("🎉").font(AppTypography.scaledFont(size: 60))
            }
            Text(appState.uiText("保存成功！", "Saved successfully!"))
                .font(AppTypography.title1)
                .foregroundColor(AppColors.textPrimary)
            Text(appState.uiText("已经添加到复习列表，继续学习下一句吧。", "The card has been added to your review list. Keep learning the next sentence."))
                .font(AppTypography.body)
                .foregroundColor(AppColors.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            Spacer()
        }
        .padding(AppLayout.paddingScreen)
        .adaptiveContentFrame(maxWidth: 620)
        .background(AppColors.background)
    }
}

struct TranslationRetryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.9 : 1)
            .opacity(configuration.isPressed ? 0.7 : 1)
            .animation(.easeInOut(duration: 0.12), value: configuration.isPressed)
    }
}

#Preview {
    NavigationStack {
        LearningDetailView(text: "The cat is sleeping on the sofa.")
            .environmentObject(AppState())
    }
}
