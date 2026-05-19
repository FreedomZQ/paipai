import SwiftUI

struct HomeView: View {
    @EnvironmentObject var appState: AppState
    @State private var showReview = false
    @State private var selectedCardListRoute: ReviewCardListRoute?
    @State private var showLocalTtsQuotaAlert = false
    @State private var localTtsQuotaAlertMessage = ""
    @State private var showEntitlementInfo = false
    @State private var areLocalTtsResourcesReady = false
    @ScaledMetric(relativeTo: .caption) private var entitlementIconSize: CGFloat = 13
    @ScaledMetric(relativeTo: .caption2) private var entitlementLinkSize: CGFloat = 10

    private var textScale: CGFloat {
        appState.textSizeOption.multiplier
    }

    private var membershipStatus: String {
        appState.localizedPlanName(
            planCode: appState.accountState?.entitlement.planCode,
            planName: appState.accountState?.entitlement.planName
        )
    }

    private var localOcrUsed: Int {
        appState.entitlementDisplaySummary(serviceType: "local_ocr").usedCount
    }

    private var localOcrTotal: Int {
        appState.entitlementDisplaySummary(serviceType: "local_ocr").totalCount
    }

    private var localTtsUsed: Int {
        appState.entitlementDisplaySummary(serviceType: "local_tts").usedCount
    }

    private var localTtsTotal: Int {
        appState.entitlementDisplaySummary(serviceType: "local_tts").totalCount
    }

    private var ocrEntitlementSummary: (used: Int, total: Int) {
        return (localOcrUsed, localOcrTotal)
    }

    private var localTtsEntitlementSummary: (used: Int, total: Int) {
        return (localTtsUsed, localTtsTotal)
    }

    private var childEntitlementSummary: (used: Int, total: Int) {
        let localCount = appState.children.count
        let entitlementCount = appState.accountState?.entitlement.childCount ?? 0
        let used = max(localCount, entitlementCount)
        let configuredLimit = appState.accountState?.entitlement.childLimit ?? max(used, 1)
        return (used, max(configuredLimit, used, 1))
    }

    private var learningCardCount: Int {
        childCards.filter { $0.proficiency < 3 }.count
    }

    private var childCards: [ReviewCard] {
        appState.reviewCards.filter { !$0.isDeleted && $0.childId == currentChild.id }
    }

    private var savedCardCount: Int {
        // 句卡删除后本地表会先更新；优先使用本地统计，避免后端摘要延迟导致首页总数短暂回跳。
        if appState.readingAchievementStats.childId == currentChild.id {
            return appState.readingAchievementStats.savedCardCount
        }
        if !childCards.isEmpty {
            return childCards.count
        }
        return appState.homeSummary?.childSummaries.first(where: { $0.childId == currentChild.id })?.savedCardCount ?? 0
    }

    private var masteredCardCount: Int {
        childCards.filter { $0.proficiency >= 3 }.count
    }

    private var currentChild: ChildProfile {
        appState.children.first(where: { $0.id == appState.selectedChild.id }) ?? appState.selectedChild
    }

