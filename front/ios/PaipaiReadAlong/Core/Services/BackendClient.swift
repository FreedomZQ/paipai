import Foundation

final class BackendClient {
    enum TransactionSource: String {
        case purchase
        case restore
    }

    enum BackendError: LocalizedError {
        case invalidResponse
        case authRequired
        case connectionUnavailable
        case server(code: String, message: String, traceId: String?)

        var errorDescription: String? {
            switch self {
            case .invalidResponse:
                return "服务器返回了无法识别的响应。"
            case .authRequired:
                return "请先登录，再查看会员权益和支付状态。"
            case .connectionUnavailable:
                return ""
            case let .server(_, message, _):
                return message
            }
        }

        var serverCode: String? {
            if case let .server(code, _, _) = self {
                return code
            }
            return nil
        }

        var traceId: String? {
            if case let .server(_, _, traceId) = self {
                return traceId
            }
            return nil
        }

        static func isConnectionFailure(_ error: Error) -> Bool {
            guard let urlError = error as? URLError else { return false }
            switch urlError.code {
            case .cannotFindHost,
                 .cannotConnectToHost,
                 .networkConnectionLost,
                 .dnsLookupFailed,
                 .notConnectedToInternet,
                 .timedOut,
                 .internationalRoamingOff,
                 .dataNotAllowed,
                 .secureConnectionFailed:
                return true
            default:
                return false
            }
        }
    }

    private let session: URLSession
    private let sessionStore: SessionStoring
    private let appCode: String
    private let routes: BackendRoute.AppScoped
    private let anonymousInstallationId: String
    let baseURL: URL
    private(set) var currentSession: StoredAuthSession?

    var hasAuthenticatedSession: Bool {
        AppIdentity.developerBackendEnabled && currentSession != nil
    }

    init(
        baseURL: URL = BackendClient.defaultBaseURL(),
        session: URLSession = .shared,
        sessionStore: SessionStoring = SecureSessionStore(),
        appCode: String = AppIdentity.appCode
    ) {
        let routes = BackendRoute.appScoped(appCode: appCode)
        self.baseURL = baseURL
        self.session = session
        self.sessionStore = sessionStore
        self.appCode = routes.appCode
        self.routes = routes
        self.anonymousInstallationId = AppIdentity.developerBackendEnabled
            ? BackendClient.loadOrCreateAnonymousInstallationId()
            : ""
        self.currentSession = AppIdentity.developerBackendEnabled ? sessionStore.load() : nil
    }

    func fetchBootstrap() async throws -> AppBootstrap {
        let envelope: Envelope<AppBootstrap> = try await send(path: "/api/v1/bootstrap/config")
        return envelope.data
    }

    func fetchPlans() async throws -> [Plan] {
        let envelope: Envelope<[Plan]> = try await send(path: "/api/v1/plans")
        return envelope.data
    }

    func fetchLegalDocs() async throws -> [LegalDocument] {
        let envelope: Envelope<[LegalDocument]> = try await send(path: "/api/v1/legal/docs")
        return envelope.data
    }

    /// 查询当前 App 的版本更新策略。
    ///
    /// 中文维护说明：该方法固定走后端 appCode-scoped 统一接口，App Store 下载 URL 由远程配置返回，
    /// 前端只负责展示当前版本、最新版本和安全跳转，不在代码里写死具体 App Store 页面。
    func fetchAppVersionPolicy(
        platform: String = BackendClient.defaultPlatform(),
        appVersion: String = BackendClient.defaultAppVersion(),
        buildNumber: String = BackendClient.defaultBuildNumber()
    ) async throws -> AppVersionPolicy {
        let queryItems = [
            URLQueryItem(name: "platform", value: platform),
            URLQueryItem(name: "appVersion", value: appVersion),
            URLQueryItem(name: "buildNumber", value: buildNumber)
        ]
        let envelope: Envelope<AppVersionPolicy> = try await send(
            path: routes.appVersionPolicy,
            queryItems: queryItems
        )
        return envelope.data
    }


    func fetchAnnouncements(
        windowDays: Int = 30,
        scene: String? = nil,
        locale: String? = nil,
        appVersion: String? = nil,
        planCode: String? = nil,
        activeOnly: Bool = false
    ) async throws -> [AppAnnouncement] {
        let queryItems = [
            URLQueryItem(name: "appCode", value: appCode),
            URLQueryItem(name: "windowDays", value: String(windowDays)),
            scene.map { URLQueryItem(name: "scene", value: $0) },
            locale.map { URLQueryItem(name: "locale", value: $0) },
            appVersion.map { URLQueryItem(name: "appVersion", value: $0) },
            planCode.map { URLQueryItem(name: "planCode", value: $0) },
            URLQueryItem(name: "activeOnly", value: activeOnly ? "true" : "false")
        ].compactMap { $0 }
        let envelope: Envelope<[AppAnnouncement]> = try await send(
            path: "/api/v1/announcements",
            queryItems: queryItems,
            requiresAuth: true
        )
        return envelope.data
    }


