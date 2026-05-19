import SwiftUI
#if os(iOS)
import UIKit
#endif

@main
struct PaipaiReadAlongApp: App {
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var appState = AppState()
    @StateObject private var announcementManager = AnnouncementManager()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .environmentObject(announcementManager)
                .tint(AppColors.primary)
                .overlay {
                    if appState.shouldPresentAnnouncementOverlay,
                       !announcementManager.currentAnnouncements.isEmpty {
                        AnnouncementView(
                            announcements: announcementManager.currentAnnouncements,
                            selectedAnnouncementID: $announcementManager.selectedAnnouncementID,
                            onDismiss: {
                                announcementManager.dismissCurrentAnnouncement()
                            },
                            onAction: { announcement in
                                guard let url = safeAnnouncementActionURL(announcement.actionUrl) else { return }
                                openURL(url)
                            }
                        )
                        .environmentObject(appState)
                    }
                }
                .task {
                    await appState.bootstrapIfNeeded()
                    await appState.refreshStartupAnnouncementOverlay(using: announcementManager)
                }
                .sheet(isPresented: $appState.isShowingLanguagePackDownloader) {
                    LanguagePackDownloadView()
                        .environmentObject(appState)
                        .interactiveDismissDisabled(appState.isLanguagePackDownloaderDismissDisabled)
                }
                .sheet(isPresented: $appState.isShowingPaywall) {
                    PaywallView()
                        .environmentObject(appState)
                }
                .alert(appState.uiText("需要处理", "Needs attention"), isPresented: appErrorAlertBinding) {
                    Button(appState.uiText("知道了", "OK")) {
                        appState.errorMessage = nil
                        appState.isLocalTtsQuotaExhausted = false
                        appState.localTtsQuotaExhaustedMessage = nil
                    }
                } message: {
                    Text(appState.errorMessage ?? "")
                }
                .onChange(of: scenePhase) { _, newPhase in
                    // App 从后台切回前台时触发。覆盖场景：
                    // - 用户把 App 挂在后台好几天后才再打开
                    // - bootstrapIfNeeded 已由 didBootstrap 拦截，不会再跑启动链路
                    // 在此按本地日期主动将权益回满，并尝试从后端拉取最新权益。
                    if newPhase == .active {
                        Task {
                            await appState.handleForegroundActivation()
                            await appState.refreshStartupAnnouncementOverlay(using: announcementManager)
                        }
                    } else {
                        Task {
                            await appState.handleForegroundDeactivation()
                        }
                    }
                }
                .onChange(of: appState.announcementOverlayRefreshToken) { _, _ in
                    Task { await appState.refreshStartupAnnouncementOverlay(using: announcementManager) }
                }
                .onChange(of: appState.hasCompletedOnboarding) { _, newValue in
                    if newValue {
                        Task { await appState.refreshStartupAnnouncementOverlay(using: announcementManager) }
                    } else {
                        announcementManager.dismissCurrentAnnouncement()
                    }
                }
                .onChange(of: appState.isInitialDataReady) { _, newValue in
                    if newValue {
                        Task { await appState.refreshStartupAnnouncementOverlay(using: announcementManager) }
                    } else {
                        announcementManager.dismissCurrentAnnouncement()
                    }
                }
                .onChange(of: appState.interfaceLocaleCode) { _, _ in
                    Task {
                        await appState.refreshBillingSurface()
                        await appState.refreshStartupAnnouncementOverlay(using: announcementManager)
                    }
                }
        }
    }

    private var appErrorAlertBinding: Binding<Bool> {
        Binding(
            get: { appState.errorMessage?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false },
            set: { isPresented in
                if !isPresented {
                    appState.errorMessage = nil
                    appState.isLocalTtsQuotaExhausted = false
                    appState.localTtsQuotaExhaustedMessage = nil
                }
            }
        )
    }

    private func safeAnnouncementActionURL(_ raw: String?) -> URL? {
        guard let raw = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
              let url = URL(string: raw),
              ["https", "mailto"].contains(url.scheme?.lowercased() ?? "") else {
            return nil
        }
        return url
    }
}

@MainActor
class AppState: ObservableObject {
    @Published var hasCompletedOnboarding: Bool {
        didSet {
            AppScopedDefaults().set(hasCompletedOnboarding, forKey: AppDefaultKey.onboardingCompleted)
        }
    }
    @Published var selectedTab: Tab = .home
    @Published var isLoading = false
    @Published var isInitialDataReady = false
    @Published var errorMessage: String? {
        didSet {
            if Self.isSilentServerConnectionMessage(errorMessage) {
                errorMessage = nil
            }
        }
    }
    @Published var isShowingPaywall = false
    @Published var isLocalTtsQuotaExhausted = false
    @Published var localTtsQuotaExhaustedMessage: String?
    @Published var announcementOverlayRefreshToken = UUID()
    @Published var parentGateRefreshToken = UUID()
    @Published var isParentGateVerified = false
    /// 请求关闭 Capture 模块（fullScreenCover）并返回伴读乐园。
    /// 由 OCRConfirmView 等子页面触发，CaptureView 观察并执行 dismiss()。
    @Published var requestDismissLocalOcrCover: Bool = false

    @Published var bootstrap: AppBootstrap = .placeholder
    @Published var authSession: StoredAuthSession?
    @Published var accountState: AccountState?
    @Published var cloudUsageState: CloudUsageState?
    @Published var homeSummary: HomeSummary?
    @Published var subscriptionStatus: SubscriptionStatus?
    @Published var availablePlans: [Plan] = Plan.defaultPlans
    @Published var legalDocs: [LegalDocument] = LegalDocument.bundledFallbackDocs
    @Published var children: [ChildProfile] = []
    @Published var selectedChild: ChildProfile = .default {
        didSet {
            guard oldValue.id != selectedChild.id || oldValue.learningTrackCode != selectedChild.learningTrackCode else { return }
            scheduleLocalTtsResourcePreload(reason: "selected_child_changed")
        }
    }
    @Published var reviewCards: [ReviewCard] = []
    @Published var recentSavedReviewCards: [ReviewCard] = []
    @Published var todayLearningCount = 0
    @Published var readingAchievementStats: ReadingAchievementStats = .empty
    @Published var familyUsageSummary: FamilyUsageSummary?
    @Published var childUsageSummaries: [String: ChildUsageSummary] = [:]
    @Published var userPreference: UserPreference?
    @Published var lastDeletionReceipt: AccountDeletionReceipt?
    @Published var interfaceLocaleCode: String
    @Published var textSizeOption: AppTextSizeOption {
        didSet {
            AppScopedDefaults().set(textSizeOption.rawValue, forKey: AppDefaultKey.textSizeOption)
            AppTypographyScale.multiplier = textSizeOption.multiplier
        }
    }
    @Published var appVersionPolicy: AppVersionPolicy?
    @Published var billingHealth: BillingHealth?
    @Published var creditProducts: [CreditProduct] = []
    @Published var entitlementRecordPage: EntitlementRecordPage?
    @Published var isEntitlementRecordSyncing = false
    @Published var entitlementRecordsLastSyncedAt: String?
    @Published var activeEntitlementUsageSummaries: [String: EntitlementUsageSummary] = [:]
    @Published var localOcrUsedToday = 0
    @Published var localTtsUsedToday = 0
    @Published var languagePackDownloadState: LanguagePackDownloadState = .idle
    @Published var isShowingLanguagePackDownloader = false
    @Published var latestUnreadWeeklyReport: LocalWeeklyReportRecord?
    @Published var isShowingWeeklyReportPrompt = false
    @Published var isShowingPromptedWeeklyReport = false
    @Published var promptedWeeklyReportChildId: String?
    @Published var promptedWeeklyReportId: String?


    let deviceInfoService = DeviceInfoService()
    let ocrService = OCRService()
    let translationService = TranslationService()
    let ttsService = TTSService()
    let backendClient = BackendClient()
    let purchaseService = AppStorePurchaseService()
    private let announcementStore = AnnouncementStore()

    private let weeklyReportCache = WeeklyReportLocalCache()
    private var didBootstrap = false
    private var entitlementSyncTask: Task<Void, Never>?
    private var usageTickTasks: [String: Task<Void, Never>] = [:]
    private var localTtsResourcePreloadTask: Task<Void, Never>?
    private var weeklyReportGenerationTask: Task<Void, Never>?
    private var preloadedLocalTtsLanguageCodes: Set<String> = []
    private var appForegroundUsageSessionId: String?
    private var reviewPageIndexByChildId: [String: Int] = [:]

    private var storageScope: String {
        authSession?.account.accountId ?? "signed-out"
    }

    private var localDeviceId: String {
        "local-\(storageScope)"
    }

    private lazy var localDatabase = LocalDatabase(dbFilename: AppIdentity.localDatabaseFilename)
    private lazy var childRepository = ChildProfileRepository(database: localDatabase)
    private lazy var reviewEventRepository = ReviewEventRepository(database: localDatabase)
    private lazy var reviewCardRepository = ReviewCardRepository(database: localDatabase)
    private lazy var learningEventRepository = LearningEventRepository(database: localDatabase)
    private lazy var usageSessionRepository = UsageSessionRepository(database: localDatabase)
    private lazy var userPreferenceRepository = UserPreferenceRepository(database: localDatabase)
    private lazy var entitlementRecordRepository = EntitlementRecordRepository(database: localDatabase)
    private lazy var localWeeklyReportRepository = LocalWeeklyReportRepository(database: localDatabase)

    init() {
        let appDefaults = AppScopedDefaults()
        self.hasCompletedOnboarding = appDefaults.bool(forKey: AppDefaultKey.onboardingCompleted)
        self.authSession = backendClient.currentSession
        self.interfaceLocaleCode = appDefaults.string(forKey: AppDefaultKey.interfaceLocale)
            ?? Locale.preferredLanguages.first
            ?? "zh-Hans"
        self.textSizeOption = appDefaults.string(forKey: AppDefaultKey.textSizeOption)
            .flatMap(AppTextSizeOption.init(rawValue:))
            ?? .medium
        AppTypographyScale.multiplier = textSizeOption.multiplier
    }

    var authMode: AuthMode {
        authSession?.authMode ?? .signedOut
    }

    var effectiveLearningTrackCode: String {
        if let preferred = userPreference?.readingTrackCode, !preferred.isEmpty {
            return preferred
        }
        if !selectedChild.learningTrackCode.isEmpty {
            return selectedChild.learningTrackCode
        }
        return bootstrap.learningTracks.first?.code ?? "zh_to_en"
    }

    var sourceLanguageCode: String {
        if let explicit = userPreference?.sourceLanguageCode, !explicit.isEmpty {
            return explicit
        }
        return languagePair(for: effectiveLearningTrackCode).source
    }

    var targetLanguageCode: String {
        if let explicit = userPreference?.targetLanguageCode, !explicit.isEmpty {
            return explicit
        }
        return languagePair(for: effectiveLearningTrackCode).target
    }

    var sourceLocalTtsLanguageCode: String {
        localTtsVoiceCode(for: sourceLanguageCode)
    }

    var targetLocalTtsLanguageCode: String {
        localTtsVoiceCode(for: targetLanguageCode)
    }

    var sourceLanguageTitle: String {
        languageTitle(for: sourceLanguageCode, fallback: uiText("原文", "Original"))
    }

    var targetLanguageTitle: String {
        languageTitle(for: targetLanguageCode, fallback: uiText("译文", "Translation"))
    }

    func localTtsLanguageCode(for languageCode: String) -> String {
        localTtsVoiceCode(for: languageCode)
    }

    func displayTitle(for languageCode: String, fallback: String? = nil) -> String {
        languageTitle(for: languageCode, fallback: fallback ?? languageCode)
    }

    func learningLanguageCode(for child: ChildProfile? = nil) -> String {
        let childTrackCode = child?.learningTrackCode ?? selectedChild.learningTrackCode
        let trackCode = childTrackCode.isEmpty ? effectiveLearningTrackCode : childTrackCode
        return languagePair(for: trackCode).target
    }

    func learningLanguageCodes(for child: ChildProfile? = nil) -> [String] {
        let activeChild = child ?? selectedChild
        let relatedChildren = children.filter { candidate in
            !candidate.isDeleted &&
            (
                candidate.id == activeChild.id ||
                (!candidate.nickname.isEmpty && candidate.nickname == activeChild.nickname)
            )
        }
        let trackCodes = (relatedChildren.isEmpty ? [activeChild] : relatedChildren)
            .map(\.learningTrackCode)
        return uniqueLanguageCodes(trackCodes.map { languagePair(for: $0).target })
    }

    var isEnglishInterface: Bool {
        !AppLocaleCatalog.normalize(interfaceLocaleCode).hasPrefix("zh")
    }

    func uiText(_ zhHans: String, _ english: String) -> String {
        localizedText(zhHans: zhHans, english: english)
    }

    func localizedText(
        zhHans: String,
        english: String,
        japanese: String? = nil,
        korean: String? = nil,
        spanish: String? = nil
    ) -> String {
        let normalized = AppLocaleCatalog.normalize(interfaceLocaleCode)
        if normalized.hasPrefix("zh") { return zhHans }
        if normalized.hasPrefix("ja") { return japanese ?? english }
        if normalized.hasPrefix("ko") { return korean ?? english }
        if normalized.hasPrefix("es") { return spanish ?? english }
        return english
    }

    func localizedGrowthEncouragement(_ text: String) -> String {
        if isEnglishInterface && (text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || text.contains("先从今天的一句开始")) {
            return "Start with one sentence today. After a few days of returning to review, your read-along rhythm will gradually become steady."
        }
        return text
    }

    func localizedUpdateTitle(_ title: String) -> String {
        let normalizedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        if normalizedTitle.isEmpty || (isEnglishInterface && (normalizedTitle.contains("发现新版本") || normalizedTitle.contains("有新版本可用"))) {
            return uiText("发现新版本", "Update available")
        }
        return title
    }

    func localizedUpdateMessage(_ message: String) -> String {
        let normalizedMessage = message.trimmingCharacters(in: .whitespacesAndNewlines)
        if normalizedMessage.isEmpty || (isEnglishInterface && (normalizedMessage.contains("发现新版本") || normalizedMessage.contains("有新版本可用") || normalizedMessage.contains("拍拍伴读") || normalizedMessage.contains("拍拍识图"))) {
            return uiText("发现新版本，可前往 App Store 查看并更新。", "A new version of Capture & Recognize is available. Open the App Store to update.")
        }
        return message
    }

    func localizedUpdateCTA(_ ctaText: String) -> String {
        if ctaText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || (isEnglishInterface && (ctaText.contains("前往") || ctaText.contains("更新"))) {
            return uiText("前往更新", "Update")
        }
        return ctaText
    }

    func localizedUpdateReleaseNotes(_ releaseNotes: [String]) -> [String] {
        if isEnglishInterface && releaseNotes.contains(where: { $0.contains("优化陪读体验") || $0.contains("稳定性") }) {
            return ["Improved the read-along experience and stability."]
        }
        return releaseNotes
    }

    func localizedAnnouncementTypeName(_ type: AnnouncementType) -> String {
        type.displayName(for: interfaceLocaleCode)
    }

    func localizedPlanName(planCode: String?, planName: String?) -> String {
        let normalizedCode = planCode?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? ""
        let normalizedName = planName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if normalizedCode == "free" || normalizedName == "免费版" {
            return uiText("免费版", "Free")
        }
        if isEnglishInterface && normalizedName == "开发本地模拟会员" {
            return "Local development member"
        }
        return normalizedName.isEmpty ? uiText("加载中", "Loading") : normalizedName
    }

