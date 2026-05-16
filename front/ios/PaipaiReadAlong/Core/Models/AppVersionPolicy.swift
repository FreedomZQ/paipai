import Foundation

/// App 版本更新策略。
///
/// 中文维护说明：该模型只承载后端统一版本接口返回的展示策略；
/// App Store 下载地址必须来自后端配置，前端不得硬编码具体 App Store URL 或 Apple ID。
struct AppVersionPolicy: Codable {
    let appCode: String
    let appName: String
    let platform: String
    let currentVersion: String
    let currentBuild: String
    let latestVersion: String
    let latestBuild: String
    let minimumSupportedVersion: String
    let updateAvailable: Bool
    let severity: String
    let blocking: Bool
    let appStoreId: String
    let appStoreUrl: String
    let downloadUrl: String?
    let storeUrl: String?
    let updateUrl: String?
    let title: String
    let message: String
    let releaseNotes: [String]
    let ctaText: String
    let complianceNote: String?

    var resolvedStoreURLString: String? {
        [downloadUrl, updateUrl, storeUrl, appStoreUrl]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first { !$0.isEmpty }
    }

    var resolvedStoreURL: URL? {
        guard let raw = resolvedStoreURLString,
              let url = URL(string: raw),
              url.scheme?.lowercased() == "https",
              url.host?.lowercased().hasSuffix("apps.apple.com") == true else {
            return nil
        }
        return url
    }

    var hasConfiguredStoreURL: Bool {
        resolvedStoreURL != nil
    }

    static func localFallback(currentVersion: String = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.1.0",
                              currentBuild: String = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1") -> AppVersionPolicy {
        AppVersionPolicy(
            appCode: AppIdentity.appCode,
            appName: "拍拍伴读",
            platform: "ios",
            currentVersion: currentVersion,
            currentBuild: currentBuild,
            latestVersion: currentVersion,
            latestBuild: currentBuild,
            minimumSupportedVersion: currentVersion,
            updateAvailable: false,
            severity: "none",
            blocking: false,
            appStoreId: "",
            appStoreUrl: "",
            downloadUrl: nil,
            storeUrl: nil,
            updateUrl: nil,
            title: "",
            message: "",
            releaseNotes: [],
            ctaText: "",
            complianceNote: nil
        )
    }
}