    func fetchAuthMe() async throws -> AuthSessionEnvelope {
        let systemEnvelope: Envelope<SystemAuthenticatedSessionView> = try await send(
            path: routes.authMe,
            requiresAuth: true
        )
        let accountState = try await fetchAccountState()
        let authEnvelope = systemEnvelope.data.toAuthSessionEnvelope(
            accountState: accountState,
            storedSession: currentSession
        )
        if let currentSession {
            let merged = currentSession.merging(accountEnvelope: authEnvelope)
            self.currentSession = merged
            sessionStore.save(merged)
        }
        return authEnvelope
    }

    func exchangeApplePreview(
        identityToken: String,
        authorizationCode: String? = nil,
        givenName: String? = nil,
        familyName: String? = nil,
        state: String? = nil,
        expectedState: String? = nil,
        nonce: String? = nil,
        expectedNonce: String? = nil,
        redirectURI: String? = nil
    ) async throws -> AppleExchangePreview {
        let envelope: Envelope<SystemAppleExchangePreviewView> = try await send(
            path: routes.appleExchange,
            method: "POST",
            body: AppleExchangePayload(
                identityToken: identityToken,
                authorizationCode: authorizationCode,
                givenName: givenName,
                familyName: familyName,
                state: state,
                expectedState: expectedState,
                nonce: nonce,
                expectedNonce: expectedNonce,
                redirectURI: redirectURI
            )
        )
        let preview = envelope.data.toAppleExchangePreview(
            stateAccepted: state == nil || expectedState == nil || state == expectedState,
            nonceAccepted: nonce == nil || expectedNonce == nil || nonce == expectedNonce
        )
        if let issuedSession = preview.session {
            persistSessionIfPresent(issuedSession)
        }
        return preview
    }

    #if DEBUG
    func createDevelopmentSession(displayName: String = "模拟器开发账号") async throws -> AuthSessionEnvelope {
        let envelope: Envelope<SystemAuthSessionIssuedView> = try await send(
            path: routes.devSession,
            method: "POST",
            body: DemoSessionPayload(
                deviceId: nil,
                clientPlatform: "ios-simulator",
                clientVersion: BackendClient.defaultAppVersion(),
                displayName: displayName
            )
        )
        let authEnvelope = envelope.data.toAuthSessionEnvelope()
        persistSessionIfPresent(authEnvelope)
        return authEnvelope
    }
    #endif

    func logout() async throws -> LogoutReceipt {
        let envelope: Envelope<SystemLogoutResultView> = try await send(
            path: routes.authLogout,
            method: "POST",
            requiresAuth: true
        )
        clearSession()
        return envelope.data.toLogoutReceipt()
    }

    func clearSession() {
        currentSession = nil
        sessionStore.clear()
    }

    func fetchAccountState() async throws -> AccountState {
        let envelope: Envelope<AccountState> = try await send(path: "/api/v1/account/me/state", requiresAuth: true)
        return envelope.data
    }

    func fetchCloudUsageState() async throws -> CloudUsageState {
        let envelope: Envelope<CloudUsageState> = try await send(path: "/api/v1/account/me/cloud-usage", requiresAuth: true)
        return envelope.data
    }

    func fetchHomeSummary() async throws -> HomeSummary {
        let envelope: Envelope<HomeSummary> = try await send(path: "/api/v1/account/me/home-summary", requiresAuth: true)
        return envelope.data
    }

    func fetchDailyLearningTask(childId: String? = nil) async throws -> DailyLearningTaskFeed {
        var queryItems: [URLQueryItem] = []
        if let childId, !childId.isEmpty {
            queryItems.append(URLQueryItem(name: "childId", value: childId))
        }
        let envelope: Envelope<DailyLearningTaskFeed> = try await send(
            path: "/api/v1/learning/daily-task",
            queryItems: queryItems,
            requiresAuth: true
        )
        return envelope.data
    }

    func completeDailyLearningTask(taskId: String, completionType: String, childId: String? = nil) async throws -> DailyLearningTaskCompletion {
        let envelope: Envelope<DailyLearningTaskCompletion> = try await send(
            path: "/api/v1/learning/daily-task/\(taskId)/complete",
            method: "POST",
            body: CompleteDailyLearningTaskPayload(completionType: completionType, childId: childId),
            requiresAuth: true
        )
        return envelope.data
    }

