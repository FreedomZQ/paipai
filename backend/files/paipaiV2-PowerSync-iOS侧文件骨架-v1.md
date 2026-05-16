# paipaiV2 PowerSync iOS 侧文件骨架 v1

日期：2026-04-20  
目标：给 iOS 端 PowerSync 第一版接入提供可直接开工的目录结构、文件职责、关键方法签名建议

---

## 0. 骨架设计原则

1. **页面不直接依赖 PowerSync SDK**
2. **页面只依赖 Repository / AppState**
3. **PowerSync 相关逻辑集中在 `Core/Sync/`**
4. **本地 only 数据与同步镜像数据分开处理**
5. **BackendClient 继续负责权威接口，不再当主学习数据源**

---

## 1. 推荐目录结构

```text
PaipaiReadAlongV2/
  App/
    PaipaiReadAlongV2App.swift

  Core/
    Models/
      SyncModels.swift
      ReviewCardModels.swift
      UsageModels.swift
      PreferenceModels.swift
      ChildModels.swift

    Sync/
      PowerSyncManager.swift
      PowerSyncConnector.swift
      PowerSyncCredentialsStore.swift
      PowerSyncBootstrapAPI.swift
      PowerSyncUploadAPI.swift
      PowerSyncInstallationStore.swift
      PowerSyncSyncState.swift
      SyncSettingsStore.swift
      LocalOnlyTables.swift

    Repositories/
      ChildRepository.swift
      ReviewCardRepository.swift
      ReviewEventRepository.swift
      UsageSessionRepository.swift
      UserPreferenceRepository.swift
      LocalAssetRepository.swift

    Services/
      BackendClient.swift
      SecureSessionStore.swift
      DeviceInfoService.swift
      OCRService.swift
      TranslationService.swift
      TTSService.swift

  Features/
    Parent/
      SyncSettingsView.swift
      SyncStatusBadgeView.swift
      SyncConflictListView.swift   // 第一版可选
```

---

## 2. `Core/Models/SyncModels.swift`

## 2.1 作用
统一放：
- installation
- bootstrap response
- token response
- sync state
- rebuild request
- upload result

## 2.2 骨架建议
```swift
import Foundation

enum CloudSyncStatus: String, Codable {
    case disabled
    case idle
    case syncing
    case failed
    case rebuildRequired
}

struct PowerSyncBootstrapRequest: Codable {
    let installationId: String
    let deviceId: String?
    let clientPlatform: String
    let deviceModel: String?
    let appVersion: String?
    let cloudSyncEnabled: Bool
    let powersyncClientId: String?
}

struct PowerSyncBootstrapResponse: Codable {
    let appCode: String
    let installationId: String
    let cloudSyncEnabled: Bool
    let initialSyncCompleted: Bool
    let powerSyncEndpoint: String
    let tokenExpiresAt: String?
    let shouldRebuild: Bool
    let serverTime: String
}

struct PowerSyncTokenResponse: Codable {
    let endpoint: String
    let token: String
    let expiresAt: String
    let claims: PowerSyncClaims
}

struct PowerSyncClaims: Codable {
    let appCode: String
    let userId: Int64
    let installationId: String
}

struct SyncRuntimeState: Equatable {
    var status: CloudSyncStatus
    var lastSyncAt: Date?
    var lastPullAt: Date?
    var lastPushAt: Date?
    var lastErrorMessage: String?
    var initialSyncCompleted: Bool
}

struct RebuildSyncRequest: Codable {
    let installationId: String
    let reason: String
}

struct PowerSyncUploadEnvelope: Codable {
    let installationId: String
    let changes: [PowerSyncChangeItem]
}

struct PowerSyncChangeItem: Codable {
    let entityType: String
    let operation: String
    let entityId: String
    let clientUpdatedAt: String
    let payload: [String: CodableValue]
}
```

> `CodableValue` 可单独定义通用 JSON 包装器。

---

## 3. `Core/Sync/PowerSyncInstallationStore.swift`

## 3.1 作用
- 管理 `installationId`
- 首次安装生成
- 长期保存在 Keychain / Secure store

## 3.2 骨架建议
```swift
import Foundation

final class PowerSyncInstallationStore {
    private let key = "paipai.powersync.installation-id"
    private let secureStore = UserDefaults.standard // 第一版可临时替换，正式建议 Keychain

    func installationId() -> String {
        if let value = secureStore.string(forKey: key), !value.isEmpty {
            return value
        }
        let newValue = UUID().uuidString
        secureStore.set(newValue, forKey: key)
        return newValue
    }

    func clear() {
        secureStore.removeObject(forKey: key)
    }
}
```

