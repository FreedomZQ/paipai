# 拍拍伴读「说写秒译」功能模块落地实施方案

文档日期：2026-05-26  
目标版本：iOS 18.0+ 首发策略内集成  
集成位置：伴读乐园 `ReadingParkView`

## 1. 目标与边界

「说写秒译」是伴读乐园中的轻量翻译练习入口，支持孩子或家长通过语音输入、文字输入提交一句或短段内容，并将内容翻译为当前孩子的目标学习语言。当前版本坚持设备端处理：

- 语音转文字：使用 iOS Speech 框架设备端识别，强制 `requiresOnDeviceRecognition = true`，不回退云识别。
- 文本翻译：沿用现有 `TranslationService`、`TranslationPipelineSupport` 与 SwiftUI `translationTask`，基于已安装/已下载的系统翻译语言包离线翻译。
- 语音朗读：沿用现有 `TTSService` 与 `AppState.playLocalTts(...)`，发音效果和朗读额度规则与当前翻译/学习页一致。
- 展示适配：页面、入口和周报专项必须跟随用户选择的 App 展示语言与展示大小，不把界面语言和学习目标语言混用。
- 云端接口：只预留能力开关、DTO、后端兼容入口和数据库字段，不在当前 UI 暴露，不上传儿童语音、原文、译文。

非目标：

- 当前版本不做云端翻译、云端语音识别、云端内容审核。
- 当前版本不默认保存翻译历史。只有用户主动点击「保存为句卡」时，才复用现有句卡加密存储流程。
- 当前版本不新增付费权益项。朗读仍消耗现有本机朗读/本机功能积分；语音识别和本地翻译不单独扣费。

## 2. 现有系统接入点

### 2.1 前端现状

可复用代码：

- 伴读乐园入口：`front/ios/PaipaiReadAlong/Features/ReadingPark/ReadingParkView.swift`
  - 已有儿童档案校验：`validatePermissionsAndOpenCapture()` 中先 `refreshParentData()`，再检查 `appState.children` 和 `appState.selectedChild`。
  - 已有统一弹窗、全屏/导航进入模式、`ReadingParkFeatureCard` 卡片样式。
- 翻译服务：`front/ios/PaipaiReadAlong/Core/Services/TranslationService.swift`
  - 已支持语言包可用性判断、iOS 18 `LanguageAvailability`、目标语言包安装探测。
- 翻译流水线工具：`front/ios/PaipaiReadAlong/Core/Services/TranslationPipelineSupport.swift`
  - 已有预处理、后处理、语言规范化、文本指纹。
- 学习详情页：`front/ios/PaipaiReadAlong/Features/Learning/LearningDetailView.swift`
  - 已有 `translationTask`、语言包下载提示、翻译重试、朗读速度、朗读权益耗尽弹窗、保存句卡。
- 朗读服务：`front/ios/PaipaiReadAlong/Core/Services/TTSService.swift`
  - 已封装 `AVSpeechSynthesizer`、语速换算、音频会话、预热与设备端朗读。
- 账户状态与本地权益：`front/ios/PaipaiReadAlong/App/PaipaiReadAlongApp.swift`
  - 已有 `playLocalTts(...)`、`saveReviewCard(...)`、`startUsageSession(...)`、本地每日额度和 Keychain 钱包逻辑。
- 本地 SQLite：`front/ios/PaipaiReadAlong/Core/Persistence/SQLiteSchema.swift`
  - 已有 `reading_review_card`、`reading_usage_session`、`reading_learning_event`、`reading_user_preference`。

### 2.2 后端现状

可复用代码：

- 鉴权解析：`ReadingAuthenticatedUserResolver`
- 账号与首页：`/api/v1/account/me/state`、`/api/v1/account/me/home-summary`
- 使用会话：`ReadingUsageCompatController`，已支持 `source_page` 维度。
- 云 OCR/TTS 兼容停用响应：
  - `ReadingOcrCompatController`
  - `ReadingTtsCompatController`
- 权益/额度配置：
  - `reading_daily_quota_config`
  - `ReadingDailyQuotaConfigService`
  - `ReadingCloudUsageService`
- 数据库已有业务表：
  - `reading_child_profile`
  - `reading_review_card`
  - `reading_usage_session`
  - `reading_user_preference`
  - `reading_cloud_service_usage`
  - `reading_cloud_service_usage_log`

## 3. 用户流程

### 3.1 入口流程

1. 用户进入「伴读乐园」。
2. 点击「说写秒译」功能卡片。
3. 前端执行和「拍拍识图」一致的儿童档案准入：
   - 若 `children` 为空：提示「请先在家长中心添加至少一名孩子档案，再进入说写秒译。」
   - 若当前 `selectedChild` 不在有效孩子列表：提示「请先在首页选择一个具体的孩子，再进入说写秒译。」
4. 通过后进入 `SayWriteTranslateView`。

### 3.2 语音输入流程

1. 用户选择「说话」分段控件。
2. 点击麦克风按钮。
3. 首次使用请求系统权限：
   - `SFSpeechRecognizer.requestAuthorization`
   - `AVAudioSession.recordPermission`
4. 前端按当前学习轨道推导识别语种：
   - 优先 `appState.sourceLanguageCode`
   - 若识别语种与目标语种一致，仍允许识别，但翻译阶段会判断是否需要直接展示原文或切换到配对源语种。
5. 创建 `SFSpeechAudioBufferRecognitionRequest`，设置：
   - `requiresOnDeviceRecognition = true`
   - `shouldReportPartialResults = true`
6. 使用 `AVAudioEngine` 采集音频，实时回填识别文本。
7. 用户停止录音或静音超时后，触发本地翻译。
8. 翻译成功后展示目标学习语言译文，并展示朗读按钮。

### 3.3 文字输入流程

1. 用户选择「写字」分段控件。
2. 在输入框输入或粘贴内容。
3. 点击「翻译」。
4. 前端使用 `NLLanguageRecognizer` 做本地源语言推断；无法可靠判断时交给系统 Translation 自动源语言。
5. 调用同一套本地翻译流水线。

### 3.4 翻译结果流程

1. 展示「原文」和「译文」两块内容。
2. 点击译文右上角朗读按钮：
   - 调用 `appState.playLocalTts(text: translation, languageCode: targetLanguageCode, rate: selectedSpeed.rate, preferCloud: false)`。
   - 朗读消耗沿用现有本机朗读权益。
3. 可选操作：
   - 「复制译文」：仅本地剪贴板，不写后端。
   - 「保存为句卡」：复用 `appState.saveReviewCard(...)`，进入现有 `reading_review_card` 加密与同步流程。

## 4. iOS 前端实现方案

### 4.1 新增文件

建议新增：

- `front/ios/PaipaiReadAlong/Features/InstantTranslate/SayWriteTranslateView.swift`
- `front/ios/PaipaiReadAlong/Core/Services/SpeechRecognitionService.swift`
- `front/ios/PaipaiReadAlong/Core/Services/InstantTranslationCoordinator.swift`
- `front/ios/PaipaiReadAlong/Core/Models/InstantTranslateModels.swift`
- `front/ios/PaipaiReadAlong/Core/Repositories/InstantTranslateEventRepository.swift`（仅记录无正文事件时新增）
- `front/ios/PaipaiReadAlong/Core/Localization/InstantTranslateCopy.swift`（建议新增；集中管理说写秒译文案 key、图表图例和错误提示的多语言文本）

命名说明：

- 对用户展示名称固定为「说写秒译」。
- 代码模块名用 `InstantTranslate`，避免中文文件名带来的工具链兼容问题。

### 4.2 伴读乐园入口改造

在 `ReadingParkView` 中新增状态：

```swift
@State private var showSayWriteTranslate = false
```

在 `features` 中新增卡片，建议放在「拍拍识图」后：

```swift
ReadingParkFeature(
    icon: "⚡️",
    title: InstantTranslateCopy.title(appState),
    description: InstantTranslateCopy.entryDescription(appState),
    buttonTitle: InstantTranslateCopy.entryButtonTitle(appState),
    isComingSoon: false,
    action: validateAndOpenSayWriteTranslate
)
```

新增进入校验：

```swift
private func validateAndOpenSayWriteTranslate() {
    Task { await validateChildAndOpenSayWriteTranslate() }
}

@MainActor
private func validateChildAndOpenSayWriteTranslate() async {
    await appState.refreshParentData()
    guard !appState.children.isEmpty else {
        childSelectionAlertMessage = InstantTranslateCopy.missingChildMessage(appState)
        return
    }
    guard appState.children.contains(where: { $0.id == appState.selectedChild.id && !$0.isDeleted }) else {
        childSelectionAlertMessage = InstantTranslateCopy.invalidChildMessage(appState)
        return
    }
    showSayWriteTranslate = true
}
```

