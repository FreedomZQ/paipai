# Paipai App Store 提审 Blocker 清单

## 1. 文档目的

本清单用于明确：

1. 当前 unified backend reading + Paipai iOS 联调工作做到哪里；
2. 哪些 blocker 还没有清零，不能把“代码已写完”误判为“已经可提审”；
3. 哪些 blocker 属于代码层，哪些 blocker 属于外部配置 / 域名 / 法务 / Apple 平台条件；
4. 如何按优先级推进，减少个人开发者上架风险。

---

## 2. 当前总体判断

### 2.1 代码层 blocker

**已大幅减少。**

目前 reading 目录内已经补齐了：

- auth
- account state / deletion
- children
- review
- daily task
- weekly report
- feedback
- OCR route
- subscription status / entitlement refresh / intake

同时 unified backend 当前：

```bash
cd /home/admin/code/app/backend && mvn -q clean verify
```

已通过。

### 2.2 提审层 blocker

**仍存在。**

主要集中在：

- iOS 真编译与真机/Simulator 联调还没完成
- OCR 真实 provider 未接
- support/privacy/terms/child-data/delete-account 公网页面未确认真实上线
- support 邮箱未确认真实可收信
- Apple Sign In / IAP 商品 / App Store Connect 真实配置未确认

---

## 3. P0 Blocker（不清零就不要提审）

## P0-1：iOS 真编译未完成

### 状态
未完成。

### 原因
当前 Linux 环境没有：

- Swift
- XcodeGen
- Xcode
- iOS Simulator

### 风险
如果不做真编译，可能出现：

- Codable 字段不匹配
- async 状态切换异常
- project.yml / Info.plist 配置错误
- 真机下 session / Keychain 行为异常

### 结论
**必须在 macOS + Xcode 下完成真编译和真联调。**

---

## P0-2：删除账号公开说明页 / 实际流程必须可用

### 状态
代码层已补后端删除动作；公网说明页是否真实可访问，未确认。

### 必须满足
- App 内存在删除账号入口
- 删除动作真实有效，不是假按钮
- 公开说明页可访问，例如：
  - `https://www.paipai.app/delete-account`

### 风险
App Review 会直接看：

- 是否有删除入口
- 是否能执行
- 是否有公开说明

### 结论
**删除账号相关页面与流程必须完成真环境验证。**

---

## P0-3：support / privacy / terms / child-data 页面必须真实上线

### 当前目标地址
- `https://www.paipai.app/support`
- `https://www.paipai.app/privacy`
- `https://www.paipai.app/terms`
- `https://www.paipai.app/child-data`
- `https://www.paipai.app/delete-account`

### 风险
如果这些 URL 只是代码里写了，但公网访问不到：

- App Review 会直接判为不完整
- Support / Privacy 信息会被视作占位

### 结论
**这些页面必须真实可访问，且与 App 内跳转一致。**

---

## P0-4：support 邮箱必须真实可收信

### 当前目标邮箱
- `support@paipai.app`

### 风险
- 用户投诉无法接收
- App Review 可能抽查支持方式
- 对个人开发者是直接运营风险

### 结论
**邮箱必须能真实收信，并最好已有值守能力。**

---

## P0-5：Apple Sign In 真配置必须完成

### 需要确认
- Team ID
- Key ID
- Private Key
- Redirect URI
- Service ID / Bundle 配置
- `https://api.paipai.app/auth/apple/callback` 可用

### 风险
如果 Apple Sign In 只是接口预留但真配置未完成：

- Apple 登录路径会在真实环境失败
- App Review 可能直接卡住登录/家长区链路

### 结论
**Apple Sign In 生产配置是提审前 P0。**

---

## P0-6：IAP / 商品 / entitlement 真实配置必须完成

### 当前商品重点
建议以当前 reading 策略为主，优先围绕：

- `family_multi_child_lifetime`
- productId：`com.paipai.readalong.family.multi_child.lifetime`

### 需要确认
- App Store Connect 中商品已创建
- 商品状态可售
- sandbox / production 配置正确
- 后端 projection 与 product mapping 对齐

