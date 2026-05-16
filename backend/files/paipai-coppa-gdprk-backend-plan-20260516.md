# 拍拍伴读 COPPA / GDPR-K 后端改造总方案

> 说明：这是技术改造方案，不是法律意见。COPPA、GDPR 及各成员国未成年人同意规则仍需律师复核。个人开发者作为 App 发布者、收款方或后端运营方时，通常仍可能是 COPPA 下的 operator、GDPR 下的 controller；本方案目标是把法律风险和云服务超额使用风险降到最低可控水平，而不是承诺“没有法律责任”。

## 1. 目标

把拍拍伴读从“强制登录 + 按用户记账 + 按需转发内容”升级为“单一对外入口 + 两个内部合规状态 + 匿名权益令牌 + 内容零留存 + 本地优先”的结构，满足以下目标：

- `Local Guest`：本地匿名状态，无需家长登录即可使用，不上传儿童内容，不采集诊断，不云同步，后端停止服务时仍可继续使用本地能力。
- `Parent Account`：家长授权状态，Apple 登录和家长同意后才开启云 OCR/TTS、云同步、购买恢复、补偿兑换和后端权威权益。
- 账号层只保留家长身份，不采集孩子身份。
- 本地 OCR / 语音默认离线可用。
- 百炼云端能力必须后端验证、预留、提交，不能超额使用。
- 后端不保存图片、原文、录音、对话历史。
- 补偿、权益、删除、审计全部可追溯，但不反推到儿童内容。
- 购买的本地设备 OCR / 本地设备语音权益可在签名快照有效期内离线使用；购买的云端权益必须依赖后端权威账本，后端不可用时不继续消耗云资源。

## 2. 法规基线

- COPPA 以 FTC 2025-04-22 发布的最终规则为基准，按一年的过渡期，项目应按 2026-04-22 作为强制收口日。
- GDPR Article 8 允许成员国把数字同意年龄设为 13-16 岁之间。
- 产品策略上，欧盟统一按 16 岁做保守阈值，成员国差异只作为后端配置项，不作为默认降级逻辑。

## 3. 现状判断

当前仓库已经有一些可复用的基础：

- `SysAuthController` 已经是 appCode 作用域的 Apple 登录入口。
- `ReadingWeeklyReportSnapshot` 已经避免保存原始句卡正文。
- `ReadingReviewCardEntity` 已经支持 `encryptedText`，说明客户端加密链路可继续沿用。
- `ReadingCloudUsageService` 已经把本地 / 云端权益分开了，但还是 `userId` 口径，且 `ensureQuota -> provider -> consume` 不是强原子。
- `ReadingDeviceEventService` 仍在收集设备型号、系统版本、IP 国家、payload，过重。
- `SysCompensationService` 仍把补偿码和 `userId` 直接绑定，和“匿名权益”目标不一致。
- `ReadingCompatService.createReviewCard()` 仍存在 legacy plaintext 兜底，生产环境必须移除。
- `ReadingBailianOcrProvider` / `ReadingBailianTtsProvider` 仍是后端代理内容路径，和“后端不接触内容”目标不一致。

## 4. 目标原则

1. 对外只保留一个主流程，本地匿名状态默认可直接使用；`Parent Account` 是内部家长授权态，用于云能力、同步、购买恢复、补偿和账号删除，孩子不单独登录，也不要求用户在两个账户间手动切换。
2. 后端只管权益、补偿、同意、审计，不管孩子内容。
3. 本地能力优先，云端能力强约束。
4. 所有可恢复内容都必须可加密、可过期、可撤销。
5. 所有记录都按 appCode 隔离，不能跨 App 串权。
6. 任何日志都不得落原文、图片、音频、设备标识。
7. 本地赠送权益和本地购买权益不能依赖后端实时在线；云端权益不能离线消耗。

## 5. 目标架构

| 层 | 组件 | 作用 |
| --- | --- | --- |
| 身份层 | Apple 登录 + 家长重认证 | 只识别家长，不识别孩子 |
| 同意层 | 亲权同意记录 + 区域年龄策略 | 记录 consent version 和国家阈值 |
| 权益层 | 匿名权益令牌 + 权益钱包 | 管理次数、订阅、补偿 |
| 本地层 | Keychain 加密计数器 + StoreKit 本地交易状态 + 后端签名快照 | 离线时继续本地服务 |
| 云端层 | 后端验证 + 短生命周期 capability token | 严禁超额，后端离线即不可用 |
| 审计层 | 低敏事件 + 过期清理 | 留痕但不留内容 |