导航方式建议使用 `navigationDestination`，与「伴读复习」「阅读周报」一致：

```swift
.navigationDestination(isPresented: $showSayWriteTranslate) {
    SayWriteTranslateView()
        .environmentObject(appState)
}
```

### 4.3 页面结构

`SayWriteTranslateView` 采用现有 App 视觉：

- 背景：`AppColors.background`
- 页面宽度：`.adaptiveContentFrame(maxWidth: 620)`
- 主容器：`ScrollView + VStack(spacing: AppLayout.spacingXL)`
- 卡片：复用 `MainCard`
- 按钮：复用 `AppColors.primary`、`AppGradients.primary`、圆角 16 的主按钮
- 图标：优先使用 SF Symbols，和当前 SwiftUI 代码保持一致

页面区块：

1. 顶部栏
   - 返回按钮
   - 当前展示语言下的功能标题
   - 当前孩子名称/目标学习语言弱提示，语言名称按展示语言本地化
2. 输入方式分段控件
   - 语音输入模式文案按当前展示语言渲染
   - 文字输入模式文案按当前展示语言渲染
3. 语音输入区
   - 大号麦克风按钮
   - 识别状态：未开始、聆听中、识别中、设备端语音包不可用
   - 实时转写文本
4. 文字输入区
   - `TextEditor`
   - 字数限制：建议 200 字，与现有 OCR 文本限制一致
5. 翻译结果区
   - 原文
   - 译文
   - 朗读按钮、复制按钮、保存句卡按钮
6. 朗读速度区
   - 复用 `LocalTtsSpeed`

### 4.4 展示语言与文字缩放

说写秒译页面必须同时支持「用户选择的展示语言」和「用户选择的文字大小」。这里的展示语言是 App 界面语言，不等同于学习目标语言；学习目标语言仍由 `source_language_code`、`target_language_code`、`reading_track_code` 决定。

#### 4.4.1 展示语言

现有 App 使用 `appState.interfaceLocaleCode`、`AppLocaleCatalog`、`appState.uiText(...)` 和 `appState.localizedText(...)` 管理界面语言。说写秒译新增页面必须沿用该机制：

- 页面所有可见文案、按钮、占位符、错误提示、权限提示、周报模块标题和图表图例，都不得硬编码单一中文字符串。
- 当前支持语言按 `AppLocaleCatalog.supportedInterfaceLocales` 处理：`zh-Hans`、`en`、`ja`、`ko`、`es`。
- 说写秒译独有文案必须覆盖当前支持的五种展示语言，使用 `appState.localizedText(zhHans:english:japanese:korean:spanish:)` 或封装后的 `InstantTranslateCopy.text(key:locale:)`。
- 只有全局历史组件已有明确兜底策略时才继续使用 `appState.uiText(zh, en)`；新增说写秒译文案不使用中英文二分兜底。
- 新增文案建议集中定义 key，例如 `instant_translate.title`、`instant_translate.voice_mode`、`instant_translate.text_mode`、`instant_translate.permission.microphone_denied`，便于未来扩展法语、德语等语言时只补文案表，不改业务逻辑。
- 后端接口返回的 `input_mode`、`language_pair_code`、`hour_bucket`、`event_status` 等只作为稳定代码值；前端展示时本地映射为当前界面语言，不直接展示原始 code。
- 后端如返回 `message`，仅作为调试或兜底文案；正式 UI 优先使用前端本地化文案，避免服务端新增语言阻塞 App 展示。

落地规则：

1. `SayWriteTranslateView`、入口卡片、权限弹窗、语言包提示、保存句卡结果、周报专项模块都从 `@EnvironmentObject var appState: AppState` 读取 `interfaceLocaleCode`，不得在 `ViewModel` 中缓存中文展示文案。
2. ViewModel、Repository、后端 DTO 只保存稳定 code，例如 `voice`、`text`、`zh-Hans_to_en`、`morning`；展示层每次渲染时再映射为当前界面语言。
3. 用户在家长中心切换展示语言后，当前页面不需要重新进入；SwiftUI body 依赖 `appState.interfaceLocaleCode` 后会自动刷新。正在录音、正在翻译、已生成的译文内容保持不变，仅 UI 标签和提示语切换语言。
4. `TextEditor` 占位提示、分段控件标题、空状态、按钮、Toast/Alert、accessibility label、周报图例都纳入文案 key 清单。
5. `language_pair_code` 展示时拆成 source/target 两端，调用 `appState.displayTitle(for:fallback:)` 或统一 `LanguageDisplayNameResolver`；`auto_to_en` 的 source 展示为当前界面语言下的「自动识别」。
6. 新增展示语言时只改 `AppLocaleCatalog.supportedInterfaceLocales`、`AppLocaleCatalog.title/subtitle` 和文案表，业务状态机、数据库字段、接口 payload 不变。

文案示例：

```swift
enum InstantTranslateCopy {
    static func title(_ appState: AppState) -> String {
        appState.localizedText(
            zhHans: "说写秒译",
            english: "Say & Write Translate",
            japanese: "話して書いて即訳",
            korean: "말하고 쓰는 즉시 번역",
            spanish: "Traducir al hablar o escribir"
        )
    }

    static func weeklyModuleTitle(_ appState: AppState) -> String {
        title(appState)
    }

    static func entryDescription(_ appState: AppState) -> String {
        appState.localizedText(
            zhHans: "说一句或写一句，秒译成学习语言",
            english: "Speak or type a sentence and translate it into the learning language",
            japanese: "話すか書くと、学習中の言語にすぐ翻訳します",
            korean: "말하거나 쓰면 학습 언어로 바로 번역해요",
            spanish: "Di o escribe una frase y tradúcela al idioma de aprendizaje"
        )
    }

    static func entryButtonTitle(_ appState: AppState) -> String {
        appState.localizedText(
            zhHans: "开始翻译",
            english: "Translate",
            japanese: "翻訳する",
            korean: "번역 시작",
            spanish: "Traducir"
        )
    }

    static func missingChildMessage(_ appState: AppState) -> String {
        appState.localizedText(
            zhHans: "请先在家长中心添加至少一名孩子档案，再进入说写秒译。",
            english: "Please add at least one child profile in Parent Center before using Say & Write Translate.",
            japanese: "保護者センターでお子さまのプロフィールを追加してから利用してください。",
            korean: "먼저 부모 센터에서 아이 프로필을 추가한 뒤 이용해 주세요.",
            spanish: "Añade primero un perfil infantil en el Centro de padres para usar esta función."
        )
    }

    static func invalidChildMessage(_ appState: AppState) -> String {
        appState.localizedText(
            zhHans: "请先在首页选择一个具体的孩子，再进入说写秒译。",
            english: "Please select a child on Home before using Say & Write Translate.",
            japanese: "ホームで利用するお子さまを選んでから利用してください。",
            korean: "홈에서 사용할 아이를 선택한 뒤 이용해 주세요.",
            spanish: "Selecciona primero un menor en Inicio para usar esta función."
        )
    }

    static func hourBucketTitle(_ bucket: String, appState: AppState) -> String {
        switch bucket {
        case "morning":
            return appState.localizedText(zhHans: "上午", english: "Morning", japanese: "午前", korean: "오전", spanish: "Mañana")
        case "afternoon":
            return appState.localizedText(zhHans: "下午", english: "Afternoon", japanese: "午後", korean: "오후", spanish: "Tarde")
        case "evening":
            return appState.localizedText(zhHans: "晚上", english: "Evening", japanese: "夜", korean: "저녁", spanish: "Noche")
        default:
            return appState.localizedText(zhHans: "深夜", english: "Late night", japanese: "深夜", korean: "심야", spanish: "Madrugada")
        }
    }

    static func autoSource(_ appState: AppState) -> String {
        appState.localizedText(
            zhHans: "自动识别",
            english: "Auto detect",
            japanese: "自動検出",
            korean: "자동 감지",
            spanish: "Detección automática"
        )
    }
}
```

语言组合展示示例：

```swift
func languagePairTitle(
    sourceLanguageCode: String?,
    targetLanguageCode: String,
    appState: AppState
) -> String {
    let source = sourceLanguageCode.map {
        appState.displayTitle(for: $0, fallback: $0)
    } ?? InstantTranslateCopy.autoSource(appState)
    let target = appState.displayTitle(for: targetLanguageCode, fallback: targetLanguageCode)
    return "\(source) -> \(target)"
}
```

语言扩展规则：