    func localizedErrorMessage(_ error: Error) -> String {
        guard !shouldSilenceUserFacingError(error) else { return "" }
        if let urlError = error as? URLError, urlError.code == .notConnectedToInternet, isLikelyLocalBackendURL() {
            return uiText(
                "当前 App 连接的是局域网后端，但设备没有允许“局域网”访问。请到「设置 > 隐私与安全 > 局域网」中打开“拍拍伴读”，然后重试。",
                "This app is using a local-network backend, but Local Network access is not allowed on this device. Open Settings > Privacy & Security > Local Network, enable Paipai Read Along, then try again."
            )
        }
        if let ocrError = error as? OCRService.OCRError {
            switch ocrError {
            case .unsupportedSystem:
                return uiText("当前系统版本暂不支持设备自带文字识别。", "This system version does not support on-device text recognition.")
            case .unreadableImage:
                return uiText("当前图片无法进行本地识别，请重新拍照后再试。", "This image cannot be recognized on device. Please retake it and try again.")
            case .noTextFound:
                return uiText("未识别到文字，请重新拍摄或上传更清晰的图片。", "No text was recognized. Please retake or upload a clearer image.")
            case let .recognitionFailed(message):
                return uiText("识别失败: \(message)", "Recognition failed: \(message)")
            }
        }
        return error.localizedDescription
    }

    private func shouldSilenceUserFacingError(_ error: Error) -> Bool {
        if Self.isCancellationError(error) {
            return true
        }
        if case BackendClient.BackendError.connectionUnavailable = error {
            return true
        }
        if BackendClient.BackendError.isConnectionFailure(error) {
            return true
        }
        return Self.isSilentServerConnectionMessage(error.localizedDescription)
    }

    static func isCancellationError(_ error: Error) -> Bool {
        if error is CancellationError {
            return true
        }
        if let urlError = error as? URLError, urlError.code == .cancelled {
            return true
        }
        let nsError = error as NSError
        return nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorCancelled
    }

    private static func isSilentServerConnectionMessage(_ message: String?) -> Bool {
        guard let normalized = message?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased(),
              !normalized.isEmpty else {
            return false
        }
        let connectionFailureFragments = [
            "could not connect to the server",
            "cannot connect to the server",
            "未能连接到服务器",
            "无法连接到服务器",
            "似乎已断开与互联网的连接",
            "the internet connection appears to be offline"
        ]
        return connectionFailureFragments.contains { normalized.contains($0) }
    }

    private func isLikelyLocalBackendURL() -> Bool {
        guard let host = backendClient.baseURL.host?.lowercased(), !host.isEmpty else { return false }
        if host == "localhost" || host == "127.0.0.1" || host == "::1" {
            return true
        }
        return host.hasPrefix("10.") || host.hasPrefix("192.168.") || host.hasPrefix("172.16.") || host.hasPrefix("172.17.") || host.hasPrefix("172.18.") || host.hasPrefix("172.19.") || host.hasPrefix("172.2") || host.hasPrefix("172.30.") || host.hasPrefix("172.31.")
    }

    /// Onboarding language choice must be available before login or sync.
    /// Persist it in app-scoped defaults so every later screen uses the same display language.
    func setOnboardingInterfaceLocale(_ locale: String) {
        interfaceLocaleCode = locale
        AppScopedDefaults().set(locale, forKey: AppDefaultKey.interfaceLocale)
    }


    var hasAuthenticatedSession: Bool {
        authSession != nil && backendClient.hasAuthenticatedSession
    }

    var shouldPresentAnnouncementOverlay: Bool {
        hasCompletedOnboarding && hasAuthenticatedSession && isInitialDataReady
    }

    struct WeeklyReportBundle {
        let childReport: WeeklyParentReport?
        let familyReport: WeeklyParentReport?
        let history: WeeklyParentReportHistory?
        let historyLoadedFromCache: Bool

        static let empty = WeeklyReportBundle(childReport: nil, familyReport: nil, history: nil, historyLoadedFromCache: false)
    }

    func bootstrapIfNeeded() async {
        guard !didBootstrap else { return }
        didBootstrap = true
        scheduleLocalTtsResourcePreload(reason: "bootstrap_begin")
        await startup()
        scheduleLocalTtsResourcePreload(reason: "bootstrap_data_ready")
        await refreshLanguagePackQueueIfNeeded()
    }

    /// App 从后台切回前台时调用。
    ///
    /// 专门处理“App 挂后台好几天跨过凌晨 0 点”的场景：
    /// 此时 `bootstrapIfNeeded` 已被 `didBootstrap` 拦截，不会再走完整启动链路，
    /// 需要在此主动触发本地日期校验，保证用户看到的权益是今天的。
    ///
    /// 具体动作：
    /// 1. `refreshLocalQuotaCaches()`：按本地日期将 `localOcrUsedToday` / `localTtsUsedToday` 重置为 0（跨天时）
    /// 2. `resetCachedQuotaIfCrossedDay()`：将 cached `accountState.quota.quotaDate` 回滚到今天、used 清零
    /// 3. 尝试后端拉取最新权益（已登录时）；后端下线时前两步已保证 UI 回满
    func handleForegroundActivation() async {
        refreshLocalQuotaCaches()
        resetCachedQuotaIfCrossedDay()
        if hasAuthenticatedSession {
            await refreshAccountState(force: true)
            await refreshBillingSurface()
            await syncEntitlementRecordsFromBackend(reason: "foreground", reportError: false)
            await startAppForegroundUsageSessionIfNeeded()
            await ensureLocalWeeklyReports(reason: "foreground")
            scheduleWeeklyReportGenerationTimer()
        } else {
            ensureLocalDeviceAccountState()
            await syncDailyQuotaGrantRecords(days: 1)
        }
    }

    func handleForegroundDeactivation() async {
        await endAppForegroundUsageSessionIfNeeded()
    }

    func refreshParentGate() {
        isParentGateVerified = false
        parentGateRefreshToken = UUID()
    }


    func startup() async {
        isLoading = true
        isInitialDataReady = false
        defer { isLoading = false }
        await performAuthenticatedStartupRefresh(reason: "startup")
        isInitialDataReady = true
    }

    func simulateStartupAfterSignIn() async {
        isLoading = true
        defer { isLoading = false }
        await performAuthenticatedStartupRefresh(reason: "startup")
    }

    private func performAuthenticatedStartupRefresh(reason: String) async {
        await loadLocalCaches()
        preloadLocalTtsResourcesNow(reason: "startup_local_caches")
        do {
            bootstrap = try await backendClient.fetchBootstrap()
        } catch {
            errorMessage = localizedErrorMessage(error)
        }
        await refreshAppVersionPolicyIfNeeded(force: true)
        if backendClient.currentSession != nil {
            do {
                _ = try await backendClient.fetchAuthMe()
            } catch {
            }
        }
        authSession = backendClient.currentSession
        await loadLocalCaches()
        preloadLocalTtsResourcesNow(reason: "startup_refreshed_local_caches")
        await refreshAllData()
        scheduleLocalTtsResourcePreload(reason: "startup_refresh_all_data")
        await synchronizeNow(reason: reason)
        await refreshBillingSurface()
        await syncEntitlementRecordsFromBackend(reason: reason, reportError: false)
        scheduleEntitlementSyncLoop()
        await refreshHomeData()
        await startAppForegroundUsageSessionIfNeeded()
        await ensureLocalWeeklyReports(reason: reason)
        scheduleWeeklyReportGenerationTimer()
    }

    func refreshAllData() async {
        await refreshAccountState(force: true)
        await refreshCloudUsageStateFromBackend()
        await refreshSubscriptionStatus()
        await refreshBillingSurface()
        await refreshParentData()
        await refreshHomeData()
        await refreshPlans()
        await refreshLegalDocs()
        await refreshPreferences()
        await refreshReviewData()
        await refreshSyncRuntimeState()
    }

    func refreshAccountState(force: Bool = false) async {
        guard hasAuthenticatedSession else {
            ensureLocalDeviceAccountState()
            await syncDailyQuotaGrantRecords(days: 1)
            return
        }
        let defaults = AppScopedDefaults()
        let today = AppClock.dateOnly(from: Date())
        if !force,
           defaults.string(forKey: AppDefaultKey.accountStateLastFetchDate) == today,
           let cachedData = defaults.data(forKey: AppDefaultKey.accountStateCache),
           let cachedState = try? JSONDecoder().decode(AccountState.self, from: cachedData) {
            accountState = cachedState
            // 后端下线跨天时，cached accountState.quota.quotaDate 可能仍是昨天，
            // 需在此先按本地日期把配额回满，避免用户看到昨天的剩余次数。
            resetCachedQuotaIfCrossedDay()
            alignLocalQuotaUsageWithEntitlement()
            return
        }
        do {
            accountState = try await backendClient.fetchAccountState()
            if let encoded = try? JSONEncoder().encode(accountState) {
                defaults.set(encoded, forKey: AppDefaultKey.accountStateCache)
                defaults.set(today, forKey: AppDefaultKey.accountStateLastFetchDate)
            }
            await refreshCloudUsageStateFromBackend()
            await refreshActiveEntitlementUsageSummaries()
            alignLocalQuotaUsageWithEntitlement()
        } catch {
            loadCachedAccountStateForOfflineUse()
        }
    }

    func refreshSubscriptionStatus() async {
        guard hasAuthenticatedSession else {
            subscriptionStatus = nil
            return
        }
        do {
            subscriptionStatus = try await backendClient.fetchSubscriptionStatus()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func refreshBillingSurface() async {
        do {
            // 进入购买页时立即请求后端健康状态；如果服务不可用，界面会直接置灰付款按钮。
            billingHealth = try await backendClient.fetchBillingHealth(locale: interfaceLocaleCode)
            if let products = try? await backendClient.fetchCreditProducts(locale: interfaceLocaleCode) {
                creditProducts = products
            }
        } catch {
            // 后端服务离线或网络失败时不继续加载商品，统一提示“服务暂不可用”。
            billingHealth = BillingHealth(
                status: "offline",
                purchaseAvailable: false,
                unavailableMessage: uiText("服务暂不可用", "Service is temporarily unavailable."),
                checkedAt: ISO8601DateFormatter().string(from: Date())
            )
            creditProducts = []
        }
    }

    func refreshEntitlementRecords(serviceType: String? = nil, statusFilter: String? = nil, page: Int = 1, pageSize: Int = 20, forceBackendSync: Bool = false) async {
        guard hasAuthenticatedSession else {
            ensureLocalDeviceAccountState()
            await syncDailyQuotaGrantRecords(days: 1)
            entitlementRecordsLastSyncedAt = AppClock.nowString()
            entitlementRecordPage = await entitlementRecordRepository.loadPage(
                accountId: storageScope,
                serviceType: serviceType,
                statusFilter: statusFilter,
                page: page,
                pageSize: pageSize
            )
            return
        }
        if forceBackendSync {
            _ = try? await backendClient.refreshEntitlementSnapshot()
            await refreshAccountState(force: true)
            await refreshCloudUsageStateFromBackend()
            await syncEntitlementRecordsFromBackend(reason: "manual_refresh", reportError: true)
            await refreshHomeData()
        } else {
            await syncEntitlementRecordsIfNeeded(reason: "records_page")
        }
        await syncDailyQuotaGrantRecords(days: 60)
        entitlementRecordPage = await entitlementRecordRepository.loadPage(
            accountId: storageScope,
            serviceType: serviceType,
            statusFilter: statusFilter,
            page: page,
            pageSize: pageSize
        )
    }

    func refreshHomeData() async {
        if children.isEmpty {
            let localChildren = await childRepository.loadActive()
            if !localChildren.isEmpty {
                children = localChildren
                if let selected = localChildren.first(where: { $0.id == selectedChild.id }) {
                    selectedChild = selected
                } else if let first = localChildren.first {
                    selectedChild = first
                }
            }
        }
        recentSavedReviewCards = await reviewCardRepository.loadRecentCards(childId: selectedChild.id, limit: 3)
        todayLearningCount = await learningEventRepository.count(childId: selectedChild.id)
        await refreshReadingAchievementStats()
        guard hasAuthenticatedSession else { return }
        do {
            let summary = try await backendClient.fetchHomeSummary()
            homeSummary = summary
            accountState = AccountState(
                accountId: summary.entitlement.accessProof?.userId ?? accountState?.accountId ?? "",
                signInProvider: accountState?.signInProvider ?? "apple",
                entitlement: summary.entitlement,
                quota: summary.quota
            )
            cacheAccountStateForToday()
            await refreshActiveEntitlementUsageSummaries()
            alignLocalQuotaUsageWithEntitlement()
            if let currentChildId = summary.currentChild?.childId,
               let matchedChild = children.first(where: { $0.id == currentChildId }) {
                selectedChild = matchedChild
                recentSavedReviewCards = await reviewCardRepository.loadRecentCards(childId: matchedChild.id, limit: 3)
                todayLearningCount = await learningEventRepository.count(childId: matchedChild.id)
                await refreshReadingAchievementStats()
            }
        } catch {
            recentSavedReviewCards = await reviewCardRepository.loadRecentCards(childId: selectedChild.id, limit: 3)
            todayLearningCount = await learningEventRepository.count(childId: selectedChild.id)
            await refreshReadingAchievementStats()
        }
        scheduleLocalTtsResourcePreload(reason: "home_data_refreshed")
    }

    func refreshPlans() async {
        do {
            let plans = try await backendClient.fetchPlans()
            if !plans.isEmpty { availablePlans = plans }
        } catch {
            // leave cached/default plans
        }
    }


    func loadWeeklyReports(childId: String) async -> WeeklyReportBundle {
        let resolvedChildId = childId.isEmpty ? selectedChild.id : childId
        let earliestReportWeekStart = await earliestAllowedWeeklyReportStart()
        await ensureLocalWeeklyReports(reason: "weekly_report_page")
        let records = await localWeeklyReportRepository.loadHistory(childId: resolvedChildId, months: 3, earliestReportWeekStart: earliestReportWeekStart)
        let historyReports = records.dropFirst().map(\.report)
        let history = WeeklyParentReportHistory(
            scope: "child",
            childId: resolvedChildId,
            isPremiumPlan: true,
            historyWeeks: 13,
            availableHistoryWeeks: records.count,
            reports: Array(historyReports),
            offlineHistoryPreviewEnabled: true
        )
        return WeeklyReportBundle(
            childReport: records.first?.report,
            familyReport: nil,
            history: historyReports.isEmpty ? nil : history,
            historyLoadedFromCache: true
        )
    }

    func localWeeklyReportHistory(childId: String) async -> [LocalWeeklyReportRecord] {
        let earliestReportWeekStart = await earliestAllowedWeeklyReportStart()
        await ensureLocalWeeklyReports(reason: "weekly_report_history")
        return await localWeeklyReportRepository.loadHistory(childId: childId.isEmpty ? selectedChild.id : childId, months: 3, earliestReportWeekStart: earliestReportWeekStart)
    }

    func markWeeklyReportOpened(reportId: String) async {
        await localWeeklyReportRepository.markOpened(reportId: reportId)
        latestUnreadWeeklyReport = await localWeeklyReportRepository.latestUnreadReport(earliestReportWeekStart: await earliestAllowedWeeklyReportStart())
        isShowingWeeklyReportPrompt = latestUnreadWeeklyReport != nil
    }

    private func ensureLocalWeeklyReports(reason: String) async {
        guard hasCompletedOnboarding, hasAuthenticatedSession, !children.isEmpty else { return }
        let earliestReportWeekStart = await earliestAllowedWeeklyReportStart()
        let result = await localWeeklyReportRepository.ensureReports(
            children: children,
            localeCode: interfaceLocaleCode,
            earliestReportWeekStart: earliestReportWeekStart
        )
        latestUnreadWeeklyReport = result.latestUnread
        isShowingWeeklyReportPrompt = result.latestUnread != nil
    }

    private func earliestAllowedWeeklyReportStart() async -> Date? {
        if let accountCreatedAt = AppClock.date(from: authSession?.account.createdAt) {
            return localWeeklyReportRepository.earliestReportWeekStart(afterAccountCreatedAt: accountCreatedAt)
        }
        let localActivityDate = await localWeeklyReportRepository.earliestLocalActivityDate()
        return localWeeklyReportRepository.earliestReportWeekStart(afterAccountCreatedAt: localActivityDate)
    }

    private func scheduleWeeklyReportGenerationTimer() {
        weeklyReportGenerationTask?.cancel()
        guard hasCompletedOnboarding, hasAuthenticatedSession else { return }
        weeklyReportGenerationTask = Task { [weak self] in
            while !Task.isCancelled {
                let target = Self.nextWeeklyReportGenerationDate(after: Date())
                let delay = max(1, target.timeIntervalSinceNow)
                try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                guard !Task.isCancelled else { return }
                await self?.ensureLocalWeeklyReports(reason: "weekly_timer")
            }
        }
    }

    nonisolated private static func nextWeeklyReportGenerationDate(after date: Date) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.firstWeekday = 2
        let currentWeekStart = calendar.date(from: calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: date)) ?? date
        var components = calendar.dateComponents([.year, .month, .day], from: currentWeekStart)
        components.hour = 0
        components.minute = 0
        components.second = 1
        let thisMonday = calendar.date(from: components) ?? date
        if thisMonday > date { return thisMonday }
        return calendar.date(byAdding: .day, value: 7, to: thisMonday) ?? date.addingTimeInterval(7 * 24 * 60 * 60)
    }

