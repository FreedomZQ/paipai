# Paipai Read Along - iOS App

拍拍伴读 iOS 应用重构项目

## 项目概述

基于 paipai/files 下的 HTML 设计规范，实现首发支持 iPhone、iPad 的原生 SwiftUI 应用。

## 技术栈

- **UI 框架**: SwiftUI（原生响应式，首发适配 iPhone/iPad）
- **OCR**: Vision 框架 (VNRecognizeTextRequest) - iOS 18.0+
- **智能分句**: NaturalLanguage 框架 (NLTokenizer) - iOS 18.0+
- **翻译**: Translation 框架 - 首发按 iOS 18.0+ 收口，降低 iOS 17.x 兼容风险
- **语音朗读**: AVFoundation (AVSpeechSynthesizer)
- **最低系统要求**: iOS 18.0, iPadOS 18.0

## 项目结构

```
PaipaiReadAlong/
├── App/
│   └── PaipaiReadAlongApp.swift      # App 入口
├── Core/
│   ├── Models/                         # 数据模型
│   │   ├── AuthModels.swift
│   │   ├── ChildModels.swift
│   │   ├── ReviewCardModels.swift
│   │   ├── AccountModels.swift
│   │   ├── SubscriptionModels.swift
│   │   ├── OCRModels.swift
│   │   └── AnnouncementModels.swift
│   └── Services/                       # 业务服务层
│       ├── DeviceInfoService.swift
│       ├── OCRService.swift
│       ├── TranslationService.swift
│       └── TTSService.swift
├── Features/                           # 功能模块
│   ├── Splash/
│   │   └── SplashView.swift
│   ├── Onboarding/
│   │   └── OnboardingView.swift
│   ├── Home/
│   │   └── HomeView.swift
│   ├── Capture/
│   │   └── CaptureView.swift
│   ├── Learning/
│   │   └── LearningDetailView.swift
│   ├── Review/
│   │   └── ReviewView.swift
│   ├── Parent/
│   │   └── ParentAreaView.swift
│   ├── Paywall/
│   │   └── PaywallView.swift
│   ├── ReadingPark/
│   │   └── ReadingParkView.swift
│   └── Components/
│       └── AnnouncementView.swift
├── Design/                             # 设计系统
│   ├── Theme.swift
│   └── Components/
│       ├── Buttons.swift
│       └── Cards.swift
└── Resources/                          # 资源文件
    ├── Info.plist
    ├── PaipaiReadAlong.entitlements
    └── PrivacyInfo.xcprivacy
```

## 功能特性

### 已实现功能

1. **启动页 (Splash)**
   - 渐变背景动画
   - Logo 和 App 名称展示
   - 加载指示器

2. **引导页 (Onboarding)**
   - 多页滑动引导
   - 隐私政策同意弹窗
   - 设备信息收集说明

3. **首页 (Home)**
   - 权益展示模块
   - 孩子信息模块
   - 今日进度统计
   - 伴读节奏展示
   - 最近保存句卡

4. **拍照与 OCR**
   - 相机预览(iPhone/iPad)
   - 相册选择
   - Vision 框架文字识别
   - 识别结果编辑

5. **学习详情**
   - 原文和译文展示
   - 语音朗读
   - 朗读速度调节
   - 保存句卡

6. **复习模块**
   - 今日待复习列表
   - 句卡展示
   - 掌握程度标记
   - 复习完成统计

7. **家长区**
   - 家长验证(数学题)
   - 孩子管理
   - 使用时长统计
   - 设置选项

8. **伴读乐园**
   - 连续学习天数
   - 成就系统
   - 学习提示

9. **付费墙**
   - 会员权益展示
   - 订阅方案选择
   - 恢复购买

10. **通知弹窗**
    - 多种类型通知
    - 频率控制
    - 富文本支持

### 核心服务

- **设备信息服务**: 检测设备类型、系统版本,隐私同意管理
- **OCR 服务**: Vision 框架文字识别,智能分句
- **翻译服务**: Translation 框架,自动语言检测
- **TTS 服务**: AVSpeechSynthesizer 语音朗读
- **使用时长监控**: 记录使用时长,30天自动删除

## 设备适配

### iPhone
- 竖屏为主
- 底部导航栏
- 触摸友好的按钮尺寸

### iPad
- 支持横竖屏
- 分栏布局
- 更大的内容区域


## 离线功能

- OCR 文字识别完全离线(Vision 框架)
- 语音朗读完全离线(AVSpeechSynthesizer)
- 本地数据缓存
- 已登录后，账号权益与购买记录可刷新