1. 新增展示语言先扩展 `AppLocaleCatalog.supportedInterfaceLocales` 和 `AppLocaleCatalog.title/subtitle`。
2. 再补齐 `InstantTranslateCopy` 或统一文案资源中的新增语言字段。
3. 说写秒译业务逻辑只依赖规范化 locale，不写 `if locale == "xx"` 的分散判断。
4. 周报 `instant_translate` payload 保持语言无关，只保存 code 和数字；展示层按 `appState.interfaceLocaleCode` 渲染。

#### 4.4.2 文字大小同步缩放

现有 App 使用 `AppTextSizeOption`、`AppTypographyScale.multiplier` 和 `AppTypography` 统一控制字号。说写秒译页面必须全部接入：

- 所有 `Text`、`TextEditor`、`TextField`、按钮 label、图表数值、图例、提示语都使用 `AppTypography.*` 或 `AppTypography.scaledFont(...)`。
- 禁止直接使用未缩放的 `.system(size:)`、`.font(.caption)`、`.font(.body)` 作为最终 UI 字号，除非外层已有明确的 App 级缩放包装。
- 不需要为 `appState.textSizeOption` 自建页面状态；但页面 body 必须显式读取它，`AppTypographyScale.multiplier` 已在 `AppState` 中随设置更新，重绘后即可同步字号。
- 固定高度控件只设置 `minHeight`，避免文字放大后被裁剪；按钮、分段控件、输入框、结果卡片应允许纵向增高。
- 长文案必须设置 `fixedSize(horizontal: false, vertical: true)` 或合理的多行布局；按钮短文案可使用 `lineLimit(1)` + `minimumScaleFactor(0.75)`，但不得牺牲可读性。
- 图表坐标、柱状图标签、语言组合 chip、时段分布图例需要按 `AppTypography.caption` 或 `AppTypography.footnote` 缩放，并预留换行空间。
- 不使用按屏幕宽度缩放字号；响应式只调整布局列数、间距和容器宽度。

同步实现规则：

1. 页面 body 必须直接依赖 `appState.textSizeOption` 或 `appState.textSizeOption.multiplier`，确保家长中心修改展示大小后当前页面即时重绘。
2. 新增组件优先使用 `AppTypography.*`；如果复用历史页面里基于 `.system(size:)` 的局部样式，必须像 `HomeView`、`EntitlementRecordsView` 一样乘以 `textScale`，并在代码旁添加中文注释说明这是为了跟随 App 展示大小。
3. `TextEditor`、转写文本、译文文本使用 `.font(AppTypography.bodyLarge)` 或 `.font(AppTypography.body)`，并设置 `minHeight` 而非固定 `height`。
4. 图表类组件的柱高、横条宽度、间距不跟字号成比例放大；只让标签、数值、图例跟随 `AppTypography`，避免大字号下图形挤占阅读空间。
5. 弹窗、Toast、权限说明复用现有组件时，需确认组件内部已使用 `AppTypography`；否则在说写秒译使用处补一层缩放样式。

页面代码建议：

```swift
struct SayWriteTranslateView: View {
    @EnvironmentObject private var appState: AppState
    @State private var draftText = ""

    private var textScale: CGFloat {
        // 中文注释：显式依赖用户选择的展示大小，保证设置变更后页面同步刷新。
        appState.textSizeOption.multiplier
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: AppLayout.spacingXL) {
                Text(InstantTranslateCopy.title(appState))
                    .font(AppTypography.title2)
                    .fixedSize(horizontal: false, vertical: true)

                TextEditor(text: $draftText)
                    .font(AppTypography.bodyLarge)
                    .frame(minHeight: max(132, 96 * textScale))
            }
        }
    }
}
```

验收尺寸：

- `small`、`medium`、`large`、`extraLarge` 四档文字大小下，页面均无文字重叠、截断、按钮内容溢出。
- iPhone 窄屏、iPad 宽屏、横竖屏下，输入框、翻译结果、周报统计卡片都能自然换行。
- 语音模式大按钮和朗读/保存按钮的触控区域不小于 `AppLayout.minimumTapTarget`。

### 4.5 语音识别服务

新增 `SpeechRecognitionService`，封装 Speech + AVFoundation，避免页面直接管理音频引擎。

核心接口：

```swift
@MainActor
final class SpeechRecognitionService: NSObject, ObservableObject {
    @Published private(set) var transcript: String = ""
    @Published private(set) var state: SpeechRecognitionState = .idle
    @Published private(set) var lastErrorMessage: String?

    func requestPermissions() async -> Bool
    func supportsOnDeviceRecognition(localeIdentifier: String) -> Bool
    func start(localeIdentifier: String) async
    func stop()
    func reset()
}
```

实现要点：

- `SFSpeechRecognizer(locale:)` 为空或 `supportsOnDeviceRecognition == false` 时，不启动录音。
- `SFSpeechAudioBufferRecognitionRequest.requiresOnDeviceRecognition = true`，确保不会上传音频到 Apple 云识别。
- `AVAudioSession` 使用 `.record` 或 `.playAndRecord`；停止录音后恢复朗读前由现有 `TTSService` 重新配置 `.playback`。
- 最大单次录音建议 20 秒，静音 2 秒或用户手动停止后自动提交。
- 每次识别只保留当前页面状态，不默认写数据库。

需要在 `Info.plist` 增加：

```xml
<key>NSMicrophoneUsageDescription</key>
<string>用于在本设备上把孩子或家长说的话转换成文字。录音不会上传到开发者服务器。</string>
<key>NSSpeechRecognitionUsageDescription</key>
<string>用于在本设备上进行语音转文字，生成待翻译内容。语音内容不会上传到开发者服务器。</string>
```

代码注释要求：

- 在 `requiresOnDeviceRecognition = true` 附近添加中文注释，说明这是合规边界，禁止回退云端识别。
- 在音频会话切换处添加中文注释，说明录音和朗读使用不同 `AVAudioSession` 类目，避免和 `TTSService` 冲突。

### 4.6 本地翻译协调器

建议将 `LearningDetailView` 中的翻译流程抽出为 `InstantTranslationCoordinator` 或共享 ViewModel，避免复制大量状态。

职责：

- 接收原文、源语言候选、目标语言。
- 调用 `TranslationPipelinePlanner.makePlan(...)`。
- 调用 `TranslationService.checkLanguagePackAvailability(...)`。
- 生成 `TranslationSession.Configuration`。
- 管理语言包下载提示、重试次数、超时。
- 输出 `translation`、`status`、`message`。

如果时间紧，第一版可在 `SayWriteTranslateView` 内复用 `LearningDetailView` 的翻译代码，但需要在后续重构中抽出公共能力，避免两处语言包下载逻辑分叉。

翻译策略：

- `targetLanguageCode = appState.targetLanguageCode`
- `sourceLanguageCode` 优先：
  - 语音输入：当前识别 locale 对应语言。
  - 文字输入：`NLLanguageRecognizer` 推断；置信不足传 `nil`，由系统自动识别。
- 若源/目标同语族：
  - 译文显示原文，并提示「内容已是目标学习语言」。
  - 仍允许朗读和保存句卡。

### 4.7 朗读与额度

朗读不新增接口，直接复用：

```swift
await appState.playLocalTts(
    text: translation,
    languageCode: appState.targetLanguageCode,
    rate: selectedSpeed.rate,
    preferCloud: false
)
```

保持现有策略：

- 启动设备端朗读后，再记录本机朗读使用。
- 朗读额度不足时使用现有 `isLocalTtsQuotaExhausted`、`localTtsQuotaExhaustedMessage`。
- 云端朗读偏好不在当前功能展示。

### 4.8 使用会话与周报

进入页面并有有效孩子时：

- 开始本地/后端使用会话：`sourcePage = "say_write_translate"`
- 离开页面结束会话。
- 若后端未启用或未登录，仍写本地 `reading_usage_session`，等待既有同步链路处理。

新增学习事件与专项元数据：

- 翻译成功后调用 `learningEventRepository.append(childId: selectedChild.id, sourcePage: "say_write_translate")`。
- 这样现有成就和周报可统计「有效练习」。
- 每次翻译尝试写入 `reading_instant_translate_event`，只记录输入方式、语言组合、时间戳、耗时、长度、状态等元数据。
- 每次翻译完成后同步更新 `reading_instant_translate_daily_stat`，供周报快速聚合。

建议第一版只在「翻译成功」时记一次学习事件，避免用户反复编辑造成虚高；失败和取消仍可记录事件状态，但不计入「总使用次数」和成就。

### 4.9 App 端记录组件

新增 `InstantTranslateEventRepository`，接入 `LocalDatabase`，负责事件表和聚合表的事务写入：