### 5.0 产品模式

说明：以下 `Local Guest` 与 `Parent Account` 是内部实现状态，不是要求用户感知的两个账户。用户应看到的是单一主流程，触发云能力时自动进入家长授权流程。

#### Local Guest

定位：最低法律风险、最高离线可用性的默认模式。

允许能力：

- 本地设备图片文字识别。
- 本地设备语音播放。
- 本地句卡、本地学习记录、本地设置。
- 每日赠送的本地设备图片文字识别次数，仅当日有效。
- 每日赠送的本地设备语音播放次数，仅当日有效。
- 本地删除、导出、重置。

禁止能力：

- 不创建后端 `userId`。
- 不创建服务端长期设备 ID。
- 不上传图片、OCR 原文、音频、句卡正文、孩子档案。
- 不采集诊断日志。
- 不云同步。
- 不使用云端 OCR/TTS。
- 不兑换补偿码。
- 不做购买恢复和跨设备权益恢复。

风险边界：

- Local Guest 的本地计数器只能防普通误用，不能承诺绝对防篡改。
- 因为不调用付费云资源，本地计数器被绕过也不应造成开发者云成本损失。
- 不确定用户地区时仍按最严格隐私文案展示，但不因地区识别而采集额外网络指纹。

#### Parent Account

定位：需要家长授权、后端权威权益或第三方云处理时进入。

触发条件：

- 开启云端语音播放或未来云端图片文字识别。
- 开启云同步。
- 恢复购买。
- 兑换补偿。
- 查看或删除云端账号数据。
- 使用购买后需要跨设备恢复的权益。

要求：

- 使用 Apple 登录创建家长账号。
- 家长区、补偿、删除账号、开启云能力前做本地 Face ID / Touch ID + 简单知识题 step-up。
- 首次开启云能力前展示直接给家长的儿童隐私通知，记录 `parent_consent`、`jurisdiction_code`、`consent_version`、`consent_scope`。
- 直接通知必须至少说明：收集哪些儿童/家长数据、用于什么目的、是否向第三方披露、留存多久、如何访问/删除/撤回同意、是否涉及跨境传输。
- Apple 登录和本地 Face ID / Touch ID 只作为家长账号和在场 step-up，不得单独宣称已经满足 COPPA verifiable parental consent；涉及儿童个人信息出设备的云能力必须使用经律师确认的可验证父母同意路径。
- 云能力同意必须按能力拆分，至少区分 `cloud_tts`、`cloud_ocr`、`sync`、`diagnostics`。
- 撤回同意后立即停止对应能力，并进入删除或保留最小账务记录流程。

### 5.1 核心身份模型

- `parent_user_id`：Apple 登录得到的家长账号，只用于账号和支付。
- `entitlement_token_id`：后端签发的随机匿名令牌，不包含设备 ID、IP、邮箱、姓名。
- `consent_scope`：家长同意的范围，例如 `local_only`、`cloud_ocr`、`cloud_tts`、`sync`。
- `jurisdiction_code`：地区或国家代码，只用于规则选择，不用于定位。

### 5.2 核心数据模型

- 权益钱包：token -> service_type -> remaining / reserved / expires_at
- 体验快照：后端签名，客户端本地缓存
- 补偿码：code_hash -> grant spec -> claim ledger
- 同意记录：parent consent version、jurisdiction、step-up 方式、时间戳
- 低敏审计：event_type、result、request_id、latency、provider，不含内容

### 5.3 合规数据矩阵

所有数据都必须先进入数据清单，再进入开发。未列入清单的数据默认禁止采集。