    func fetchCurrentWeeklyReport(childId: String? = nil, scope: String = "child") async throws -> WeeklyParentReport {
        var queryItems = [URLQueryItem(name: "scope", value: scope)]
        if let childId, !childId.isEmpty {
            queryItems.append(URLQueryItem(name: "childId", value: childId))
        }
        let envelope: Envelope<WeeklyParentReport> = try await send(
            path: "/api/v1/reports/weekly/current",
            queryItems: queryItems,
            requiresAuth: true
        )
        return envelope.data
    }

    func fetchWeeklyReportHistory(childId: String? = nil, scope: String = "child") async throws -> WeeklyParentReportHistory {
        var queryItems = [URLQueryItem(name: "scope", value: scope)]
        if let childId, !childId.isEmpty {
            queryItems.append(URLQueryItem(name: "childId", value: childId))
        }
        let envelope: Envelope<WeeklyParentReportHistory> = try await send(
            path: "/api/v1/reports/weekly/history",
            queryItems: queryItems,
            requiresAuth: true
        )
        return envelope.data
    }


    func fetchSubscriptionStatus() async throws -> SubscriptionStatus {
        let envelope: Envelope<SubscriptionStatus> = try await send(path: "/api/v1/subscriptions/status", requiresAuth: true)
        return envelope.data
    }

    func fetchBillingHealth(locale: String? = nil) async throws -> BillingHealth {
        // 购买页进入时先探测后端服务状态；locale 传给后端用于返回当前界面语言的禁用提示。
        let queryItems = [
            locale.map { URLQueryItem(name: "locale", value: $0) }
        ].compactMap { $0 }
        let envelope: Envelope<BillingHealth> = try await send(path: "/api/v1/billing/health", queryItems: queryItems, requiresAuth: false)
        return envelope.data
    }

    func fetchDailyLoginGiftConfig(planCode: String? = nil) async throws -> DailyLoginGiftConfig {
        let queryItems = [
            planCode.map { URLQueryItem(name: "planCode", value: $0) }
        ].compactMap { $0 }
        // 中文说明：启动和回前台时读取后端数据库里的统一日赠积分配置，失败时 AppState 会使用上次缓存值。
        let envelope: Envelope<DailyLoginGiftConfig> = try await send(
            path: "/api/v1/billing/daily-login-gift-config",
            queryItems: queryItems,
            requiresAuth: false
        )
        return envelope.data
    }

    func verifyPurchasePermission(productCode: String?, locale: String? = nil) async throws -> PurchasePermissionDecision {
        // 点击购买前再次调用后端实时验证接口，防止数据库刚刚禁购后客户端仍继续付款流程。
        let queryItems = [
            productCode.map { URLQueryItem(name: "productCode", value: $0) },
            locale.map { URLQueryItem(name: "locale", value: $0) }
        ].compactMap { $0 }
        let envelope: Envelope<PurchasePermissionDecision> = try await send(
            path: "/api/v1/billing/purchase-permission",
            queryItems: queryItems,
            requiresAuth: false
        )
        return envelope.data
    }

    func fetchCreditProducts(locale: String? = nil) async throws -> [CreditProduct] {
        let queryItems = [
            locale.map { URLQueryItem(name: "locale", value: $0) }
        ].compactMap { $0 }
        let envelope: Envelope<[CreditProduct]> = try await send(
            path: "/api/v1/billing/resource-packs",
            queryItems: queryItems,
            requiresAuth: false
        )
        return envelope.data
    }

    func submitInternalPurchase(productCode: String, purchaseTicket: String, idempotencyKey: String, locale: String? = nil) async throws -> InternalPurchaseReceipt {
        let envelope: Envelope<InternalPurchaseReceipt> = try await send(
            path: "/api/v1/billing/internal-purchases",
            method: "POST",
            body: InternalPurchaseRequestPayload(
                productCode: productCode,
                purchaseTicket: purchaseTicket,
                idempotencyKey: idempotencyKey,
                locale: locale ?? "zh-Hans"
            ),
            requiresAuth: true
        )
        return envelope.data
    }

