# paipai

拍拍伴读。

## iOS 首发合规口径

当前 iOS / iPadOS 首发版本采用无登录、无个人开发者自有后端、无第三方分析广告 SDK 的本地优先方案。拍照识字、朗读、句卡、学习记录和本机积分默认只保存在当前设备，不上传到开发者服务器。

本机积分通过 StoreKit 2 Consumable IAP 购买，余额保存在当前设备 Keychain。购买或赠送的积分不按日期过期，但使用后会扣减；消耗型本机积分不承诺跨设备自动恢复。

详见 [front/ios/README.md](/Users/zhangqi/工作/APP/拍拍伴读/paipai/front/ios/README.md)。
