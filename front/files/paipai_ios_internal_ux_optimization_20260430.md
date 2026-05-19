# Paipai iOS 内部页面 UX 优化记录（2026-04-30）

## 范围

本轮只做 iOS SwiftUI 前端页面布局、交互可用性与视觉舒适度优化；未改后端接口、数据结构、业务流程或认证/计费逻辑。

项目路径：`/home/admin/code/app/paipai/ios/PaipaiReadAlong`

## P0：基础可用性与不阻断使用

- 家长验证改为只保留算题验证。
  - `Core/Services/ParentGateService.swift` 保留为空兼容文件。
  - 已移除 `LocalAuthentication` / `LAContext` / `deviceOwnerAuthentication` 实现残留。
- 根视图增加全局错误弹窗。
  - 异步任务写入 `appState.errorMessage` 后，用户能看到明确反馈，不再“点了没反应”。
- 全局按钮与按钮样式统一触控目标。
  - `PrimaryButton` / `SecondaryButton` / `IconButton` / `CloseButton` / `BackButton` 均提升到 44pt+ 触控热区。
  - 旧 `.primaryButton()` / `.secondaryButton()` modifier 也同步提升最小高度与 content shape。
- 输入框和键盘行为完善。
  - 家长算题、验证码、OCR 确认、反馈页等输入区补充键盘类型、Done toolbar 或滚动时收起键盘。
  - 多行输入统一使用 `appTextEditorSurface`。
- 小屏横排挤压处理。
  - 首页权益/成长统计、家长引导 chips、OCR 识别模式、孩子用量 chips 等改为 `ViewThatFits` 或 `LazyVGrid`，空间不足自动换行/竖排。
- 关键播放/识别/切换等图标按钮补足 44pt 可点击区域。

## P1：页面流畅度与一致性

- 全局卡片阴影减重，降低滚动列表中的视觉负担与层级噪音。
- 列表页统一隐藏系统滚动背景并使用 App 背景色。
  - 孩子档案页等列表视觉更一致。
- 孩子档案页行内容适配小屏。
  - 孩子信息标题区用 `ViewThatFits`。
  - 今日/累计/本周用量 chips 用自适应网格。
- 支持与隐私页优化。
  - 邮箱、链接、法律文档入口补充 44pt+ 行高。
  - 反馈 TextEditor 增加占位提示与统一输入框样式。
- OCR 确认页优化。
  - 识别模式横排空间不足时自动竖排。
  - TextEditor 使用统一多行输入样式。

## P2：视觉舒适度与细节 polish

- 同步状态 badge 降低阴影强度并设置最小高度，避免悬浮组件过重。
- 公告弹层最大宽度、横向边距、阴影强度优化，适配 iPad/Mac 宽屏。
- InfoCard/UsageItem 补充最小高度、单行缩放，减少数字/标题挤压。
- 家长区、首页等 chips/卡片保持统一圆角、轻背景和可读间距。

## 修改文件

- `ios/PaipaiReadAlong/App/PaipaiReadAlongApp.swift`
- `ios/PaipaiReadAlong/Core/Services/ParentGateService.swift`
- `ios/PaipaiReadAlong/Design/Theme.swift`
- `ios/PaipaiReadAlong/Design/Components/Buttons.swift`
- `ios/PaipaiReadAlong/Design/Components/Cards.swift`
- `ios/PaipaiReadAlong/Features/Capture/CaptureView.swift`
- `ios/PaipaiReadAlong/Features/Components/AnnouncementView.swift`
- `ios/PaipaiReadAlong/Features/Home/HomeView.swift`
- `ios/PaipaiReadAlong/Features/Learning/LearningDetailView.swift`
- `ios/PaipaiReadAlong/Features/Parent/ManageChildrenView.swift`
- `ios/PaipaiReadAlong/Features/Parent/ParentAreaView.swift`
- `ios/PaipaiReadAlong/Features/Parent/SupportAndPrivacyView.swift`
- `ios/PaipaiReadAlong/Features/Parent/SyncSettingsView.swift`
- `ios/PaipaiReadAlong/Features/Parent/SyncStatusBadgeView.swift`

## 已执行检查

- Swift 文件大括号/圆括号平衡检查：PASS
- P0 release/static preflight：PASS
- 自定义 UX 静态检查：PASS
  - parent math only
  - global error alert
  - adaptive content helper
  - standard text editor surface
  - button min tap target
  - home responsive fallback
  - capture keyboard dismissal
  - support feedback editor surface
  - manage children adaptive chips

## 当前环境限制

当前运行环境是 Linux，未安装 `xcodebuild` / `swiftc`，因此这里无法完成真实 iOS 编译、模拟器运行或 Archive 验证。当前结论仅限静态源码与项目预检通过。需要在 macOS/Xcode 环境继续执行一次真实编译与真机/模拟器冒烟测试。