| 数据类别 | 模式 | 目的 | GDPR 合法性基础 | COPPA 要求 | 留存 |
| --- | --- | --- | --- | --- | --- |
| 本地图片 / 本地 OCR 文本 / 本地音频 | Local Guest / Parent Account | 本地识别、朗读、复习 | 不出设备，后端不处理 | 不上传、不分享、不用于画像 | 用户本地控制 |
| Apple 家长账号标识 | Parent Account | 账号、安全、购买恢复 | 履行合同 / 账号安全必要处理 | 作为家长身份，不作为儿童身份 | 账号存续期，删除后清理或法定最小留存 |
| 家长同意记录 | Parent Account | 证明授权、控制云能力 | 法定义务 / 明示同意记录 | 记录 verifiable parental consent 的范围、版本、时间 | 法规和争议应对必要期限 |
| `jurisdiction_code` | Parent Account | 选择年龄阈值和隐私规则 | 履行法定义务 / 合规必要 | 仅用于规则选择，不用于定位或画像 | 与同意记录同期限 |
| 匿名权益 token / wallet / ledger | Parent Account | 权益、防超额、补偿 | 履行合同 / 防欺诈安全必要处理 | 不包含儿童身份、设备 ID、IP、内容 | 必要审计期，期满聚合或删除 |
| 云 OCR/TTS 内容 | Parent Account cloud opt-in | 第三方云处理 | 家长明示同意 + 履行用户请求 | 云能力前取得父母授权，禁止二次用途 | 业务后端 0 留存，供应商按 DPA/服务条款约束 |
| 低敏诊断 | Parent Account diagnostics opt-in | 故障定位、安全审计 | 明示同意；安全事件可用安全必要处理 | 不含儿童内容、设备指纹、精确 IP | 7-30 天 |
| 服务器传输层 IP | Parent Account / 基础设施 | 安全、防滥用、网络传输 | 安全必要处理，最小化 | 不作为儿童画像或持久标识 | 访问日志脱敏或截断，默认 7 天内清理 |

明确禁止：

- 广告跟踪、跨 App 跟踪、儿童画像、行为广告。
- 把 IP、DNS、CDN POP、加速线路作为唯一地区或年龄判断依据。
- 为 Local Guest 创建服务端长期标识。
- 用诊断 payload 自由 JSON 收集未列入清单的数据。
- 把儿童内容用于模型训练、人工标注、营销、推荐画像。

## 6. 按功能拆解

### 6.1 登录与家长门禁

产品对外保持单一主入口：默认进入本地匿名状态。保持 Apple 登录作为唯一正式家长授权方式，不新增孩子登录，不新增手机号 / 邮箱登录作为主入口，也不要求用户在两个账户间手动切换。

改造点：

- 首次启动默认允许进入 `Local Guest`，不得调用后端创建账号或上传儿童内容。
- 进入云能力、云同步、补偿、购买恢复、账号删除前，自动弹出 `Parent Account` 授权流程，无需用户手动切换账户。
- 登录成功后，后端签发的 session 只代表家长。
- 家长区进入、补偿兑换、删除账号、开启云能力前，要求本地 Face ID / Touch ID + 简单知识题二次确认。
- 二次确认只作为“家长已在场”的 step-up 证明，不把生物特征数据传给后端。
- 云能力开启前必须完成 direct notice + 可验证父母同意 + granular consent；未完成时只能保留 Local Guest 本地模式。
- EU 场景下，首次进入云能力前必须写入 `jurisdiction_code` 和 `consent_version`。
- 生产环境禁止 demo / dev session；`Local Guest` 不是后端 session，不得拥有服务端 `userId`。

建议新增：

- `reading_parent_consent`
- `reading_jurisdiction_policy`

### 6.2 本地 OCR / 语音

本地能力分三类：

1. 每日赠送的本地设备图片文字识别次数：仅当日有效，客户端本地计数，允许离线使用。
2. 每日赠送的本地设备语音播放次数：仅当日有效，客户端本地计数，允许离线使用。
3. 购买的本地设备图片文字识别 / 本地设备语音播放次数：购买时由后端控制有效期，当前暂定 30 天；后端签发可离线使用的签名快照。

实现策略：

1. 客户端在 Keychain 保存加密计数器。
2. 每日赠送权益本地按自然日或后端策略日滚动，记录 `grant_date`、`service_type`、`remaining`、`counter_version`、`signature_or_mac`。
3. 购买本地权益以后端签名快照为准，快照包含 `policy_version`、`issued_at`、`expires_at`、`service_limits`、`purchase_ref_hash`、`signature`。
4. 当前购买本地权益默认 `expires_at = purchase_verified_at + 30 days`，后续可由后端配置。
5. StoreKit 本地交易状态可作为后端停服时的降级校验，但不能扩大发放云端权益。
6. 客户端只信任有效签名快照，不信任本地明文数字。
7. 快照过期后，购买本地权益自动回收为 0 或最严格限制；每日赠送权益仍按本地规则继续生成。
8. 后端恢复后，优先同步最新快照，覆盖本地旧状态。