    func fetchRecentAnnouncements(windowDays: Int = 30, scene: String? = nil, activeOnly: Bool = false) async -> [AppAnnouncement] {
        guard hasAuthenticatedSession else { return [] }
        return (try? await backendClient.fetchAnnouncements(
            windowDays: windowDays,
            scene: scene,
            locale: interfaceLocaleCode,
            appVersion: BackendClient.defaultAppVersion(),
            planCode: accountState?.entitlement.planCode,
            activeOnly: activeOnly
        )) ?? []
    }

    func localizeAnnouncements(_ announcements: [Announcement]) async -> [Announcement] {
        await translationService.localizeAnnouncements(announcements, targetLanguageCode: interfaceLocaleCode)
    }

    func cachedRecentAnnouncements() -> [AppAnnouncement] {
        announcementStore.loadRecent()
    }

    func refreshStartupAnnouncementOverlay(using manager: AnnouncementManager, windowDays: Int = 30) async {
        guard shouldPresentAnnouncementOverlay else {
            manager.dismissCurrentAnnouncement()
            return
        }
        await refreshAnnouncementOverlay(using: manager, windowDays: windowDays)
    }

    func refreshAnnouncementOverlay(using manager: AnnouncementManager, windowDays: Int = 30) async {
        guard shouldPresentAnnouncementOverlay else {
            manager.dismissCurrentAnnouncement()
            return
        }
        let remoteAnnouncements = await fetchRecentAnnouncements(windowDays: windowDays, scene: "app_launch", activeOnly: true)
        if remoteAnnouncements.isEmpty {
            await manager.presentCachedDailyLaunchAnnouncements(
                userId: authSession?.account.accountId,
                targetLocale: interfaceLocaleCode,
                translationService: translationService
            )
        } else {
            await manager.presentDailyLaunchAnnouncements(
                remoteAnnouncements,
                userId: authSession?.account.accountId,
                targetLocale: interfaceLocaleCode,
                translationService: translationService
            )
        }
    }

    func extractCloudOcrText(imageData: Data, mimeType: String = "image/jpeg") async throws -> OcrExtractReceipt {
        // 首发合规策略：云端 OCR 暂未开放，不再把图片编码后发往业务后端。
        throw BackendClient.BackendError.server(code: "cloud_ocr_disabled", message: "云端识图暂未开放，请使用设备端识别。", traceId: nil)
    }

    func refreshLegalDocs() async {
        do {
            let remoteDocs = try await backendClient.fetchLegalDocs()
            let visibleRemoteDocs = remoteDocs.filter { $0.resolvedURL != nil }
            legalDocs = visibleRemoteDocs.isEmpty ? LegalDocument.bundledFallbackDocs : remoteDocs
        } catch {
            legalDocs = legalDocs.isEmpty ? LegalDocument.bundledFallbackDocs : legalDocs
        }
    }

    /// 刷新设置页使用的版本更新策略。
    ///
    /// 中文维护说明：该数据来自后端统一版本接口；每天首次启动时请求一次，并缓存后端返回的
    /// 版本号、更新说明和 App Store 链接。接口异常时不展示更新提示，避免离线状态误导用户。
    func refreshAppVersionPolicyIfNeeded(force: Bool = false) async {
        if appVersionPolicy == nil {
            appVersionPolicy = AppVersionPolicy.localFallback()
        }
        let defaults = AppScopedDefaults()
        let today = AppClock.dateOnly(from: Date())
        if appVersionPolicy == nil,
           let cachedData = defaults.data(forKey: AppDefaultKey.appVersionPolicyCache),
           let cachedPolicy = try? JSONDecoder().decode(AppVersionPolicy.self, from: cachedData),
           shouldKeepAppVersionPolicy(cachedPolicy) {
            appVersionPolicy = cachedPolicy
        }
        guard force || defaults.string(forKey: AppDefaultKey.appVersionPolicyLastFetchDate) != today else { return }
        do {
            let policy = try await backendClient.fetchAppVersionPolicy()
            defaults.set(today, forKey: AppDefaultKey.appVersionPolicyLastFetchDate)
            if let encoded = try? JSONEncoder().encode(policy) {
                defaults.set(encoded, forKey: AppDefaultKey.appVersionPolicyCache)
            }
            appVersionPolicy = shouldKeepAppVersionPolicy(policy) ? policy : nil
        } catch {
            // Version update refresh is best-effort. Network, timeout, and 5xx
            // failures must stay silent on the parent entitlements surface.
            appVersionPolicy = AppVersionPolicy.localFallback()
        }
    }

    private func shouldKeepAppVersionPolicy(_ policy: AppVersionPolicy) -> Bool {
        policy.appCode == AppIdentity.appCode
    }

    private func isDifferentAppVersion(_ policy: AppVersionPolicy) -> Bool {
        policy.latestVersion != BackendClient.defaultAppVersion() || (!policy.latestBuild.isEmpty && policy.latestBuild != BackendClient.defaultBuildNumber())
    }

    private func compareVersion(_ lhs: String, to rhs: String) -> ComparisonResult {
        let lhsParts = versionParts(lhs)
        let rhsParts = versionParts(rhs)
        let count = max(lhsParts.count, rhsParts.count)
        for index in 0..<count {
            let lhsValue = index < lhsParts.count ? lhsParts[index] : 0
            let rhsValue = index < rhsParts.count ? rhsParts[index] : 0
            if lhsValue > rhsValue { return .orderedDescending }
            if lhsValue < rhsValue { return .orderedAscending }
        }
        return .orderedSame
    }

    private func versionParts(_ version: String) -> [Int] {
        version.split { !$0.isNumber }.map { Int($0) ?? 0 }
    }

    func refreshParentData() async {
        let localChildren = await childRepository.loadActive()
        children = localChildren
        if let currentChildId = homeSummary?.currentChild?.childId,
           let matchedChild = children.first(where: { $0.id == currentChildId }) {
            selectedChild = matchedChild
        } else if let selected = children.first(where: { $0.id == selectedChild.id }) {
            selectedChild = selected
        } else if let first = children.first {
            selectedChild = first
        }

        let usagePolicy = bootstrap.usagePolicy ?? .default
        familyUsageSummary = await usageSessionRepository.familySummary(
            children: children,
            recentSummaryDays: usagePolicy.safeRecentSummaryDays,
            retentionDays: usagePolicy.safeRetentionDays
        )
        childUsageSummaries = children.isEmpty ? [:] : await usageSessionRepository.childSummaries(
            children: children,
            recentSummaryDays: usagePolicy.safeRecentSummaryDays,
            retentionDays: usagePolicy.safeRetentionDays
        )
        await refreshReadingAchievementStats()
    }

    func refreshReviewData() async {
        reviewCards = await reviewCardRepository.loadReviewCards(childId: selectedChild.id)
        await refreshReadingAchievementStats()
    }

    func refreshReadingAchievementStats() async {
        let childId = selectedChild.id
        guard !childId.isEmpty else {
            readingAchievementStats = .empty
            return
        }
        let cards = await reviewCardRepository.loadReviewCards(childId: childId)
        let activity = await usageSessionRepository.achievementActivity(childId: childId)
        let learningEventCount = await learningEventRepository.countAll(childId: childId)
        readingAchievementStats = ReadingAchievementStats(
            childId: childId,
            learningEventCount: learningEventCount,
            savedCardCount: cards.filter { !$0.isDeleted }.count,
            masteredCardCount: cards.filter { !$0.isDeleted && $0.proficiency >= 3 }.count,
            effectiveSessionCount: activity.effectiveSessionCount,
            activeDayCount: activity.activeDayCount,
            currentStreakDays: activity.currentStreakDays
        )
    }

    func savedReviewPageIndex(childId: String? = nil) -> Int {
        reviewPageIndexByChildId[childId ?? selectedChild.id] ?? 0
    }

    func saveReviewPageIndex(_ index: Int, childId: String? = nil) {
        reviewPageIndexByChildId[childId ?? selectedChild.id] = max(index, 0)
    }

    func refreshPreferences() async {
        let localPreference = await userPreferenceRepository.current()
        userPreference = localPreference
        interfaceLocaleCode = localPreference.uiLocale
        AppScopedDefaults().set(localPreference.uiLocale, forKey: AppDefaultKey.interfaceLocale)
    }

    func createChildProfile(nickname: String, ageBand: String, learningTrackCode: String) async -> Bool {
        guard hasAuthenticatedSession else {
            errorMessage = uiText("请先登录后再创建孩子档案。", "Please sign in before creating a child profile.")
            return false
        }
        if let entitlement = accountState?.entitlement,
           entitlement.remainingChildSlots <= 0 {
            errorMessage = uiText("当前套餐的孩子档案数已满。", "The current plan has reached the child profile limit.")
            isShowingPaywall = true
            return false
        }
        let child = await childRepository.upsertLocal(nickname: nickname, ageBand: ageBand, learningTrackCode: learningTrackCode)
        selectedChild = child
        children.removeAll { $0.id == child.id }
        children.insert(child, at: 0)
        await syncPreferencesForLearningTrack(learningTrackCode)
        await refreshParentData()
        await synchronizeNow(reason: "create_child")
        await refreshAccountState()
        await refreshHomeData()
        await enqueueLanguagePackIfNeeded(for: learningTrackCode, presentDownloader: true)
        // 孩子资料页本身也是 sheet。保存后立刻启动队列，避免全局下载页暂时无法展示时任务没有真正开始。
        Task { await self.startPendingLanguagePackDownloads() }
        return true
    }

    func updateChildProfile(childId: String, nickname: String, ageBand: String, learningTrackCode: String) async -> Bool {
        guard hasAuthenticatedSession else {
            errorMessage = uiText("请先登录后再编辑孩子档案。", "Please sign in before editing a child profile.")
            return false
        }
        let previousLearningTrackCode = children.first(where: { $0.id == childId })?.learningTrackCode
        _ = await childRepository.upsertLocal(id: childId, nickname: nickname, ageBand: ageBand, learningTrackCode: learningTrackCode)
        await syncPreferencesForLearningTrack(learningTrackCode)
        await refreshParentData()
        await synchronizeNow(reason: "update_child")
        await refreshHomeData()
        if previousLearningTrackCode != learningTrackCode {
            await enqueueLanguagePackIfNeeded(for: learningTrackCode, presentDownloader: true)
            // 编辑孩子切换学习语种后，同步启动下载队列，确保本地状态能及时落盘。
            Task { await self.startPendingLanguagePackDownloads() }
        }
        return true
    }

    func deleteChildProfile(childId: String) async -> Bool {
        guard hasAuthenticatedSession else {
            errorMessage = uiText("请先登录后再删除孩子档案。", "Please sign in before deleting a child profile.")
            return false
        }
        _ = await childRepository.softDeleteLocal(id: childId)
        await refreshParentData()
        if selectedChild.id == childId {
            selectedChild = children.first ?? .default
        }
        await synchronizeNow(reason: "delete_child")
        await refreshAccountState()
        await refreshHomeData()
        return true
    }

    func hasExistingChildUsingLearningTrack(_ learningTrackCode: String, excluding childId: String? = nil) -> Bool {
        children.contains { child in
            child.learningTrackCode == learningTrackCode && child.id != childId
        }
    }

    func isLanguagePackDownloaded(for learningTrackCode: String) -> Bool {
        let pair = languagePair(for: learningTrackCode)
        return downloadedLanguagePackKeys.contains(languagePackKey(source: pair.source, target: pair.target))
    }

    func isTranslationTargetLanguagePackDownloaded(target: String) -> Bool {
        let normalizedTarget = normalizedLanguageCode(target)
        let targetFamily = languageFamily(normalizedTarget)
        return downloadedLanguagePackKeys.contains { key in
            guard let rawTarget = key.components(separatedBy: "->").last else { return false }
            return languageFamily(rawTarget) == targetFamily
        }
    }

    func markLanguagePackDownloaded(source: String, target: String) {
        let key = languagePackKey(source: normalizedLanguageCode(source), target: normalizedLanguageCode(target))
        markLanguagePackDownloaded(key)
    }

    func learningTrackDisplayName(_ code: String) -> String {
        if let track = bootstrap.learningTracks.first(where: { $0.code == code }) {
            return track.label
        }
        if let track = LearningTrack(rawValue: code) {
            return track.displayName
        }
        switch code {
        case "zh_to_en": return uiText("英语", "English")
        case "en_to_zh": return uiText("中文", "Chinese")
        case "en_to_ja": return uiText("日语", "Japanese")
        case "en_to_ko": return uiText("韩语", "Korean")
        case "en_to_es": return uiText("西班牙语", "Spanish")
        case "bilingual": return uiText("双语", "Bilingual")
        default: return displayTitle(for: languagePair(for: code).target, fallback: code)
        }
    }

    func enqueueLanguagePackIfNeeded(for learningTrackCode: String, presentDownloader: Bool = true) async {
        let pair = languagePair(for: learningTrackCode)
        let key = languagePackKey(source: pair.source, target: pair.target)
        if downloadedLanguagePackKeys.contains(key) { return }
        var queue = pendingLanguagePackKeys
        if !queue.contains(key) {
            queue.append(key)
            AppScopedDefaults().set(queue, forKey: AppDefaultKey.pendingLanguagePackQueue)
        }
        languagePackDownloadState = .downloading(
            name: languagePackName(for: learningTrackCode),
            totalSize: "256MB",
            speed: uiText("等待网络", "Waiting for network"),
            progress: 0,
            remainingTime: uiText("计算中", "Calculating")
        )
        if presentDownloader {
            isShowingLanguagePackDownloader = true
        }
    }

    func startPendingLanguagePackDownloads() async {
        let activeKeys = pendingLanguagePackKeys
        guard !activeKeys.isEmpty else { return }
        isShowingLanguagePackDownloader = true
        for key in activeKeys {
            await simulateLanguagePackDownload(for: key)
        }
    }

    func retryLanguagePackDownload() {
        Task { await startPendingLanguagePackDownloads() }
    }

    var isLanguagePackDownloaderDismissDisabled: Bool {
        switch languagePackDownloadState {
        case .downloading, .completed:
            return true
        case .idle, .failed:
            return false
        }
    }

    private var pendingLanguagePackKeys: [String] {
        AppScopedDefaults().stringArray(forKey: AppDefaultKey.pendingLanguagePackQueue) ?? []
    }

    private var downloadedLanguagePackKeys: Set<String> {
        Set(AppScopedDefaults().stringArray(forKey: AppDefaultKey.downloadedLanguagePacks) ?? [])
    }

    private func languagePackKey(source: String, target: String) -> String {
        "\(source)->\(target)"
    }

    private func markLanguagePackDownloaded(_ key: String) {
        var downloaded = downloadedLanguagePackKeys
        downloaded.insert(key)
        AppScopedDefaults().set(Array(downloaded), forKey: AppDefaultKey.downloadedLanguagePacks)
        let queue = pendingLanguagePackKeys.filter { $0 != key }
        AppScopedDefaults().set(queue, forKey: AppDefaultKey.pendingLanguagePackQueue)
    }

    private func languagePackName(for learningTrackCode: String) -> String {
        switch learningTrackCode {
        case "en_to_zh": return uiText("中文基础语言包", "Chinese Foundation Language Pack")
        case "bilingual": return uiText("双语基础语言包", "Bilingual Foundation Language Pack")
        default: return uiText("英语基础语言包", "English Foundation Language Pack")
        }
    }