    private var currentChildDisplayName: String {
        if !currentChild.nickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return currentChild.nickname
        }
        return appState.uiText("宝贝 1", "Child 1")
    }

    private var currentChildStreakDays: Int {
        appState.readingAchievementStats.childId == currentChild.id
            ? appState.readingAchievementStats.currentStreakDays
            : appState.homeSummary?.growth.currentStreakDays ?? 0
    }

    private var currentChildWeeklyActiveDays: Int {
        if let recentDailyUsage = appState.childUsageSummaries[currentChild.id]?.recentDailyUsage {
            return recentDailyUsage.suffix(7).filter { $0.durationSeconds > 0 }.count
        }
        return appState.homeSummary?.growth.weeklyActiveDays ?? 0
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: AppLayout.spacingXL) {
                    membershipSection
                    childInfoSection
                    progressSection
                    growthSection
                    recentCardsSection
                }
                .padding(.horizontal, AppLayout.paddingScreen)
                .padding(.top, AppLayout.spacingS)
                .padding(.bottom, AppLayout.spacingL)
                .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
            }
            .background(AppColors.background)
            .toolbar(.hidden, for: .navigationBar)
            .toolbarBackground(.hidden, for: .navigationBar)
            .navigationDestination(isPresented: $showReview) {
                ReviewView()
                    .environmentObject(appState)
            }
            .navigationDestination(item: $selectedCardListRoute) { route in
                ReviewCardListView(route: route)
                    .environmentObject(appState)
            }
            .navigationDestination(isPresented: $showEntitlementInfo) {
                EntitlementRecordsView()
                    .environmentObject(appState)
            }
            .alert(appState.uiText("发音权益已用完", "Pronunciation quota used up"), isPresented: $showLocalTtsQuotaAlert) {
                Button(appState.uiText("关闭", "Close"), role: .cancel) {
                    showLocalTtsQuotaAlert = false
                }
            } message: {
                Text(localTtsQuotaAlertMessage)
            }
            .task {
                await appState.bootstrapIfNeeded()
                areLocalTtsResourcesReady = false
                await appState.preloadLocalTtsResourcesForCurrentContext(reason: "home")
                areLocalTtsResourcesReady = true
                await appState.refreshParentData()
                await appState.refreshReviewData()
                await appState.refreshHomeData()
            }
            .onChange(of: appState.selectedChild.id) { _, _ in
                Task {
                    await appState.refreshReviewData()
                    await appState.refreshHomeData()
                }
            }
        }
    }

    private var membershipSection: some View {
        Button {
            showEntitlementInfo = true
        } label: {
            VStack(spacing: AppLayout.spacingXS) {
                HStack(alignment: .center) {
                    MembershipBadge(status: membershipStatus, expiryDate: nil, textScale: textScale)
                    Spacer(minLength: AppLayout.spacingS)
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.right.circle.fill")
                            .font(.system(size: entitlementIconSize * textScale, weight: .semibold))
                        Text(appState.uiText("权益详情", "Details"))
                            .font(.system(size: entitlementLinkSize * textScale, weight: .semibold))
                            .lineLimit(1)
                            .minimumScaleFactor(0.75)
                    }
                    .foregroundColor(.white.opacity(0.92))
                }

                HStack(spacing: 5) {
                    CompactUsageItem(
                        icon: "camera.fill",
                        label: appState.uiText("文字识别", "OCR"),
                        used: ocrEntitlementSummary.used,
                        total: max(ocrEntitlementSummary.total, 1),
                        textScale: textScale
                    )
                    CompactUsageItem(
                        icon: "speaker.wave.2.fill",
                        label: appState.uiText("朗读", "Read"),
                        used: localTtsEntitlementSummary.used,
                        total: max(localTtsEntitlementSummary.total, 1),
                        textScale: textScale
                    )
                    CompactUsageItem(
                        icon: "person.2.fill",
                        label: appState.uiText("孩子数", "Children"),
                        used: childEntitlementSummary.used,
                        total: childEntitlementSummary.total,
                        textScale: textScale
                    )
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .background(AppGradients.primary)
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var childInfoSection: some View {
        ViewThatFits(in: .horizontal) {
            HStack {
                currentChildSummary
                Spacer()
                switchChildMenu
            }
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                currentChildSummary
                switchChildMenu
            }
        }
    }


    private var currentChildSummary: some View {
        HStack(spacing: AppLayout.spacingM) {
            Text(currentChild.avatarEmoji)
                .font(AppTypography.scaledFont(size: 40))
            VStack(alignment: .leading, spacing: 4) {
                Text(currentChildDisplayName)
                    .font(AppTypography.title3)
                    .foregroundColor(AppColors.textPrimary)
                Text(appState.uiText("当前学习孩子", "Current child"))
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textSecondary)
            }
        }
    }

    @ViewBuilder
    private var switchChildMenu: some View {
        if !appState.children.isEmpty {
            Menu {
                ForEach(appState.children) { child in
                    Button(child.nickname) {
                        appState.selectedChild = child
                    }
                }
            } label: {
                HStack(spacing: 4) {
                    Text(appState.uiText("切换孩子", "Switch child"))
                        .font(AppTypography.bodySmall)
                    Image(systemName: "chevron.down")
                        .font(AppTypography.scaledFont(size: 12))
                }
                .foregroundColor(AppColors.textPrimary)
                .frame(minHeight: AppLayout.minimumTapTarget)
                .padding(.horizontal, 12)
                .background(Color.white)
                .clipShape(Capsule())
                .shadow(color: Color.black.opacity(0.06), radius: 4, x: 0, y: 2)
            }
        }
    }

    private var progressSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(appState.uiText("今日进度", "Today's progress"))
                    .font(AppTypography.headline)

                HStack(spacing: AppLayout.spacingS) {
                    progressMetric(icon: "checkmark.circle", title: appState.uiText("今日完成", "Done today"), value: "\(appState.todayLearningCount)")
                    progressMetric(icon: "square.stack", title: appState.uiText("已保存", "Saved"), value: "\(savedCardCount)") {
                        selectedCardListRoute = ReviewCardListRoute(initialFilter: .all, scope: .todaySaved)
                    }
                    progressMetric(icon: "book", title: appState.uiText("学习中", "Learning"), value: "\(learningCardCount)") {
                        selectedCardListRoute = ReviewCardListRoute(initialFilter: .learning, scope: .all)
                    }
                }
            }
        }
    }

    private func progressMetric(icon: String, title: String, value: String, action: (() -> Void)? = nil) -> some View {
        Button {
            action?()
        } label: {
            VStack(spacing: 4) {
                HStack(spacing: 3) {
                    Image(systemName: icon)
                        .font(AppTypography.scaledFont(size: 10, weight: .semibold))
                        .foregroundColor(AppColors.primary)
                        .frame(width: 12)
                    Text(title)
                        .font(AppTypography.scaledFont(size: 10, weight: .medium))
                        .foregroundColor(AppColors.textSecondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.58)
                }
                .frame(maxWidth: .infinity)

                Text(value)
                    .font(AppTypography.scaledFont(size: 17, weight: .semibold, design: .rounded))
                    .foregroundColor(AppColors.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity, minHeight: 58)
            .padding(.horizontal, 4)
            .padding(.vertical, 8)
            .background(AppColors.primary.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
        }
        .buttonStyle(.plain)
        .allowsHitTesting(action != nil)
    }

    private var growthSection: some View {
        let growth = appState.homeSummary?.growth ?? .empty
        return MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                HStack {
                    Text(appState.uiText("伴读节奏", "Learning rhythm"))
                        .font(AppTypography.headline)
                    Spacer()
                }

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: AppLayout.spacingS)], spacing: AppLayout.spacingS) {
                    Button {
                        selectedCardListRoute = ReviewCardListRoute(initialFilter: .all, scope: .all)
                    } label: {
                        rhythmMetric(icon: "square.stack.fill", title: appState.uiText("总句卡", "Total cards"), value: "\(savedCardCount)", color: AppColors.primary)
                    }
                    .buttonStyle(.plain)
                    Button {
                        selectedCardListRoute = ReviewCardListRoute(initialFilter: .mastered, scope: .all)
                    } label: {
                        rhythmMetric(icon: "star.fill", title: appState.uiText("已掌握", "Mastered"), value: "\(masteredCardCount)", color: AppColors.success)
                    }
                    .buttonStyle(.plain)
                    rhythmMetric(icon: "flame.fill", title: appState.uiText("连续学习", "Streak"), value: "\(currentChildStreakDays)", color: AppColors.warning)
                    rhythmMetric(icon: "calendar.badge.checkmark", title: appState.uiText("本周活跃", "Active this week"), value: "\(currentChildWeeklyActiveDays)", color: AppColors.info)
                }

                Text(appState.localizedGrowthEncouragement(growth.encouragement))
                    .font(AppTypography.footnote)
                    .foregroundColor(AppColors.textSecondary)
            }
        }
    }

    private func rhythmMetric(icon: String, title: String, value: String, color: Color) -> some View {
        HStack(spacing: AppLayout.spacingS) {
            Image(systemName: icon)
                .font(AppTypography.scaledFont(size: 14, weight: .semibold))
                .foregroundColor(color)
                .frame(width: 28, height: 28)
                .background(color.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusS, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(AppTypography.scaledFont(size: 10, weight: .medium))
                    .foregroundColor(AppColors.textSecondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text(value)
                    .font(AppTypography.scaledFont(size: 18, weight: .bold, design: .rounded))
                    .foregroundColor(AppColors.textPrimary)
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
        .padding(.horizontal, AppLayout.spacingS)
        .padding(.vertical, AppLayout.spacingS)
        .background(color.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
    }

    private var recentCardsSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                HStack {
                    Text(appState.uiText("最近保存", "Recently saved"))
                        .font(AppTypography.headline)
                    Spacer()
                    Text(appState.uiText("最近 3 句", "Latest 3"))
                        .font(AppTypography.footnote)
                        .foregroundColor(AppColors.textSecondary)
                }

                if !appState.recentSavedReviewCards.isEmpty {
                    VStack(spacing: AppLayout.spacingS) {
                        ForEach(appState.recentSavedReviewCards.prefix(3)) { card in
                            QuoteCard(
                                text: card.text,
                                translation: card.supportHint.isEmpty ? nil : card.supportHint,
                                isCompact: true,
                                isPlaybackEnabled: areLocalTtsResourcesReady,
                                onPlay: {
                                    guard areLocalTtsResourcesReady else { return }
                                    Task {
                                        let didPlay = await appState.playSourceLocalTts(text: card.text)
                                        await MainActor.run {
                                            handleLocalTtsQuotaResult(didPlay: didPlay)
                                        }
                                    }
                                },
                                onPlayTranslation: card.supportHint.isEmpty ? nil : {
                                    guard areLocalTtsResourcesReady else { return }
                                    Task {
                                        let didPlay = await appState.playTargetLocalTts(text: card.supportHint)
                                        await MainActor.run {
                                            handleLocalTtsQuotaResult(didPlay: didPlay)
                                        }
                                    }
                                }
                            )
                        }
                    }
                } else {
                    emptyRecentCardsView
                }
            }
        }
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

    private var emptyRecentCardsView: some View {
        VStack(alignment: .leading, spacing: AppLayout.spacingM) {
            Text(appState.uiText("还没有保存的句卡，今天先从一句开始。", "No saved cards yet — start with one sentence today."))
                .font(AppTypography.footnote)
                .foregroundColor(AppColors.textSecondary)
            VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                activationStep(icon: "camera.fill", text: appState.uiText("拍绘本里的一句或一小段", "Capture one sentence or a short paragraph"))
                activationStep(icon: "square.and.arrow.down", text: appState.uiText("保存成句卡，明天回来复习", "Save it as a card and review tomorrow"))
                activationStep(icon: "chart.line.uptrend.xyaxis", text: appState.uiText("家长区会逐步看到陪读节奏", "Parents can gradually see the learning rhythm"))
            }
        }
    }

    private func activationStep(icon: String, text: String) -> some View {
        HStack(alignment: .top, spacing: AppLayout.spacingS) {
            Image(systemName: icon)
                .font(AppTypography.scaledFont(size: 14, weight: .semibold))
                .foregroundColor(AppColors.primary)
                .frame(width: 18)
                .padding(.top, 2)
            Text(text)
                .font(AppTypography.footnote)
                .foregroundColor(AppColors.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, minHeight: 36, alignment: .leading)
    }
}

struct CompactUsageItem: View {
    let icon: String
    let label: String
    let used: Int
    let total: Int
    let textScale: CGFloat
    @ScaledMetric(relativeTo: .caption) private var iconSize: CGFloat = 11
    @ScaledMetric(relativeTo: .caption2) private var labelSize: CGFloat = 9
    @ScaledMetric(relativeTo: .caption) private var valueSize: CGFloat = 11

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: iconSize * textScale, weight: .semibold))
                .foregroundColor(.white)
                .frame(width: 12)

            VStack(alignment: .leading, spacing: 1) {
                Text(label)
                    .font(.system(size: labelSize * textScale, weight: .medium))
                    .foregroundColor(.white.opacity(0.9))
                    .lineLimit(1)
                    .minimumScaleFactor(0.55)
                Text("\(used)/\(total)")
                    .font(.system(size: valueSize * textScale, weight: .semibold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 5)
        .padding(.vertical, 5)
        .frame(maxWidth: .infinity, minHeight: 36, alignment: .leading)
        .background(Color.white.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusS, style: .continuous))
    }
}