## 合规与隐私

- 隐私清单文件 (PrivacyInfo.xcprivacy)
- 隐私政策同意弹窗
- 数据最小化原则
- 使用记录主口径为 `UsageSessionRepository` 本地表；家长区展示今日、最近 7 天和累计统计，30 天留存策略仍以后端/上线验收为准
- 付费页增长文案从后端 `reading_paywall_growth` 远程配置读取，本地保留 fallback；真实价格与扣款金额仍以 StoreKit / Apple 确认弹窗为准
- 公告按 `scene + locale + appVersion + planCode` 拉取；公告 action 仅允许安全 scheme（全局弹窗允许 `https` / `mailto`，支持中心列表仅渲染 `https`）

## 构建说明

### P0 静态预检

```bash
cd /Users/zhangqi/工作/APP/uniAPP_IOS/paipai/ios
./scripts/p0_static_preflight.sh
```

该脚本会检查首发 target 是否仍收口为 iOS/iPadOS 18.0+、已知编译风险符号是否残留、PrivacyInfo 是否可解析、App 内置法务文档是否存在。

### 发布态 project.yml 渲染与归档预检

归档前不要直接使用带 `__FILL_FROM_DB_release_ios.*__` 占位符的开发态 `project.yml`。先从后端 `release_ios` 命名空间取值后注入环境变量，生成发布态配置：

```bash
cd /Users/zhangqi/工作/APP/uniAPP_IOS/paipai/ios
export RELEASE_IOS_BUNDLE_IDENTIFIER="com.paipai.readalong"
export RELEASE_IOS_DEVELOPMENT_TEAM="你的10位AppleTeamID"
export RELEASE_IOS_MARKETING_VERSION="1.0.0"
export RELEASE_IOS_CURRENT_PROJECT_VERSION="1"
export RELEASE_IOS_PAIPAI_API_BASE_URL="https://你的正式后端域名"
./scripts/render_release_project_yml.py --source project.yml --output project.release.yml
./scripts/p0_archive_preflight.sh project.release.yml
```

通过后用发布态配置生成 Xcode 工程，不要覆盖开发态 `project.yml`：

```bash
xcodegen generate --spec project.release.yml
```

或者直接在 Mac 开发机运行归档入口脚本：

```bash
./scripts/p0_archive_build.sh
```

脚本会依次执行静态预检、渲染 `project.release.yml`、发布态预检、`xcodegen generate --spec` 和 `xcodebuild archive`。建议在独立 release 分支或临时工作树执行，避免把正式 Team/API 配置混入本地开发。

### 使用 xcodegen 生成项目

```bash
cd /Users/zhangqi/工作/APP/uniAPP_IOS/paipai/ios
xcodegen generate
```

### 在 Xcode 中打开

```bash
open PaipaiReadAlong.xcodeproj
```

### 配置要求

- Xcode 16.0+(建议使用最新稳定版,匹配 iOS 18 SDK)
- iOS 18.0+ / iPadOS 18.0+
- Swift 5.10

## 注意事项

1. **翻译功能**: 首发按 iOS 18.0+ 验收设备端翻译,避免对 iOS 17.x 做未验证承诺
2. **语言包下载**: Translation 框架语言包按需下载,首次使用需要网络
3. **首发平台**: XcodeGen target 已收口为 iOS；Mac/Catalyst 需单独完成编译和 UX 验收后再开启
4. **后端兼容性**: 确保所有 API 调用与统一后端保持兼容

## 上线前验收事项

1. 在 Mac / Xcode 16+ 环境执行 `xcodegen generate --spec project.release.yml` 与 `xcodebuild archive`，完成真机或模拟器主链路验收
2. 用 App Store Sandbox 验证家庭版购买、恢复购买、后端权益刷新和 Paywall fallback 文案
3. 验证 Apple 登录、账号删除验证码、法务文档链接、支持反馈、公告 action 与隐私同意链路
4. 验证本地 OCR / 朗读 / 句卡保存 / 复习 / 家长区 usage 统计 / 本地数据在 iPhone 与 iPad 上的完整闭环
5. Mac/Catalyst 仍作为后续专项评估，不纳入当前 iOS/iPadOS 18.0+ 首发承诺
6. 补齐单元测试和 UI 自动化测试；当前仓内静态预检不替代 Xcode 编译验收

## 许可证

Copyright © 2024 Paipai Read Along. All rights reserved.