    private func languagePackName(forKey key: String) -> String {
        if key.contains("->en") { return uiText("英语基础语言包", "English Foundation Language Pack") }
        if key.contains("->zh") { return uiText("中文基础语言包", "Chinese Foundation Language Pack") }
        return uiText("学习语言包", "Learning Language Pack")
    }

    private func simulateLanguagePackDownload(for key: String) async {
        for step in 0...10 {
            let progress = Double(step) / 10
            languagePackDownloadState = .downloading(
                name: languagePackName(forKey: key),
                totalSize: "256MB",
                speed: progress < 1 ? "3.2MB/s" : "0MB/s",
                progress: progress,
                remainingTime: progress < 1 ? uiText("约 \(max(1, 8 - step)) 秒", "About \(max(1, 8 - step))s") : uiText("即将完成", "Finishing")
            )
            try? await Task.sleep(nanoseconds: 180_000_000)
        }
        markLanguagePackDownloaded(key)
        languagePackDownloadState = .completed(name: languagePackName(forKey: key))
        isShowingLanguagePackDownloader = true
    }

    func submitFeedback(category: String, content: String, contactEmail: String? = nil) async -> Bool {
        do {
            _ = try await backendClient.submitFeedback(
                category: category,
                content: content,
                contactEmail: contactEmail,
                authMode: authMode,
                traceId: nil
            )
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func saveReviewCard(text: String, supportHint: String?, sourceLanguageCode overrideSourceLanguageCode: String? = nil, targetLanguageCode overrideTargetLanguageCode: String? = nil) async -> Bool {
        guard hasAuthenticatedSession else { return false }
        let localCardLimit = accountState?.entitlement.localCardLimit ?? 20
        let activeCards = await reviewCardRepository.loadAll()
        let activeCardCount = activeCards.filter { !$0.isDeleted }.count
        if activeCardCount >= localCardLimit {
            errorMessage = uiText("当前句卡数量已达上限，请先升级套餐。", "You have reached the current review card limit. Please upgrade first.")
            isShowingPaywall = true
            return false
        }
        let childId = selectedChild.id
        let learningTrackCode = userPreference?.readingTrackCode ?? selectedChild.learningTrackCode
        let deviceId = localDeviceId
        let resolvedSourceLanguageCode = overrideSourceLanguageCode ?? sourceLanguageCode
        let resolvedTargetLanguageCode = overrideTargetLanguageCode ?? targetLanguageCode
        let learningLanguageCode = learningLanguageCode(for: selectedChild)
        guard await reviewCardRepository.createLocalCard(
            deviceId: deviceId,
            childId: childId,
            learningTrackCode: learningTrackCode,
            learningLanguageCode: learningLanguageCode,
            text: text,
            supportHint: supportHint,
            sourceLanguageCode: resolvedSourceLanguageCode,
            targetLanguageCode: resolvedTargetLanguageCode
        ) != nil else {
            errorMessage = uiText("句卡保存失败，请稍后重试。", "Failed to save the card. Please try again.")
            return false
        }
        await refreshReviewData()
        recentSavedReviewCards = await reviewCardRepository.loadRecentCards(childId: selectedChild.id, limit: 3)
        await synchronizeNow(reason: "save_review_card")
        await refreshHomeData()
        return true
    }

    func recordReviewResult(cardId: String, resultLevel: String) async {
        guard hasAuthenticatedSession else { return }
        guard let updatedCard = await reviewCardRepository.applyReviewResult(cardId: cardId, resultLevel: resultLevel) else { return }
        await reviewEventRepository.append(
            childId: updatedCard.childId ?? selectedChild.id,
            cardId: updatedCard.id,
            eventType: "completed",
            resultLevel: resultLevel
        )
        await refreshReviewData()
        recentSavedReviewCards = await reviewCardRepository.loadRecentCards(childId: selectedChild.id, limit: 3)
        await synchronizeNow(reason: "record_review_result")
        await refreshHomeData()
    }

    /// 删除当前句卡并刷新所有依赖句卡数量/掌握数量的首页与伴读节奏数据。
    func deleteReviewCard(cardId: String) async -> Bool {
        guard hasAuthenticatedSession else {
            errorMessage = uiText("请先登录后再删除句卡。", "Please sign in before deleting a card.")
            return false
        }
        guard await reviewCardRepository.softDeleteLocal(cardId: cardId) else {
            errorMessage = uiText("句卡删除失败，请稍后重试。", "Failed to delete the card. Please try again.")
            return false
        }
        await refreshReviewData()
        recentSavedReviewCards = await reviewCardRepository.loadRecentCards(childId: selectedChild.id, limit: 3)
        await refreshReadingAchievementStats()
        // 本地列表和关键统计先完成，保证句卡列表返回足够快；云端同步和首页汇总随后补齐。
        Task { @MainActor in
            await synchronizeNow(reason: "delete_review_card")
            await refreshHomeData()
            await refreshSyncRuntimeState()
        }
        return true
    }

    func recordLocalOcrUsage(source: String) async -> Bool {
        let idempotencyKey = UUID().uuidString.lowercased()
        // 先在本地兜底 +1，避免后端返回延迟导致首页/家长中心看不到扣减的错觉。
        // 成功路径下会再用 alignLocalQuotaUsageWithEntitlement() 与服务端取 max 对齐。
        ensureLocalDeviceAccountStateIfNeeded()
        recordLocalQuotaUsage(kind: "local_ocr", source: source, amount: 1)
        guard hasAuthenticatedSession else { return true }
        do {
            accountState = try await backendClient.recordQuotaUsage(
                kind: "local_ocr",
                source: source,
                languageCode: sourceLanguageCode,
                amount: 1,
                idempotencyKey: idempotencyKey
            )
            cacheAccountStateForToday()
            alignLocalQuotaUsageWithEntitlement()
            if source == "cloud_ocr" {
                await refreshCloudUsageStateFromBackend()
            }
            await syncEntitlementRecordsFromBackend(reason: "local_ocr_usage", reportError: false)
            await refreshActiveEntitlementUsageSummaries()
            // 扣减成功后刷新首页汇总，确保家长中心/首页立即展示最新剩余次数。
            await refreshHomeData()
            return true
        } catch {
            if source == "cloud_ocr" {
                await refreshCloudUsageStateFromBackend()
            }
            await refreshActiveEntitlementUsageSummaries()
            await refreshHomeData()
            try? await backendClient.reportDeviceEvent(
                eventType: "local_ocr_record_failed",
                appVersion: BackendClient.defaultAppVersion(),
                buildNumber: BackendClient.defaultBuildNumber(),
                locale: interfaceLocaleCode,
                payload: [
                    "source": source,
                    "quotaDate": AppClock.dateOnly(from: Date()),
                    "localUsed": "\(localOcrUsedToday)",
                    "idempotencyKey": idempotencyKey,
                    "error": error.localizedDescription
                ]
            )
            return false
        }
    }

    func validateLocalOcrQuotaBeforeRecognition(requiredAmount: Int = 1) async -> LocalOcrQuotaValidation {
        let required = max(requiredAmount, 1)
        refreshLocalQuotaCaches()
        if hasAuthenticatedSession {
            do {
                accountState = try await backendClient.fetchAccountState()
                cacheAccountStateForToday()
                await refreshCloudUsageStateFromBackend()
            } catch {
                loadCachedAccountStateForOfflineUse()
            }
        } else {
            loadCachedAccountStateForOfflineUse()
        }
        resetCachedQuotaIfCrossedDay()

        let maxLimit = max(accountState?.quota.localOcrLimit ?? fallbackLocalOcrLimit, 0)
        let serverUsed = max(accountState?.quota.localOcrUsed ?? 0, 0)
        let usedAmount = max(serverUsed, localOcrUsedToday)
        let remainingAmount = max(maxLimit - usedAmount, 0)

        guard remainingAmount >= required else {
            let message = uiText(
                "图片识别权益不足：本次识别需要 \(required) 次，当前剩余 \(remainingAmount) 次。请让家长在家长区查看权益并补充次数后再识别。",
                "Not enough image recognition quota: this recognition needs \(required), and you have \(remainingAmount) left. Please ask a parent to review benefits and add quota from the parent area before recognizing."
            )
            return LocalOcrQuotaValidation(
                isAllowed: false,
                requiredAmount: required,
                maxLimit: maxLimit,
                usedAmount: usedAmount,
                remainingAmount: remainingAmount,
                message: message
            )
        }

        return LocalOcrQuotaValidation(
            isAllowed: true,
            requiredAmount: required,
            maxLimit: maxLimit,
            usedAmount: usedAmount,
            remainingAmount: remainingAmount,
            message: nil
        )
    }

    func recordLearningEvent(sourcePage: String) async {
        guard hasAuthenticatedSession else { return }
        let didRecord = await learningEventRepository.append(childId: selectedChild.id, sourcePage: sourcePage)
        guard didRecord else { return }
        await synchronizeNow(reason: "record_learning_event")
        await refreshHomeData()
    }

    func purchaseInternal(product: CreditProduct) async -> Bool {
        guard authMode == .formalAccount else {
            errorMessage = uiText("请先使用家长账号登录，再发起购买。", "Please sign in with the parent account before purchasing.")
            return false
        }
        guard billingHealth?.purchaseAvailable == true else {
            errorMessage = billingHealth?.unavailableMessage ?? uiText("暂时无法购买", "Purchasing is temporarily unavailable.")
            return false
        }
        do {
            // 用户点击购买后再次向后端实时校验当前购买项状态，防止数据库临时禁购后继续付款。
            let permission = try await backendClient.verifyPurchasePermission(productCode: product.productCode, locale: interfaceLocaleCode)
            guard permission.allowed else {
                errorMessage = permission.message?.isEmpty == false ? permission.message : uiText("服务暂不可用", "Service is temporarily unavailable.")
                await refreshBillingSurface()
                return false
            }
            let requestId = UUID().uuidString.lowercased()
            _ = try await backendClient.submitInternalPurchase(
                productCode: product.productCode,
                purchaseTicket: requestId,
                idempotencyKey: requestId,
                locale: interfaceLocaleCode
            )
            await refreshAllData()
            await syncEntitlementRecordsFromBackend(reason: "purchase", reportError: false)
            await refreshActiveEntitlementUsageSummaries()
            entitlementRecordPage = await entitlementRecordRepository.loadPage(
                accountId: storageScope,
                serviceType: nil,
                page: 1,
                pageSize: 20
            )
            alignLocalQuotaUsageWithEntitlement()
            await refreshHomeData()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func restorePurchases() async {
        errorMessage = uiText("当前版本不支持外部恢复购买，请由家长在购买页重新发起内部购买。", "This version does not support external restore purchases. Please repurchase from the parent page.")
    }

    func acceptPrivacyConsent() async {
        let locale = interfaceLocaleCode
        deviceInfoService.hasAcceptedPrivacyConsent = true
        if hasAuthenticatedSession {
            userPreference = await userPreferenceRepository.updateLocal(
                uiLocale: locale,
                sourceLanguageCode: sourceLanguageCode,
                targetLanguageCode: targetLanguageCode,
                readingTrackCode: effectiveLearningTrackCode,
            )
            interfaceLocaleCode = locale
            AppScopedDefaults().set(locale, forKey: AppDefaultKey.interfaceLocale)
            await synchronizeNow(reason: "privacy_accept")
            await refreshPreferences()
        }
    }

    func completeAppleSignIn(
        authorizationCode: String,
        identityToken: String,
        state: String,
        nonce: String,
        givenName: String? = nil,
        familyName: String? = nil
    ) async -> Bool {
        let selectedInterfaceLocale = interfaceLocaleCode
        do {
            _ = try await backendClient.exchangeApplePreview(
                identityToken: identityToken,
                authorizationCode: authorizationCode,
                givenName: givenName,
                familyName: familyName,
                state: state,
                expectedState: state,
                nonce: nonce,
                expectedNonce: nonce,
                redirectURI: nil
            )
            return await completeAuthenticatedSignIn(locale: selectedInterfaceLocale, reason: "apple_login")
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    #if DEBUG
    func completeDevelopmentSignIn() async -> Bool {
        let selectedInterfaceLocale = interfaceLocaleCode
        do {
            _ = try await backendClient.createDevelopmentSession()
            return await completeAuthenticatedSignIn(locale: selectedInterfaceLocale, reason: "dev_login")
        } catch {
            let defaults = AppScopedDefaults()
            if let cachedData = defaults.data(forKey: AppDefaultKey.accountStateCache),
               let cachedState = try? JSONDecoder().decode(AccountState.self, from: cachedData) {
                accountState = cachedState
            } else {
                accountState = makeOfflineDevelopmentAccountState()
                cacheAccountStateForToday()
            }
            if authSession == nil {
                authSession = makeOfflineDevelopmentSession(accountId: accountState?.accountId ?? "dev-offline")
            }
            setOnboardingInterfaceLocale(selectedInterfaceLocale)
            userPreference = await userPreferenceRepository.updateLocal(
                uiLocale: selectedInterfaceLocale,
                sourceLanguageCode: sourceLanguageCode,
                targetLanguageCode: targetLanguageCode,
                readingTrackCode: effectiveLearningTrackCode,
            )
            await loadLocalCaches()
            alignLocalQuotaUsageWithEntitlement()
            await performAuthenticatedStartupRefresh(reason: "dev_login_offline")
            return true
        }
    }
    #endif

    private func completeAuthenticatedSignIn(locale selectedInterfaceLocale: String, reason: String) async -> Bool {
        authSession = backendClient.currentSession
        guard authSession != nil else {
            errorMessage = uiText("登录成功响应未包含有效会话，请稍后重试。", "The sign-in response did not include a valid session. Please try again.")
            return false
        }
        setOnboardingInterfaceLocale(selectedInterfaceLocale)
        userPreference = await userPreferenceRepository.updateLocal(
            uiLocale: selectedInterfaceLocale,
            sourceLanguageCode: sourceLanguageCode,
            targetLanguageCode: targetLanguageCode,
            readingTrackCode: effectiveLearningTrackCode,
        )
        await loadLocalCaches()
        await reportSuccessfulLoginDeviceEvent(locale: selectedInterfaceLocale, reason: reason)
        await performAuthenticatedStartupRefresh(reason: reason)
        announcementOverlayRefreshToken = UUID()
        return true
    }

    private func reportSuccessfulLoginDeviceEvent(locale: String, reason: String) async {
        // 默认登录链路不再上报设备事件。低敏诊断必须由家长在设置中单独开启后再调用。
    }

    func signOut() async {
        cancelAllUsageTicks()
        cancelEntitlementSyncLoop()
        let currentScope = storageScope
        if hasAuthenticatedSession {
            do {
                _ = try await backendClient.logout()
            } catch {
                errorMessage = error.localizedDescription
            }
        }
        await disconnectForSignOut(scope: currentScope)
        authSession = nil
        accountState = nil
        homeSummary = nil
        subscriptionStatus = nil
        entitlementRecordPage = nil
        announcementOverlayRefreshToken = UUID()
    }

    func requestDeletionCode(email: String) async -> EmailVerificationTicketReceipt? {
        do {
            return try await backendClient.requestDeletionCode(email: email)
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    func redeemCompensationCode(_ compensationCode: String) async -> CompensationRedeemReceipt? {
        let normalizedCode = normalizedCompensationCode(compensationCode)
        guard !hasRedeemedCompensationCodeLocally(normalizedCode) else {
            errorMessage = nil
            return nil
        }
        do {
            let receipt = try await backendClient.redeemCompensationCode(normalizedCode)
            if receipt.status == "applied" {
                markCompensationCodeRedeemedLocally(normalizedCode)
            }
            if let refreshedAccount = receipt.accountState {
                accountState = refreshedAccount
                cacheAccountStateForToday()
            } else if hasAuthenticatedSession {
                accountState = try? await backendClient.fetchAccountState()
                if accountState != nil {
                    cacheAccountStateForToday()
                }
            }
            await refreshCloudUsageStateFromBackend()
            await syncEntitlementRecordsFromBackend(reason: "compensation_redeem", reportError: false)
            await entitlementRecordRepository.upsertCompensationRedeemReceipt(
                accountId: storageScope,
                receipt: receipt,
                syncedAt: AppClock.nowString()
            )
            await refreshActiveEntitlementUsageSummaries()
            entitlementRecordPage = await entitlementRecordRepository.loadPage(
                accountId: storageScope,
                serviceType: nil,
                page: 1,
                pageSize: 20
            )
            alignLocalQuotaUsageWithEntitlement()
            await refreshHomeData()
            return receipt
        } catch {
            errorMessage = nil
            return nil
        }
    }

    private func normalizedCompensationCode(_ raw: String) -> String {
        let compact = raw.uppercased().filter { $0.isLetter || $0.isNumber }
        guard compact.count == 17, compact.hasPrefix("PP") else {
            return raw.uppercased().trimmingCharacters(in: .whitespacesAndNewlines)
        }
        let body = String(compact.dropFirst(2))
        return "PP-\(body.prefix(5))-\(body.dropFirst(5).prefix(5))-\(body.suffix(5))"
    }

    private func hasRedeemedCompensationCodeLocally(_ code: String) -> Bool {
        let codes = AppScopedDefaults().stringArray(forKey: AppDefaultKey.redeemedCompensationCodes) ?? []
        return codes.contains(code)
    }

    private func markCompensationCodeRedeemedLocally(_ code: String) {
        let defaults = AppScopedDefaults()
        var codes = defaults.stringArray(forKey: AppDefaultKey.redeemedCompensationCodes) ?? []
        guard !codes.contains(code) else { return }
        codes.append(code)
        if codes.count > 500 {
            codes = Array(codes.suffix(500))
        }
        defaults.set(codes, forKey: AppDefaultKey.redeemedCompensationCodes)
    }

    func confirmDeletion(code: String, email: String) async -> Bool {
        cancelAllUsageTicks()
        cancelEntitlementSyncLoop()
        let currentScope = storageScope
        do {
            let receipt = try await backendClient.confirmDeletionByCode(code: code, email: email)
            lastDeletionReceipt = receipt
            await clearLocalData(scope: currentScope)
            weeklyReportCache.clear(accountId: currentScope)
            authSession = nil
            accountState = nil
            homeSummary = nil
            entitlementRecordPage = nil
            children = []
            reviewCards = []
            familyUsageSummary = nil
            childUsageSummaries = [:]
            userPreference = nil
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    @discardableResult
    func playLocalTts(text: String, language: String, rate: Float = 1.0, preferCloud: Bool = false) async -> Bool {
        let normalizedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedText.isEmpty else { return false }
        guard hasLocalTtsQuotaAvailableLocally() else {
            isLocalTtsQuotaExhausted = true
            localTtsQuotaExhaustedMessage = uiText(
                "今日发音权益已用完，暂时无法继续发音。请让家长在家长区查看权益并补充次数后再发音。",
                "Today's pronunciation quota is used up, so playback is temporarily unavailable. Please ask a parent to review benefits and add quota from the parent area before playing audio again."
            )
            return false
        }
        if preferCloud {
            // 首发合规策略：云端朗读入口关闭，忽略该偏好并继续走设备端朗读。
        }

        // 设备端朗读优先立即启动播放，再在后台补记账，避免把首响卡在网络往返上。
        let didStartPlayback = await ttsService.speakFromSwiftConcurrency(text, language: language, rate: rate)
        guard didStartPlayback else {
            errorMessage = ttsService.lastTTSError?.localizedDescription
                ?? uiText("本地发音启动失败，请检查设备音量或稍后重试。", "Local speech failed to start. Please check device volume or try again shortly.")
            return false
        }
        recordLocalQuotaUsage(kind: "local_tts", source: "device_tts", amount: 1)
        return true
    }

    @discardableResult
    func playSourceLocalTts(text: String, rate: Float = 1.0, preferCloud: Bool = false) async -> Bool {
        await playLocalTts(text: text, language: sourceLocalTtsLanguageCode, rate: rate, preferCloud: preferCloud)
    }

    @discardableResult
    func playTargetLocalTts(text: String, rate: Float = 1.0, preferCloud: Bool = false) async -> Bool {
        await playLocalTts(text: text, language: targetLocalTtsLanguageCode, rate: rate, preferCloud: preferCloud)
    }

    func preloadLocalTtsResourcesForCurrentContext(reason: String) async {
        await Task.yield()
        let languages = localTtsPreloadLanguageCodes()
        guard !languages.isEmpty else { return }
        await ttsService.preloadDeviceSpeechEngine(languageCodes: languages)
        markLocalTtsLanguagesPreloaded(languages)
        _ = reason
    }

    func preloadLocalTtsResources(languageCodes: [String], reason: String) async {
        let languages = uniqueLocalTtsLanguageCodes(languageCodes.map { localTtsVoiceCode(for: $0) })
        guard !languages.isEmpty else { return }
        await Task.yield()
        await ttsService.preloadDeviceSpeechEngine(languageCodes: languages)
        markLocalTtsLanguagesPreloaded(languages)
        _ = reason
    }

    func isLocalTtsResourceReady(languageCode: String) -> Bool {
        ttsService.hasCachedDeviceVoice(for: localTtsVoiceCode(for: languageCode))
    }

    private func scheduleLocalTtsResourcePreload(reason: String) {
        let languages = localTtsPreloadLanguageCodesToLoad()
        guard !languages.isEmpty else { return }
        localTtsResourcePreloadTask?.cancel()
        localTtsResourcePreloadTask = Task { @MainActor [weak self] in
            guard let self, !Task.isCancelled else { return }
            await Task.yield()
            await self.ttsService.preloadDeviceSpeechEngine(languageCodes: languages)
            self.markLocalTtsLanguagesPreloaded(languages)
            try? await Task.sleep(nanoseconds: 80_000_000)
            if !Task.isCancelled {
                _ = reason
            }
        }
    }

    private func preloadLocalTtsResourcesNow(reason: String) {
        let languages = localTtsPreloadLanguageCodesToLoad()
        guard !languages.isEmpty else { return }
        ttsService.preloadDeviceVoices(languageCodes: languages)
        markLocalTtsLanguagesPreloaded(languages)
        _ = reason
    }

    private func localTtsPreloadLanguageCodes() -> [String] {
        let activeChildren = children.filter { !$0.isDeleted }
        let trackCodes = activeChildren.isEmpty
            ? [effectiveLearningTrackCode]
            : activeChildren.map(\.learningTrackCode)
        var languageCodes: [String] = []
        for trackCode in trackCodes {
            let pair = languagePair(for: trackCode)
            languageCodes.append(localTtsVoiceCode(for: pair.source))
            languageCodes.append(localTtsVoiceCode(for: pair.target))
        }
        languageCodes.append(localTtsVoiceCode(for: interfaceLocaleCode))
        return uniqueLocalTtsLanguageCodes(languageCodes)
    }

    private func localTtsPreloadLanguageCodesToLoad() -> [String] {
        localTtsPreloadLanguageCodes().filter { language in
            !preloadedLocalTtsLanguageCodes.contains(language) &&
            !ttsService.hasCachedDeviceVoice(for: language)
        }
    }

    private func markLocalTtsLanguagesPreloaded(_ languageCodes: [String]) {
        for languageCode in uniqueLocalTtsLanguageCodes(languageCodes) {
            preloadedLocalTtsLanguageCodes.insert(languageCode)
        }
    }

    private func uniqueLocalTtsLanguageCodes(_ languageCodes: [String]) -> [String] {
        var result: [String] = []
        for code in languageCodes {
            let normalized = code.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !normalized.isEmpty, !result.contains(normalized) else { continue }
            result.append(normalized)
        }
        return result
    }

    @discardableResult
    func playLocalTts(text: String, languageCode: String, rate: Float = 1.0, preferCloud: Bool = false) async -> Bool {
        await playLocalTts(text: text, language: localTtsLanguageCode(for: languageCode), rate: rate, preferCloud: preferCloud)
    }

    private func hasLocalTtsQuotaAvailableLocally() -> Bool {
        refreshLocalQuotaCaches()
        // 跨天场景：即使后端拉取失败，也要把 cached 权益按新一天回满，
        // 防止用 accountState.quota.localTtsUsed（昨日值）误判为仍有残量或已用完。
        resetCachedQuotaIfCrossedDay()
        let serverUsed = accountState?.quota.localTtsUsed ?? 0
        let serverLimit = accountState?.quota.localTtsLimit ?? fallbackLocalTtsLimit
        let effectiveUsed = max(serverUsed, localTtsUsedToday)
        return effectiveUsed < serverLimit
    }

    private func alignLocalQuotaUsageWithEntitlement() {
        refreshLocalQuotaCaches()
        // 本地日期变更时先把 cached accountState.quota 回滚到今天，
        // 再与本地使用量对齐，确保后端下线跨天后权益能自动回满。
        resetCachedQuotaIfCrossedDay()
        let today = AppClock.dateOnly(from: Date())
        let defaults = AppScopedDefaults()
        if let captureQuota = accountState?.quota {
            let activeCreditUsed = activeEntitlementUsageSummaries["local_ocr"]?.usedCount ?? 0
            let used = max(captureQuota.localOcrUsed, localOcrUsedToday, activeCreditUsed)
            localOcrUsedToday = used
            accountState = accountStateWithLocalUsage(localOcrUsed: used, localTtsUsed: nil)
            defaults.set(today, forKey: AppDefaultKey.localOcrQuotaDate)
            defaults.set(used, forKey: AppDefaultKey.localOcrUsed)
        }
        if let localTtsQuota = accountState?.quota {
            let activeCreditUsed = activeEntitlementUsageSummaries["local_tts"]?.usedCount ?? 0
            let used = max(localTtsQuota.localTtsUsed, localTtsUsedToday, activeCreditUsed)
            localTtsUsedToday = used
            accountState = accountStateWithLocalUsage(localOcrUsed: nil, localTtsUsed: used)
            defaults.set(today, forKey: AppDefaultKey.localTtsQuotaDate)
            defaults.set(used, forKey: AppDefaultKey.localTtsUsed)
        }
        cacheAccountStateForToday()
    }

    private func loadCachedAccountStateForOfflineUse() {
        guard hasAuthenticatedSession else {
            ensureLocalDeviceAccountState()
            return
        }
        let defaults = AppScopedDefaults()
        if accountState == nil,
           let cachedData = defaults.data(forKey: AppDefaultKey.accountStateCache),
           let cachedState = try? JSONDecoder().decode(AccountState.self, from: cachedData) {
            accountState = cachedState
        }
        // 后端不可用且已跨天时，用本地日期把缓存权益回满到今天，
        // 保障免费会员每日 OCR/朗读次数能正常发放。
        resetCachedQuotaIfCrossedDay()
        alignLocalQuotaUsageWithEntitlement()
    }

    /// 当缓存的 accountState.quota.quotaDate 不是今天（本地时区）时，
    /// 按新一天把 localOcrUsed/localTtsUsed 清零、quotaDate 更新为今天，
    /// limit 保留不变（保留后端已授予的购买次数）。
    /// 该方法是客户端本地驱动的日清逻辑，完全不依赖后端是否可用。
    private func resetCachedQuotaIfCrossedDay() {
        guard let state = accountState else { return }
        let today = AppClock.dateOnly(from: Date())
        let cachedDate = state.quota.quotaDate
        guard !cachedDate.isEmpty, cachedDate != today else { return }
        let localOcrLimit = max(state.quota.localOcrLimit, 0)
        let localTtsLimit = max(state.quota.localTtsLimit, 0)
        accountState = AccountState(
            accountId: state.accountId,
            signInProvider: state.signInProvider,
            entitlement: state.entitlement,
            quota: DailyQuota(
                quotaDate: today,
                localOcrLimit: localOcrLimit,
                localOcrUsed: 0,
                localOcrRemaining: localOcrLimit,
                localTtsLimit: localTtsLimit,
                localTtsUsed: 0,
                localTtsRemaining: localTtsLimit
            )
        )
        // 同步回写缓存，避免下次冷启动又读到昨天的 quotaDate。
        cacheAccountStateForToday()
    }

    private func recordLocalQuotaUsage(kind: String, source: String, amount: Int) {
        refreshLocalQuotaCaches()
        let defaults = AppScopedDefaults()
        let today = AppClock.dateOnly(from: Date())
        if kind == "local_ocr" {
            let used = localOcrUsedToday + amount
            localOcrUsedToday = used
            defaults.set(today, forKey: AppDefaultKey.localOcrQuotaDate)
            defaults.set(used, forKey: AppDefaultKey.localOcrUsed)
            accountState = accountStateWithLocalUsage(localOcrUsed: used, localTtsUsed: nil)
            if source == "cloud_ocr" {
                defaults.set(today, forKey: AppDefaultKey.cloudOcrQuotaDate)
                defaults.set(defaults.integer(forKey: AppDefaultKey.cloudOcrUsed) + amount, forKey: AppDefaultKey.cloudOcrUsed)
            }
        } else if kind == "local_tts" {
            let used = localTtsUsedToday + amount
            localTtsUsedToday = used
            defaults.set(today, forKey: AppDefaultKey.localTtsQuotaDate)
            defaults.set(used, forKey: AppDefaultKey.localTtsUsed)
            accountState = accountStateWithLocalUsage(localOcrUsed: nil, localTtsUsed: used)
            if source == "cloud_tts" {
                defaults.set(today, forKey: AppDefaultKey.cloudTtsQuotaDate)
                defaults.set(defaults.integer(forKey: AppDefaultKey.cloudTtsUsed) + amount, forKey: AppDefaultKey.cloudTtsUsed)
            }
        }
        // 同步写入“按日使用次数历史”，供周报等本地统计使用。
        // 仅在本地 UserDefaults 记录，后端下线时也会照常累加。
        appendDailyQuotaUsageHistory(kind: kind, source: source, amount: amount, date: today)
        cacheAccountStateForToday()
        Task {
            await self.applyLocalDailyGrantUsageToEntitlementCache(kind: kind, amount: amount, quotaDate: today)
            await self.syncDailyQuotaGrantRecords(days: self.hasAuthenticatedSession ? 60 : 1)
            await self.refreshActiveEntitlementUsageSummaries()
        }
    }

    private func applyLocalDailyGrantUsageToEntitlementCache(kind: String, amount: Int, quotaDate: String) async {
        guard amount > 0, let entitlement = accountState?.entitlement else { return }
        let serviceType: String
        let fallbackTotalCount: Int
        if kind == "local_ocr" {
            serviceType = "local_ocr"
            fallbackTotalCount = max(entitlement.dailyLocalOcrLimit, 0)
        } else if kind == "local_tts" {
            serviceType = "local_tts"
            fallbackTotalCount = max(entitlement.dailyLocalTtsLimit, 0)
        } else {
            return
        }
        guard fallbackTotalCount > 0 else { return }
        let window = dailyQuotaGrantWindow(quotaDate: quotaDate)
        await entitlementRecordRepository.incrementCachedDailyGrantUsage(
            accountId: storageScope,
            serviceType: serviceType,
            amount: amount,
            quotaDate: quotaDate,
            fallbackTotalCount: fallbackTotalCount,
            acquiredAt: window.acquiredAt,
            expiresAt: window.expiresAt,
            syncedAt: AppClock.nowString()
        )
    }

    // MARK: - 每日使用次数历史（供周报等场景使用）

    /// 每日使用次数历史的本地保留天数。超出部分会被丢弃，避免 UserDefaults 膨胀。
    private var dailyQuotaUsageHistoryRetentionDays: Int { 90 }

    /// 返回最近 `days` 天的使用次数历史（按日期升序，覆盖今天）。
    /// 即使某天没有使用记录，也会补齐为 0，便于周报等页面直接渲染。
    /// - Parameter days: 需要返回的天数，默认 7，会被限制在 [1, 保留天数] 区间。
    func dailyQuotaUsageHistory(days: Int = 7) -> [DailyQuotaUsageRecord] {
        let safeDays = min(max(days, 1), dailyQuotaUsageHistoryRetentionDays)
        let stored = loadDailyQuotaUsageHistoryRaw()
        let byDate = Dictionary(uniqueKeysWithValues: stored.map { ($0.usageDate, $0) })
        let calendar = Calendar(identifier: .gregorian)
        let today = Date()
        return (0..<safeDays).compactMap { offset -> DailyQuotaUsageRecord? in
            guard let date = calendar.date(byAdding: .day, value: -(safeDays - 1) + offset, to: today) else { return nil }
            let key = AppClock.dateOnly(from: date)
            return byDate[key] ?? DailyQuotaUsageRecord(usageDate: key)
        }
    }

    /// 累加当日的使用次数到本地历史，并保留最近 `dailyQuotaUsageHistoryRetentionDays` 天。
    private func appendDailyQuotaUsageHistory(kind: String, source: String, amount: Int, date: String) {
        guard amount > 0, !date.isEmpty else { return }
        var records = loadDailyQuotaUsageHistoryRaw()
        var record = records.first(where: { $0.usageDate == date }) ?? DailyQuotaUsageRecord(usageDate: date)
        if kind == "local_ocr" {
            if source == "cloud_ocr" {
                record.cloudOcrCount += amount
            } else {
                record.localOcrCount += amount
            }
        } else if kind == "local_tts" {
            if source == "cloud_tts" {
                record.cloudTtsCount += amount
            } else {
                record.localTtsCount += amount
            }
        } else {
            return
        }
        records.removeAll { $0.usageDate == date }
        records.append(record)
        saveDailyQuotaUsageHistory(trimDailyQuotaUsageHistory(records))
    }

    /// 从 UserDefaults 读取历史记录，按日期升序返回。
    private func loadDailyQuotaUsageHistoryRaw() -> [DailyQuotaUsageRecord] {
        let defaults = AppScopedDefaults()
        guard let data = defaults.data(forKey: AppDefaultKey.dailyQuotaUsageHistory),
              let decoded = try? JSONDecoder().decode([DailyQuotaUsageRecord].self, from: data) else {
            return []
        }
        return decoded.sorted { $0.usageDate < $1.usageDate }
    }

    /// 将历史记录持久化到 UserDefaults。
    private func saveDailyQuotaUsageHistory(_ records: [DailyQuotaUsageRecord]) {
        let defaults = AppScopedDefaults()
        guard let data = try? JSONEncoder().encode(records.sorted { $0.usageDate < $1.usageDate }) else { return }
        defaults.set(data, forKey: AppDefaultKey.dailyQuotaUsageHistory)
    }

    /// 保留最近 `dailyQuotaUsageHistoryRetentionDays` 天数据，早于截止日期的记录会被丢弃。
    private func trimDailyQuotaUsageHistory(_ records: [DailyQuotaUsageRecord]) -> [DailyQuotaUsageRecord] {
        let calendar = Calendar(identifier: .gregorian)
        guard let cutoff = calendar.date(byAdding: .day, value: -(dailyQuotaUsageHistoryRetentionDays - 1), to: Date()) else {
            return records
        }
        let cutoffKey = AppClock.dateOnly(from: cutoff)
        return records.filter { $0.usageDate >= cutoffKey }
    }

    private func accountStateWithLocalUsage(localOcrUsed: Int?, localTtsUsed: Int?) -> AccountState? {
        guard let accountState else { return nil }
        let quota = accountState.quota
        let nextCaptureUsed = min(max(localOcrUsed ?? quota.localOcrUsed, 0), max(quota.localOcrLimit, 0))
        let nextSpeechUsed = min(max(localTtsUsed ?? quota.localTtsUsed, 0), max(quota.localTtsLimit, 0))
        return AccountState(
            accountId: accountState.accountId,
            signInProvider: accountState.signInProvider,
            entitlement: accountState.entitlement,
            quota: DailyQuota(
                // 若 quotaDate 为空或不是今天（后端下线跨天场景），
                // 使用当前本地日期覆盖，确保 UI 和持久化的 quotaDate 保持最新。
                quotaDate: {
                    let today = AppClock.dateOnly(from: Date())
                    return (quota.quotaDate.isEmpty || quota.quotaDate != today) ? today : quota.quotaDate
                }(),
                localOcrLimit: quota.localOcrLimit,
                localOcrUsed: nextCaptureUsed,
                localOcrRemaining: max(quota.localOcrLimit - nextCaptureUsed, 0),
                localTtsLimit: quota.localTtsLimit,
                localTtsUsed: nextSpeechUsed,
                localTtsRemaining: max(quota.localTtsLimit - nextSpeechUsed, 0)
            )
        )
    }

    private func cacheAccountStateForToday() {
        guard hasAuthenticatedSession else { return }
        let defaults = AppScopedDefaults()
        if let encoded = try? JSONEncoder().encode(accountState) {
            defaults.set(encoded, forKey: AppDefaultKey.accountStateCache)
            defaults.set(AppClock.dateOnly(from: Date()), forKey: AppDefaultKey.accountStateLastFetchDate)
        }
    }

    private var fallbackLocalTtsLimit: Int {
        10
    }

    private var fallbackLocalOcrLimit: Int {
        5
    }

    private func ensureLocalDeviceAccountStateIfNeeded() {
        guard !hasAuthenticatedSession else { return }
        ensureLocalDeviceAccountState()
    }

    private func ensureLocalDeviceAccountState() {
        guard !hasAuthenticatedSession else { return }
        refreshLocalQuotaCaches()
        accountState = makeLocalDeviceAccountState()
        cacheAccountStateForToday()
    }

    private func makeLocalDeviceAccountState() -> AccountState {
        let today = AppClock.dateOnly(from: Date())
        let localOcrLimit = fallbackLocalOcrLimit
        let localTtsLimit = fallbackLocalTtsLimit
        let localOcrUsed = min(max(localOcrUsedToday, 0), localOcrLimit)
        let localTtsUsed = min(max(localTtsUsedToday, 0), localTtsLimit)
        let entitlement = AccountEntitlement(
            planCode: "free",
            planName: "免费版",
            dailyLocalOcrLimit: localOcrLimit,
            dailyLocalTtsLimit: localTtsLimit,
            childLimit: 1,
            localCardLimit: 20,
            childCount: children.count,
            remainingChildSlots: max(1 - children.count, 0),
            advancedVoiceEnabled: false,
            premiumActive: false,
            validUntil: nil,
            authoritative: false,
            multiChildEnabled: false,
            dailyPlanScope: "single_child",
            weeklyReportScope: "child",
            weeklyReportHistoryWeeks: 0,
            historyEnabled: false,
            serverVerified: false,
            verificationSource: "device_local_daily",
            accessProof: BackendAccessProof(
                appCode: AppIdentity.appCode,
                userId: "signed-out",
                plan: "free",
                status: "device_local",
                serverTime: nil,
                policy: "device_local_daily_quota",
                allowed: true
            )
        )
        return AccountState(
            accountId: "signed-out",
            signInProvider: "device_local",
            entitlement: entitlement,
            quota: DailyQuota(
                quotaDate: today,
                localOcrLimit: localOcrLimit,
                localOcrUsed: localOcrUsed,
                localOcrRemaining: max(localOcrLimit - localOcrUsed, 0),
                localTtsLimit: localTtsLimit,
                localTtsUsed: localTtsUsed,
                localTtsRemaining: max(localTtsLimit - localTtsUsed, 0)
            )
        )
    }

    private func refreshCloudUsageStateFromBackend() async {
        do {
            cloudUsageState = try await backendClient.fetchCloudUsageState()
        } catch {
            // 后端不可用时保留上一次成功拉取到的云端权益快照，
            // 避免把 trialLimit/purchasedCredits 重置为 0/fallback 小默认值。
            // 本地设备 OCR / TTS 仍可通过 localOcrUsedToday / localTtsUsedToday 正常扣减。
            refreshLocalQuotaCaches()
            guard cloudUsageState == nil else {
                return
            }
            // 首次启动且缓存为空时，沿用已缓存的 accountState.quota（来自持久化的 AccountState 缓存），
            // 仅在完全没有任何数据时退回到保守的默认值，确保不会覆盖后端已授予的购买次数。
            let defaults = AppScopedDefaults()
            let localOcrLimit = accountState?.quota.localOcrLimit ?? 0
            let localTtsLimit = accountState?.quota.localTtsLimit ?? fallbackLocalTtsLimit
            cloudUsageState = CloudUsageState(
                ocr: CloudQuotaState(
                    serviceType: "cloud_ocr",
                    trialLimit: localOcrLimit,
                    trialUsed: min(defaults.integer(forKey: AppDefaultKey.cloudOcrUsed), max(localOcrLimit, 0)),
                    purchasedCredits: 0,
                    purchasedUsed: 0,
                    remainingCount: max(localOcrLimit - defaults.integer(forKey: AppDefaultKey.cloudOcrUsed), 0),
                    updatedAt: AppClock.nowString()
                ),
                tts: CloudQuotaState(
                    serviceType: "cloud_tts",
                    trialLimit: localTtsLimit,
                    trialUsed: min(defaults.integer(forKey: AppDefaultKey.cloudTtsUsed), max(localTtsLimit, 0)),
                    purchasedCredits: 0,
                    purchasedUsed: 0,
                    remainingCount: max(localTtsLimit - defaults.integer(forKey: AppDefaultKey.cloudTtsUsed), 0),
                    updatedAt: AppClock.nowString()
                )
            )
        }
    }

    #if DEBUG
    private func makeOfflineDevelopmentAccountState() -> AccountState {
        let today = AppClock.dateOnly(from: Date())
        let entitlement = AccountEntitlement(
            planCode: "dev_local",
            planName: "开发本地模拟会员",
            dailyLocalOcrLimit: 50,
            dailyLocalTtsLimit: 100,
            childLimit: 5,
            localCardLimit: 500,
            childCount: children.count,
            remainingChildSlots: max(5 - children.count, 0),
            advancedVoiceEnabled: true,
            premiumActive: true,
            validUntil: nil,
            authoritative: false,
            multiChildEnabled: true,
            dailyPlanScope: "family",
            weeklyReportScope: "family",
            weeklyReportHistoryWeeks: 12,
            historyEnabled: true,
            serverVerified: false,
            verificationSource: "debug_local_fallback",
            accessProof: BackendAccessProof(
                appCode: AppIdentity.appCode,
                userId: "dev-offline",
                plan: "dev_local",
                status: "offline_fallback",
                serverTime: nil,
                policy: "debug_local_only",
                allowed: true
            )
        )
        return AccountState(
            accountId: "dev-offline",
            signInProvider: "dev_login_offline",
            entitlement: entitlement,
            quota: DailyQuota(
                quotaDate: today,
                localOcrLimit: 100,
                localOcrUsed: localOcrUsedToday,
                localOcrRemaining: max(100 - localOcrUsedToday, 0),
                localTtsLimit: 100,
                localTtsUsed: localTtsUsedToday,
                localTtsRemaining: max(100 - localTtsUsedToday, 0)
            )
        )
    }

    private func makeOfflineDevelopmentSession(accountId: String) -> StoredAuthSession {
        StoredAuthSession(
            accessToken: "debug-offline-token",
            tokenType: "Bearer",
            sessionId: "debug-offline-session",
            sessionType: "dev_login_offline",
            expiresAt: "2099-12-31T23:59:59Z",
            account: AuthAccount(
                accountId: accountId,
                signInProvider: "dev_login_offline",
                formalAccount: true,
                email: nil,
                identityVerificationState: "offline_fallback"
            )
        )
    }
    #endif

    private func reportLocalTtsUsage(language: String, preferCloud: Bool) async -> Bool {
        let usageSource = preferCloud ? "cloud_tts" : "device_tts"
        // 先在本地扣减兜底 +1（与 OCR 扣减保持一致），避免后端响应延迟/后端掉线/用户中途切页导致
        // 句卡列表→复习页点击喇叭后“朗读剩余次数没有立即变化”的观感；后端成功后会由 alignLocalQuotaUsageWithEntitlement() 以 max 方式与服务端对齐，不会重复计数。
        recordLocalQuotaUsage(kind: "local_tts", source: usageSource, amount: 1)
        guard preferCloud else {
            // 当前设备端发音只做本地播放和本地使用历史统计，不调用后端发音次数接口。
            return true
        }
        guard hasAuthenticatedSession else {
            // 未登录时仅在本地扣减，保证设备端朗读仍能使用。
            return false
        }
        let idempotencyKey = UUID().uuidString.lowercased()
        do {
            accountState = try await backendClient.recordQuotaUsage(
                kind: "local_tts",
                source: usageSource,
                languageCode: language,
                amount: 1,
                idempotencyKey: idempotencyKey
            )
            cacheAccountStateForToday()
            if preferCloud {
                await refreshCloudUsageStateFromBackend()
            }
            alignLocalQuotaUsageWithEntitlement()
            await syncEntitlementRecordsFromBackend(reason: "local_tts_usage", reportError: false)
            await refreshActiveEntitlementUsageSummaries()
            return true
        } catch {
            // 本地扣减已在入口处完成，此处不再重复 +1，避免后端暂时不可用时多扣一次。
            if preferCloud {
                await refreshCloudUsageStateFromBackend()
            }
            await refreshActiveEntitlementUsageSummaries()
            try? await backendClient.reportDeviceEvent(
                eventType: "local_tts_failed",
                appVersion: BackendClient.defaultAppVersion(),
                buildNumber: BackendClient.defaultBuildNumber(),
                locale: interfaceLocaleCode,
                payload: [
                    "language": language,
                    "mode": preferCloud ? "cloud" : "device",
                    "quotaDate": AppClock.dateOnly(from: Date()),
                    "localUsed": "\(localTtsUsedToday)",
                    "idempotencyKey": idempotencyKey,
                    "error": error.localizedDescription
                ]
            )
            return true
        }
    }

    func synthesizeCloudSpeech(text: String, language: String, rate: Float = 1.0) async -> CloudSpeechReceipt? {
        // 首发合规策略：不把儿童正文发往业务后端云 TTS 代理。
        // 后续开放时必须先完成家长 direct notice、granular consent、reservation/capability 链路。
        errorMessage = uiText(
            "云端朗读暂未开放，请使用设备端朗读。",
            "Cloud speech is not enabled yet. Please use on-device speech."
        )
        return nil
    }

    func updateLanguagePreferences(interfaceLocale: String, readingTrackCode: String) async {
        interfaceLocaleCode = interfaceLocale
        AppScopedDefaults().set(interfaceLocale, forKey: AppDefaultKey.interfaceLocale)
        let pair = languagePair(for: readingTrackCode)
        userPreference = await userPreferenceRepository.updateLocal(
            uiLocale: interfaceLocale,
            sourceLanguageCode: pair.source,
            targetLanguageCode: pair.target,
            readingTrackCode: readingTrackCode,
        )
        await synchronizeNow(reason: "update_language_preferences")
        await refreshParentData()
        await refreshHomeData()
        await refreshLanguagePackQueueIfNeeded()
    }


    func updateInterfaceLocale(_ locale: String) async {
        interfaceLocaleCode = locale
        AppScopedDefaults().set(locale, forKey: AppDefaultKey.interfaceLocale)
        userPreference = await userPreferenceRepository.updateLocal(
            uiLocale: locale,
            sourceLanguageCode: sourceLanguageCode,
            targetLanguageCode: targetLanguageCode,
            readingTrackCode: effectiveLearningTrackCode,
        )
        await synchronizeNow(reason: "update_interface_locale")
        await refreshParentData()
        await refreshHomeData()
        await refreshLanguagePackQueueIfNeeded()
    }


    func startUsageSession(sessionUuid: String, sourcePage: String) async {
        guard hasAuthenticatedSession else { return }
        let childId = selectedChild.id
        guard !childId.isEmpty else { return }
        _ = await usageSessionRepository.startSession(
            childId: childId,
            sessionId: sessionUuid,
            sourcePage: sourcePage,
            clientPlatform: "ios",
            deviceModel: nil
        )
        if sourcePage == "app_foreground" {
            _ = try? await backendClient.startUsageSession(
                childId: childId,
                sessionUuid: sessionUuid,
                sourcePage: sourcePage,
                clientPlatform: "ios",
                deviceModel: nil
            )
        }
        await refreshParentData()
        await refreshSyncRuntimeState()
        scheduleUsageTick(for: sessionUuid)
    }

    func endUsageSession(sessionUuid: String) async {
        guard hasAuthenticatedSession else { return }
        cancelUsageTick(for: sessionUuid)
        _ = await usageSessionRepository.endSession(sessionId: sessionUuid)
        if sessionUuid.hasPrefix("app-") {
            _ = try? await backendClient.endUsageSession(sessionUuid: sessionUuid)
        }
        await refreshParentData()
        await refreshReadingAchievementStats()
        await synchronizeNow(reason: "usage_session_end")
    }

    func startAppForegroundUsageSessionIfNeeded() async {
        guard hasAuthenticatedSession, appForegroundUsageSessionId == nil else { return }
        guard !selectedChild.id.isEmpty else { return }
        let sessionId = "app-\(UUID().uuidString.lowercased())"
        appForegroundUsageSessionId = sessionId
        await startUsageSession(sessionUuid: sessionId, sourcePage: "app_foreground")
    }

    func endAppForegroundUsageSessionIfNeeded() async {
        guard let sessionId = appForegroundUsageSessionId else { return }
        appForegroundUsageSessionId = nil
        await endUsageSession(sessionUuid: sessionId)
    }

    private func scheduleUsageTick(for sessionUuid: String) {
        usageTickTasks[sessionUuid]?.cancel()
        usageTickTasks[sessionUuid] = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(60))
                guard !Task.isCancelled, self.hasAuthenticatedSession else { break }
                _ = await self.usageSessionRepository.tickSession(sessionId: sessionUuid)
                await self.refreshParentData()
                await self.synchronizeNow(reason: "usage_session_tick")
            }
        }
    }

    private func cancelUsageTick(for sessionUuid: String) {
        usageTickTasks[sessionUuid]?.cancel()
        usageTickTasks[sessionUuid] = nil
    }

    private func synchronizeNow(reason: String) async {
        await refreshSyncRuntimeState()
    }

    private func scheduleEntitlementSyncLoop() {
        entitlementSyncTask?.cancel()
        guard hasAuthenticatedSession else { return }
        entitlementSyncTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(3600))
                guard !Task.isCancelled, self.hasAuthenticatedSession else { break }
                await self.performFullEntitlementSync(reason: "timer")
            }
        }
    }

    private func cancelEntitlementSyncLoop() {
        entitlementSyncTask?.cancel()
        entitlementSyncTask = nil
    }

    func performFullEntitlementSync(reason: String) async {
        guard hasAuthenticatedSession else { return }
        await refreshAccountState(force: true)
        await refreshSubscriptionStatus()
        await refreshBillingSurface()
        await refreshCloudUsageStateFromBackend()
        await syncEntitlementRecordsFromBackend(reason: reason, reportError: false)
        await refreshHomeData()
        await synchronizeNow(reason: reason)
    }

    func entitlementDisplaySummary(serviceType: String) -> EntitlementUsageSummary {
        let normalizedServiceType = serviceType.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if normalizedServiceType == "local_tts" || normalizedServiceType == "tts" || normalizedServiceType == "cloud_tts" {
            let summaryKey = normalizedServiceType == "cloud_tts" ? "cloud_tts" : "local_tts"
            let activeCredit = activeEntitlementUsageSummaries[summaryKey] ?? .empty(serviceType: summaryKey)
            if normalizedServiceType == "cloud_tts" {
                return activeCredit
            }
            let dailyLimit = max(accountState?.entitlement.dailyLocalTtsLimit ?? 0, 0)
            let total = max(accountState?.quota.localTtsLimit ?? 0, dailyLimit + max(activeCredit.totalCount, 0), 0)
            let used = min(max(accountState?.quota.localTtsUsed ?? 0, localTtsUsedToday, activeCredit.usedCount, 0), max(total, 0))
            return EntitlementUsageSummary(
                serviceType: "local_tts",
                totalCount: total,
                usedCount: used,
                remainingCount: max(total - used, 0)
            )
        }

        if normalizedServiceType == "cloud_ocr" {
            return activeEntitlementUsageSummaries["cloud_ocr"] ?? .empty(serviceType: "cloud_ocr")
        }

        let activeCredit = activeEntitlementUsageSummaries["local_ocr"] ?? .empty(serviceType: "local_ocr")
        let dailyLimit = max(accountState?.entitlement.dailyLocalOcrLimit ?? 0, 0)
        let total = max(accountState?.quota.localOcrLimit ?? 0, dailyLimit + max(activeCredit.totalCount, 0), 0)
        let used = min(max(accountState?.quota.localOcrUsed ?? 0, localOcrUsedToday, activeCredit.usedCount, 0), max(total, 0))
        return EntitlementUsageSummary(
            serviceType: "local_ocr",
            totalCount: total,
            usedCount: used,
            remainingCount: max(total - used, 0)
        )
    }

    private func syncEntitlementRecordsIfNeeded(reason: String) async {
        guard hasAuthenticatedSession else { return }
        let defaults = AppScopedDefaults()
        let today = AppClock.dateOnly(from: Date())
        let accountId = storageScope
        entitlementRecordsLastSyncedAt = defaults.string(forKey: entitlementRecordSyncAtKey(accountId: accountId))
        guard defaults.string(forKey: entitlementRecordSyncDateKey(accountId: accountId)) != today else { return }
        await syncEntitlementRecordsFromBackend(reason: reason, reportError: false)
    }

    private func syncEntitlementRecordsFromBackend(reason: String, reportError: Bool) async {
        guard hasAuthenticatedSession else { return }
        let accountId = storageScope
        isEntitlementRecordSyncing = true
        defer { isEntitlementRecordSyncing = false }

        do {
            var allRecords: [EntitlementRecord] = []
            var page = 1
            let pageSize = 100
            var hasMore = true
            while hasMore && page <= 10 {
                let response = try await backendClient.fetchEntitlementRecords(page: page, pageSize: pageSize)
                allRecords.append(contentsOf: response.records)
                hasMore = response.hasMore
                page += 1
            }
            let syncedAt = AppClock.nowString()
            await entitlementRecordRepository.replaceAll(accountId: accountId, records: allRecords, syncedAt: syncedAt)
            await syncDailyQuotaGrantRecords(days: 60)
            await refreshActiveEntitlementUsageSummaries()
            let defaults = AppScopedDefaults()
            defaults.set(AppClock.dateOnly(from: Date()), forKey: entitlementRecordSyncDateKey(accountId: accountId))
            defaults.set(syncedAt, forKey: entitlementRecordSyncAtKey(accountId: accountId))
            entitlementRecordsLastSyncedAt = syncedAt
        } catch {
            if reportError {
                errorMessage = localizedErrorMessage(error)
            }
        }
    }

    private func refreshActiveEntitlementUsageSummaries() async {
        guard hasAuthenticatedSession else {
            activeEntitlementUsageSummaries = [:]
            return
        }
        let now = AppClock.nowString()
        let localOcrSummary = await entitlementRecordRepository.loadActiveCreditSummary(
            accountId: storageScope,
            serviceType: "local_ocr",
            now: now
        )
        let localTtsSummary = await entitlementRecordRepository.loadActiveCreditSummary(
            accountId: storageScope,
            serviceType: "local_tts",
            now: now
        )
        let cloudOcrSummary = await entitlementRecordRepository.loadActiveCreditSummary(
            accountId: storageScope,
            serviceType: "cloud_ocr",
            now: now
        )
        let cloudTtsSummary = await entitlementRecordRepository.loadActiveCreditSummary(
            accountId: storageScope,
            serviceType: "cloud_tts",
            now: now
        )
        activeEntitlementUsageSummaries = [
            localOcrSummary.serviceType: localOcrSummary,
            localTtsSummary.serviceType: localTtsSummary,
            cloudOcrSummary.serviceType: cloudOcrSummary,
            cloudTtsSummary.serviceType: cloudTtsSummary
        ]
    }

    private func entitlementRecordSyncDateKey(accountId: String) -> String {
        "\(AppDefaultKey.entitlementRecordsLastSyncDate).\(accountId)"
    }

    private func entitlementRecordSyncAtKey(accountId: String) -> String {
        "\(AppDefaultKey.entitlementRecordsLastSyncAt).\(accountId)"
    }

    private func syncDailyQuotaGrantRecords(days: Int = 60) async {
        guard let entitlement = accountState?.entitlement else { return }
        let safeDays = min(max(days, 1), dailyQuotaUsageHistoryRetentionDays)
        let syncedAt = AppClock.nowString()
        let localOcrLimit = max(entitlement.dailyLocalOcrLimit, 0)
        let localTtsLimit = max(entitlement.dailyLocalTtsLimit, 0)
        for record in dailyQuotaUsageHistory(days: safeDays) {
            let window = dailyQuotaGrantWindow(quotaDate: record.usageDate)
            if localOcrLimit > 0 {
                let hasBackendDailyGift = await entitlementRecordRepository.hasAuthoritativeDailyGift(
                    accountId: storageScope,
                    serviceType: "local_ocr",
                    quotaDate: record.usageDate
                )
                if !hasBackendDailyGift {
                    await entitlementRecordRepository.upsertDailyGrant(
                        accountId: storageScope,
                        serviceType: "local_ocr",
                        totalCount: localOcrLimit,
                        usedCount: min(max(record.localOcrTotalCount, 0), localOcrLimit),
                        quotaDate: record.usageDate,
                        acquiredAt: window.acquiredAt,
                        expiresAt: window.expiresAt,
                        syncedAt: syncedAt
                    )
                }
            }
            if localTtsLimit > 0 {
                let hasBackendDailyGift = await entitlementRecordRepository.hasAuthoritativeDailyGift(
                    accountId: storageScope,
                    serviceType: "local_tts",
                    quotaDate: record.usageDate
                )
                if !hasBackendDailyGift {
                    await entitlementRecordRepository.upsertDailyGrant(
                        accountId: storageScope,
                        serviceType: "local_tts",
                        totalCount: localTtsLimit,
                        usedCount: min(max(record.localTtsTotalCount, 0), localTtsLimit),
                        quotaDate: record.usageDate,
                        acquiredAt: window.acquiredAt,
                        expiresAt: window.expiresAt,
                        syncedAt: syncedAt
                    )
                }
            }
        }
    }

    private func dailyQuotaGrantWindow(quotaDate: String) -> (acquiredAt: String, expiresAt: String) {
        let parts = quotaDate.split(separator: "-").compactMap { Int($0) }
        var components = DateComponents()
        let calendar = Calendar.current
        components.calendar = calendar
        components.timeZone = calendar.timeZone
        components.year = parts.count > 0 ? parts[0] : nil
        components.month = parts.count > 1 ? parts[1] : nil
        components.day = parts.count > 2 ? parts[2] : nil
        components.hour = 0
        components.minute = 0
        components.second = 0
        let start = calendar.date(from: components) ?? Date()
        let nextDay = calendar.date(byAdding: .day, value: 1, to: start) ?? start
        let end = calendar.date(byAdding: .second, value: -1, to: nextDay) ?? nextDay
        return (AppClock.string(from: start), AppClock.string(from: end))
    }

    private func refreshSyncRuntimeState() async {
    }

    private func syncPreferencesForLearningTrack(_ learningTrackCode: String) async {
        let pair = languagePair(for: learningTrackCode)
        userPreference = await userPreferenceRepository.updateLocal(
            uiLocale: interfaceLocaleCode,
            sourceLanguageCode: pair.source,
            targetLanguageCode: pair.target,
            readingTrackCode: learningTrackCode,
        )
    }

    func refreshLanguagePackQueueIfNeeded() async {
        var shouldPresentDownloader = false
        for child in children {
            let pair = languagePair(for: child.learningTrackCode)
            let key = languagePackKey(source: pair.source, target: pair.target)
            if !downloadedLanguagePackKeys.contains(key) {
                shouldPresentDownloader = true
                await enqueueLanguagePackIfNeeded(for: child.learningTrackCode, presentDownloader: false)
            }
        }
        if shouldPresentDownloader {
            await startPendingLanguagePackDownloads()
        }
    }

    private func loadLocalCaches() async {
        let preference = await userPreferenceRepository.current()
        userPreference = preference
        let localInterfaceLocale = AppScopedDefaults().string(forKey: AppDefaultKey.interfaceLocale) ?? interfaceLocaleCode
        entitlementRecordsLastSyncedAt = hasAuthenticatedSession
            ? AppScopedDefaults().string(forKey: entitlementRecordSyncAtKey(accountId: storageScope))
            : nil
        let resolvedInterfaceLocale = preference.persisted ? preference.uiLocale : localInterfaceLocale
        interfaceLocaleCode = resolvedInterfaceLocale
        AppScopedDefaults().set(resolvedInterfaceLocale, forKey: AppDefaultKey.interfaceLocale)
        refreshLocalQuotaCaches()
        children = await childRepository.loadActive()
        if let existing = children.first(where: { $0.id == selectedChild.id }) {
            selectedChild = existing
        } else if let first = children.first {
            selectedChild = first
        }
        reviewCards = await reviewCardRepository.loadDueCards()
        if hasAuthenticatedSession {
            await refreshActiveEntitlementUsageSummaries()
            entitlementRecordPage = await entitlementRecordRepository.loadPage(accountId: storageScope, serviceType: nil, page: 1, pageSize: 20)
        } else {
            ensureLocalDeviceAccountState()
            await syncDailyQuotaGrantRecords(days: 1)
            activeEntitlementUsageSummaries = [:]
            entitlementRecordsLastSyncedAt = AppClock.nowString()
            entitlementRecordPage = await entitlementRecordRepository.loadPage(accountId: storageScope, serviceType: nil, page: 1, pageSize: 20)
        }
        let usagePolicy = bootstrap.usagePolicy ?? .default
        familyUsageSummary = await usageSessionRepository.familySummary(
            children: children,
            recentSummaryDays: usagePolicy.safeRecentSummaryDays,
            retentionDays: usagePolicy.safeRetentionDays
        )
        childUsageSummaries = children.isEmpty ? [:] : await usageSessionRepository.childSummaries(
            children: children,
            recentSummaryDays: usagePolicy.safeRecentSummaryDays,
            retentionDays: usagePolicy.safeRetentionDays
        )
        await refreshSyncRuntimeState()
    }

    private func refreshLocalQuotaCaches() {
        let defaults = AppScopedDefaults()
        let today = AppClock.dateOnly(from: Date())
        if defaults.string(forKey: AppDefaultKey.localOcrQuotaDate) != today {
            defaults.set(today, forKey: AppDefaultKey.localOcrQuotaDate)
            defaults.set(0, forKey: AppDefaultKey.localOcrUsed)
            localOcrUsedToday = 0
        } else {
            localOcrUsedToday = defaults.integer(forKey: AppDefaultKey.localOcrUsed)
        }
        if defaults.string(forKey: AppDefaultKey.localTtsQuotaDate) != today {
            defaults.set(today, forKey: AppDefaultKey.localTtsQuotaDate)
            defaults.set(0, forKey: AppDefaultKey.localTtsUsed)
            localTtsUsedToday = 0
        } else {
            localTtsUsedToday = defaults.integer(forKey: AppDefaultKey.localTtsUsed)
        }
        if defaults.string(forKey: AppDefaultKey.cloudOcrQuotaDate) != today {
            defaults.set(today, forKey: AppDefaultKey.cloudOcrQuotaDate)
            defaults.set(0, forKey: AppDefaultKey.cloudOcrUsed)
        }
        if defaults.string(forKey: AppDefaultKey.cloudTtsQuotaDate) != today {
            defaults.set(today, forKey: AppDefaultKey.cloudTtsQuotaDate)
            defaults.set(0, forKey: AppDefaultKey.cloudTtsUsed)
        }
    }

    private func disconnectForSignOut(scope: String) async {
        // 退出登录仅断开同步通道 + 清理账号级的同步凭证。
        // 禁止在此将设备级持久化数据（onboarding / privacy / accountStateCache / quota.* /
        // dailyQuotaUsageHistory 等）清掉，这些数据仅在 App 卸载时随 sandbox 清除。
        cancelAllUsageTicks()
    }

    private func clearLocalData(scope: String) async {
        // 删除账号时清理账号级本地学习数据，权益记录缓存保留到 App 卸载。
        // 禁止在此移除以下设备级持久化 key，它们仅在用户卸载 App 时随 sandbox 清除：
        // - AppDefaultKey.dailyQuotaUsageHistory（每日使用次数历史，周报数据源）
        // - AppDefaultKey.accountStateCache / accountStateLastFetchDate（后端下线跨天回满用）
        // - AppDefaultKey.localCapture* / localSpeech* / cloudOcr* / cloudTts*（本地配额缓存）
        // - AppDefaultKey.onboarding* / privacy* / interfaceLocale 等设备级偏好
        cancelAllUsageTicks()
        await childRepository.clear()
        await reviewEventRepository.clear()
        await reviewCardRepository.clear()
        await learningEventRepository.clear()
        await usageSessionRepository.clear()
        await userPreferenceRepository.clear()
    }

    private func cancelAllUsageTicks() {
        usageTickTasks.values.forEach { $0.cancel() }
        usageTickTasks.removeAll()
    }

    private func languagePair(for learningTrackCode: String) -> (source: String, target: String) {
        let normalizedCode = learningTrackCode
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "-", with: "_")
            .lowercased()
        let parts = normalizedCode.components(separatedBy: "_to_")
        if parts.count == 2 {
            return (source: normalizedLanguageCode(parts[0]), target: normalizedLanguageCode(parts[1]))
        }
        switch learningTrackCode {
        case "en_to_zh":
            return (source: "en", target: "zh-Hans")
        case "bilingual":
            return (source: "en", target: "zh-Hans")
        default:
            return (source: "zh-Hans", target: "en")
        }
    }

    private func localTtsVoiceCode(for languageCode: String) -> String {
        let normalized = languageCode.lowercased()
        if normalized.hasPrefix("zh") { return "zh-CN" }
        if normalized.hasPrefix("en") { return "en-US" }
        if normalized.hasPrefix("ja") { return "ja-JP" }
        if normalized.hasPrefix("ko") { return "ko-KR" }
        if normalized.hasPrefix("es") { return "es-ES" }
        if normalized.hasPrefix("fr") { return "fr-FR" }
        if normalized.hasPrefix("de") { return "de-DE" }
        if normalized.hasPrefix("it") { return "it-IT" }
        if normalized.hasPrefix("pt") { return "pt-BR" }
        return languageCode
    }

    private func languageTitle(for languageCode: String, fallback: String) -> String {
        let normalized = languageCode.lowercased()
        if normalized.hasPrefix("zh") {
            return localizedText(
                zhHans: "中文",
                english: "Chinese",
                japanese: "中国語",
                korean: "중국어",
                spanish: "Chino"
            )
        }
        if normalized.hasPrefix("en") { return "English" }
        if normalized.hasPrefix("ja") {
            return localizedText(
                zhHans: "日语",
                english: "Japanese",
                japanese: "日本語",
                korean: "일본어",
                spanish: "Japonés"
            )
        }
        if normalized.hasPrefix("ko") {
            return localizedText(
                zhHans: "韩语",
                english: "Korean",
                japanese: "韓国語",
                korean: "한국어",
                spanish: "Coreano"
            )
        }
        if normalized.hasPrefix("es") {
            return localizedText(
                zhHans: "西班牙语",
                english: "Spanish",
                japanese: "スペイン語",
                korean: "스페인어",
                spanish: "Español"
            )
        }
        if normalized.hasPrefix("fr") {
            return localizedText(
                zhHans: "法语",
                english: "French",
                japanese: "フランス語",
                korean: "프랑스어",
                spanish: "Francés"
            )
        }
        if normalized.hasPrefix("de") {
            return localizedText(
                zhHans: "德语",
                english: "German",
                japanese: "ドイツ語",
                korean: "독일어",
                spanish: "Alemán"
            )
        }
        if normalized.hasPrefix("it") {
            return localizedText(
                zhHans: "意大利语",
                english: "Italian",
                japanese: "イタリア語",
                korean: "이탈리아어",
                spanish: "Italiano"
            )
        }
        if normalized.hasPrefix("pt") {
            return localizedText(
                zhHans: "葡萄牙语",
                english: "Portuguese",
                japanese: "ポルトガル語",
                korean: "포르투갈어",
                spanish: "Portugués"
            )
        }
        return fallback
    }

    private func normalizedLanguageCode(_ languageCode: String) -> String {
        let lowered = languageCode
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "_", with: "-")
            .lowercased()
        if lowered == "zh" || lowered.hasPrefix("zh-hans") || lowered.hasPrefix("zh-cn") {
            return "zh-Hans"
        }
        if lowered.hasPrefix("zh-hant") || lowered.hasPrefix("zh-tw") || lowered.hasPrefix("zh-hk") {
            return "zh-Hant"
        }
        if let primary = lowered.split(separator: "-").first {
            return String(primary)
        }
        return lowered
    }

    private func uniqueLanguageCodes(_ codes: [String]) -> [String] {
        var result: [String] = []
        for code in codes {
            let normalized = normalizedLanguageCode(code)
            let family = normalized.lowercased().split(separator: "-").first.map(String.init) ?? normalized.lowercased()
            guard !normalized.isEmpty, !result.contains(where: {
                let existingFamily = normalizedLanguageCode($0).lowercased().split(separator: "-").first.map(String.init) ?? normalizedLanguageCode($0).lowercased()
                return existingFamily == family
            }) else { continue }
            result.append(normalized)
        }
        return result
    }

    private func languageFamily(_ languageCode: String) -> String {
        let normalized = normalizedLanguageCode(languageCode).lowercased()
        if normalized.hasPrefix("zh") { return "zh" }
        if let primary = normalized.split(separator: "-").first {
            return String(primary)
        }
        return normalized
    }
}

enum Tab: String, CaseIterable {
    case home = "home"
    case readingPark = "readingPark"
    case parent = "parent"

    var icon: String {
        switch self {
        case .home:
            return "house.fill"
        case .readingPark:
            return "tent.fill"
        case .parent:
            return "person.2.fill"
        }
    }

    @MainActor
    func displayTitle(using appState: AppState) -> String {
        switch self {
        case .home:
            return appState.uiText("首页", "Home")
        case .readingPark:
            return appState.uiText("伴读乐园", "Learning Park")
        case .parent:
            return appState.uiText("家长中心", "Parents")
        }
    }
}

enum LanguagePackDownloadState: Equatable {
    case idle
    case downloading(name: String, totalSize: String, speed: String, progress: Double, remainingTime: String)
    case completed(name: String)
    case failed(name: String, message: String)
}

struct LanguagePackDownloadView: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: AppLayout.spacingXL) {
                Spacer(minLength: 20)
                Image(systemName: iconName)
                    .font(AppTypography.scaledFont(size: 52, weight: .semibold))
                    .foregroundColor(iconColor)
                    .frame(width: 96, height: 96)
                    .background(iconColor.opacity(0.12))
                    .clipShape(Circle())