> 正式落地时建议改成 Keychain，不建议长期放 UserDefaults。

---

## 4. `Core/Sync/PowerSyncCredentialsStore.swift`

## 4.1 作用
缓存：
- PowerSync endpoint
- token
- expiry

## 4.2 骨架建议
```swift
import Foundation

struct PowerSyncCredentials: Codable {
    let endpoint: String
    let token: String
    let expiresAt: Date
}

final class PowerSyncCredentialsStore {
    func save(_ credentials: PowerSyncCredentials) {}
    func load() -> PowerSyncCredentials? { nil }
    func clear() {}
    func isValid(_ credentials: PowerSyncCredentials) -> Bool { credentials.expiresAt > Date() }
}
```

---

## 5. `Core/Sync/PowerSyncBootstrapAPI.swift`

## 5.1 作用
负责对接 backend：
- `/bootstrap`
- `/token`
- `/rebuild`

## 5.2 骨架建议
```swift
import Foundation

protocol PowerSyncBootstrapAPIProtocol {
    func bootstrap(_ request: PowerSyncBootstrapRequest) async throws -> PowerSyncBootstrapResponse
    func requestToken(installationId: String) async throws -> PowerSyncTokenResponse
    func rebuild(_ request: RebuildSyncRequest) async throws
}

final class PowerSyncBootstrapAPI: PowerSyncBootstrapAPIProtocol {
    private let backendClient: BackendClient

    init(backendClient: BackendClient) {
        self.backendClient = backendClient
    }

    func bootstrap(_ request: PowerSyncBootstrapRequest) async throws -> PowerSyncBootstrapResponse {
        try await backendClient.powerSyncBootstrap(request)
    }

    func requestToken(installationId: String) async throws -> PowerSyncTokenResponse {
        try await backendClient.powerSyncToken(installationId: installationId)
    }

    func rebuild(_ request: RebuildSyncRequest) async throws {
        _ = try await backendClient.powerSyncRebuild(request)
    }
}
```

---

## 6. `Core/Sync/PowerSyncUploadAPI.swift`

## 6.1 作用
PowerSync Connector 上传本地变更时调用 unified backend。

## 6.2 骨架建议
```swift
import Foundation

protocol PowerSyncUploadAPIProtocol {
    func upload(_ envelope: PowerSyncUploadEnvelope) async throws -> PowerSyncUploadResult
}

final class PowerSyncUploadAPI: PowerSyncUploadAPIProtocol {
    private let backendClient: BackendClient

    init(backendClient: BackendClient) {
        self.backendClient = backendClient
    }

    func upload(_ envelope: PowerSyncUploadEnvelope) async throws -> PowerSyncUploadResult {
        try await backendClient.powerSyncUpload(envelope)
    }
}
```

---

## 7. `Core/Sync/SyncSettingsStore.swift`

## 7.1 作用
- 记录用户是否开启云同步
- 同步状态 UI 使用

## 7.2 骨架建议
```swift
import Foundation

final class SyncSettingsStore {
    private let key = "paipai.cloud-sync-enabled"
    private let defaults = UserDefaults.standard

    func cloudSyncEnabled() -> Bool {
        defaults.bool(forKey: key)
    }

    func setCloudSyncEnabled(_ enabled: Bool) {
        defaults.set(enabled, forKey: key)
    }
}
```

> 第一版可与后端 preference 字段双向同步，但本地仍需有即时状态。

---

## 8. `Core/Sync/LocalOnlyTables.swift`

## 8.1 作用
定义本地 only 表：
- 图片路径
- runtime kv

## 8.2 骨架建议
```swift
import Foundation

struct LocalAssetFileRef: Codable, Identifiable {
    let id: String
    let ownerTable: String
    let ownerId: String
    let assetType: String
    let localPath: String
    let fileSizeBytes: Int?
    let mimeType: String?
    let createdAt: Date
    let updatedAt: Date
}

struct LocalRuntimeKV: Codable {
    let key: String
    let valueJSON: String
    let updatedAt: Date
}
```

> 真正的 SQL/ORM 映射按 PowerSync/SQLite 接入方式实现。

---

## 9. `Core/Sync/PowerSyncConnector.swift`

## 9.1 作用
这是 iOS 端最关键的桥：
- 提供 token
- 上传本地变更到 backend
- 处理上传失败/拒绝