```swift
final class InstantTranslateEventRepository {
    func startEvent(_ event: InstantTranslateEventDraft) async -> String
    func completeEvent(id: String, result: InstantTranslateEventResult) async
    func failEvent(id: String, errorCode: String) async
    func markSavedAsCard(eventId: String, cardId: String) async
    func weeklySummary(childId: String, weekStart: Date, weekEnd: Date) async -> InstantTranslateWeeklySummary
}
```

写入规则：

1. 用户点击「翻译」或语音识别产生最终文本并进入翻译流程时，插入 `event_status='started'`。
2. 翻译成功时更新为 `event_status='completed'`，写入 `translation_duration_ms`、`total_duration_ms`、`source_text_length`、`translated_text_length`、`event_date`、`event_hour`。
3. 语音模式额外写入 `recognition_duration_ms`，用于后续评估语音输入体验。
4. 用户点击朗读时累加 `tts_play_count`，该字段只记录本功能内译文朗读次数，不替代现有 `local_tts` 额度记录。
5. 用户点击「保存为句卡」并保存成功后，更新 `saved_as_card=1`、`saved_card_id=<review_card.id>`；原文和译文只进入现有句卡加密表。
6. 用户未保存句卡时，事件表不得保存原文、译文、语音、转写全文、翻译全文；页面关闭后只保留元数据。
7. 失败或取消时更新 `event_status='failed'/'cancelled'` 和 `error_code`，不计入周报总使用次数，但计入内部质量分析的失败数。

## 5. 后端实现方案

### 5.1 当前版本请求流程

核心翻译不请求后端。

当前版本后端只承担：

- 查询账号/孩子/权益快照。
- 同步使用会话、学习事件、句卡。
- 可选同步说写秒译低敏元数据，供登录态多端汇总和运营聚合。
- 提供未来云端接口的 disabled/compat 响应。
- 返回稳定 code 和聚合数据；界面展示语言由 App 本地文案表负责，后端 `message` 只作为调试兜底。

高效流程：

1. App 启动/进入页面前已有 `bootstrapIfNeeded()` 和 `refreshParentData()`。
2. 进入功能后不发翻译请求。
3. 有登录态时，仅在会话开始/结束、保存句卡、元数据同步时走 API。
4. 无后端或网络不可用时，本地功能完整可用。

### 5.2 新增兼容控制器

建议新增：

- `backend/src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingInstantTranslateCompatController.java`
- `backend/src/main/java/com/apphub/backend/apps/reading/compat/service/ReadingInstantTranslateService.java`

当前只提供两个能力：

1. 查询能力开关：

```http
GET /api/v1/translation/instant/capability
```

响应：

```json
{
  "cloudTranslateEnabled": false,
  "deviceTranslateRequired": true,
  "cloudSpeechRecognitionEnabled": false,
  "message": "当前版本仅支持设备端语音识别与设备端翻译。"
}
```

2. 云端翻译兼容停用响应：

```http
POST /api/v1/translation/instant
```

当前返回：

```json
{
  "accepted": false,
  "provider": "device_required",
  "code": "cloud_translate_disabled",
  "message": "云端秒译暂未开放，当前版本请使用设备端翻译。"
}
```

后端中文注释要求：

- 控制器类注释说明「兼容未来云端入口，当前不接收儿童正文」。
- `POST` 方法内注释说明「不得读取或落库请求正文，避免当前版本形成云处理事实」。

3. 元数据批量同步接口：

```http
POST /api/v1/translation/instant/events/batch
```

请求体只允许低敏元数据，禁止传原文、译文、音频或图片：

```json
{
  "events": [
    {
      "eventId": "instant|child-a|2026-05-26T08:12:30.123Z|8f2a",
      "childId": "child-a",
      "sessionUuid": "550e8400-e29b-41d4-a716-446655440000",
      "inputMode": "voice",
      "providerType": "device",
      "sourceLanguageCode": "zh-Hans",
      "targetLanguageCode": "en",
      "languagePairCode": "zh-Hans_to_en",
      "eventDate": "2026-05-26",
      "eventHour": 20,
      "sourceTextLength": 12,
      "translatedTextLength": 36,
      "recognitionDurationMs": 1800,
      "translationDurationMs": 420,
      "totalDurationMs": 2600,
      "ttsPlayCount": 1,
      "savedAsCard": false,
      "savedCardId": null,
      "eventStatus": "completed",
      "startedAt": "2026-05-26T12:12:30Z",
      "completedAt": "2026-05-26T12:12:33Z",
      "recordVersion": 1
    }
  ]
}
```

响应：

```json
{
  "acceptedCount": 1,
  "ignoredCount": 0,
  "rejectedCount": 0,
  "message": "说写秒译元数据已同步。"
}
```

接口规则：

- `events` 单批最多 100 条。
- `eventId` 幂等，后端以 `(app_code, user_id, id)` 或 `(app_code, user_id, event_id)` 去重。
- 后端必须校验 `childId` 属于当前用户。
- 请求体若出现 `sourceText`、`translatedText`、`audioBase64`、`imageBase64` 等正文/媒体字段，直接返回 400。
- 接口响应不返回需要直接上屏的业务文案；如确需提示，返回 `code` / `messageKey`，App 按 `interfaceLocaleCode` 映射展示。
- 当前首发包可不调用该接口；本地周报不依赖后端。该接口用于后续登录态多端汇总和产品运营低敏分析。

4. 周报摘要查询接口：

```http
GET /api/v1/translation/instant/weekly-summary?childId=child-a&weekStart=2026-05-18
```

响应只返回聚合值：

```json
{
  "childId": "child-a",
  "weekStart": "2026-05-18",
  "weekEnd": "2026-05-24",
  "totalUseCount": 18,
  "averagePerDay": 2.57,
  "activeDays": 5,
  "topLanguagePairs": [
    { "languagePairCode": "zh-Hans_to_en", "useCount": 14, "ratio": 0.78 }
  ],
  "hourBuckets": [
    { "bucket": "evening", "useCount": 11, "ratio": 0.61 }
  ]
}
```

本接口用于后台运营或未来服务端周报生成；iOS 当前周报仍优先读本地 SQLite。

### 5.3 服务扩展点

预留接口，不在当前 UI 使用：

```java
public interface ReadingInstantTranslateProvider {
    InstantTranslateProviderType type();
    boolean enabled();
    InstantTranslateReceipt translate(InstantTranslateRequest request);
}
```

当前实现：

- `DeviceOnlyInstantTranslateProvider`：只返回 `device_required`，不处理正文。
- 未来云端实现：`CloudInstantTranslateProvider`，必须依赖家长同意、capability token、reservation、审计日志。

### 5.4 与现有权益兼容

当前不新增可售权益，不新增客户端展示：

- 语音识别：设备端免费，不扣次数。
- 翻译：设备端免费，不扣次数。
- 译文朗读：继续走 `local_tts` / 本机功能积分。

未来如开放云端，可新增：

- `cloud_translate`
- `cloud_stt`

但首发 DDL 可只预留 `feature_code` 和 `service_type`，默认 daily_limit=0、UI 不展示。

## 6. 数据库方案

### 6.1 复用表

必须复用：

- `reading_child_profile`
  - 用于入口准入和 child_id 外键。
- `reading_user_preference`
  - 使用 `source_language_code`、`target_language_code`、`reading_track_code` 推导目标学习语言。
- `reading_usage_session`
  - `source_page = 'say_write_translate'` 记录学习时长。
- `reading_review_card`
  - 用户主动保存时写入句卡，沿用现有 `encrypted_text`、`content_encryption_version`、`content_key_id`。
- `reading_instant_translate_event`
  - App 端专项事件明细表，作为说写秒译统计源表。
- `reading_instant_translate_daily_stat`
  - App 端专项日聚合表，按孩子、日期、语言组合、输入方式、小时桶汇总使用次数和耗时。
- `reading_cloud_service_usage` / `reading_cloud_service_usage_log`
  - 未来云端翻译/识别扣费时复用；当前不消耗。
- `reading_daily_quota_config`
  - 当前不新增本地翻译额度；未来可配置 `cloud_translate`、`cloud_stt` 为 0。

### 6.2 新增后端事件表

建议新增一张低敏事件表，只保存统计和排障信息，不保存原文、译文、音频。