重点要求：

- 本地计数器只能做 UX 和离线保护，不能成为最终权威。
- 任何本地计数器都必须带版本号和过期时间。
- 发现篡改、签名不一致、版本倒退，一律回退到最严格限制。
- 本地赠送权益和购买本地权益不得调用云端供应商，因此不会产生开发者云成本超额风险。
- 本地购买权益可接受“离线期间按最后有效快照继续服务”，但不得把离线快照转换成云端次数。

### 6.3 百炼云端 API

云端 OCR / 云端语音是强约束区。结合当前产品规划，近期重点是“购买云端语音播放次数”，未来可扩展到“购买云端图片文字识别次数”。注意：购买云端语音播放次数不能和本地设备图片文字识别次数混在同一个不可区分余额里，必须按 `service_type` 独立记账。

云端能力必须做到：

- 后端必须先验证权益。
- 后端必须先预留次数，再允许请求出站。
- 无后端或后端验证失败时，云端能力不可用。
- 不得出现超额调用。
- 不得把 Local Guest 的本地次数兑换成云端次数。
- 不得在 capability token 中包含儿童内容、设备 ID、IP、邮箱或 Apple ID。

推荐做法：

1. 客户端必须处于 `Parent Account`，并已获得对应 `consent_scope`。
2. 客户端请求后端，携带匿名权益令牌、`service_type`、幂等键。
3. 后端在事务内创建 `reservation`，同时把可用额度转入 `reserved`。
4. 后端签发短生命周期、一次性 capability token，给客户端或 AI 网关使用。
5. 客户端把内容直接发往 AI 网关 / 百炼，不经过业务后端落库。
6. 成功后提交 reservation；失败或超时后释放 reservation。
7. capability token 过期未提交时，由后台任务自动 release 或 expire，不允许重复消耗。
8. 供应商回调、客户端 commit、后台 reconciliation 三者都必须使用同一个 idempotency key 防重复扣减。

这条链路的重点不是“后端代理内容”，而是“后端只发放能力，不保存内容”。

### 6.4 权益使用和记录

当前项目用 `userId` 直接记账，后续应切换到匿名权益令牌。

权益类型必须拆分：

| 权益 | 模式 | 账本权威 | 有效期 | 后端停服时 |
| --- | --- | --- | --- | --- |
| 每日赠送本地 OCR | Local Guest / Parent Account | 本地 Keychain 计数器 | 当日有效 | 可继续用 |
| 每日赠送本地语音 | Local Guest / Parent Account | 本地 Keychain 计数器 | 当日有效 | 可继续用 |
| 购买本地 OCR | Parent Account 购买后可本地离线使用 | 后端签名快照 + StoreKit 本地状态 | 当前暂定 30 天 | 快照有效期内可继续用 |
| 购买本地语音 | Parent Account 购买后可本地离线使用 | 后端签名快照 + StoreKit 本地状态 | 当前暂定 30 天 | 快照有效期内可继续用 |
| 购买云端语音 | Parent Account | 后端 wallet / reservation / ledger | 后端配置 | 不可继续消耗 |
| 未来购买云端 OCR | Parent Account | 后端 wallet / reservation / ledger | 后端配置 | 不可继续消耗 |

建议规则：

- 权益使用表不再以真实用户身份为主键。
- 使用记录只保留 token、service_type、delta、来源、幂等键、时间戳。
- 不保留原文内容、不保留设备 ID、不保留 IP 明文。
- 记录表和快照表分离，ledger 只追加，snapshot 只读。
- `service_type` 至少包括 `local_ocr`、`local_tts`、`cloud_ocr`、`cloud_tts`。
- `wallet` 必须拆分 `remaining`、`reserved`、`used`、`expires_at`，云端能力扣减必须先 reserve 再 commit。
- 本地权益 grant 可以生成签名快照，但不进入云端 reservation。
- 购买记录只能通过 `purchase_ref_hash`、`transaction_id_hash`、`token_hash` 关联，不把儿童内容或孩子档案放入权益账本。

推荐状态流：

- `grant`
- `reserve`
- `commit`
- `release`
- `expire`
- `adjust`

### 6.5 权益补偿

补偿分两种：

1. 全体补偿：按 campaign 自动给所有有效 token 触发一次。
2. 指定用户补偿：通过一次性补偿码发放，但不把“谁用了哪个码”写成可回溯的明文关联。