                VStack(spacing: AppLayout.spacingM) {
                    Text(title)
                        .font(AppTypography.title2)
                        .foregroundColor(AppColors.textPrimary)
                        .multilineTextAlignment(.center)
                    Text(subtitle)
                        .font(AppTypography.body)
                        .foregroundColor(AppColors.textSecondary)
                        .multilineTextAlignment(.center)
                }

                MainCard {
                    VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                        HStack {
                            Text(packName)
                                .font(AppTypography.headline)
                                .foregroundColor(AppColors.textPrimary)
                            Spacer()
                            Text(progressText)
                                .font(AppTypography.headline)
                                .foregroundColor(AppColors.primary)
                        }
                        ProgressView(value: progress)
                            .tint(AppColors.primary)
                        infoRow(appState.uiText("文件总大小", "Total size"), totalSize)
                        infoRow(appState.uiText("当前下载速度", "Download speed"), speed)
                        infoRow(appState.uiText("预计剩余时间", "Time remaining"), remainingTime)
                        Text(statusText)
                            .font(AppTypography.footnote)
                            .foregroundColor(AppColors.textSecondary)
                    }
                }

                Spacer(minLength: 20)

                switch appState.languagePackDownloadState {
                case .completed:
                    PrimaryButton(title: appState.uiText("知道了", "Got it"), icon: "checkmark") {
                        dismiss()
                    }
                case .failed:
                    PrimaryButton(title: appState.uiText("重试下载", "Retry download"), icon: "arrow.clockwise") {
                        appState.retryLanguagePackDownload()
                    }
                default:
                    Text(appState.uiText("下载完成前请停留在此窗口。", "Please keep this window open until the download completes."))
                        .font(AppTypography.footnote)
                        .foregroundColor(AppColors.textTertiary)
                        .multilineTextAlignment(.center)
                }
            }
            .padding(AppLayout.paddingScreen)
            .adaptiveContentFrame(maxWidth: 520)
            .background(AppColors.background.ignoresSafeArea())
            .navigationTitle(appState.uiText("语言包下载", "Language Pack Download"))
            .navigationBarTitleDisplayMode(.inline)
            .task {
                await appState.startPendingLanguagePackDownloads()
            }
        }
    }

    private var iconName: String {
        switch appState.languagePackDownloadState {
        case .completed: return "checkmark.circle.fill"
        case .failed: return "exclamationmark.triangle.fill"
        default: return "arrow.down.circle.fill"
        }
    }

    private var iconColor: Color {
        switch appState.languagePackDownloadState {
        case .completed: return AppColors.success
        case .failed: return AppColors.error
        default: return AppColors.primary
        }
    }

    private var title: String {
        switch appState.languagePackDownloadState {
        case .completed: return appState.uiText("语言包准备就绪", "Language pack is ready")
        case .failed: return appState.uiText("语言包下载失败", "Language pack download failed")
        default: return appState.uiText("正在下载学习语言包", "Downloading learning language pack")
        }
    }

    private var subtitle: String {
        switch appState.languagePackDownloadState {
        case .completed:
            return appState.uiText("现在可以使用该学习语言了。", "You can now use this learning language.")
        case .failed:
            return appState.uiText("请检查网络后重试下载。", "Please check your network and retry the download.")
        default:
            return appState.uiText("请保持网络畅通，下载完成后会显示确认提示。", "Please keep your network connected. A confirmation will appear after the download completes.")
        }
    }

    private var packName: String {
        switch appState.languagePackDownloadState {
        case let .downloading(name, _, _, _, _), let .completed(name), let .failed(name, _): return name
        case .idle: return appState.uiText("学习语言包", "Learning Language Pack")
        }
    }

    private var progress: Double {
        if case let .downloading(_, _, _, progress, _) = appState.languagePackDownloadState { return progress }
        if case .completed = appState.languagePackDownloadState { return 1 }
        return 0
    }

    private var progressText: String {
        "\(Int(progress * 100))%"
    }

    private var totalSize: String {
        if case let .downloading(_, totalSize, _, _, _) = appState.languagePackDownloadState { return totalSize }
        return "256MB"
    }

    private var speed: String {
        if case let .downloading(_, _, speed, _, _) = appState.languagePackDownloadState { return speed }
        return "0MB/s"
    }

    private var remainingTime: String {
        if case let .downloading(_, _, _, _, remainingTime) = appState.languagePackDownloadState { return remainingTime }
        return appState.uiText("已完成", "Completed")
    }

    private var statusText: String {
        switch appState.languagePackDownloadState {
        case let .downloading(_, _, _, progress, _): return appState.uiText("下载中... 已完成 \(Int(progress * 100))%", "Downloading... \(Int(progress * 100))% completed")
        case .completed: return appState.uiText("语言包准备就绪", "Language pack is ready")
        case let .failed(_, message): return message
        case .idle: return appState.uiText("等待下载任务", "Waiting for download task")
        }
    }

    private func infoRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
                .font(AppTypography.footnote)
                .foregroundColor(AppColors.textSecondary)
            Spacer()
            Text(value)
                .font(AppTypography.footnote.weight(.semibold))
                .foregroundColor(AppColors.textPrimary)
        }
    }
}