    func fetchEntitlementRecords(serviceType: String? = nil, page: Int = 1, pageSize: Int = 20) async throws -> EntitlementRecordPage {
        let queryItems = [
            serviceType.map { URLQueryItem(name: "serviceType", value: $0) },
            URLQueryItem(name: "timezone", value: TimeZone.current.identifier),
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "pageSize", value: String(pageSize))
        ].compactMap { $0 }
        let envelope: Envelope<EntitlementRecordPage> = try await send(
            path: "/api/v1/billing/entitlement-records",
            queryItems: queryItems,
            requiresAuth: true
        )
        return envelope.data
    }

    func refreshEntitlementSnapshot() async throws -> EntitlementRefreshReceipt {
        let envelope: Envelope<EntitlementRefreshReceipt> = try await send(path: "/api/v1/subscriptions/entitlements/refresh", method: "POST", requiresAuth: true)
        return envelope.data
    }

    func submitTransactionIntake(source: TransactionSource, payload: StoreTransactionPayload) async throws -> TransactionIntakeReceipt {
        let endpoint = source == .purchase
            ? "/api/v1/subscriptions/app-store/purchases/intake"
            : "/api/v1/subscriptions/app-store/restores/intake"
        let envelope: Envelope<TransactionIntakeReceipt> = try await send(path: endpoint, method: "POST", body: payload, requiresAuth: true)
        return envelope.data
    }

    func updatePrivacyConsent(_ payload: PrivacyConsentPayload) async throws -> PrivacyConsentReceipt {
        let envelope: Envelope<PrivacyConsentReceipt> = try await send(
            path: "/api/v1/billing/privacy/consents",
            method: "POST",
            body: payload,
            requiresAuth: true
        )
        return envelope.data
    }

    func requestAccountDeletion(
        provider: String,
        confirmDataDeletion: Bool = true,
        idempotencyKey: String? = nil,
        verificationCode: String? = nil,
        email: String? = nil
    ) async throws -> AccountDeletionReceipt {
        let envelope: Envelope<AccountDeletionReceipt> = try await send(
            path: "/api/v1/account/deletion-requests",
            method: "POST",
            body: AccountDeletionPayload(
                provider: provider,
                confirmDataDeletion: confirmDataDeletion,
                idempotencyKey: idempotencyKey,
                verificationCode: verificationCode,
                email: email
            ),
            requiresAuth: true
        )
        return envelope.data
    }

    func submitFeedback(category: String, content: String, contactEmail: String? = nil, authMode: AuthMode, traceId: String? = nil) async throws -> FeedbackSubmissionReceipt {
        let envelope: Envelope<FeedbackSubmissionReceipt> = try await send(
            path: "/api/v1/feedback",
            method: "POST",
            body: FeedbackPayload(
                category: category,
                content: content,
                contactEmail: contactEmail,
                appVersion: BackendClient.defaultAppVersion(),
                clientPlatform: "ios",
                authMode: authMode.rawValue,
                traceId: traceId
            )
        )
        return envelope.data
    }

    func extractOcrText(imageBase64: String, mimeType: String, promptOverride: String? = nil) async throws -> OcrExtractReceipt {
        throw BackendError.server(code: "cloud_ocr_disabled", message: "云端识图暂未开放。", traceId: nil)
    }


    func synthesizeCloudSpeech(text: String, languageCode: String, rate: Float) async throws -> CloudSpeechReceipt {
        throw BackendError.server(code: "cloud_tts_disabled", message: "云端朗读暂未开放。", traceId: nil)
    }


    func reportDeviceEvent(
        eventType: String,
        bundleId: String? = Bundle.main.bundleIdentifier,
        clientPlatform: String = "ios",
        deviceModel: String? = nil,
        systemName: String? = nil,
        systemVersion: String? = nil,
        appVersion: String? = nil,
        buildNumber: String? = nil,
        locale: String? = nil,
        payload: [String: String] = [:],
        diagnosticsOptIn: Bool = false,
        result: String? = nil
    ) async throws {
        // 默认链路不上传设备事件。仅家长显式开启 diagnostics 时才发送低敏诊断。
        guard diagnosticsOptIn else { return }
        let _: Envelope<DeviceEventReceipt> = try await send(
            path: "/api/v1/account/device-event",
            method: "POST",
            body: DeviceEventPayload(
                eventType: eventType,
                bundleId: bundleId,
                clientPlatform: clientPlatform,
                deviceModel: deviceModel,
                systemName: systemName,
                systemVersion: systemVersion,
                appVersion: appVersion,
                buildNumber: buildNumber,
                locale: locale,
                ipCountry: nil,
                payload: payload,
                diagnosticsOptIn: diagnosticsOptIn,
                result: result
            ),
            requiresAuth: true
        )
    }

    func recordQuotaUsage(kind: String, source: String, languageCode: String? = nil, amount: Int = 1, idempotencyKey: String = UUID().uuidString.lowercased()) async throws -> AccountState {
        let envelope: Envelope<AccountState> = try await send(
            path: "/api/v1/account/quota/usage",
            method: "POST",
            body: QuotaUsagePayload(
                kind: kind,
                source: source,
                languageCode: languageCode,
                amount: amount,
                idempotencyKey: idempotencyKey,
                occurredAt: AppClock.nowString()
            ),
            requiresAuth: true
        )
        return envelope.data
    }

    func fetchChildUsageSummary(childId: String) async throws -> ChildUsageSummary {
        let envelope: Envelope<ChildUsageSummary> = try await send(path: "/api/v1/usage/children/\(childId)/summary", requiresAuth: true)
        return envelope.data
    }

    func fetchFamilyUsageSummary() async throws -> FamilyUsageSummary {
        let envelope: Envelope<FamilyUsageSummary> = try await send(path: "/api/v1/usage/family/summary", requiresAuth: true)
        return envelope.data
    }

    func startUsageSession(
        childId: String,
        sessionUuid: String,
        sourcePage: String,
        clientPlatform: String,
        deviceModel: String?,
        startedAt: String? = nil
    ) async throws -> UsageSessionStartReceipt {
        let envelope: Envelope<UsageSessionStartReceipt> = try await send(
            path: "/api/v1/usage/session/start",
            method: "POST",
            body: UsageSessionStartPayload(
                childId: childId,
                sessionUuid: sessionUuid,
                startedAt: startedAt,
                clientPlatform: clientPlatform,
                deviceModel: deviceModel,
                sourcePage: sourcePage
            ),
            requiresAuth: true
        )
        return envelope.data
    }

    func endUsageSession(sessionUuid: String, endedAt: String? = nil) async throws -> UsageSessionEndReceipt {
        let envelope: Envelope<UsageSessionEndReceipt> = try await send(
            path: "/api/v1/usage/session/end",
            method: "POST",
            body: UsageSessionEndPayload(sessionUuid: sessionUuid, endedAt: endedAt),
            requiresAuth: true
        )
        return envelope.data
    }

    func requestDeletionCode(email: String) async throws -> EmailVerificationTicketReceipt {
        let envelope: Envelope<EmailVerificationTicketReceipt> = try await send(
            path: "/api/v1/account/deletion/request-code",
            method: "POST",
            body: DeletionCodeRequestPayload(email: email),
            requiresAuth: true
        )
        return envelope.data
    }

    func redeemCompensationCode(_ compensationCode: String) async throws -> CompensationRedeemReceipt {
        let envelope: Envelope<CompensationRedeemReceipt> = try await send(
            path: "/api/v1/account/compensation/redeem",
            method: "POST",
            body: CompensationRedeemPayload(compensationCode: compensationCode),
            requiresAuth: false
        )
        return envelope.data
    }

    func confirmDeletionByCode(code: String, email: String, idempotencyKey: String? = nil) async throws -> AccountDeletionReceipt {
        let envelope: Envelope<AccountDeletionReceipt> = try await send(
            path: "/api/v1/account/deletion/confirm",
            method: "POST",
            body: DeletionConfirmPayload(code: code, email: email, confirmDataDeletion: true, idempotencyKey: idempotencyKey),
            requiresAuth: true
        )
        clearSession()
        return envelope.data
    }

    private func persistSessionIfPresent(_ authEnvelope: AuthSessionEnvelope) {
        guard let stored = authEnvelope.persistedSession else { return }
        currentSession = stored
        sessionStore.save(stored)
    }

    private func send<T: Decodable>(path: String, method: String = "GET", requiresAuth: Bool = false) async throws -> T {
        try await send(path: path, queryItems: [], method: method, bodyData: nil, requiresAuth: requiresAuth)
    }

    private func send<T: Decodable>(path: String, queryItems: [URLQueryItem], method: String = "GET", requiresAuth: Bool = false) async throws -> T {
        try await send(path: path, queryItems: queryItems, method: method, bodyData: nil, requiresAuth: requiresAuth)
    }

    private func send<T: Decodable, Body: Encodable>(path: String, method: String = "GET", body: Body, requiresAuth: Bool = false) async throws -> T {
        let bodyData = try JSONEncoder().encode(body)
        return try await send(path: path, queryItems: [], method: method, bodyData: bodyData, requiresAuth: requiresAuth)
    }

    private func send<T: Decodable>(path: String, method: String, bodyData: Data?, requiresAuth: Bool) async throws -> T {
        try await send(path: path, queryItems: [], method: method, bodyData: bodyData, requiresAuth: requiresAuth)
    }

    private func send<T: Decodable>(path: String, queryItems: [URLQueryItem], method: String, bodyData: Data?, requiresAuth: Bool) async throws -> T {
        guard AppIdentity.developerBackendEnabled else {
            // 无后端首发模式下，任何遗漏的调用都必须在发出 URLRequest 前失败。
            // 这样即使某个隐藏页面误触发 BackendClient，也不会访问个人开发者服务器或测试域名。
            throw BackendError.connectionUnavailable
        }
        enforceAppScopedRouteBoundary(path: path)
        let url = makeURL(path: path, queryItems: queryItems)
        var request = try makeRequest(url: url, method: method, requiresAuth: requiresAuth)
        if let bodyData {
            request.httpBody = bodyData
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            if BackendError.isConnectionFailure(error) {
                throw BackendError.connectionUnavailable
            }
            throw error
        }
        guard let httpResponse = response as? HTTPURLResponse else {
            throw BackendError.invalidResponse
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            if httpResponse.statusCode == 401 {
                clearSession()
            }
            if let error = try? JSONDecoder().decode(ApiErrorPayload.self, from: data) {
                throw BackendError.server(code: error.code, message: error.message, traceId: error.traceId)
            }
            throw BackendError.invalidResponse
        }
        return try JSONDecoder().decode(T.self, from: data)
    }

    private func enforceAppScopedRouteBoundary(path: String) {
        precondition(
            !isForbiddenAuthCompatibilityRoute(path),
            "Auth compatibility route \(path) is forbidden in BackendClient. Use app-scoped system auth routes for appCode \(appCode)."
        )
        if path.hasPrefix("/api/v1/system/auth/") {
            let expectedPrefix = BackendRoute.appScoped(appCode: appCode).systemAuthPrefix
            precondition(
                path.hasPrefix(expectedPrefix),
                "System auth route must include the current appCode \(appCode): \(path)"
            )
        }
    }

    private func isForbiddenAuthCompatibilityRoute(_ path: String) -> Bool {
        let components = path.split(separator: "/").map(String.init)
        switch components {
        case ["api", "v1", "auth", "me"],
             ["api", "v1", "auth", "logout"],
             ["api", "v1", "auth", "apple", "exchange"]:
            return true
        default:
            return false
        }
    }

    private func makeURL(path: String, queryItems: [URLQueryItem]) -> URL {
        let rawURL = baseURL.appending(path: path)
        guard !queryItems.isEmpty,
              var components = URLComponents(url: rawURL, resolvingAgainstBaseURL: false) else {
            return rawURL
        }
        components.queryItems = queryItems
        return components.url ?? rawURL
    }

    private func makeRequest(url: URL, method: String, requiresAuth: Bool) throws -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 15
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if !anonymousInstallationId.isEmpty {
            request.setValue(anonymousInstallationId, forHTTPHeaderField: "X-Paipai-Anonymous-Id")
        }
        if requiresAuth {
            guard let accessToken = currentSession?.accessToken else {
                throw BackendError.authRequired
            }
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }
        return request
    }

    private static func loadOrCreateAnonymousInstallationId() -> String {
        let defaults = AppScopedDefaults()
        if let existing = defaults.string(forKey: AppDefaultKey.anonymousInstallationId),
           !existing.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return existing
        }
        let created = "ios-\(UUID().uuidString.lowercased())"
        defaults.set(created, forKey: AppDefaultKey.anonymousInstallationId)
        return created
    }

    private static func defaultBaseURL() -> URL {
        if !AppIdentity.developerBackendEnabled {
            return URL(string: "https://paipai-local-only.invalid")!
        }

        let key = AppIdentity.apiBaseURLInfoDictionaryKey
        guard let raw = Bundle.main.object(forInfoDictionaryKey: key) as? String else {
            fatalError("Missing \(key) in Info.plist. Refusing to fall back to localhost for release builds.")
        }

        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            fatalError("\(key) is blank in Info.plist. Refusing to fall back to localhost for release builds.")
        }

        guard let url = URL(string: trimmed), url.scheme != nil, url.host != nil else {
            fatalError("\(key) is invalid: \(trimmed)")
        }

        return url
    }

    static func defaultAppVersion() -> String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? "0.1.0"
    }

    static func defaultBuildNumber() -> String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String) ?? "1"
    }

    static func defaultPlatform() -> String {
        #if os(iOS)
        return "ios"
        #else
        return "apple"
        #endif
    }

    private static func defaultDeviceLabel() -> String {
        #if os(iOS)
        return "iOS"
        #else
        return "ApplePlatform"
        #endif
    }
}