建议改造：

- `sys_compensation_code` 保留 code_hash、benefit_type、grant spec、max_uses、expires_at。
- `sys_user_compensation_record` 改成 token 维度或 claim hash 维度。
- admin 生成补偿码时，不要把 `X-Forwarded-For` 当 operator 身份落库，只保留后台账号或匿名 operator id。

### 6.6 数据采集

当前最需要收紧的是设备事件和内容回填。

默认应收集仅限：

- 请求 ID，必须为随机、短期、不可跨会话追踪的 ID。
- 事件类型
- 结果状态
- provider / model
- latency
- 区域策略版本

仅在 `Parent Account` 且家长选择诊断后，才允许上报低敏诊断。安全事件和防滥用所需的最小日志可不依赖诊断同意，但必须单独标记为 security log，严禁用于产品分析或画像。

不应收集：

- 图片原文
- OCR 原文
- 音频正文
- 设备型号
- 系统版本
- 精确 IP
- IP 国家推断结果，除非是区域策略强需要，且只保留家长确认后的 coarse country code
- DNS resolver、CDN POP、网络加速线路、ASN、运营商等可形成网络指纹的数据
- 用户画像标签
- 广告 ID、IDFA、IDFV、第三方分析 SDK 用户 ID
- 自由格式 payload

特别注意：

- `ReadingDeviceEventService` 当前字段过宽，必须拆成“业务计数”和“诊断日志”两张逻辑表。
- `ReadingCompatService.createReviewCard()` 必须在生产环境禁止 plaintext fallback。
- `ReadingBailianOcrProvider` / `ReadingBailianTtsProvider` 不得打原文日志、base64 日志、失败回显日志。
- 隐私政策和 App Store Privacy Nutrition Label 必须与实际采集字段逐项一致。

### 6.7 删除与留存

建议按数据类型分开：

| 数据 | 留存 |
| --- | --- |
| 图片 / 文本 / 音频 | 0 留存，处理完即清空 |
| 购买本地权益签名快照 | 到购买权益 `expires_at` 截止，当前默认最长 30 天 |
| 临时离线缓冲快照 | 48-72 小时，仅用于后端短时不可用时的本地降级缓冲 |
| 业务权益 ledger | 仅保留必要审计期 |
| 补偿码 claim 记录 | 仅保留审计期 |
| 诊断日志 | 7-30 天 |
| 同意记录 | 仅保留法规和争议应对所需期限 |

留存要求：

- 每张含个人数据或假名数据的表必须有 `retention_policy_code` 或可由数据清单映射到留存策略。
- 后端必须有 purge job 和 purge audit，不允许只在文档中声明删除。
- 备份数据必须有独立过期策略；删除请求完成后，不得从备份主动恢复已删除儿童数据。
- 删除账号时，应区分“立即删除的儿童内容和同步数据”与“为账务、反欺诈、争议处理保留的最小权益记录”。
- 任何本地缓冲快照在超过有效期后必须自动失效，不得被重放成新的购买权益。

### 6.8 用户权利与家长控制

Parent Account 必须提供家长可操作入口，覆盖 GDPR 数据主体权利和 COPPA 父母控制权。

必须支持：

- 访问权：家长可查看账号、同意范围、权益余额、云同步状态、第三方云能力开关。
- 更正权：家长可修改地区选择、偏好、孩子本地档案；云端如不保存孩子档案则明确说明无云端可改数据。
- 删除权 / 被遗忘权：家长可删除云端账号数据、同步数据、权益 token、诊断日志；本地数据由设备内删除入口清除。
- 撤回同意：撤回 `cloud_tts`、`cloud_ocr`、`sync`、`diagnostics` 后立即停止对应处理。
- 数据可携带权：可导出家长账号权益记录、同意记录、可导出的本地学习数据；导出不得包含第三方不允许披露的安全密钥或 capability token。
- 限制处理 / 反对处理：争议、投诉或未完成删除时，暂停云处理和非必要诊断。
- COPPA 父母权利：父母可审查已收集的儿童个人信息、要求删除、拒绝进一步收集或使用；如果产品选择本地零上传，应在儿童数据说明中写明后端无可审查的儿童内容。

流程要求：

- GDPR 权利请求默认 1 个月内完成或说明延期原因。
- 删除和导出请求必须要求家长重认证。
- 完成、拒绝、延期都必须写入 `reading_privacy_request`，记录 request type、status、scope、deadline、completed_at、reason_code。
- 不得因为家长撤回云能力同意而影响 Local Guest 本地基础使用。