struct ContentView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        Group {
            if !appState.hasCompletedOnboarding {
                OnboardingView()
            } else if !appState.isInitialDataReady {
                SplashView(onComplete: {})
            } else {
                MainTabView()
            }
        }
    }
}

struct MainTabView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        currentTabView
            .background(AppColors.background.ignoresSafeArea())
            .ignoresSafeArea(.keyboard, edges: .bottom)
            .contentMargins(.bottom, AppLayout.bottomNavigationContentInset, for: .scrollContent)
            .safeAreaInset(edge: .bottom, spacing: AppLayout.spacingS) {
                BottomTabBar(selectedTab: $appState.selectedTab)
                    .environmentObject(appState)
                    .frame(maxWidth: AppLayout.bottomNavigationMaxWidth)
                    .padding(.horizontal, AppLayout.spacingM)
                    .padding(.top, AppLayout.spacingXS)
                    .padding(.bottom, AppLayout.spacingS)
                    .frame(maxWidth: .infinity)
                    .background(.regularMaterial)
            }
            .ignoresSafeArea(.keyboard, edges: .bottom)
            .onChange(of: appState.selectedTab) { oldValue, newValue in
                if newValue == .parent && oldValue != .parent {
                    appState.refreshParentGate()
                }
            }
            .alert(appState.uiText("新的阅读周报已生成", "New weekly reading report is ready"), isPresented: $appState.isShowingWeeklyReportPrompt) {
                Button(appState.uiText("稍后", "Later"), role: .cancel) {}
                Button(appState.uiText("查看", "View")) {
                    appState.promptedWeeklyReportChildId = appState.latestUnreadWeeklyReport?.childId
                    appState.promptedWeeklyReportId = appState.latestUnreadWeeklyReport?.id
                    appState.isShowingPromptedWeeklyReport = true
                }
            } message: {
                Text(appState.latestUnreadWeeklyReport.map { "\($0.childName) · \($0.weekStart) ~ \($0.weekEnd)" } ?? appState.uiText("有一份未查看的阅读周报。", "There is an unread weekly reading report."))
            }
            .sheet(isPresented: $appState.isShowingPromptedWeeklyReport) {
                NavigationStack {
                    WeeklyReportView(
                        initialChildId: appState.promptedWeeklyReportChildId,
                        initialReportId: appState.promptedWeeklyReportId,
                        showsCloseButton: true
                    )
                        .environmentObject(appState)
                }
            }
    }

    @ViewBuilder
    private var currentTabView: some View {
        switch appState.selectedTab {
        case .home:
            HomeView()
        case .readingPark:
            ReadingParkView()
        case .parent:
            ParentAreaView()
        }
    }
}