## 9.2 骨架建议
```swift
import Foundation

protocol PowerSyncConnectorProtocol {
    func fetchCredentials() async throws -> PowerSyncCredentials
    func uploadChanges(_ changes: [PowerSyncChangeItem]) async throws -> PowerSyncUploadResult
}

final class PowerSyncConnector: PowerSyncConnectorProtocol {
    private let installationStore: PowerSyncInstallationStore
    private let credentialsStore: PowerSyncCredentialsStore
    private let bootstrapAPI: PowerSyncBootstrapAPIProtocol
    private let uploadAPI: PowerSyncUploadAPIProtocol

    init(
        installationStore: PowerSyncInstallationStore,
        credentialsStore: PowerSyncCredentialsStore,
        bootstrapAPI: PowerSyncBootstrapAPIProtocol,
        uploadAPI: PowerSyncUploadAPIProtocol
    ) {
        self.installationStore = installationStore
        self.credentialsStore = credentialsStore
        self.bootstrapAPI = bootstrapAPI
        self.uploadAPI = uploadAPI
    }

    func fetchCredentials() async throws -> PowerSyncCredentials {
        if let cached = credentialsStore.load(), credentialsStore.isValid(cached) {
            return cached
        }
        let installationId = installationStore.installationId()
        let tokenView = try await bootstrapAPI.requestToken(installationId: installationId)
        let credentials = PowerSyncCredentials(
            endpoint: tokenView.endpoint,
            token: tokenView.token,
            expiresAt: ISO8601DateFormatter().date(from: tokenView.expiresAt) ?? Date().addingTimeInterval(300)
        )
        credentialsStore.save(credentials)
        return credentials
    }

    func uploadChanges(_ changes: [PowerSyncChangeItem]) async throws -> PowerSyncUploadResult {
        let installationId = installationStore.installationId()
        let envelope = PowerSyncUploadEnvelope(installationId: installationId, changes: changes)
        return try await uploadAPI.upload(envelope)
    }
}
```

---

## 10. `Core/Sync/PowerSyncManager.swift`

## 10.1 作用
统一管理：
- PowerSync client 生命周期
- 启动/停止
- 首轮 bootstrap
- rebuild
- runtime sync state

## 10.2 骨架建议
```swift
import Foundation

@MainActor
final class PowerSyncManager: ObservableObject {
    @Published private(set) var runtimeState = SyncRuntimeState(
        status: .disabled,
        lastSyncAt: nil,
        lastPullAt: nil,
        lastPushAt: nil,
        lastErrorMessage: nil,
        initialSyncCompleted: false
    )

    private let bootstrapAPI: PowerSyncBootstrapAPIProtocol
    private let connector: PowerSyncConnectorProtocol
    private let installationStore: PowerSyncInstallationStore
    private let syncSettingsStore: SyncSettingsStore

    init(
        bootstrapAPI: PowerSyncBootstrapAPIProtocol,
        connector: PowerSyncConnectorProtocol,
        installationStore: PowerSyncInstallationStore,
        syncSettingsStore: SyncSettingsStore
    ) {
        self.bootstrapAPI = bootstrapAPI
        self.connector = connector
        self.installationStore = installationStore
        self.syncSettingsStore = syncSettingsStore
    }

    func bootstrap(deviceId: String?, deviceModel: String?, appVersion: String?) async {
        do {
            let request = PowerSyncBootstrapRequest(
                installationId: installationStore.installationId(),
                deviceId: deviceId,
                clientPlatform: "ios",
                deviceModel: deviceModel,
                appVersion: appVersion,
                cloudSyncEnabled: syncSettingsStore.cloudSyncEnabled(),
                powersyncClientId: nil
            )
            let response = try await bootstrapAPI.bootstrap(request)
            runtimeState.initialSyncCompleted = response.initialSyncCompleted
            runtimeState.status = response.cloudSyncEnabled ? .idle : .disabled
        } catch {
            runtimeState.status = .failed
            runtimeState.lastErrorMessage = error.localizedDescription
        }
    }

    func startIfNeeded() async {
        guard syncSettingsStore.cloudSyncEnabled() else {
            runtimeState.status = .disabled
            return
        }
        runtimeState.status = .syncing
        do {
            _ = try await connector.fetchCredentials()
            runtimeState.status = .idle
        } catch {
            runtimeState.status = .failed
            runtimeState.lastErrorMessage = error.localizedDescription
        }
    }

    func requestRebuild() async {
        runtimeState.status = .rebuildRequired
    }

    func stop() {
        runtimeState.status = .disabled
    }
}
```

> 真接 SDK 时，把 PowerSync client 实例塞进这个 Manager。

---