### 6.9 第三方处理、共享与跨境

原则：不出售、不共享给广告网络、不做跨 App 跟踪。第三方只能作为实现明确功能的 processor / service provider 使用。

第三方清单必须至少覆盖：

- Apple：Sign in with Apple、StoreKit、App Store 交易和退款通知。
- 云 OCR/TTS 或 AI 网关 / 百炼：仅在 Parent Account 且家长开启对应云能力后处理内容。
- 托管、数据库、日志、CDN、DNS、邮件或客服供应商。
- PowerSync 或其他同步供应商，如果启用云同步。

上线前要求：

- 建立 `reading_vendor_registry` 或等价台账，记录供应商、数据类别、处理目的、地区、子处理者、留存、DPA/服务条款链接、是否用于训练模型。
- 与处理儿童数据或家长个人数据的供应商签订 DPA 或确认服务条款满足 processor / service provider 要求。
- 云 OCR/TTS 供应商必须承诺不把儿童内容用于广告、画像、训练或人工质检，除非另行取得符合法规的明确授权；产品默认不得开启这类用途。
- EU 数据出境必须有适用传输机制，例如 SCC、充分性决定或其他合法机制，并完成风险评估。
- 中国境内外链路如果涉及个人信息出境或境外访问，应单独做本地法合规评估；DNS/CDN/加速服务日志也纳入供应商台账。
- 第三方故障、数据泄露或政策变化必须能通过远程配置关闭对应云能力。

### 6.10 Cookie、SDK、自动化决策与泄露通知

- iOS App 默认不使用 Cookie 进行跟踪；如 WebView、官网或后台管理使用 Cookie，只允许严格必要 Cookie，分析或营销 Cookie 必须单独同意，儿童场景默认关闭。
- 禁止接入广告 SDK、跨 App 分析 SDK、再营销 SDK。
- 不做对儿童产生法律或类似重大影响的自动化决策；学习报告只作为家长参考，不作为评分、筛选或画像。
- 建立数据泄露响应流程：发现、分级、遏制、调查、通知、复盘。GDPR 场景下按 72 小时监管通知要求设计；涉及儿童或高风险时准备通知用户/家长。
- 后端安全措施至少包括 TLS、密钥轮换、最小权限、生产日志脱敏、管理员操作审计、备份加密、供应商密钥隔离、删除任务监控。

## 7. 推荐数据库改造

建议新增或重构如下对象：

```text
reading_parent_consent
reading_jurisdiction_policy
reading_entitlement_token
reading_entitlement_wallet
reading_entitlement_reservation
reading_entitlement_ledger
reading_entitlement_snapshot
reading_compensation_campaign
reading_compensation_claim
reading_privacy_event
reading_privacy_request
reading_data_deletion_request
reading_vendor_registry
reading_security_incident
```

如果想尽量少动现有表，可以先做兼容迁移：

- `reading_cloud_service_usage` 增加 reservation 字段。
- `reading_cloud_service_credit_grant` 增加 token_hash / claim_hash。
- 本地购买权益增加 `local_entitlement_snapshot` 或并入 `reading_entitlement_snapshot`，保存签名快照元数据，不保存儿童内容。
- `sys_compensation_code` 增加 claim scope 与匿名令牌绑定方式。
- `sys_user_device_event` 仅保留低敏 diagnostics，或只作兼容，不再用于儿童数据。

## 8. 推荐接口改造

### 8.1 保留

- `POST /api/v1/system/auth/apps/{appCode}/apple/exchange`
- `GET /api/v1/system/auth/apps/{appCode}/me`
- `POST /api/v1/account/deletion-requests`
- `GET /api/v1/legal/docs`

### 8.2 新增或重构

- `POST /api/v1/account/consent/parent`
- `GET /api/v1/account/entitlement/snapshot`
- `POST /api/v1/account/entitlement/reserve`
- `POST /api/v1/account/entitlement/commit`
- `POST /api/v1/account/entitlement/release`
- `GET /api/v1/account/entitlement/local-snapshot`
- `POST /api/v1/account/entitlement/local-snapshot/refresh`
- `POST /api/v1/account/compensation/redeem`
- `POST /api/v1/account/privacy/delete`
- `GET /api/v1/account/privacy/export`
- `POST /api/v1/account/privacy/consent/withdraw`
- `POST /api/v1/account/privacy/restrict-processing`
- `POST /api/v1/account/privacy/telemetry` 仅限低敏诊断