## 建议 macOS 侧回归路径

1. 首页：切换孩子、查看权益、进入拍照/复习/乐园/家长区。
2. 拍照 OCR：重新拍照、重新识别、手动编辑文本、继续朗读。
3. 复习页：朗读、四个掌握程度按钮、小屏横向/竖向切换。
4. 家长区：算题进入、孩子列表、添加/编辑孩子、周报、订阅、法律文档、支持反馈。
6. iPhone SE / 标准 iPhone / iPad / Mac Catalyst 可调整窗口各跑一遍基础流程。

## 2026-04-30 16:55 追加复查与补丁

按“只做前端页面布局/交互优化，不碰后端逻辑”的边界再次复查后，追加补齐以下低风险体验项：

- 订阅页：
  - “恢复购买”按钮补充 `minHeight: AppLayout.minimumTapTarget` 与 `contentShape`，提升可点击区域。
  - 套餐卡片补充最小高度与 content shape，长价格增加单行缩放，降低本地化价格挤压风险。
  - 权益说明行补充最小高度，长文案支持换行。
- 语言偏好页：
  - “准备翻译语言包”按钮补充 44pt+ 触控高度和完整点击区域。
- 复习页：
  - “朗读”按钮补充明确点击区域。
  - 掌握程度按钮补充 content shape。
  - 复习完成页改为 ScrollView 容器，小屏和家庭版提示较长时不再容易纵向溢出。
  - 复习完成页统计行改为 `ViewThatFits`，空间不足时自动竖向排布。
  - “完成”文字按钮补充 44pt+ 点击高度。
- 孩子档案页：
  - 用量 chips 填充自适应网格单元，提升阅读一致性。

追加检查：

- Swift brace/paren balance：PASS
- no device auth remnants：PASS
- paywall restore tap target：PASS
- review completion scrollable：PASS
- language prepare tap target：PASS
- text editors standardized：PASS
- `ios/scripts/p0_static_preflight.sh`：PASS

限制仍然不变：当前 Linux 环境没有 `xcodebuild` / `swiftc`，真实 iOS 编译、模拟器、Archive 仍需在 macOS/Xcode 环境执行。

## 2026-04-30 17:22 追加前端页面展示升级

用户确认“上述内容全部可以开始做，仅完善升级 APP 前端页面展示，不修改后端逻辑”后，继续在 SwiftUI 前端展示层补齐剩余低风险项：

- Splash 首屏：
  - 使用统一 `AppLayout` 间距，减少固定底部空白。
  - 标题/副标题居中多行安全展示，提升小屏适配。
- Onboarding / 隐私同意 / Apple 登录页：
  - 跳过介绍、上一步、下一步、查看并同意、隐私政策、用户协议、关闭、重新选择语言等入口补足 44pt+ 点击热区与 `contentShape`。
  - 主要渐变按钮阴影稍微减重，保持和内部页面一致。
- 拍照页：
  - 中间文字取景框从固定宽度改为随容器宽度自适应，避免小屏横向挤压。
  - 相册/拍照按钮补齐 `.buttonStyle(.plain)` 与明确点击区域。
- 学习保存成功页：
  - “去伴读乐园轻量练习”文字按钮补足整行点击热区。
- 家长区：
  - Apple 登录按钮从固定高度改为最小高度 52。
  - App Store 更新按钮补充完整点击区域与 plain button style。
- 首页：
  - 激活步骤行补充最小阅读高度，长文案更稳定。
- 删除账号页：
  - 人工 fallback 链接补足整行 44pt+ 点击热区。
- 周报页：
  - 多孩子选择器：3 个以内继续 segmented；超过 3 个自动使用 menu，避免横向挤压。
  - 周报统计 chips、建议项、历史记录行补充最小高度和换行规则。
- 法律文档页：
  - 文档列表行补足 56pt 点击高度，使用 insetGrouped list 风格。
- 伴读乐园：
  - 统计卡和成就 badge 补充稳定高度与两行标题，减少网格跳动和挤压。

追加检查：

- Swift brace/paren balance：PASS
- no backend/auth device remnants：PASS
- parent Apple sign-in min height：PASS
- delete account support links tap target：PASS
- capture buttons explicit style：PASS
- home activation steps min height：PASS
- onboarding primary actions content shape：PASS
- weekly child picker adaptive：PASS
- weekly stat chips min height：PASS
- legal docs list row tap height：PASS
- reading park grid stable heights：PASS
- all text editors standardized：PASS
- `ios/scripts/p0_static_preflight.sh`：PASS

仍未做也不应在当前 Linux 环境声称完成：真实 Xcode 编译、模拟器 UI 回归、Archive/TestFlight 验证。
