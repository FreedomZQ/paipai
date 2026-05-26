import SwiftUI

struct ReviewView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject var appState: AppState

    let initialCardId: String?
    let showsAllLearningCards: Bool
    @State private var cards: [ReviewCard] = []
    @State private var currentCardIndex = 0
    @State private var isSubmitting = false
    @State private var usageSessionId = UUID().uuidString
    @State private var usageSessionActive = false
    @State private var usageChildId = ""
    @State private var hasLoadedCards = false
    @State private var showLocalTtsQuotaAlert = false
    @State private var localTtsQuotaAlertMessage = ""
    @State private var areLocalTtsResourcesReady = false
    @State private var selectedSpeed: LocalTtsSpeed = .normal
    /// 待二次确认删除的句卡；非空时展示中文确认弹窗。
    @State private var cardPendingDeletion: ReviewCard?
    /// 删除成功后的视觉反馈弹窗，用户确认后返回上一层句卡列表。
    @State private var showDeleteSuccessAlert = false
    /// 删除失败时展示给用户的本地化错误信息。
    @State private var deleteFailureMessage = ""
    @State private var showDeleteFailureAlert = false

    init(initialCardId: String? = nil, showsAllLearningCards: Bool = false) {
        self.initialCardId = initialCardId
        self.showsAllLearningCards = showsAllLearningCards
        _selectedSpeed = State(initialValue: LocalTtsSpeed.persistedSelection())
    }

    var body: some View {
        VStack(spacing: 0) {
            if !hasLoadedCards {
                loadingView
            } else if cards.isEmpty {
                reviewStatusView
            } else if currentCardIndex < cards.count {
                let card = cards[currentCardIndex]
                ScrollView {
                    VStack(spacing: AppLayout.spacingXL) {
                        reviewHeader
                        progressSection
                        reviewCardView(card)
                        if cards.count > 1 {
                            pageControlSection
                        }
                        reviewResultSection(card: card)
                    }
                    .padding(AppLayout.paddingScreen)
                    .padding(.bottom, AppLayout.spacingL)
                    .adaptiveContentFrame(maxWidth: AppLayout.readableMaxWidth)
                }
                .gesture(
                    DragGesture(minimumDistance: 32)
                        .onEnded { value in
                            guard cards.count > 1 else { return }
                            if value.translation.width < -60 {
                                showNextCard()
                            } else if value.translation.width > 60 {
                                showPreviousCard()
                            }
                        }
                )
                .appScrollDismissesKeyboardInteractively()
            } else {
                ReviewCompletionView(count: cards.count) { dismiss() }
            }
        }
        .background(AppColors.background.ignoresSafeArea())
        .navigationTitle(appState.uiText("今日复习", "Today's Review"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .navigationBar)
        .alert(appState.uiText("朗读权益已用完", "Read-aloud quota used up"), isPresented: $showLocalTtsQuotaAlert) {
            Button(appState.uiText("关闭", "Close"), role: .cancel) {
                showLocalTtsQuotaAlert = false
            }
        } message: {
            Text(localTtsQuotaAlertMessage)
        }
        .alert(appState.uiText("删除当前句卡？", "Delete this card?"), isPresented: Binding(get: {
            cardPendingDeletion != nil
        }, set: { isPresented in
            if !isPresented {
                cardPendingDeletion = nil
            }
        })) {
            Button(appState.uiText("取消", "Cancel"), role: .cancel) {
                cardPendingDeletion = nil
            }
            Button(appState.uiText("确认", "Confirm"), role: .destructive) {
                guard let card = cardPendingDeletion else { return }
                cardPendingDeletion = nil
                Task { await deleteCurrentCard(card) }
            }
        } message: {
            Text(appState.uiText("确定要删除当前记录吗？此操作不可撤销。", "Are you sure you want to delete this card? This action cannot be undone."))
        }
        .alert(appState.uiText("删除成功", "Deleted"), isPresented: $showDeleteSuccessAlert) {
            Button(appState.uiText("确认", "Confirm")) {
                dismiss()
            }
        } message: {
            Text(appState.uiText("当前句卡已删除，句卡列表和首页统计已更新。", "This card has been deleted. The card list and home statistics have been updated."))
        }
        .alert(appState.uiText("删除失败", "Delete failed"), isPresented: $showDeleteFailureAlert) {
            Button(appState.uiText("知道了", "OK")) {
                showDeleteFailureAlert = false
            }
        } message: {
            Text(deleteFailureMessage)
        }
        .task {
            await appState.bootstrapIfNeeded()
            areLocalTtsResourcesReady = false
            await appState.preloadLocalTtsResourcesForCurrentContext(reason: "review")
            areLocalTtsResourcesReady = true
            usageChildId = appState.selectedChild.id
            await startUsageSessionIfNeeded()
            await appState.refreshReviewData()
            applyReviewCards(appState.reviewCards)
            if initialCardId == nil, showsAllLearningCards {
                currentCardIndex = min(appState.savedReviewPageIndex(childId: usageChildId), max(cards.count - 1, 0))
            }
            hasLoadedCards = true
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
                await appState.refreshReviewData()
                applyReviewCards(appState.reviewCards)
                if initialCardId == nil, showsAllLearningCards {
                    currentCardIndex = min(appState.savedReviewPageIndex(childId: usageChildId), max(cards.count - 1, 0))
                }
            }
        }
        .onDisappear {
            appState.ttsService.stop()
            Task {
                await endUsageSessionIfNeeded()
            }
        }
    }

    private var reviewHeader: some View {
        HStack {
            scrollBackButton
            Spacer()
            Text(appState.uiText("复习中", "Reviewing"))
                .font(AppTypography.scaledFont(size: 18, weight: .semibold))
                .foregroundColor(AppColors.textPrimary)
            Spacer()
            Color.clear.frame(width: 44, height: 44)
        }
    }

    private var loadingView: some View {
        VStack(spacing: AppLayout.spacingM) {
            Spacer()
            ProgressView()
                .progressViewStyle(.circular)
            Text(appState.uiText("正在准备复习内容...", "Preparing review cards..."))
                .font(AppTypography.body)
                .foregroundColor(AppColors.textSecondary)
            Spacer()
        }
        .padding(AppLayout.paddingScreen)
        .adaptiveContentFrame(maxWidth: 620)
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

    private var progressSection: some View {
        VStack(spacing: 8) {
            Text(appState.uiText("第 \(currentCardIndex + 1) / \(cards.count) 张", "Card \(currentCardIndex + 1) / \(cards.count)"))
                .font(AppTypography.scaledFont(size: 16))
                .foregroundColor(Color(hex: "#666666"))
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color(hex: "#F5F5F5"))
                    Capsule()
                        .fill(
                            LinearGradient(
                                colors: [Color(hex: "#06D6A0"), Color(hex: "#118AB2")],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: proxy.size.width * progressRatio)
                }
            }
            .frame(height: 8)
        }
        .frame(maxWidth: .infinity)
    }

    private var progressRatio: CGFloat {
        guard !cards.isEmpty else { return 0 }
        return CGFloat(currentCardIndex + 1) / CGFloat(cards.count)
    }

    private func reviewCardView(_ card: ReviewCard) -> some View {
        VStack(spacing: 16) {
            languageHeader(title: appState.uiText("原文", "Original"), languageCode: originalLanguageCode(for: card))
            playableTextArea(
                text: card.text,
                font: .system(size: 20, weight: .semibold),
                color: Color(hex: "#333333")
            ) {
                playOriginal(card)
            }

            if !card.supportHint.isEmpty {
                languageHeader(title: appState.uiText("译文", "Translation"), languageCode: translationLanguageCode(for: card))
                playableTextArea(
                    text: card.supportHint,
                    font: .system(size: 16),
                    color: Color(hex: "#666666")
                ) {
                    playTranslation(card)
                }
            }

            if initialCardId != nil {
                speedSelector
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 32)
        .frame(maxWidth: .infinity)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .shadow(color: Color.black.opacity(0.08), radius: 12, x: 0, y: 2)
    }

    private func languageHeader(title: String, languageCode: String) -> some View {
        HStack(spacing: AppLayout.spacingS) {
            Text(title)
                .font(AppTypography.footnote.weight(.medium))
                .foregroundColor(AppColors.textSecondary)
            Text(appState.displayTitle(for: languageCode))
                .font(AppTypography.scaledFont(size: 11, weight: .semibold))
                .foregroundColor(AppColors.primary)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(AppColors.primary.opacity(0.1))
                .clipShape(Capsule())
            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func playableTextArea(text: String, font: Font, color: Color, action: @escaping () -> Void) -> some View {
        ZStack(alignment: .topTrailing) {
            Text(text)
                .font(font)
                .foregroundColor(color)
                .lineSpacing(4)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 40)
                .padding(.vertical, 4)

            SpeakerButton(isCompact: true, tint: AppColors.primary, action: action)
                .disabled(!areLocalTtsResourcesReady)
                .opacity(areLocalTtsResourcesReady ? 1 : 0.45)
        }
        .frame(maxWidth: .infinity)
    }

    private var pageControlSection: some View {
        HStack(spacing: AppLayout.spacingM) {
            Button {
                showPreviousCard()
            } label: {
                Label(appState.uiText("上一张", "Previous"), systemImage: "chevron.left")
                    .font(AppTypography.footnote.weight(.semibold))
                    .foregroundColor(canShowPrevious ? AppColors.textPrimary : AppColors.textTertiary)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .stroke(AppColors.border, lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(!canShowPrevious)
            .opacity(canShowPrevious ? 1 : 0.45)

            VStack(spacing: 2) {
                Text("\(currentCardIndex + 1) / \(cards.count)")
                    .font(AppTypography.footnote.weight(.semibold))
                    .foregroundColor(AppColors.textPrimary)
                Text(appState.uiText("页码", "Page"))
                    .font(AppTypography.scaledFont(size: 10, weight: .medium))
                    .foregroundColor(AppColors.textSecondary)
            }
            .frame(minWidth: 52)

            Button {
                showNextCard()
            } label: {
                Label(appState.uiText("下一张", "Next"), systemImage: "chevron.right")
                    .font(AppTypography.footnote.weight(.semibold))
                    .foregroundColor(canShowNext ? AppColors.textPrimary : AppColors.textTertiary)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .stroke(AppColors.border, lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(!canShowNext)
            .opacity(canShowNext ? 1 : 0.45)
        }
    }

    private var canShowPrevious: Bool {
        currentCardIndex > 0
    }

    private var canShowNext: Bool {
        currentCardIndex < cards.count - 1
    }

    private var speedSelector: some View {
        VStack(spacing: AppLayout.spacingS) {
            Picker(appState.uiText("朗读速度", "Local TTS speed"), selection: $selectedSpeed) {
                ForEach(LocalTtsSpeed.allCases) { speed in
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
                // 句卡详情和学习页共用同一个语速偏好，用户切换页面后不用重复设置。
                newValue.persist()
                appState.ttsService.stop()
            }
        }
        .padding(.top, AppLayout.spacingS)
        .frame(maxWidth: .infinity)
    }

    private func speedTitle(_ speed: LocalTtsSpeed) -> String {
        switch speed {
        case .extraSlow: return appState.uiText("很慢 (0.5x)", "Very slow (0.5x)")
        case .normal: return appState.uiText("正常速度 (1.0x)", "Normal (1.0x)")
        case .extraFast: return appState.uiText("很快 (1.5x)", "Very fast (1.5x)")
        }
    }

    private func reviewResultSection(card: ReviewCard) -> some View {
        VStack(spacing: 12) {
            Text(appState.uiText("掌握程度", "How well did you remember it?"))
                .font(AppTypography.footnote)
                .foregroundColor(AppColors.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
            HStack(spacing: 12) {
                ForEach(ReviewResult.allCases, id: \.self) { result in
                    ResultButton(result: result) {
                        Task { await recordResult(result, for: card) }
                    }
                    .disabled(isSubmitting)
                }
            }
            // 删除按钮固定放在掌握程度按钮下方居中，避免和复习操作混淆。
            Button(role: .destructive) {
                requestDeleteConfirmation(for: card)
            } label: {
                Label(appState.uiText("删除当前记录", "Delete current card"), systemImage: "trash")
                    .font(AppTypography.scaledFont(size: 14, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
                    .foregroundColor(AppColors.error)
                    .frame(minWidth: 176, minHeight: 46)
                    .padding(.horizontal, AppLayout.spacingL)
                    .background(AppColors.error.opacity(0.08))
                    .overlay(
                        RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous)
                            .stroke(AppColors.error.opacity(0.35), lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
                    .contentShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(isSubmitting)
            .opacity(isSubmitting ? 0.55 : 1)
            .accessibilityLabel(appState.uiText("删除当前记录", "Delete current card"))
        }
    }

    private var reviewStatusView: some View {
        VStack(spacing: AppLayout.spacingXL) {
            Spacer()
            Text(appState.reviewCards.isEmpty ? "✨" : "🎉")
                .font(AppTypography.scaledFont(size: appState.reviewCards.isEmpty ? 72 : 80))
            Text(appState.reviewCards.isEmpty ? appState.uiText("还没有保存的句卡", "No saved cards yet") : appState.uiText("复习完成！", "Review complete!"))
                .font(AppTypography.title2)
                .foregroundColor(AppColors.textPrimary)
                .multilineTextAlignment(.center)
            if appState.reviewCards.isEmpty {
                Text(appState.uiText("先在学习页保存一句，之后就能在伴读复习里完整查看和练习。", "Save a sentence from the learning page first, then review it here."))
                    .font(AppTypography.body)
                    .foregroundColor(AppColors.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
            }
            PrimaryButton(title: appState.uiText("回伴读乐园", "Back to Learning Park"), icon: "tent.fill") {
                dismiss()
            }
            .padding(.horizontal)
            Spacer()
        }
        .padding(AppLayout.paddingScreen)
        .adaptiveContentFrame(maxWidth: 620)
    }

    private func showPreviousCard() {
        guard canShowPrevious else { return }
        withAnimation(.easeInOut(duration: 0.2)) {
            currentCardIndex -= 1
        }
        saveCurrentPageIfNeeded()
    }

    private func showNextCard() {
        guard canShowNext else { return }
        withAnimation(.easeInOut(duration: 0.2)) {
            currentCardIndex += 1
        }
        saveCurrentPageIfNeeded()
    }

    private func saveCurrentPageIfNeeded() {
        guard initialCardId == nil, showsAllLearningCards else { return }
        appState.saveReviewPageIndex(currentCardIndex, childId: usageChildId.isEmpty ? nil : usageChildId)
    }

    private func startUsageSessionIfNeeded() async {
        guard !usageSessionActive, appState.hasAuthenticatedSession else { return }
        usageChildId = appState.selectedChild.id
        await appState.startUsageSession(sessionUuid: usageSessionId, sourcePage: "review")
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

    private func playOriginal(_ card: ReviewCard) {
        guard areLocalTtsResourcesReady else { return }
        Task {
            let didPlay = await appState.playLocalTts(
                text: card.text,
                languageCode: appState.localTtsLanguageCode(for: originalLanguageCode(for: card)),
                rate: selectedSpeed.rate
            )
            await MainActor.run {
                handleLocalTtsQuotaResult(didPlay: didPlay)
            }
        }
    }

    private func playTranslation(_ card: ReviewCard) {
        guard areLocalTtsResourcesReady else { return }
        guard !card.supportHint.isEmpty else { return }
        Task {
            let didPlay = await appState.playLocalTts(
                text: card.supportHint,
                languageCode: appState.localTtsLanguageCode(for: translationLanguageCode(for: card)),
                rate: selectedSpeed.rate
            )
            await MainActor.run {
                handleLocalTtsQuotaResult(didPlay: didPlay)
            }
        }
    }

    private func originalLanguageCode(for card: ReviewCard) -> String {
        // 句卡保存了当时识别出的源语种；旧数据没有该字段时再使用当前用户设置。
        normalizedLanguageCode(card.sourceLanguageCode) ?? appState.sourceLanguageCode
    }

    private func translationLanguageCode(for card: ReviewCard) -> String {
        // 译文朗读优先使用句卡目标语种，确保和用户保存句卡时选择的学习语种一致。
        normalizedLanguageCode(card.targetLanguageCode)
            ?? normalizedLanguageCode(card.learningLanguageCode)
            ?? appState.targetLanguageCode
    }

    private func normalizedLanguageCode(_ languageCode: String?) -> String? {
        guard let languageCode = languageCode?.trimmingCharacters(in: .whitespacesAndNewlines),
              !languageCode.isEmpty else {
            return nil
        }
        return languageCode
    }

    private func handleLocalTtsQuotaResult(didPlay: Bool) {
        guard !didPlay, appState.isLocalTtsQuotaExhausted else { return }
        localTtsQuotaAlertMessage = appState.localTtsQuotaExhaustedMessage
            ?? appState.uiText(
                "今日发音权益已用完，暂时无法继续发音。请让家长在家长区查看权益并补充次数后再发音。",
                "Today's pronunciation quota is used up, so playback is temporarily unavailable. Please ask a parent to review benefits and add quota from the parent area before playing audio again."
            )
        appState.isLocalTtsQuotaExhausted = false
        appState.localTtsQuotaExhaustedMessage = nil
        appState.errorMessage = nil
        showLocalTtsQuotaAlert = true
    }

    private func applyReviewCards(_ loadedCards: [ReviewCard]) {
        if let initialCardId, let targetCard = loadedCards.first(where: { $0.id == initialCardId }) {
            cards = [targetCard]
            currentCardIndex = 0
        } else if showsAllLearningCards {
            cards = loadedCards
                .filter { !$0.isDeleted && $0.proficiency < 3 }
                .sorted { lhs, rhs in
                    (AppClock.date(from: lhs.updatedAt) ?? .distantPast) > (AppClock.date(from: rhs.updatedAt) ?? .distantPast)
                }
            currentCardIndex = min(currentCardIndex, max(cards.count - 1, 0))
            saveCurrentPageIfNeeded()
        } else {
            cards = loadedCards.filter { card in
                guard card.proficiency < 3 else { return false }
                guard let nextReviewAt = card.nextReviewAt else { return true }
                return (AppClock.date(from: nextReviewAt) ?? Date()) <= Date()
            }
            currentCardIndex = min(currentCardIndex, max(cards.count - 1, 0))
        }
    }

    /// 点击删除按钮后只记录待删除对象，真正删除必须经过二次确认弹窗。
    private func requestDeleteConfirmation(for card: ReviewCard) {
        guard !isSubmitting else { return }
        cardPendingDeletion = card
    }

    /// 确认后执行数据库软删除，并刷新复习列表、首页今日进度与伴读节奏统计。
    private func deleteCurrentCard(_ card: ReviewCard) async {
        isSubmitting = true
        let didDelete = await appState.deleteReviewCard(cardId: card.id)
        if didDelete {
            if initialCardId != nil {
                // 从句卡列表进入时直接返回列表，避免展示复习完成页或删除成功弹窗。
                isSubmitting = false
                dismiss()
                return
            }
            cards.removeAll { $0.id == card.id }
            currentCardIndex = min(currentCardIndex, max(cards.count - 1, 0))
            saveCurrentPageIfNeeded()
            showDeleteSuccessAlert = true
        } else {
            deleteFailureMessage = appState.errorMessage
                ?? appState.uiText("删除当前记录失败，请稍后重试。", "Failed to delete this card. Please try again.")
            showDeleteFailureAlert = true
        }
        isSubmitting = false
    }

    private func recordResult(_ result: ReviewResult, for card: ReviewCard) async {
        isSubmitting = true
        await appState.recordReviewResult(cardId: card.id, resultLevel: result.backendValue)
        cards.removeAll { $0.id == card.id }
        if cards.isEmpty {
            currentCardIndex = 0
        } else {
            currentCardIndex = min(currentCardIndex, cards.count - 1)
        }
        isSubmitting = false
    }
}

enum ReviewResult: CaseIterable {
    case hard
    case good

    var icon: String {
        switch self {
        case .hard: return "🤔"
        case .good: return "😊"
        }
    }

    var backendValue: String {
        switch self {
        case .hard: return "hard"
        case .good: return "remembered"
        }
    }
}

struct ResultButton: View {
    @EnvironmentObject var appState: AppState
    let result: ReviewResult
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Text(result.icon).font(AppTypography.scaledFont(size: 20))
                Text(displayName)
                    .font(AppTypography.scaledFont(size: 16, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .foregroundColor(buttonColor)
            .frame(maxWidth: .infinity, minHeight: 54)
            .padding(.horizontal, 8)
            .background(backgroundColor)
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(buttonColor, lineWidth: 2)
            )
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var displayName: String {
        switch result {
        case .hard: return appState.uiText("有点儿难", "Hard")
        case .good: return appState.uiText("记住了", "Remembered")
        }
    }

    private var buttonColor: Color {
        switch result {
        case .hard: return Color(hex: "#FF9800")
        case .good: return Color(hex: "#4CAF50")
        }
    }

    private var backgroundColor: Color {
        switch result {
        case .hard: return Color(hex: "#FFF3E0")
        case .good: return Color(hex: "#E8F5E8")
        }
    }
}

struct ReviewCompletionView: View {
    @EnvironmentObject var appState: AppState
    let count: Int
    let onComplete: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 30) {
                Text("🎊").font(AppTypography.scaledFont(size: 88))
                Text(appState.uiText("今日复习完成！", "Review Complete!"))
                    .font(AppTypography.title1)
                    .foregroundColor(AppColors.textPrimary)
                    .multilineTextAlignment(.center)
                VStack(spacing: 16) {
                    StatRow(icon: "checkmark.circle.fill", title: appState.uiText("完成复习", "Completed"), value: appState.uiText("\(count) 张", "\(count) cards"), color: AppColors.success)
                    StatRow(icon: "flame.fill", title: appState.uiText("继续保持", "Keep going"), value: appState.uiText("明天再来", "Come back tomorrow"), color: AppColors.warning)
                    StatRow(icon: "star.fill", title: appState.uiText("陪读节奏", "Rhythm"), value: appState.uiText("持续建立中", "Still building"), color: AppColors.accentYellow)
                }
                .padding(.horizontal, 40)

                VStack(spacing: AppLayout.spacingM) {
                    PrimaryButton(title: appState.uiText("回首页看今日进度", "Back to home progress"), icon: "house.fill") {
                        appState.selectedTab = .home
                        onComplete()
                    }
                    SecondaryButton(title: appState.uiText("去伴读乐园轻量练习", "Go to Learning Park"), icon: "tent.fill") {
                        appState.selectedTab = .readingPark
                        onComplete()
                    }
                    Button(appState.uiText("完成", "Done")) { onComplete() }
                        .font(AppTypography.body)
                        .foregroundColor(AppColors.primary)
                        .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget)
                        .contentShape(Rectangle())
                }
                .padding(.horizontal)
                .padding(.bottom, 40)
            }
            .padding(AppLayout.paddingScreen)
            .adaptiveContentFrame(maxWidth: 620)
        }
        .background(AppColors.background)
        .appScrollDismissesKeyboardInteractively()
    }
}

struct StatRow: View {
    let icon: String
    let title: String
    let value: String
    let color: Color

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack {
                Image(systemName: icon).foregroundColor(color).frame(width: 24)
                Text(title).font(AppTypography.body).foregroundColor(AppColors.textSecondary)
                Spacer()
                Text(value).font(AppTypography.headline).foregroundColor(AppColors.textPrimary)
            }
            VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                HStack(spacing: AppLayout.spacingS) {
                    Image(systemName: icon).foregroundColor(color).frame(width: 24)
                    Text(title).font(AppTypography.body).foregroundColor(AppColors.textSecondary)
                }
                Text(value).font(AppTypography.headline).foregroundColor(AppColors.textPrimary)
            }
        }
        .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
    }
}

#Preview {
    NavigationStack {
        ReviewView().environmentObject(AppState())
    }
}