### 8.3 需要下线或降级

- 后端代理内容的云 OCR / 云 TTS 生产路径
- 任何返回原文、图片、音频的后端审计接口
- 任何基于设备 ID 的配额归因
- 任何把本地次数直接转换成云端次数的接口
- 任何 Local Guest 调用后端创建账号、同步儿童内容或上传诊断的接口
- 任何广告跟踪、画像、营销归因或跨 App 分析接口

### 8.4 后端停服降级策略

- Local Guest：完整保留本地 OCR、本地语音、本地句卡、本地记录、本地删除。
- 已购买本地权益的家长授权态：在后端签名快照和 StoreKit 本地交易状态有效时继续使用，最多使用到快照 `expires_at`。
- 家长授权态云端权益：后端不可用时不签发 capability token，不允许云端消耗。
- 补偿、购买恢复、云同步、账号删除：提示后端服务恢复后再处理；账号删除请求可先在本地排队，但不得声称云端已删除。
- 如果后端短时不可用，权利请求必须进入重试队列或替代联系渠道，不得永久拒绝或丢失；本地队列与重试状态必须可审计。
- 法律文案需明确：本地模式可离线使用；云端和账号服务依赖后端可用性。

## 9. 文件级落地点

下面这些文件应作为第一批改造点：

| 文件 | 动作 |
| --- | --- |
| `backend/src/main/resources/apps/reading/app-definition.yml` | 加入 privacy / consent / region policy 配置 |
| `backend/src/main/java/com/apphub/backend/sys/auth/controller/SysAuthController.java` | 保持 Parent Account Apple-only，确保正式环境无 demo / dev 会话；Local Guest 不创建后端会话 |
| `backend/src/main/java/com/apphub/backend/apps/reading/common/ReadingAuthenticatedUserResolver.java` | 只承载家长会话，不扩展孩子身份 |
| `backend/src/main/java/com/apphub/backend/apps/reading/compat/service/ReadingCompatService.java` | 移除 plaintext fallback，收紧句卡存储和权益视图 |
| `backend/src/main/java/com/apphub/backend/apps/reading/compat/service/ReadingCloudUsageService.java` | 改成 reservation / commit / release 模式，拆分 `local_ocr` / `local_tts` / `cloud_ocr` / `cloud_tts`，禁止本地权益转云端权益 |
| `backend/src/main/java/com/apphub/backend/apps/reading/compat/service/ReadingDeviceEventService.java` | 砍掉 device model / system / payload 采集 |
| `backend/src/main/java/com/apphub/backend/apps/reading/provider/ReadingBailianOcrProvider.java` | 从生产内容代理改成 legacy / test only，或移出生产路径 |
| `backend/src/main/java/com/apphub/backend/apps/reading/provider/ReadingBailianTtsProvider.java` | 同上，生产上只保留 capability 流 |
| `backend/src/main/java/com/apphub/backend/apps/reading/compensation/controller/ReadingCompensationCompatController.java` | 改成匿名令牌兑换入口 |
| `backend/src/main/java/com/apphub/backend/sys/compensation/service/SysCompensationService.java` | 补偿记录去 user 明文，改 claim 账本 |
| `backend/src/main/resources/db/migration/V43__compensation_code_center.sql` | 增加 token / claim 结构 |
| `backend/src/main/resources/db/migration/V38__reading_cloud_usage_authoritative_log.sql` | 增加 reservation / snapshot 支持 |
| `backend/src/main/resources/db/migration/V40__reading_cloud_usage_data_repair.sql` | 修复余额和预约一致性 |
| `backend/src/main/resources/static/legal/privacy-policy.html` | 重写儿童隐私披露 |
| `backend/src/main/resources/static/legal/child-data.html` | 重写儿童数据说明 |
| `front/ios/PaipaiReadAlong/Resources/PrivacyInfo.xcprivacy` | 按最终数据清单同步 Apple 隐私营养标签 |

## 10. 分期执行建议

### Phase 0. 先止血

- 前端入口保持单一主流程，本地匿名状态默认直入；触发云功能时再进入家长授权流程。
- Local Guest 默认不请求后端、不上传儿童内容、不采集诊断。
- 禁止生产环境 plaintext review card fallback。
- 停掉 device-level 过度采集。
- 关闭所有内容日志。
- 明确 cloud OCR / TTS 只走受控路径。

