import Foundation

/// 兼容旧命名的薄别名。
///
/// P3 逐步把底层实现迁到更通用的 `AppIdentity`，这里先保留旧类型名，避免一次性改 target
/// 或漏掉某个未纳入当前静态扫描的引用点。
enum PaipaiAppIdentity {
    static let appCode = AppIdentity.appCode
    static let fallbackBundleIdentifier = AppIdentity.fallbackBundleIdentifier
    static var bundleIdentifier: String { AppIdentity.bundleIdentifier }
    static let localDatabaseFilename = AppIdentity.localDatabaseFilename
    static let storageNamespace = AppIdentity.storageNamespace
    static let apiBaseURLInfoDictionaryKey = AppIdentity.apiBaseURLInfoDictionaryKey
}