### 风险
- Paywall 显示了价格但商品不可买
- 恢复购买后权益不刷新
- productId 与 entitlementCode 对不齐

### 结论
**IAP 真配置未完成前，不建议提审。**

---

## 4. P1 Blocker（建议在提审前尽量清掉）

## P1-1：OCR 还不是“真识别”

### 当前状态
已接管：
- 鉴权
- quota
- audit

未完成：
- 真 OCR provider

### 风险
如果产品文案或提审材料把 OCR 写成稳定可用核心能力，但实际上只返回 `manual_fallback/not_configured`：

- 用户体验会不一致
- 审核可能认为功能未完成

### 建议
二选一：

#### 方案 A
提审前接入真实 OCR provider。

#### 方案 B
如果来不及接入，就把产品文案和审核口径改成：
- OCR 不是承诺性核心卖点
- 当前版本主打句卡、复习、周报、多孩子权益
- OCR 只是受控输入辅助

### 当前建议
从低风险角度，**如果 OCR 还没接真 provider，就不要把它写成核心承诺卖点。**

---

## P1-2：前端仍需真联调确认“没有本地绕过口子”

### 当前状态
代码已开始收口，但未在 Xcode 真跑。

### 风险
静态改写不等于真机状态一定正确。

### 需要重点确认
- onboarding 创建孩子是否只走后端
- 保存句卡是否只走后端
- review event 是否只走后端
- OCR 是否必须有后端 session
- 无 session 时是否还能偷偷使用本地付费能力

---

## P1-3：真实部署域名 / HTTPS / 回调链路

### 需要确认
- `api.paipai.app`
- HTTPS 证书
- webhook 回调
- Apple redirect URI

### 风险
即使本地可跑，线上域名和证书若不正确，提审和真机都会失败。

---

## 5. P2 项（可在提审后继续优化，但最好有计划）

## P2-1：reading 业务域继续细化

当前已经有最小可用实现，但后续还可继续细化：

- children 规则
- review card 内容结构
- weekly report 文案策略
- feedback 工单流转
- OCR provider 接入

### 结论
不是当前提审硬 blocker，但建议继续在 `apps/reading` 内独立迭代。

---

## P2-2：指标 / 运营 / 风险观测

建议后续补充：

- deletion request 执行统计
- OCR audit 聚合观测
- entitlement refresh 失败率
- restore 成功率
- weekly report 空内容率

对个人开发者来说，这些会直接影响后续运维成本。

---

## 6. 当前推荐优先级

按最小风险提审顺序，建议：

### 第一步：先清 P0
1. macOS + Xcode 真编译
2. 删除账号真链路验证
3. support/privacy/terms/child-data/delete-account 页面上线
4. support 邮箱可收信
5. Apple Sign In 真配置
6. IAP 真配置

### 第二步：再看 OCR 策略
- 若要把 OCR 当卖点：接真 provider
- 若不接：收紧文案，不把 OCR 作为承诺性核心卖点

### 第三步：最后整理提审材料
- Review Notes
- Support URL
- Privacy URL
- Terms URL
- Child Data URL
- Delete Account URL
- 演示账号 / 演示路径说明

---

## 7. 当前结论

### 已经不是 blocker 的部分
- unified backend reading 目录内付费内容接口缺失
- deletion 后端假动作
- entitlement refresh 旧路径缺失
- 付费主链路继续完全依赖客户端本地状态

### 仍然是 blocker 的部分
- iOS 真编译 / 真联调
- 公网 support/legal/delete-account 资产
- support 邮箱
- Apple Sign In 真配置
- IAP 真配置
- OCR 是否作为真实卖点的最终策略

---

## 8. 结论一句话

如果今天问“代码是不是已经足够支撑 reading 付费内容回到后端？”——**是**。  
如果今天问“是不是已经可以直接提审？”——**还不是**，因为剩下的 blocker 已经主要不在 Java 代码里，而在 **Xcode 真联调 + 外部生产条件 + 审核资产**。 
