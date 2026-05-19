import Foundation

/// 带 appCode 命名空间的 UserDefaults 包装器。
///
/// 中文维护说明：所有本地偏好都必须通过这里读写，禁止在业务代码中直接使用
/// `UserDefaults.standard.set(...)` 或裸 key。这样未来 App Group、共享 SDK、第二个 App
/// 复用同一套代码时，不会串 onboarding、隐私授权、语言或公告缓存状态。
struct AppScopedDefaults {
    private let namespace: String
    private let defaults: UserDefaults

    init(namespace: String = AppIdentity.appCode, defaults: UserDefaults = .standard) {
        self.namespace = namespace
        self.defaults = defaults
    }

    func key(_ rawKey: String) -> String {
        "\(namespace).\(rawKey)"
    }

    func bool(forKey rawKey: String) -> Bool {
        defaults.bool(forKey: key(rawKey))
    }

    func object(forKey rawKey: String) -> Any? {
        defaults.object(forKey: key(rawKey))
    }

    func string(forKey rawKey: String) -> String? {
        defaults.string(forKey: key(rawKey))
    }

    func stringArray(forKey rawKey: String) -> [String]? {
        defaults.stringArray(forKey: key(rawKey))
    }

    func data(forKey rawKey: String) -> Data? {
        defaults.data(forKey: key(rawKey))
    }

    func integer(forKey rawKey: String) -> Int {
        defaults.integer(forKey: key(rawKey))
    }

    func set(_ value: Bool, forKey rawKey: String) {
        defaults.set(value, forKey: key(rawKey))
    }

    func set(_ value: String?, forKey rawKey: String) {
        defaults.set(value, forKey: key(rawKey))
    }

    func set(_ value: Date, forKey rawKey: String) {
        defaults.set(value, forKey: key(rawKey))
    }

    func set(_ value: Int, forKey rawKey: String) {
        defaults.set(value, forKey: key(rawKey))
    }

    func set(_ value: Data, forKey rawKey: String) {
        defaults.set(value, forKey: key(rawKey))
    }

    func set(_ value: [String], forKey rawKey: String) {
        defaults.set(value, forKey: key(rawKey))
    }

    func removeObject(forKey rawKey: String) {
        defaults.removeObject(forKey: key(rawKey))
    }
}

enum AppDefaultKey {
    // MARK: - 设备级持久化（仅 App 卸载时随 sandbox 清除）
    //
    // 以下 key 属于“设备级上下文”，绑定到物理设备而非某个账号。
    // 无论用户退出登录、删除账号、或未来新增的任何“清除缓存”入口，
    // 都不得对它们调用 `removeObject` / `removePersistentDomain`。
    // 只有用户从系统中卸载 App 时，iOS 才会随 sandbox 一并删除。
    static let onboardingCompleted = "onboarding.completed"
    static let privacyPolicyAccepted = "privacy.policy.accepted"
    static let privacyConsentDate = "privacy.consent.date"
    static let deviceInfoCollectionAccepted = "privacy.device-info.accepted"
    static let interfaceLocale = "interface.locale"
    static let textSizeOption = "ui.text-size.option"
    static let announcementsCache = "announcements.cache"
    static let announcementsDismissed = "announcements.dismissed"
    static let announcementsPresentationState = "announcements.presentation-state"
    static let announcementsDailyPresentationState = "announcements.daily-presentation-state"
    static let localOcrPermissionRequested = "local_ocr.permission.requested"
    static let localOcrPermissionGranted = "local_ocr.permission.granted"
    static let localOcrPermissionDenied = "local_ocr.permission.denied"
    static let downloadedLanguagePacks = "language.pack.downloaded"
    static let pendingLanguagePackQueue = "language.pack.pending.queue"
    static let learningPlaybackSpeed = "learning.playback.speed"
    static let appVersionPolicyCache = "app.version.policy.cache"
    static let appVersionPolicyLastFetchDate = "app.version.policy.last-fetch-date"
    static let anonymousInstallationId = "anonymous.installation.id"
    static let redeemedCompensationCodes = "compensation.redeemed.codes"

    // MARK: - 权益缓存（仅 App 卸载时清除）
    //
    // 涉及账户权益与每日配额缓存，用于后端下线时的跨天回满、本地先扣减等容错逻辑。
    // 退出登录、删除账号等操作也不会清理这些 key，以保证切换账号后仍能恢复权益视图。
    static let accountStateCache = "account.state.cache"
    static let accountStateLastFetchDate = "account.state.last-fetch-date"
    static let localOcrQuotaDate = "quota.local.local_ocr.date"
    static let localOcrUsed = "quota.local.local_ocr.used"
    static let localTtsQuotaDate = "quota.local.local_tts.date"
    static let localTtsUsed = "quota.local.local_tts.used"
    static let cloudOcrQuotaDate = "quota.cloud.ocr.date"
    static let cloudOcrUsed = "quota.cloud.ocr.used"
    static let cloudTtsQuotaDate = "quota.cloud.tts.date"
    static let cloudTtsUsed = "quota.cloud.tts.used"
    static let entitlementRecordsLastSyncDate = "entitlement.records.last-sync-date"
    static let entitlementRecordsLastSyncAt = "entitlement.records.last-sync-at"

    /// 按日的 OCR / 语音朗读使用次数历史（JSON），用于周报等本地统计。
    ///
    /// 设备级持久化数据：**仅在用户卸载 App 时清除**。
    /// 退出登录、删除账号、清除缓存、切换账号等任何 App 内操作都不得对它调用 `removeObject`，
    /// 否则会导致历史周报统计数据丢失。
    static let dailyQuotaUsageHistory = "quota.daily.usage.history"
}
