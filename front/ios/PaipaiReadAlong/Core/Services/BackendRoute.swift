import Foundation

/// 后端 App 级路由构造器。
///
/// 中文维护说明：
/// - 账号、Apple 登录、退出登录这几类接口必须在 URL 中显式携带 appCode。
/// - 这样未来多个 App 共用同一套后端时，不会误用另一个 App 的 Apple audience、session 或权益边界。
/// - 普通 reading 兼容接口仍保留 `/api/v1/...`，但鉴权边界一律走这里。
enum BackendRoute {
    struct AppScoped {
        let appCode: String

        fileprivate init(appCode: String) {
            self.appCode = BackendRoute.checkedAppCode(appCode)
        }

        // MARK: - App-scoped system auth routes

        /// 统一 system auth 路由前缀。
        ///
        /// `BackendClient` 会用它做边界校验，避免有人重新拼出不带 appCode 的旧兼容路径。
        var systemAuthPrefix: String {
            "/api/v1/system/auth/apps/\(appCode)/"
        }

        var authMe: String {
            systemAuthPrefix + "me"
        }

        var appleExchange: String {
            systemAuthPrefix + "apple/exchange"
        }

        var devSession: String {
            systemAuthPrefix + "sessions/dev"
        }

        var appleRefresh: String {
            systemAuthPrefix + "apple/refresh"
        }

        var appleRevoke: String {
            systemAuthPrefix + "apple/revoke"
        }

        var authLogout: String {
            systemAuthPrefix + "logout"
        }

        /// 多 App 通用版本更新策略接口。
        ///
        /// 中文维护说明：设置页只能通过该 appCode-scoped 路由获取最新版本和 App Store URL，
        /// 不允许在前端按产品名硬编码下载地址。
        var appVersionPolicy: String {
            "/api/v1/apps/\(appCode)/release/app-version"
        }
    }

    private static let legacyReadingAppCode = "reading"

    static func appScoped(appCode: String = AppIdentity.appCode) -> AppScoped {
        AppScoped(appCode: appCode)
    }

    private static func checkedAppCode(_ appCode: String) -> String {
        let trimmed = appCode.trimmingCharacters(in: .whitespacesAndNewlines)
        precondition(!trimmed.isEmpty, "Backend route appCode must not be blank.")
        precondition(
            trimmed != legacyReadingAppCode,
            "Legacy internal appCode 'reading' must not be used in backend routes. Use paipai_readingcompanion or a new appCode."
        )
        precondition(
            trimmed.range(of: #"^[A-Za-z0-9_\-]+$"#, options: .regularExpression) != nil,
            "Backend route appCode contains unsupported characters: \(trimmed)"
        )
        return trimmed
    }
}