## 11. Repository 层骨架

## 11.1 原则
页面不要直接查 BackendClient。  
页面统一走 Repository：
- 读：本地 SQLite / PowerSync mirror
- 写：本地 upsert/delete
- 同步：PowerSync 自动/后台完成

---

## 11.2 `Core/Repositories/ReviewCardRepository.swift`
```swift
import Foundation

protocol ReviewCardRepositoryProtocol {
    func fetchRecent(childId: String?) async throws -> [ReviewCard]
    func fetchDueToday(childId: String?) async throws -> [ReviewCard]
    func upsertCard(_ card: ReviewCard) async throws
    func softDelete(cardId: String) async throws
}

final class ReviewCardRepository: ReviewCardRepositoryProtocol {
    func fetchRecent(childId: String?) async throws -> [ReviewCard] { [] }
    func fetchDueToday(childId: String?) async throws -> [ReviewCard] { [] }
    func upsertCard(_ card: ReviewCard) async throws {}
    func softDelete(cardId: String) async throws {}
}
```

## 11.3 `Core/Repositories/ReviewEventRepository.swift`
```swift
import Foundation

protocol ReviewEventRepositoryProtocol {
    func appendReviewEvent(cardId: String, childId: String, resultLevel: String) async throws
}

final class ReviewEventRepository: ReviewEventRepositoryProtocol {
    func appendReviewEvent(cardId: String, childId: String, resultLevel: String) async throws {}
}
```

## 11.4 `Core/Repositories/UsageSessionRepository.swift`
```swift
import Foundation

protocol UsageSessionRepositoryProtocol {
    func startSession(id: String, childId: String, sourcePage: String) async throws
    func endSession(id: String, endedAt: Date) async throws
    func fetchRecentSummary(childId: String?) async throws -> FamilyUsageSummary?
}

final class UsageSessionRepository: UsageSessionRepositoryProtocol {
    func startSession(id: String, childId: String, sourcePage: String) async throws {}
    func endSession(id: String, endedAt: Date) async throws {}
    func fetchRecentSummary(childId: String?) async throws -> FamilyUsageSummary? { nil }
}
```

## 11.5 `Core/Repositories/UserPreferenceRepository.swift`
```swift
import Foundation

protocol UserPreferenceRepositoryProtocol {
    func fetchPreference() async throws -> UserPreference?
    func savePreference(_ preference: UserPreference) async throws
}

final class UserPreferenceRepository: UserPreferenceRepositoryProtocol {
    func fetchPreference() async throws -> UserPreference? { nil }
    func savePreference(_ preference: UserPreference) async throws {}
}
```

---

## 12. `BackendClient.swift` 需要新增的骨架方法

```swift
extension BackendClient {
    func powerSyncBootstrap(_ request: PowerSyncBootstrapRequest) async throws -> PowerSyncBootstrapResponse {
        fatalError("implement")
    }

    func powerSyncToken(installationId: String) async throws -> PowerSyncTokenResponse {
        fatalError("implement")
    }

    func powerSyncUpload(_ envelope: PowerSyncUploadEnvelope) async throws -> PowerSyncUploadResult {
        fatalError("implement")
    }

    func powerSyncRebuild(_ request: RebuildSyncRequest) async throws -> EmptyResponse {
        fatalError("implement")
    }
}
```

---

## 13. `App/PaipaiReadAlongV2App.swift` 改造骨架

## 13.1 AppState 新增依赖
建议新增：
```swift
@Published var syncRuntimeState = SyncRuntimeState(...)

let installationStore = PowerSyncInstallationStore()
let syncSettingsStore = SyncSettingsStore()
lazy var powerSyncBootstrapAPI = PowerSyncBootstrapAPI(backendClient: backendClient)
lazy var powerSyncUploadAPI = PowerSyncUploadAPI(backendClient: backendClient)
lazy var powerSyncConnector = PowerSyncConnector(
    installationStore: installationStore,
    credentialsStore: PowerSyncCredentialsStore(),
    bootstrapAPI: powerSyncBootstrapAPI,
    uploadAPI: powerSyncUploadAPI
)
lazy var powerSyncManager = PowerSyncManager(
    bootstrapAPI: powerSyncBootstrapAPI,
    connector: powerSyncConnector,
    installationStore: installationStore,
    syncSettingsStore: syncSettingsStore
)
```

