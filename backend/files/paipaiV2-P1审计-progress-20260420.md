# paipaiV2 P1 审计进度（2026-04-20）

## 当前结论
- backend：P1 本轮代码已落地，`mvn -q verify` 通过。
- frontend：P1 主链路已完成静态接线与代码审计；当前环境无 Xcode，尚未完成 iOS 编译/真机/模拟器验证。

## 已完成项

### P1-1 家长区设备级验证与 fallback
- 新增 `Core/Services/ParentGateService.swift`
- `ParentAreaView` 改为通过服务调用 `LocalAuthentication` 的 `.deviceOwnerAuthentication`
- 系统鉴权失败时保留数学题 fallback
- `Info.plist` 已补 `NSFaceIDUsageDescription`

### P1-2 删除账号二次确认（邮箱验证码）
- backend 删除验证码链路已完成并已在 P0 收口
- 前端新增：
  - `Features/Parent/DeleteAccountView.swift`
  - `Features/Components/VerificationCodeInputView.swift`
- 支持邮箱验证码发送 / 输入 / 删除确认
- 增加 fallback 指引：可跳转删除帮助页 / support 邮箱

### P1-3 动态计划 / 语言偏好 / 权益展示
- backend `plans()` 已从单一 lifetime 包改为多计划动态返回
- `PlanView` 新增字段：
  - `historyEnabled`
  - `supportedLocales`
  - `supportedLearningTrackCodes`
- 前端新增 `Features/Parent/LanguagePreferenceView.swift`
- Paywall 已改为动态展示：
  - childLimit
  - dailyLocalOcrLimit
  - historyEnabled
  - supportedLocales
  - supportedLearningTrackCodes
- 语言偏好与界面语种可写回 preferences

### P1-4 使用时长统计增强
- backend `ReadingUsageService` 新增最近 7 天 usage 聚合
- `ChildUsageSummaryView / FamilyUsageSummaryView` 已扩展 `recentDailyUsage`
- 前端 `UsageModels.swift` 已同步扩展
- `ParentAreaView` / `ManageChildrenView` 已展示最近 7 天 usage
- `CaptureView` 已补 usage session start / end

### P1-5 隐私清单与法务页面
- `Info.plist` 已收口：
  - 相机
  - 相册
  - Face ID
- `PrivacyInfo.xcprivacy` 已收口：
  - PhotosorVideos
  - EmailAddress
  - UserID
  - ProductInteraction
  - 无 tracking / 无 DeviceID
- 新增法务 HTML：
  - `backend/files/privacy-policy.html`
  - `backend/files/terms-of-service.html`
  - `backend/files/child-data.html`
- 同步复制到：
  - `src/main/resources/static/legal/`
- `/api/v1/legal/docs` 已支持相对路径转绝对 URL
- `ReadingPublicCompatControllerWebMvcTest` 已通过

## 本轮通过的验证
- `mvn -q verify` ✅
- `mvn -q -Dtest=ReadingPublicCompatControllerWebMvcTest test` ✅
- 前端静态接线检查 ✅
  - ParentGateService
  - DeleteAccountView
  - LanguagePreferenceView
  - Capture usage session
  - Paywall dynamic fields
  - recentDailyUsage 展示

## 当前剩余 blocker
- 无 Xcode / swiftc / xcodebuild 环境
- 因此尚未完成：
  - iOS 编译验证
  - Simulator smoke test
  - 真机回归验证

## 下一步建议
1. 在 Mac / Xcode 环境执行 iOS 编译
2. 逐项验证：
   - 家长区设备验证
   - 删除账号验证码
   - 语言偏好切换
   - Paywall 动态权益
   - capture / learning / review usage
   - legal docs 打开
3. 编译通过后，再进入 P1 最终验收