private struct SystemDecodedAppleIdentityTokenView: Decodable {
    let subject: String?
    let nonce: String?
}

private struct SystemCurrentUserView: Decodable {
    let userId: Int64
    let appCode: String
    let userType: String
    let displayName: String
    let status: String
    let createdAt: String?

    private enum CodingKeys: String, CodingKey {
        case userId
        case appCode
        case userType
        case displayName
        case status
        case createdAt
        case createdAtSnake = "created_at"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        userId = try container.decode(Int64.self, forKey: .userId)
        appCode = try container.decode(String.self, forKey: .appCode)
        userType = try container.decode(String.self, forKey: .userType)
        displayName = try container.decode(String.self, forKey: .displayName)
        status = try container.decode(String.self, forKey: .status)
        createdAt = try container.decodeIfPresent(String.self, forKey: .createdAt)
            ?? container.decodeIfPresent(String.self, forKey: .createdAtSnake)
    }
}

private struct SystemAuthSessionIssuedView: Decodable {
    let appCode: String
    let sessionSource: String
    let sessionToken: String
    let expiresAt: String
    let user: SystemCurrentUserView

    func toAuthSessionEnvelope(accountState: AccountState? = nil) -> AuthSessionEnvelope {
        AuthSessionEnvelope(
            session: SessionSummary(
                accessToken: sessionToken,
                tokenType: "Bearer",
                sessionId: sessionToken,
                sessionType: sessionSource,
                expiresAt: expiresAt
            ),
            account: AuthAccount(
                accountId: String(user.userId),
                signInProvider: sessionSource,
                formalAccount: true,
                email: nil,
                identityVerificationState: user.status,
                createdAt: user.createdAt
            ),
            accountState: accountState,
            recentSessions: [],
            note: nil
        )
    }
}