```sql
CREATE TABLE public.reading_instant_translate_event (
    id character varying(64) NOT NULL,
    app_code character varying(64) DEFAULT 'paipai_readingcompanion'::character varying NOT NULL,
    user_id bigint NOT NULL,
    child_id character varying(64) NOT NULL,
    session_uuid character varying(64),
    input_mode character varying(16) NOT NULL,
    provider_type character varying(32) DEFAULT 'device'::character varying NOT NULL,
    stt_engine character varying(64),
    translation_engine character varying(64),
    source_language_code character varying(32),
    target_language_code character varying(32) NOT NULL,
    language_pair_code character varying(96) NOT NULL,
    event_date date NOT NULL,
    event_hour smallint NOT NULL,
    source_text_length integer DEFAULT 0 NOT NULL,
    translated_text_length integer DEFAULT 0 NOT NULL,
    source_text_fingerprint character varying(128),
    translated_text_fingerprint character varying(128),
    recognition_duration_ms integer DEFAULT 0 NOT NULL,
    translation_duration_ms integer DEFAULT 0 NOT NULL,
    total_duration_ms integer DEFAULT 0 NOT NULL,
    tts_play_count integer DEFAULT 0 NOT NULL,
    saved_as_card boolean DEFAULT false NOT NULL,
    saved_card_id character varying(64),
    event_status character varying(32) DEFAULT 'completed'::character varying NOT NULL,
    error_code character varying(64),
    started_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    record_version integer DEFAULT 1 NOT NULL,
    CONSTRAINT reading_instant_translate_event_pkey PRIMARY KEY (id),
    CONSTRAINT reading_instant_translate_event_input_mode_chk CHECK ((input_mode)::text IN ('voice', 'text')),
    CONSTRAINT reading_instant_translate_event_provider_chk CHECK ((provider_type)::text IN ('device', 'cloud')),
    CONSTRAINT reading_instant_translate_event_status_chk CHECK ((event_status)::text IN ('started', 'completed', 'failed', 'cancelled')),
    CONSTRAINT reading_instant_translate_event_hour_chk CHECK (event_hour >= 0 AND event_hour <= 23),
    CONSTRAINT reading_instant_translate_event_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sys_user(id),
    CONSTRAINT reading_instant_translate_event_child_id_fkey FOREIGN KEY (child_id) REFERENCES public.reading_child_profile(id),
    CONSTRAINT reading_instant_translate_event_saved_card_id_fkey FOREIGN KEY (saved_card_id) REFERENCES public.reading_review_card(id)
);

CREATE INDEX idx_reading_instant_translate_event_user_created
    ON public.reading_instant_translate_event USING btree (app_code, user_id, created_at DESC);

CREATE INDEX idx_reading_instant_translate_event_child_created
    ON public.reading_instant_translate_event USING btree (child_id, created_at DESC);

CREATE INDEX idx_reading_instant_translate_event_child_week
    ON public.reading_instant_translate_event USING btree (app_code, child_id, event_date, event_status);

CREATE INDEX idx_reading_instant_translate_event_language_pair
    ON public.reading_instant_translate_event USING btree (app_code, child_id, language_pair_code, event_date);

CREATE INDEX idx_reading_instant_translate_event_session
    ON public.reading_instant_translate_event USING btree (app_code, session_uuid);
```

说明：

- `event_date` 与 `event_hour` 使用用户本地时区写入，周报按本地自然周聚合。
- `language_pair_code` 固定格式为 `{source}_to_{target}`，例如 `zh-Hans_to_en`。
- `source_text_fingerprint` 和 `translated_text_fingerprint` 默认可为空；如确需排重，必须使用设备 Keychain 派生密钥做 HMAC-SHA256 后截断，禁止使用可被短文本字典反推的裸哈希。
- `saved_as_card=false` 且 `saved_card_id` 为空表示用户没有保存句卡，此时事件表绝不保存具体翻译内容。
- `provider_type='cloud'` 仅未来使用；当前写入时固定 `device`。

如后端需要支撑运营查询，可增加服务端日聚合表，结构与 App 端 `reading_instant_translate_daily_stat` 保持同名字段。该表由批量同步接口按事件增量 upsert，不作为客户端周报的唯一来源。

### 6.3 App 端 SQLite 表

App 端必须新增说写秒译专项元数据表。`reading_instant_translate_event` 是源表，`reading_instant_translate_daily_stat` 是自动聚合表。两张表都不保存原文、译文、语音或转写全文。

需要同步更新 `ReadingLocalTableName`：

```swift
enum ReadingLocalTableName {
    static let instantTranslateEvent = "reading_instant_translate_event"
    static let instantTranslateDailyStat = "reading_instant_translate_daily_stat"
}
```

#### 6.3.1 明细事件表

```sql
CREATE TABLE IF NOT EXISTS reading_instant_translate_event (
    id TEXT PRIMARY KEY NOT NULL,
    app_code TEXT NOT NULL,
    child_id TEXT NOT NULL,
    session_uuid TEXT,
    input_mode TEXT NOT NULL,
    provider_type TEXT NOT NULL DEFAULT 'device',
    stt_engine TEXT,
    translation_engine TEXT,
    source_language_code TEXT,
    target_language_code TEXT NOT NULL,
    language_pair_code TEXT NOT NULL,
    event_date TEXT NOT NULL,
    event_hour INTEGER NOT NULL,
    source_text_length INTEGER NOT NULL DEFAULT 0,
    translated_text_length INTEGER NOT NULL DEFAULT 0,
    source_text_fingerprint TEXT,
    translated_text_fingerprint TEXT,
    recognition_duration_ms INTEGER NOT NULL DEFAULT 0,
    translation_duration_ms INTEGER NOT NULL DEFAULT 0,
    total_duration_ms INTEGER NOT NULL DEFAULT 0,
    tts_play_count INTEGER NOT NULL DEFAULT 0,
    saved_as_card INTEGER NOT NULL DEFAULT 0,
    saved_card_id TEXT,
    event_status TEXT NOT NULL DEFAULT 'completed',
    error_code TEXT,
    started_at TEXT NOT NULL,
    completed_at TEXT,
    deleted_at TEXT,
    record_version INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reading_instant_translate_event_child_created
    ON reading_instant_translate_event(child_id, created_at);

CREATE INDEX IF NOT EXISTS idx_reading_instant_translate_event_child_week
    ON reading_instant_translate_event(child_id, event_date, event_status);

CREATE INDEX IF NOT EXISTS idx_reading_instant_translate_event_pair
    ON reading_instant_translate_event(child_id, language_pair_code, event_date);

CREATE INDEX IF NOT EXISTS idx_reading_instant_translate_event_hour
    ON reading_instant_translate_event(child_id, event_hour, event_date);
```

字段规则：

- `id`：客户端生成，建议格式 `instant|{childId}|{startedAtMillis}|{shortUUID}`。
- `input_mode`：`voice` 或 `text`。
- `provider_type`：当前固定 `device`，未来云端才允许 `cloud`。
- `event_date`：本地日期 `YYYY-MM-DD`，与现有周报自然周口径一致。
- `event_hour`：本地小时 `0...23`，用于使用时段分布。
- `language_pair_code`：由 `source_language_code + "_to_" + target_language_code` 生成；源语言不确定时使用 `auto_to_{target}`。
- `source_text_length`、`translated_text_length`：只保存字符数，不保存内容。
- `recognition_duration_ms`：语音转文字耗时，文字输入为 0。
- `translation_duration_ms`：从提交翻译到系统翻译返回的耗时。
- `total_duration_ms`：从开始输入或点击翻译到译文展示完成的总耗时。
- `saved_as_card`：0 表示未保存句卡；1 表示已经通过现有 `reading_review_card` 保存。
- `saved_card_id`：仅在 `saved_as_card=1` 时写入，对应现有加密句卡。
- `source_text_fingerprint`、`translated_text_fingerprint`：默认 `NULL`；如后续确需排重，只能写本机密钥 HMAC 后的截断值，不同步到后端。

#### 6.3.2 日聚合表

```sql
CREATE TABLE IF NOT EXISTS reading_instant_translate_daily_stat (
    id TEXT PRIMARY KEY NOT NULL,
    app_code TEXT NOT NULL,
    child_id TEXT NOT NULL,
    stat_date TEXT NOT NULL,
    language_pair_code TEXT NOT NULL,
    input_mode TEXT NOT NULL,
    hour_bucket TEXT NOT NULL,
    usage_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    cancelled_count INTEGER NOT NULL DEFAULT 0,
    saved_card_count INTEGER NOT NULL DEFAULT 0,
    tts_play_count INTEGER NOT NULL DEFAULT 0,
    total_recognition_duration_ms INTEGER NOT NULL DEFAULT 0,
    total_translation_duration_ms INTEGER NOT NULL DEFAULT 0,
    total_duration_ms INTEGER NOT NULL DEFAULT 0,
    first_used_at TEXT,
    last_used_at TEXT,
    record_version INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_reading_instant_translate_daily_stat_scope
    ON reading_instant_translate_daily_stat(child_id, stat_date, language_pair_code, input_mode, hour_bucket);

CREATE INDEX IF NOT EXISTS idx_reading_instant_translate_daily_stat_child_date
    ON reading_instant_translate_daily_stat(child_id, stat_date);
```