struct ReviewCardListRoute: Identifiable, Hashable {
    let initialFilter: ReviewCardListFilter
    let scope: ReviewCardListScope

    var id: String {
        "\(scope.rawValue)-\(initialFilter.rawValue)"
    }
}

enum ReviewCardListScope: String, Hashable {
    case all
    case todaySaved
}

struct ReviewCardListView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var appState: AppState

    let initialFilter: ReviewCardListFilter
    let scope: ReviewCardListScope
    @State private var selectedFilter: ReviewCardListFilter
    @State private var selectedReviewCardId: String?
    @State private var currentPage = 0

    private let pageSize = 5

    init(route: ReviewCardListRoute) {
        self.initialFilter = route.initialFilter
        self.scope = route.scope
        _selectedFilter = State(initialValue: route.initialFilter)
    }

    private var childCards: [ReviewCard] {
        appState.reviewCards
            .filter { !$0.isDeleted && $0.childId == appState.selectedChild.id }
            .filter { card in
                switch scope {
                case .all:
                    return true
                case .todaySaved:
                    return card.isSavedToday
                }
            }
            .sorted { lhs, rhs in
                (AppClock.date(from: lhs.updatedAt) ?? .distantPast) > (AppClock.date(from: rhs.updatedAt) ?? .distantPast)
            }
    }

    private var filteredCards: [ReviewCard] {
        switch selectedFilter {
        case .all:
            return childCards
        case .mastered:
            return childCards.filter { $0.proficiency >= 3 }
        case .learning:
            return childCards.filter { $0.proficiency < 3 }
        }
    }

    private var totalCount: Int {
        filteredCards.count
    }

    private var totalPages: Int {
        max(Int(ceil(Double(totalCount) / Double(pageSize))), 1)
    }

    private var visibleCards: [ReviewCard] {
        let safePage = min(currentPage, totalPages - 1)
        let start = safePage * pageSize
        guard start < filteredCards.count else { return [] }
        return Array(filteredCards.dropFirst(start).prefix(pageSize))
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: AppLayout.spacingM) {
                header
                filterPicker
                countCard
                cardList
                paginationControls
            }
            .padding(AppLayout.paddingScreen)
            .padding(.bottom, AppLayout.spacingL)
            .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
        }
        .background(AppColors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(item: $selectedReviewCardId) { cardId in
            ReviewView(initialCardId: cardId)
                .environmentObject(appState)
        }
        .task {
            await appState.refreshReviewData()
        }
        .onChange(of: selectedFilter) { _, _ in
            currentPage = 0
        }
        .onChange(of: totalCount) { _, _ in
            currentPage = min(currentPage, totalPages - 1)
        }
    }

    private var header: some View {
        HStack {
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

            Spacer()

            Text(scope == .todaySaved ? appState.uiText("今日保存", "Saved today") : appState.uiText("句卡列表", "Card list"))
                .font(AppTypography.scaledFont(size: 18, weight: .semibold))
                .foregroundColor(AppColors.textPrimary)

            Spacer()
            Color.clear.frame(width: 44, height: 44)
        }
    }

    private var filterPicker: some View {
        MainCard {
            Picker(appState.uiText("筛选条件", "Filter"), selection: $selectedFilter) {
                ForEach(ReviewCardListFilter.allCases) { filter in
                    Text(filterTitle(filter)).tag(filter)
                }
            }
            .pickerStyle(.segmented)
        }
    }

    private var countCard: some View {
        MainCard {
            HStack(spacing: AppLayout.spacingM) {
                Image(systemName: selectedFilter.icon)
                    .font(AppTypography.scaledFont(size: 18, weight: .semibold))
                    .foregroundColor(selectedFilter.color)
                    .frame(width: 40, height: 40)
                    .background(selectedFilter.color.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
                VStack(alignment: .leading, spacing: 4) {
                    Text(filterTitle(selectedFilter))
                        .font(AppTypography.headline)
                        .foregroundColor(AppColors.textPrimary)
                    Text(scope == .todaySaved
                         ? appState.uiText("今日共 \(totalCount) 条记录", "\(totalCount) records saved today")
                         : appState.uiText("共 \(totalCount) 条记录", "\(totalCount) records"))
                        .font(AppTypography.footnote)
                        .foregroundColor(AppColors.textSecondary)
                }
                Spacer()
            }
        }
    }

    @ViewBuilder
    private var cardList: some View {
        if visibleCards.isEmpty {
            MainCard {
                VStack(spacing: AppLayout.spacingM) {
                    Text("📭")
                        .font(AppTypography.scaledFont(size: 42))
                    Text(appState.uiText("当前筛选下暂无句卡", "No cards match this filter"))
                        .font(AppTypography.body)
                        .foregroundColor(AppColors.textSecondary)
                }
                .frame(maxWidth: .infinity, minHeight: 140)
            }
        } else {
            LazyVStack(spacing: AppLayout.spacingS) {
                ForEach(visibleCards) { card in
                    Button {
                        selectedReviewCardId = card.id
                    } label: {
                        cardRow(card)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func cardRow(_ card: ReviewCard) -> some View {
        MainCard {
            HStack(spacing: AppLayout.spacingS) {
                VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                    Text(card.text)
                        .font(AppTypography.body.weight(.semibold))
                        .foregroundColor(AppColors.textPrimary)
                        .lineLimit(1)
                        .truncationMode(.tail)

                    if !card.supportHint.isEmpty {
                        Text(card.supportHint)
                            .font(AppTypography.footnote)
                            .foregroundColor(AppColors.textSecondary)
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                HStack(spacing: 6) {
                    Text(statusText(card))
                        .font(AppTypography.scaledFont(size: 11, weight: .semibold))
                        .foregroundColor(statusColor(card))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(statusColor(card).opacity(0.12))
                        .clipShape(Capsule())

                    Image(systemName: "chevron.right")
                        .font(AppTypography.scaledFont(size: 12, weight: .semibold))
                        .foregroundColor(AppColors.textTertiary)
                        .frame(width: 12)
                }
                .fixedSize(horizontal: true, vertical: false)
            }
        }
    }

    private var paginationControls: some View {
        HStack(spacing: AppLayout.spacingM) {
            Button {
                currentPage = max(currentPage - 1, 0)
            } label: {
                Label(appState.uiText("上一页", "Previous"), systemImage: "chevron.left")
                    .font(AppTypography.footnote.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 46)
                    .foregroundColor(currentPage > 0 ? AppColors.textPrimary : AppColors.textTertiary)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(currentPage <= 0)
            .opacity(currentPage > 0 ? 1 : 0.45)

            Text("\(min(currentPage + 1, totalPages)) / \(totalPages)")
                .font(AppTypography.footnote.weight(.semibold))
                .foregroundColor(AppColors.textSecondary)
                .frame(minWidth: 56)

            Button {
                currentPage = min(currentPage + 1, totalPages - 1)
            } label: {
                Label(appState.uiText("下一页", "Next"), systemImage: "chevron.right")
                    .font(AppTypography.footnote.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 46)
                    .foregroundColor(currentPage < totalPages - 1 ? AppColors.textPrimary : AppColors.textTertiary)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(currentPage >= totalPages - 1)
            .opacity(currentPage < totalPages - 1 ? 1 : 0.45)
        }
    }

    private func statusText(_ card: ReviewCard) -> String {
        if card.proficiency >= 3 {
            return appState.uiText("已掌握", "Mastered")
        }
        return appState.uiText("学习中", "Learning")
    }

    private func statusColor(_ card: ReviewCard) -> Color {
        card.proficiency >= 3 ? AppColors.success : AppColors.warning
    }

    private func filterTitle(_ filter: ReviewCardListFilter) -> String {
        switch filter {
        case .all: return appState.uiText("总句卡", "All cards")
        case .mastered: return appState.uiText("已掌握", "Mastered")
        case .learning: return appState.uiText("学习中", "Learning")
        }
    }
}

enum ReviewCardListFilter: String, CaseIterable, Identifiable, Hashable {
    case all
    case mastered
    case learning

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .all: return "square.stack.fill"
        case .mastered: return "star.fill"
        case .learning: return "book.fill"
        }
    }

    var color: Color {
        switch self {
        case .all: return AppColors.primary
        case .mastered: return AppColors.success
        case .learning: return AppColors.warning
        }
    }
}

private extension ReviewCard {
    var isSavedToday: Bool {
        let savedAt = createdAt ?? updatedAt
        guard let savedAt,
              let savedDate = AppClock.date(from: savedAt) else {
            return false
        }
        return Calendar.current.isDateInToday(savedDate)
    }
}

struct CompactPrimaryButton: View {
    let title: String
    let icon: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let icon {
                    Image(systemName: icon)
                        .font(AppTypography.scaledFont(size: 13, weight: .semibold))
                }
                Text(title)
                    .font(AppTypography.scaledFont(size: 14, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 40)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(AppGradients.primary)
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
            .shadow(color: AppColors.secondary.opacity(0.18), radius: 8, x: 0, y: 3)
            .contentShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(.isButton)
    }
}

#Preview {
    HomeView().environmentObject(AppState())
}