private struct SystemAppleExchangePreviewView: Decodable {
    let sessionIssued: Bool
    let overallStatus: String
    let exchangeStatus: String
    let identityStatus: String
    let note: String
    let decodedToken: SystemDecodedAppleIdentityTokenView?
    let diagnostics: [String: String?]?
    let issuedSession: SystemAuthSessionIssuedView?

    func toAppleExchangePreview(stateAccepted: Bool, nonceAccepted: Bool) -> AppleExchangePreview {
        AppleExchangePreview(
            sessionIssued: sessionIssued,
            status: overallStatus,
            codeExchangeStatus: exchangeStatus,
            identityTokenStatus: identityStatus,
            stateAccepted: stateAccepted,
            nonceAccepted: nonceAccepted,
            decodedSubject: decodedToken?.subject,
            decodedNonce: decodedToken?.nonce,
            note: note,
            configuration: ["sessionIssued": sessionIssued],
            diagnostics: diagnostics ?? [:],
            session: issuedSession?.toAuthSessionEnvelope()
        )
    }
}

private struct SystemAuthenticatedSessionView: Decodable {
    let appCode: String
    let sessionSource: String
    let sessionStatus: String
    let expiresAt: String
    let user: SystemCurrentUserView

    func toAuthSessionEnvelope(accountState: AccountState? = nil, storedSession: StoredAuthSession?) -> AuthSessionEnvelope {
        let sessionId = storedSession?.sessionId ?? String(user.userId)
        return AuthSessionEnvelope(
            session: SessionSummary(
                accessToken: storedSession?.accessToken,
                tokenType: storedSession?.tokenType ?? "Bearer",
                sessionId: sessionId,
                sessionType: sessionSource,
                expiresAt: expiresAt
            ),
            account: AuthAccount(
                accountId: String(user.userId),
                signInProvider: sessionSource,
                formalAccount: true,
                email: nil,
                identityVerificationState: sessionStatus,
                createdAt: user.createdAt
            ),
            accountState: accountState,
            recentSessions: [
                RecentSession(
                    sessionId: sessionId,
                    sessionType: sessionSource,
                    sessionStatus: sessionStatus,
                    expiresAt: expiresAt,
                    lastSeenAt: nil
                )
            ],
            note: "current session loaded"
        )
    }
}

