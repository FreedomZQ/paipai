# Paipai Read Along - iOS App

拍拍伴读 iOS / iPadOS 首发版本采用无登录、无个人开发者自有后端、无第三方分析广告 SDK 的本地优先方案。

## 技术栈

- UI：SwiftUI
- OCR：Vision `VNRecognizeTextRequest`
- 分句：NaturalLanguage `NLTokenizer`
- 翻译：Apple Translation framework，按系统语言包能力运行
- 朗读：AVFoundation `AVSpeechSynthesizer`
- 购买：StoreKit 2 Consumable IAP
- 本机钱包：Keychain + CryptoKit
- 最低系统：iOS 18.0 / iPadOS 18.0

## 数据与隐私

拍拍伴读默认在当前设备上处理学习内容。拍照识字、朗读、学习记录、生词本和历史记录不会上传到开发者服务器。

本机积分保存在当前设备的系统 Keychain 中，购买或赠送的积分不会按日期过期。删除 App 后，同一设备通常可以恢复本机积分；但更换设备、抹掉设备、系统清理 Keychain 或家长主动重置本机钱包后可能无法恢复。

每日免费次数不是付费积分，卸载或清除数据后不会恢复。购买、恢复/刷新购买、外链、法律文档、支持入口、本地数据删除和本机钱包重置均放在家长门后。

## 首发边界

- 不创建 App 自有账号，不要求 Sign in with Apple。
- 不配置 `PAIPAI_API_BASE_URL`，生产路径不请求个人开发者后端。
- 不上传孩子照片、音频、OCR 文本、句卡正文、孩子档案或设备标识。
- 不接入 Firebase、友盟、AppsFlyer、Adjust、广告 SDK、IDFA、ATT 或跨 App 跟踪。
- 不展示或售卖云端 API 积分。
- 不承诺消耗型本机积分跨设备自动恢复。

## 本机积分

首发只提交本机积分 Consumable IAP：

- `com.paipai.readalong.local.ocr.100`
- `com.paipai.readalong.local.ocr.300`
- `com.paipai.readalong.local.tts.100`
- `com.paipai.readalong.local.tts.300`

购买成功后，App 只接受 StoreKit verified transaction，按 `Transaction.id` 的哈希幂等发放到本机 Keychain 钱包。钱包写入成功后才调用 `transaction.finish()`。恢复/刷新购买只处理 `AppStore.sync()`、`Transaction.unfinished` 和当前设备 Keychain 钱包，不启用 `SKIncludeConsumableInAppPurchaseHistory`。

## 构建与预检

```bash
cd /Users/zhangqi/工作/APP/拍拍伴读/paipai/front/ios
./scripts/p0_static_preflight.sh
```

发布态配置通过环境变量生成，不需要后端 URL：

```bash
export RELEASE_IOS_BUNDLE_IDENTIFIER="com.paipai.readalong"
export RELEASE_IOS_DEVELOPMENT_TEAM="你的10位AppleTeamID"
export RELEASE_IOS_MARKETING_VERSION="1.0.0"
export RELEASE_IOS_CURRENT_PROJECT_VERSION="1"
./scripts/render_release_project_yml.py --source project.yml --output project.release.yml
./scripts/p0_archive_preflight.sh project.release.yml
xcodegen generate --spec project.release.yml
```

归档入口：

```bash
./scripts/p0_archive_build.sh
```

## App Review Notes

```text
This Kids Category app uses on-device processing by default. OCR, read-aloud, learning records, and local credit balances are stored only on the user's device. The app does not require login, does not use a developer-operated backend for account or credit recovery, and does not integrate third-party analytics, advertising, IDFA, or tracking SDKs.

Local credits are consumable In-App Purchases used for on-device OCR/read-aloud features. Purchased credits do not expire by date. They are stored in the iOS Keychain for same-device persistence. The restore/refresh purchase button is behind a parental gate and processes unfinished StoreKit transactions plus same-device local wallet state. The app does not claim cross-device restoration of consumable credits.

All purchase opportunities, restore actions, external links, and data reset actions are behind a parental gate.
```

## 上线前验收

1. Xcode 真编译、真机或模拟器主链路验收：引导、创建孩子、拍照/选图、设备端 OCR、设备端朗读、保存句卡、复习、家长区、本地数据删除、本机钱包重置。
2. StoreKit Sandbox 验证四个本机积分商品：购买成功、取消、pending、未完成交易补发、重复交易不重复发放、恢复/刷新无新增交易文案。
3. 抓包确认默认学习流程不请求个人开发者后端，孩子图片、音频、OCR 文本和句卡正文不离设备。
4. App Store Connect 隐私标签、Kids Category 设置、IAP 元数据、App Review Notes、隐私政策、儿童数据说明和服务条款保持一致。
5. 欧盟上架前完成 DSA trader 自评；如不能接受联系信息展示，先不要在欧盟 27 个地区销售。
