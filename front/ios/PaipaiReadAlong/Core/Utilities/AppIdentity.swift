import Foundation

/// App 级身份中心。
///
/// P3 这里先把名字从 `PaipaiAppIdentity` 提升为更可模板化的 `AppIdentity`，
/// 这样后续若接入第二个 App，可以直接替换实现或按 target 注入，而不是让所有底层
/// 存储和路由代码继续绑定产品名。
enum AppIdentity {
    /// 拍拍伴读在统一后端中的产品身份。
    ///
    /// 中文维护说明：
    /// - 这个值必须和后端 `apps/reading/app-definition.yml` 里的 `app.code` 保持一致。
    /// - 新增第二个 App 时，不要在业务代码里搜索替换字符串；应为新 target 提供自己的 AppIdentity。
    /// - UserDefaults、Keychain、本地数据库和 system auth 路由都会使用它做隔离边界。
    static let appCode = "paipai_readingcompanion"

    /// 发布前 Info.plist 应注入真实 bundle id；这里仅作为极端情况下的兜底展示值。
    /// 不能把它当作 Apple 登录 clientId / App Store bundleId 的权威来源。
    static let fallbackBundleIdentifier = "com.paipai.readalong"

    static var bundleIdentifier: String {
        Bundle.main.bundleIdentifier ?? fallbackBundleIdentifier
    }

    /// App-scoped local database filename to avoid cross-app cache reuse.
    static let localDatabaseFilename = "\(appCode)-local.sqlite"

    /// Shared prefix for local storage namespaces (UserDefaults / Keychain / cache metadata).
    static let storageNamespace = appCode

    /// Info.plist key used by this app target to inject the backend base URL.
    static let apiBaseURLInfoDictionaryKey = "PAIPAI_API_BASE_URL"
}