聚合维度：

- `stat_date`：本地日期。
- `language_pair_code`：语言组合。
- `input_mode`：语音或文字。
- `hour_bucket`：`morning`、`afternoon`、`evening`、`night`。

`hour_bucket` 规则：

- `morning`：06:00-11:59
- `afternoon`：12:00-17:59
- `evening`：18:00-21:59
- `night`：22:00-05:59

聚合写入规则：

1. `event_status='completed'` 时，`usage_count += 1`、`success_count += 1`。
2. `event_status='failed'` 时，`failed_count += 1`，不增加 `usage_count`。
3. `event_status='cancelled'` 时，`cancelled_count += 1`，不增加 `usage_count`。
4. 保存句卡成功时，按事件原始维度 `saved_card_count += 1`，并更新事件表 `saved_as_card=1`。
5. 朗读译文时，事件表 `tts_play_count += 1`，日聚合表 `tts_play_count += 1`。
6. `total_translation_duration_ms`、`total_recognition_duration_ms`、`total_duration_ms` 只累计成功事件。

一致性要求：

- 明细事件表是统计源表；日聚合表是缓存。
- `InstantTranslateEventRepository.completeEvent(...)` 必须在一个 SQLite 事务内同时更新事件表和日聚合表。
- 周报生成前可执行 `rebuildInstantTranslateDailyStats(childId:weekStart:weekEnd:)`，当聚合表缺失或版本不匹配时从事件表重算。
- 删除孩子、本地数据清理、账号退出时，两张表跟随现有本地数据清理流程删除。
- 若未来同步到后端，客户端只同步明细元数据；服务端自行聚合，避免客户端聚合错误污染运营统计。

#### 6.3.3 内容保存规则

仅在用户主动点击「保存为句卡」并成功后，原文/译文才进入现有 `reading_review_card`：

- `encrypted_text`：保存原文密文。
- `translated_text` 或加密扩展字段：保存译文，需继续遵循现有加密策略。
- `content_encryption_version`、`content_key_id`：复用当前句卡加密字段。

说写秒译事件表不新增 `source_text`、`translated_text`、`audio_path`、`audio_blob`、`transcript` 等字段。若产品后续要求「最近翻译」列表，必须另行评审，并采用 `encrypted_source_text`、`encrypted_translated_text`、`content_encryption_version`、`content_key_id` 这组加密字段；默认不启用。

#### 6.3.4 App 端模型

建议新增模型：

```swift
struct InstantTranslateEventRecord: Codable, Hashable, Identifiable {
    let id: String
    let childId: String
    let inputMode: String
    let sourceLanguageCode: String?
    let targetLanguageCode: String
    let languagePairCode: String
    let eventDate: String
    let eventHour: Int
    let sourceTextLength: Int
    let translatedTextLength: Int
    let recognitionDurationMs: Int
    let translationDurationMs: Int
    let totalDurationMs: Int
    let ttsPlayCount: Int
    let savedAsCard: Bool
    let savedCardId: String?
    let eventStatus: String
    let startedAt: String
    let completedAt: String?
}

struct InstantTranslateWeeklySummary: Codable, Hashable {
    let totalUseCount: Int
    let averagePerDay: Double
    let averagePerActiveDay: Double
    let activeDays: Int
    let topLanguagePairs: [InstantTranslateLanguagePairStat]
    let hourBuckets: [InstantTranslateHourBucketStat]
    let dailyCounts: [Int]
    let averageTranslationDurationMs: Int
}
```

### 6.4 配置数据

当前版本不需要新增 `reading_daily_quota_config`。如需为未来云端预留，可插入但保持不可用：

```sql
INSERT INTO public.reading_daily_quota_config
    (app_code, plan_code, feature_code, daily_limit, status)
VALUES
    ('paipai_readingcompanion', 'free', 'cloud_translate', 0, 'active'),
    ('paipai_readingcompanion', 'standard_single_child', 'cloud_translate', 0, 'active'),
    ('paipai_readingcompanion', 'family_multi_child_lifetime', 'cloud_translate', 0, 'active'),
    ('paipai_readingcompanion', 'free', 'cloud_stt', 0, 'active'),
    ('paipai_readingcompanion', 'standard_single_child', 'cloud_stt', 0, 'active'),
    ('paipai_readingcompanion', 'family_multi_child_lifetime', 'cloud_stt', 0, 'active')
ON CONFLICT (app_code, plan_code, feature_code) DO NOTHING;
```

`app-definition.yml` 建议新增：

```yaml
translation:
  deviceOnlyFirstRelease: "true"
  cloudProcessingEnabled: "false"
  cloudSpeechRecognitionEnabled: "false"
  capabilityTokenRequired: "true"
```

## 7. 周报统计方案

### 7.1 数据源

现有周报由 `LocalWeeklyReportRepository.ensureReports(...)` 本地生成，并将结果写入 `reading_weekly_report.report_json`。说写秒译专项统计接入同一条链路：

- 明细源表：`reading_instant_translate_event`
- 快速聚合表：`reading_instant_translate_daily_stat`
- 周报输出模型：`WeeklyParentReport.modules`
- UI 展示入口：`WeeklyReportView.reportContent(...)`

周报生成时按孩子和本地自然周读取数据：

```swift
let instantTranslateSummary = await instantTranslateEventRepository.weeklySummary(
    childId: child.id,
    weekStart: weekStart,
    weekEnd: weekEnd
)
```

然后在 `buildReport(...)` 中追加模块：

```swift
WeeklyReportModule.local(
    code: "instant_translate",
    title: InstantTranslateCopy.weeklyModuleTitle(appState),
    payload: instantTranslateSummary.toWeeklyReportPayload()
)
```

注意：`WeeklyReportModule.title` 是展示层标题，不作为统计 key。若周报缓存已保存旧语言标题，`WeeklyReportView` 渲染时仍应优先按 `module.code == "instant_translate"` 映射当前 `appState.interfaceLocaleCode` 下的标题、图例和说明，避免用户切换展示语言后看到混合语言界面。

### 7.2 统计口径

专项统计只统计 `event_status='completed'` 的成功翻译事件。失败和取消事件用于质量分析，不进入孩子周报的总使用次数。

统计项：

- 周期内总使用次数：`totalUseCount = COUNT(completed events)`
- 日均使用频次：`averagePerDay = totalUseCount / 7`
- 活跃日均频次：`averagePerActiveDay = totalUseCount / activeDays`，`activeDays` 为本周有成功翻译的天数。
- 常用语言组合分布：按 `language_pair_code` 分组，输出 `useCount` 和 `ratio`，最多展示 Top 3，其余归入 `other`。
- 使用时段分布规律：按 `hour_bucket` 分组，输出 `morning`、`afternoon`、`evening`、`night` 的次数和占比。
- 每日次数趋势：输出 7 天数组 `dailyCounts`，周一到周日排列。
- 平均翻译时长：`averageTranslationDurationMs = SUM(translation_duration_ms) / totalUseCount`。
- 语音输入占比：`voiceRatio = voiceCompletedCount / totalUseCount`，用于判断孩子更偏好说还是写。
- 保存句卡转化：`savedCardRate = savedCardCount / totalUseCount`，只统计说写秒译中点击保存并成功的事件。

SQL 口径示例：

```sql
-- 周期内总使用次数
SELECT COUNT(*) AS total_use_count
FROM reading_instant_translate_event
WHERE child_id = ?
  AND event_status = 'completed'
  AND event_date >= ?
  AND event_date <= ?;

-- 常用语言组合
SELECT language_pair_code, COUNT(*) AS use_count
FROM reading_instant_translate_event
WHERE child_id = ?
  AND event_status = 'completed'
  AND event_date >= ?
  AND event_date <= ?
GROUP BY language_pair_code
ORDER BY use_count DESC, language_pair_code ASC;

-- 使用时段分布
SELECT
  CASE
    WHEN event_hour BETWEEN 6 AND 11 THEN 'morning'
    WHEN event_hour BETWEEN 12 AND 17 THEN 'afternoon'
    WHEN event_hour BETWEEN 18 AND 21 THEN 'evening'
    ELSE 'night'
  END AS hour_bucket,
  COUNT(*) AS use_count
FROM reading_instant_translate_event
WHERE child_id = ?
  AND event_status = 'completed'
  AND event_date >= ?
  AND event_date <= ?
GROUP BY hour_bucket;
```