private struct SystemLogoutResultView: Decodable {
    let appCode: String
    let sessionStatus: String
    let revokedAt: String?

    func toLogoutReceipt() -> LogoutReceipt {
        LogoutReceipt(loggedOut: true, status: sessionStatus)
    }
}

private struct Envelope<T: Decodable>: Decodable {
    let success: Bool
    let data: T
}

private struct ApiErrorPayload: Decodable {
    let code: String
    let message: String
    let traceId: String?

    private enum CodingKeys: String, CodingKey {
        case code
        case message
        case traceId
        case status
        case error
        case path
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let decodedCode = try container.decodeIfPresent(String.self, forKey: .code)
            ?? container.decodeIfPresent(String.self, forKey: .error)
            ?? container.decodeIfPresent(Int.self, forKey: .status).map(String.init)
            ?? "HTTP_ERROR"
        let decodedMessage = try container.decodeIfPresent(String.self, forKey: .message)
            ?? container.decodeIfPresent(String.self, forKey: .error)
            ?? "服务器请求失败，请稍后重试。"
        code = decodedCode
        message = decodedMessage
        traceId = try container.decodeIfPresent(String.self, forKey: .traceId)
    }
}

private struct AppleExchangePayload: Encodable {
    let identityToken: String
    let authorizationCode: String?
    let givenName: String?
    let familyName: String?
    let state: String?
    let expectedState: String?
    let nonce: String?
    let expectedNonce: String?
    let redirectURI: String?

