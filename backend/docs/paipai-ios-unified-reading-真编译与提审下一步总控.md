# Paipai iOS × Unified Backend Reading 下一步总控单

## 1. 文档定位

这是一份总控文档，用于把下面三份文件串起来：

1. `paipai-reading-联调清单.md`
2. `paipai-ios-macos-xcode-真编译与联调执行单.md`
3. `reading-ocr-provider-接入位点设计.md`
4. `paipai-AppStore-提审-blocker清单.md`

如果你只想看“一句话下一步怎么推进”，直接看本文件。

---

## 2. 当前状态摘要

### 代码状态
- unified backend 的 reading 目录内付费内容接口已补齐
- reading 业务表已新增
- deletion / entitlement / children / review / weekly / OCR / feedback 主链路已回到后端
- `mvn -q clean verify` 已通过

### 联调状态
- Linux 环境：静态对齐完成
- macOS + Xcode：未开始真编译验证

### 提审状态
- 仍未达到可直接提审状态
- 剩余 blocker 主要在外部条件，不在 Java 编译层

---

## 3. 下一步最优执行顺序

### Phase 1：真编译与真联调
先执行：

- `paipai-ios-macos-xcode-真编译与联调执行单.md`

目标：
- 把 iOS 真编译跑通
- 确认前端不再残留本地绕过路径
- 确认 reading 后端接口与 Swift 模型真实匹配

### Phase 2：决定 OCR 提审策略
再执行：

- `reading-ocr-provider-接入位点设计.md`

目标：
- 决定提审前是否接真 OCR provider
- 若不接，收紧产品文案与审核口径

### Phase 3：清 App Store blocker
最后执行：

- `paipai-AppStore-提审-blocker清单.md`

目标：
- support/privacy/terms/delete-account 页面上线
- support 邮箱可收信
- Apple Sign In / IAP 真配置完成
- 形成提审可交付状态

---

## 4. 推荐执行原则

### 原则 1：reading 业务继续只在 reading 目录里扩
不要把 reading 的 children / review / OCR / weekly report 再拆进其它项目域。

### 原则 2：付费边界必须以后端为准
任何涉及：
- 多孩子
- 周报范围
- OCR 权限
- 购买恢复

都必须继续由 reading 后端权威判断。

### 原则 3：先稳上架，再做高风险增强
对于 OCR 这种高风险输入能力，建议：
- 先保证鉴权 / 审计 / quota
- 再决定是否引入真 provider

---

## 5. 给你的最短结论

### 如果你现在只问一句“下一步先做什么？”
答案是：

**先去 macOS + Xcode 跑真编译和真联调。**

因为现在最大的未知数已经不是 Java 后端，而是：

- iOS 真编译是否通过
- 真交互下是否还有本地绕过
- 真实 support/legal/Apple/IAP 条件是否就绪