### 7.3 周报 payload 结构

`instant_translate` 模块 payload 建议固定如下，便于后续前后端同构：

```json
{
  "total_use_count": 18,
  "average_per_day": 2.57,
  "average_per_active_day": 3.6,
  "active_days": 5,
  "daily_counts": [2, 0, 3, 4, 1, 5, 3],
  "average_translation_duration_ms": 430,
  "voice_count": 11,
  "text_count": 7,
  "voice_ratio": 0.61,
  "saved_card_count": 4,
  "saved_card_rate": 0.22,
  "top_language_pairs": [
    { "language_pair_code": "zh-Hans_to_en", "source": "zh-Hans", "target": "en", "use_count": 14, "ratio": 0.78 },
    { "language_pair_code": "en_to_zh-Hans", "source": "en", "target": "zh-Hans", "use_count": 4, "ratio": 0.22 }
  ],
  "hour_buckets": [
    { "bucket": "morning", "use_count": 2, "ratio": 0.11 },
    { "bucket": "afternoon", "use_count": 3, "ratio": 0.17 },
    { "bucket": "evening", "use_count": 11, "ratio": 0.61 },
    { "bucket": "night", "use_count": 2, "ratio": 0.11 }
  ]
}
```

Swift 转换建议：

```swift
extension InstantTranslateWeeklySummary {
    func toWeeklyReportPayload() -> [String: LocalPayloadValue] {
        [
            "total_use_count": .int(totalUseCount),
            "average_per_day": .double(averagePerDay),
            "average_per_active_day": .double(averagePerActiveDay),
            "active_days": .int(activeDays),
            "daily_counts": .array(dailyCounts.map { .int($0) }),
            "average_translation_duration_ms": .int(averageTranslationDurationMs),
            "top_language_pairs": .array(topLanguagePairs.map(\.payloadValue)),
            "hour_buckets": .array(hourBuckets.map(\.payloadValue))
        ]
    }
}
```

### 7.4 可视化展示

在 `WeeklyReportView` 中新增 `instantTranslateSection(_ report:)`，建议放在 `trendSection` 后、`analysisSection` 前。

展示方式：

- 概览卡片：在「报告概览」中新增一张统计卡，标题和副文案按当前展示语言渲染，核心数值显示 `total_use_count`。
- 日均频次卡：显示 `average_per_day`，保留 1 位小数，标题按当前展示语言渲染。
- 每日次数柱状图：使用 `daily_counts` 生成 7 根柱，颜色使用 `AppColors.info`，和现有 `dailyBarChart` 保持同尺寸。
- 常用语言组合：使用横向 chip 或简洁横条，展示 Top 3 语言组合和占比，例如当前界面语言下的 `中文 -> 英文 78%` / `Chinese -> English 78%`。
- 使用时段分布：使用 4 段横向堆叠条或 2x2 小卡，展示上午/下午/晚上/深夜占比；时段名称和最高占比解读均按当前展示语言渲染。
- 输入方式分布：用两个小胶囊展示语音/文字次数，标签按当前展示语言渲染，帮助判断孩子偏好。
- 保存句卡转化：显示 `saved_card_count` 和 `saved_card_rate`，说明有多少翻译结果沉淀为复习素材。

周报文案实现：

- 优先复用 `WeeklyReportView` 现有 `reportText(zhHans, english, ja:ko:es:)` 方法；若说写秒译文案放在 `InstantTranslateCopy`，则 `WeeklyReportView` 通过同一套 key 调用，不能只使用 `isEnglish` 二分逻辑。
- `top_language_pairs` 展示时不要直接渲染 `zh-Hans_to_en`，需要拆出 `source`、`target` 后用 `appState.displayTitle(for:fallback:)` 转成当前界面语言。
- `hour_buckets.bucket`、输入方式 `voice/text` 和 `other` 分组必须统一经文案表转换，方便后续添加新展示语言。

UI 约束：

- 不展示任何原文、译文、转写文本样例。
- 不新增营销式大图；延续周报页 `ReportSectionCard`、`statCard`、`analysisCard` 样式。
- 所有标题、统计副文案、语言组合名称、时段名称、输入方式名称都按 `appState.interfaceLocaleCode` 本地化展示；payload 里的 code 不直接出现在 UI 上。
- 所有数值、图例、chip、柱状图标签都使用 `AppTypography` 或历史周报页同等缩放方式，跟随 `appState.textSizeOption` 同步变大变小。
- 数值为空时隐藏本模块，避免 0 数据占据主要视线；但在空周报预览中可用当前展示语言提示该功能的练习次数将成为周报内容之一。

### 7.5 自动聚合与一致性

自动聚合链路：

1. `SayWriteTranslateView` 完成一次翻译。
2. `InstantTranslateEventRepository.completeEvent(...)` 在一个 SQLite 事务中更新事件表和日聚合表。
3. 调用 `appState.scheduleWeeklyReportGenerationTimer()` 或轻量标记 `weekly_report_dirty`，让本周结束后的 `ensureLocalWeeklyReports(...)` 读取最新数据。
4. `LocalWeeklyReportRepository.insertGeneratedReport(...)` 调用 `weeklyInstantTranslateSummary(...)`，将结果写入 `WeeklyParentReport.modules`。
5. `WeeklyReportView` 从 `report.modules.first(where: { $0.code == "instant_translate" })` 读取并展示。

一致性校验：

- 每次生成周报前，按 `record_version` 判断是否需要从明细事件表重建日聚合。
- 周报已生成后，如果同一自然周被补写事件，应删除或更新对应 `reading_weekly_report` 记录，重新生成 `report_json`。
- `daily_stat.usage_count` 必须等于同维度 `event_status='completed'` 明细数量；调试构建可加断言，生产构建发现不一致则以明细表重算。
- 保存句卡转化以 `reading_instant_translate_event.saved_as_card=1` 为准，不反向扫描所有句卡，避免把普通 OCR 句卡算入说写秒译。
- 后端同步成功与否不影响本地周报；本地周报永远以 App SQLite 为准。

## 8. 权限与隐私控制

### 8.1 儿童档案准入

必须与「拍拍识图」一致：

- 功能卡点击即校验儿童档案。
- 未添加儿童信息不得进入模块。
- 当前孩子无效不得进入模块。
- 文案明确引导到家长中心或首页选择孩子。

### 8.2 系统权限

语音输入需要：

- 麦克风权限
- 语音识别权限

权限拒绝处理：

- 页面内展示提示。
- 提供「前往设置」按钮，复用 `UIApplication.openSettingsURLString`。
- 不允许回退云端语音识别。
- 允许用户切换到「写字」模式继续使用。

### 8.3 数据最小化

当前版本数据边界：

- 录音不保存。
- 录音不上传开发者服务器。
- 语音转写结果只存在页面状态，除非用户主动保存句卡。
- 翻译结果只存在页面状态，除非用户主动保存句卡。
- App 端和后端事件表不保存正文，只保存长度、语言、状态、耗时、时段、保存句卡关联等元数据。
- 未保存为句卡的翻译结果不得持久化；已保存为句卡时只通过现有句卡加密链路保存。
- 诊断默认关闭，只有家长显式开启低敏诊断后才能上报设备事件。

## 9. 云端 API 预留设计

未来云端能力必须满足以下前置条件：

1. 家长显式同意云处理。
2. 后端返回 capability token，限定用户、孩子、能力、过期时间和最大文本长度。
3. 发起 reservation，锁定一次云端调用额度。
4. Provider 调用成功后 commit reservation；失败 rollback。
5. 云端 provider 不保存儿童正文，业务库只记录低敏审计。
6. UI 必须清晰展示云处理开关和家长授权入口；当前版本不展示。

预留前端接口：

```swift
protocol InstantTranslationProviding {
    func translate(_ request: InstantTranslationRequest) async throws -> InstantTranslationResult
}

struct DeviceInstantTranslationProvider: InstantTranslationProviding { }
struct CloudInstantTranslationProvider: InstantTranslationProviding { }
```

预留后端接口：

```http
POST /api/v1/translation/instant/reservations
POST /api/v1/translation/instant
POST /api/v1/translation/instant/reservations/{reservationId}/commit
POST /api/v1/translation/instant/reservations/{reservationId}/rollback
```

当前所有云端接口返回 disabled，不接收正文处理。

## 10. 开发任务拆解

### 10.1 前端任务

1. 新增 `SpeechRecognitionService`
   - Speech 权限申请
   - 麦克风权限申请
   - 设备端识别可用性判断
   - 录音开始/停止/清理