    enum CodingKeys: String, CodingKey {
        case identityToken
        case authorizationCode
        case givenName
        case familyName
        case state
        case expectedState
        case nonce
        case expectedNonce
        case redirectURI = "redirectUri"
    }
}

private struct DemoSessionPayload: Encodable {
    let deviceId: String?
    let clientPlatform: String
    let clientVersion: String
    let displayName: String
}

private struct CreateChildPayload: Encodable {
    let nickname: String
    let ageBand: String
    let learningTrackCode: String
    let parentConfirmed: Bool
}

private struct UpdateChildPayload: Encodable {
    let nickname: String
    let ageBand: String
    let learningTrackCode: String
    let parentConfirmed: Bool
}

private struct AccountDeletionPayload: Encodable {
    let provider: String
    let confirmDataDeletion: Bool
    let idempotencyKey: String?
    let verificationCode: String?
    let email: String?
}

private struct FeedbackPayload: Encodable {
    let category: String
    let content: String
    let contactEmail: String?
    let appVersion: String
    let clientPlatform: String
    let authMode: String
    let traceId: String?
}

private struct CompleteDailyLearningTaskPayload: Encodable {
    let completionType: String
    let childId: String?
}

private struct CloudSpeechPayload: Encodable {
    let text: String
    let languageCode: String
    let rate: Float
}

private struct QuotaUsagePayload: Encodable {
    let kind: String
    let source: String
    let languageCode: String?
    let amount: Int
    let idempotencyKey: String
    let occurredAt: String
}

private struct OcrExtractPayload: Encodable {
    let imageBase64: String
    let mimeType: String
    let promptOverride: String?
}


private struct DeviceEventPayload: Encodable {
    let eventType: String
    let bundleId: String?
    let clientPlatform: String
    let deviceModel: String?
    let systemName: String?
    let systemVersion: String?
    let appVersion: String?
    let buildNumber: String?
    let locale: String?
    let ipCountry: String?
    let payload: [String: String]
    let diagnosticsOptIn: Bool
    let result: String?
}

private struct DeviceEventReceipt: Decodable {
    let eventId: Int?
    let eventType: String
    let recordedAt: String
    let authenticated: Bool
}

private struct UsageSessionStartPayload: Encodable {
    let childId: String
    let sessionUuid: String
    let startedAt: String?
    let clientPlatform: String
    let deviceModel: String?
    let sourcePage: String
}

private struct UsageSessionEndPayload: Encodable {
    let sessionUuid: String
    let endedAt: String?
}

private struct DeletionCodeRequestPayload: Encodable {
    let email: String
}

private struct DeletionConfirmPayload: Encodable {
    let code: String
    let email: String
    let confirmDataDeletion: Bool
    let idempotencyKey: String?
}

private struct InternalPurchaseRequestPayload: Encodable {
    let productCode: String
    let purchaseTicket: String
    let idempotencyKey: String
    let locale: String
}

private struct EmptyPayload: Encodable {}

struct StoreTransactionPayload: Encodable {
    let productId: String
    let transactionId: String?
    let originalTransactionId: String
    let environment: String?
    let storefront: String?
    let appAccountToken: String?
    let signedTransactionInfo: String
    let signedRenewalInfo: String?
    let idempotencyKey: String?
    let refundDataSharingConsent: Bool?
    let consentPolicyVersion: String?
    let consentRegion: String?
}

struct PrivacyConsentPayload: Encodable {
    let consentType: String
    let consented: Bool
    let policyVersion: String
    let regionCode: String
    let sourceType: String
    let sourceRef: String?
}

struct PrivacyConsentReceipt: Decodable {
    let appCode: String
    let userId: Int64?
    let consentType: String
    let consentStatus: String
    let policyVersion: String?
    let regionCode: String?
    let sourceType: String?
    let sourceRef: String?
    let consentedAt: String?
    let revokedAt: String?
}