private struct BottomTabBar: View {
    @EnvironmentObject var appState: AppState
    @Binding var selectedTab: Tab

    var body: some View {
        let idiom = UIDevice.current.userInterfaceIdiom
        let isLargeDevice = idiom == .pad || idiom == .mac
        let iconSize = AppLayout.bottomNavigationIconSize + (isLargeDevice ? 2 : 0)
        let textSize = AppLayout.bottomNavigationTextSize + (isLargeDevice ? 2 : 0)
        let itemHeight = AppLayout.bottomNavigationItemHeight + (isLargeDevice ? 4 : 0)

        HStack(spacing: AppLayout.bottomNavigationSpacing) {
            ForEach(Tab.allCases, id: \.self) { tab in
                Button {
                    if tab == .parent {
                        appState.refreshParentGate()
                    }
                    selectedTab = tab
                } label: {
                    VStack(spacing: AppLayout.spacingXS) {
                        Image(systemName: tab.icon)
                            .font(AppTypography.scaledFont(size: iconSize, weight: .semibold))
                        Text(tab.displayTitle(using: appState))
                            .font(AppTypography.scaledFont(size: textSize, weight: .medium))
                            .lineLimit(1)
                            .minimumScaleFactor(0.82)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: itemHeight)
                    .foregroundColor(selectedTab == tab ? AppColors.primary : AppColors.textSecondary)
                    .background(
                        RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous)
                            .fill(selectedTab == tab ? AppColors.primary.opacity(0.12) : Color.clear)
                    )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, AppLayout.spacingS)
        .padding(.vertical, 6)
        .frame(minHeight: AppLayout.bottomNavigationHeight)
        .background(Color.white)
        .overlay(
            RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous)
                .stroke(AppColors.border.opacity(0.7), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous))
        .shadow(color: Color.black.opacity(0.08), radius: 12, x: 0, y: 4)
    }
}