2. 新增 `SayWriteTranslateView`
   - 输入模式切换
   - 语音识别 UI
   - 文本输入 UI
   - 翻译结果 UI
   - 朗读与保存句卡
   - 全量文案接入 `appState.localizedText(...)` / `InstantTranslateCopy`
   - 全量字号接入 `AppTypography` 和 `appState.textSizeOption`
3. 抽取或复用本地翻译逻辑
   - 首选抽取 `InstantTranslationCoordinator`
   - 时间不足时先在新页面局部复用，后续重构
4. 改造 `ReadingParkView`
   - 新增功能卡
   - 新增儿童档案校验
   - 新增导航目的地
5. 更新 `Info.plist`
   - `NSMicrophoneUsageDescription`
   - `NSSpeechRecognitionUsageDescription`
6. 更新本地 SQLite schema
   - 新增 `reading_instant_translate_event`
   - 新增 `reading_instant_translate_daily_stat`
   - 只记录低敏元数据，不默认保存正文
7. 新增 `InstantTranslateEventRepository`
   - 翻译开始/成功/失败/取消记录
   - 朗读次数与保存句卡转化回写
   - 周报专项 summary 聚合
8. 改造本地周报
   - `LocalWeeklyReportRepository.buildReport(...)` 追加 `instant_translate` module
   - `WeeklyReportView` 新增说写秒译专项展示区
   - 周报专项展示标题、图例、语言组合和时段名称按当前展示语言渲染
   - 周报专项卡片、图表标签和 chip 支持四档展示大小同步缩放

### 10.2 后端任务

1. 新增 `ReadingInstantTranslateCompatController`
2. 新增 `ReadingInstantTranslateService`
3. 新增 DTO：
   - `InstantTranslateCapabilityView`
   - `InstantTranslateDisabledReceipt`
   - `InstantTranslateEventRequest`
   - `InstantTranslateEventBatchRequest`
   - `InstantTranslateEventReceipt`
   - `InstantTranslateWeeklySummaryView`
4. 新增 MyBatis-Plus entity/mapper/crud service：
   - `ReadingInstantTranslateEventEntity`
   - `ReadingInstantTranslateEventMapper`
   - `ReadingInstantTranslateEventCrudService`
   - 可选 `ReadingInstantTranslateDailyStatEntity`
5. 新增迁移 SQL
   - 创建 `reading_instant_translate_event`
   - 可选创建 `reading_instant_translate_daily_stat`
   - 创建索引
   - 可选插入 `cloud_translate=0`、`cloud_stt=0`
6. 新增接口
   - `GET /api/v1/translation/instant/capability`
   - `POST /api/v1/translation/instant`
   - `POST /api/v1/translation/instant/events/batch`
   - `GET /api/v1/translation/instant/weekly-summary`
7. 更新 OpenAPI 注解和测试

### 10.3 同步与周报任务

1. `source_page` 增加约定值：`say_write_translate`
2. 本地周报统计将其归入「有效练习」
3. 周报新增「说写秒译」专项模块，展示总次数、日均频次、语言组合、时段分布
4. 本地 weekly summary 必须优先使用 `reading_instant_translate_event` 重算，聚合表仅作性能缓存
5. 后端同步仅传元数据，失败不影响本地周报生成

## 11. 测试与验收

### 11.1 iOS 验收

- 无儿童档案时，点击「说写秒译」不能进入。
- 有儿童档案时，可以进入页面。
- 展示语言切换为 `zh-Hans`、`en`、`ja`、`ko`、`es` 后，入口卡、页面标题、按钮、权限提示、错误提示、语言组合名称和周报专项文案均显示为当前选择语言。
- 未来新增展示语言时，只补 `AppLocaleCatalog` 与 `InstantTranslateCopy` 文案，不需要改说写秒译业务状态、SQLite 字段或后端 payload。
- 展示大小切换 `small`、`medium`、`large`、`extraLarge` 后，输入区、转写区、译文区、操作按钮、周报图表标签即时同步缩放。
- 首次语音输入会请求麦克风和语音识别权限。
- 权限拒绝后，不崩溃，可切换文字输入。
- 开启飞行模式、且语言包已下载时，文字翻译可用。
- 设备端语音识别包不可用时，语音模式提示明确，不回退云端。
- 翻译语言包缺失时，出现系统下载提示流程。
- 译文朗读声音、速度、额度扣减与学习详情页一致。
- 朗读额度不足时，使用现有额度耗尽弹窗。
- 保存句卡后，复习列表能看到新句卡。
- 离开页面后录音停止、朗读停止、使用会话结束。
- 未保存句卡时，`reading_instant_translate_event` 只有元数据，没有原文、译文、音频或转写全文。
- 保存句卡后，事件表只更新 `saved_as_card` 和 `saved_card_id`，正文仍只在现有句卡加密链路内保存。
- 周报中能看到说写秒译总次数、日均次数、语言组合分布和时段分布。
- 修改测试数据后重新生成周报，展示值与 SQLite 明细统计一致。

### 11.2 后端验收

- `GET /api/v1/translation/instant/capability` 返回 device-only。
- `POST /api/v1/translation/instant` 当前返回 disabled，不调用 provider，不落正文。
- `POST /api/v1/translation/instant/events/batch` 能幂等接收元数据。
- 元数据接口收到 `sourceText`、`translatedText`、`audioBase64` 等字段时拒绝请求。
- `GET /api/v1/translation/instant/weekly-summary` 返回聚合数据，且与事件表 SQL 统计一致。
- 新事件表不包含原文/译文字段。
- 删除孩子或账号时，事件数据遵循现有留存/删除策略。
- Flyway 初始化和本地测试库可通过。

### 11.3 合规验收

- `Info.plist` 权限文案说明本设备处理、不上传开发者服务器。
- `PrivacyInfo.xcprivacy` 如新增需要声明的 Required Reason API，再同步更新。
- 隐私政策若产品文案对语音输入有显著变化，需补充「设备端语音转文字」说明。
- 当前 UI 不出现云端翻译、云端语音识别入口。
- 产品运营分析只能使用聚合元数据，不得要求导出具体翻译内容。

## 12. 风险与处理

- 风险：部分语言不支持设备端语音识别。
  - 处理：进入语音模式前检查 `supportsOnDeviceRecognition`，不可用时提示下载系统语言/听写资源或切换文字输入。
- 风险：Translation 语言包下载由系统控制，无法给真实字节进度。
  - 处理：沿用现有估算进度和 `prepareTranslation()` 成功/失败校准。
- 风险：录音和朗读都使用 AVFoundation，音频会话可能冲突。
  - 处理：录音停止后释放 `AVAudioEngine`，朗读前让 `TTSService` 重新配置 `.playback`。
- 风险：复制 `LearningDetailView` 翻译逻辑导致后续维护困难。
  - 处理：优先抽取 `InstantTranslationCoordinator`，至少将语言包提示文案和状态机集中。
- 风险：保存翻译历史会扩大儿童数据面。
  - 处理：首版不保存历史；如后续需要，必须本机加密且提供删除入口。
- 风险：日聚合表与明细事件表出现不一致。
  - 处理：明细事件表作为唯一可信来源，周报生成前按版本或校验结果重建聚合表。
- 风险：短文本指纹存在字典反推风险。
  - 处理：默认不写指纹；确需排重时只写本机 Keychain 密钥 HMAC 截断值，且不向后端同步。
- 风险：新页面只覆盖中文文案，用户切换展示语言后出现中英混杂。
  - 处理：所有说写秒译文案集中在 `InstantTranslateCopy`，评审时按 key 清单逐项检查入口、页面、弹窗、周报和空状态。
- 风险：大字号下图表、按钮或输入框发生截断。
  - 处理：固定高度改为 `minHeight`，周报图例允许换行，四档展示大小和窄屏作为验收必测项。

## 13. 推荐落地顺序

1. 前端入口和儿童档案准入。
2. `InstantTranslateCopy` 文案表与展示大小缩放规则。
3. `SpeechRecognitionService` 与语音权限。
4. `SayWriteTranslateView` 文字输入、翻译、朗读闭环。
5. 接入语音输入转写。
6. 保存句卡与使用会话统计。
7. 后端 capability/disabled 兼容接口。
8. App 端低敏事件表与日聚合表。
9. 周报自动聚合和专项展示。
10. 后端元数据批量同步和服务端周报摘要接口。
11. 成就、运营后台或更多报表增强。

第一版 MVP 至少完成 1-9，确保 App 端可离线记录说写秒译元数据、按用户展示语言和展示大小正确渲染，并生成周报专项统计；后端元数据同步可作为登录态或运营分析增强延后。