## 13.2 启动顺序建议
```swift
func startup() async {
    isLoading = true
    defer { isLoading = false }

    do {
        bootstrap = try await backendClient.fetchBootstrap()

        if backendClient.currentSession != nil {
            _ = try? await backendClient.fetchAuthMe()
        }

        authSession = backendClient.currentSession
        guard authSession != nil else {
            // 无 guest，直接停在登录状态
            return
        }

        let device = deviceInfoService.currentDeviceInfo
        await powerSyncManager.bootstrap(
            deviceId: device.deviceId,
            deviceModel: device.model,
            appVersion: device.appVersion
        )
        await powerSyncManager.startIfNeeded()

        syncRuntimeState = powerSyncManager.runtimeState

        await refreshAllDataFromRepositories()
    } catch {
        errorMessage = error.localizedDescription
    }
}
```

### 新增方法
```swift
func refreshAllDataFromRepositories() async {
    // 不再优先依赖 BackendClient 拉主学习数据
}
```

---

## 14. 页面迁移骨架建议

## 14.1 `CaptureView.swift`
### 当前目标
识别后“保存句卡”时改为写本地 Repository。

### 新逻辑
```swift
let card = ReviewCard(
    id: UUID().uuidString,
    childId: appState.selectedChild.id,
    sourceText: recognizedText,
    translatedText: translatedText,
    sourceLanguageCode: appState.sourceLanguageCode,
    targetLanguageCode: appState.targetLanguageCode,
    sourceType: "camera",
    ...
)
try await appState.reviewCardRepository.upsertCard(card)
```

---

## 14.2 `LearningDetailView.swift`
- 保存改走 `ReviewCardRepository`
- 不等网络
- 图片路径存 `LocalAssetRepository`

---

## 14.3 `ReviewView.swift`
- `recordResult` 时：
  1. 写本地 `review_event`
  2. 更新本地 `review_card`
  3. UI 立即刷新

---

## 14.4 `ParentAreaView.swift`
- usage / child / preference 改从本地 repository 拉
- 增加 `SyncStatusBadgeView`
- 设置入口增加 `SyncSettingsView`

---

## 15. `Features/Parent/SyncSettingsView.swift`

## 15.1 作用
- 控制云同步开关
- 展示最近同步时间
- 展示是否已完成首轮同步
- 提供重新同步按钮

## 15.2 骨架建议
```swift
import SwiftUI

struct SyncSettingsView: View {
    @EnvironmentObject var appState: AppState
    @State private var cloudSyncEnabled = false

    var body: some View {
        Form {
            Section("Cloud Sync") {
                Toggle("Enable cloud sync", isOn: $cloudSyncEnabled)
                    .onChange(of: cloudSyncEnabled) { _, newValue in
                        appState.syncSettingsStore.setCloudSyncEnabled(newValue)
                        Task { await appState.powerSyncManager.startIfNeeded() }
                    }
            }

            Section("Status") {
                Text("Status: \(appState.syncRuntimeState.status.rawValue)")
                if let lastSyncAt = appState.syncRuntimeState.lastSyncAt {
                    Text("Last sync: \(lastSyncAt.formatted())")
                }
            }

            Section {
                Button("Rebuild Sync") {
                    Task { await appState.powerSyncManager.requestRebuild() }
                }
            }
        }
        .task {
            cloudSyncEnabled = appState.syncSettingsStore.cloudSyncEnabled()
        }
    }
}
```

---

## 16. 可删除/可收缩的旧代码建议

在新架构稳定后，可逐步清理：
1. 直接从页面里调用 BackendClient 拉主学习数据的逻辑
2. 依赖内存态而不是本地持久化的 review 主数据流
3. 旧 `InterfaceLanguageSettingsView`（已经包装成新页）
4. 若 `reading_review_event_v2` / `reading_usage_session_v2` 稳定后，旧表的直接写入代码可删

### 注意
- 清理要在同步主链路跑通后做
- 第一阶段不建议大删，只建议“新路径优先 + 旧路径弃用”

---

## 17. 开发顺序（iOS 侧）

### 第一步
- `project.yml` 加包
- `Core/Models/SyncModels.swift`
- `Core/Sync/*` 底座

### 第二步
- `BackendClient.swift` 增 PowerSync 接口
- `AppState` 启动接线

### 第三步
- `Core/Repositories/*`
- 本地 mirror 数据读写

### 第四步
- Capture / Learning / Review 页面迁移
- Parent / Preference / SyncSettings 页面迁移

### 第五步
- 清理旧数据流
- 联调多设备同步 / 离线恢复

---

## 18. 本文档用途
本文件可以直接用来：
1. 创建目录结构
2. 起草 Swift 文件
3. 给前端开发按文件分工
4. 对照页面迁移顺序推进