### Phase 1. 建权威账本

- 上线匿名权益令牌。
- 上线 reservation / commit / release。
- 建立 `local_ocr` / `local_tts` / `cloud_ocr` / `cloud_tts` 独立 service_type。
- 购买本地权益签发 30 天或后端配置有效期的签名快照。
- 购买云端权益只进入 wallet / reservation / ledger，不进入离线快照。
- 补偿码改成 token claim。
- 购买本地权益签名快照和临时离线缓冲快照都必须加上版本、过期时间和防重放字段。

### Phase 2. 做区域与同意

- 新增 parent consent 记录。
- 新增地区年龄策略表。
- EU 默认 16 岁家长授权。
- 更新隐私政策和儿童数据说明。
- 增加数据清单、供应商台账、第三方 DPA 检查。
- 增加家长访问、导出、删除、撤回同意、限制处理入口。

### Phase 3. 收尾和审计

- 增加自动 purge job。
- 补齐删除账号后的一致性校验。
- 增加数据泄露响应 runbook 和安全事件台账。
- 校验 App Store Privacy Nutrition Label、隐私政策、实际代码三者一致。
- 生成合规审计清单和上线阻断条件。

## 11. 验收标准

1. 云端 OCR / TTS 没有后端验证令牌就不能调用。
2. Local Guest 无需登录即可使用本地 OCR / 本地语音 / 本地句卡，且不向后端上传儿童内容或诊断。
3. 每日赠送本地 OCR / 本地语音仅当日有效，后端离线时仍可按本地规则使用。
4. 购买本地 OCR / 本地语音在后端签名快照有效期内可离线使用，当前默认不超过购买后 30 天或后端配置期限。
5. 购买云端语音和未来云端 OCR 必须先 reserve，再 capability，再 commit / release；后端不可用时不能继续消耗。
6. 本地权益不得转换为云端权益，`local_ocr`、`local_tts`、`cloud_ocr`、`cloud_tts` 账本隔离。
7. 后端数据库里找不到原始图片、原文、音频。
8. 设备型号、系统版本、payload 不再进入默认业务表。
9. 补偿码可用，但不能从记录里反推出儿童内容。
10. 删除账号后，权益令牌和儿童业务记录都能被清理。
11. 生产环境不再存在 demo / dev session 作为正式用户入口；Local Guest 不创建后端 session，云功能由家长授权流程接管。
12. 法律文案明确区分本地模式、家长账号、云能力、第三方处理、后端停服影响。
13. 家长可访问、导出、删除、撤回同意、限制云处理；请求有状态、截止时间和完成记录。
14. 第三方供应商全部进入 vendor registry；未完成 DPA/服务条款确认的供应商不得处理儿童内容或家长个人数据。
15. 不存在广告 SDK、跨 App 跟踪 SDK、营销 Cookie 或儿童画像。
16. 数据泄露响应、日志脱敏、purge job、管理员审计均有可执行机制和验证记录。

## 12. 剩余风险

- IP 地址仍可能被基础设施或云服务接触，这属于传输层现实暴露，不能靠业务代码彻底消除。
- 百炼 / OSS / IDaaS 是否保留最小日志和处理痕迹，仍要通过 DPA 和服务条款约束。
- GDPR 成员国年龄门槛不同，必须把国家阈值做成配置，不要写死。
- COPPA 与欧盟规则都会继续变化，技术方案必须保留版本化开关。
- Local Guest 本地计数器无法 100% 防越狱、重装、改时间、Keychain 篡改；因此只能承载不产生开发者云成本的本地能力。
- 个人开发者如果同时发布、运营、收款和决定数据处理目的，仍可能承担 operator / controller 责任；本方案只能降低风险，不能消灭主体责任。

## 13. 参考链接

- FTC COPPA 最终规则新闻稿：<https://www.ftc.gov/news-events/news/press-releases/2025/01/ftc-finalizes-changes-childrens-privacy-rule-limiting-companies-ability-monetize-kids-data>
- FTC 联邦公报最终规则：<https://www.federalregister.gov/documents/2025/04/22/2025-05904/childrens-online-privacy-protection-rule>
- GDPR / Article 8（EUR-Lex）：<https://eur-lex.europa.eu/eli/reg/2016/679/oj/eng>
