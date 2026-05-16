# paipaiV2 离线本地存储 + 云同步方案 v1

日期：2026-04-20
适用范围：统一 backend 下的多 App 架构（当前先落地拍拍伴读）

---

## 1. 目标与约束

### 目标
1. **App 主要功能在后端掉线、升级、临时不可用时仍可使用**
2. 本地保存：
   - 识别后的语句
   - 翻译结果
   - 复习句子 / 复习记录
   - 孩子档案与学习轨迹
3. 在用户登录并同意同步时，支持**云端同步与多设备恢复**
4. 同步能力不是拍拍伴读独占，而是要为后续多个 App 复用打基础

### 关键约束
- 当前 backend 是**多个 App 共用的统一后台**
- 需要区分：
  - **通用同步基础能力**（适合后续所有 App）
  - **拍拍伴读特有业务数据**
- 低运维成本优先
- 购买、权益、孩子数量上限、价格、公告等仍应以**后端为最终权威**

---

## 2. 对参考文档（PowerSync 方案）的评估

参考文档核心思想是对的：
- 本地优先
- UUID 主键
- 增量同步
- 软删除
- 冲突处理
- 强制恢复入口

但是对当前项目，我**不建议第一阶段直接落地 PowerSync**，原因如下：

### 不建议现在直接上 PowerSync 的原因
1. **会新增一层常驻同步服务**
   - PowerSync Service
   - WebSocket 连接器
   - 与现有 Java backend 的认证打通
   - 运维复杂度上升

2. **与你当前“低运维成本”目标不完全一致**
   - 现在项目是第一个 App
   - 当前更重要的是先把“本地可用 + 有序同步”跑通
   - 没必要马上引入新的同步中间件基础设施

3. **拍拍伴读当前是“个人数据同步”，不是多人协同编辑**
   - 识别结果、翻译、复习记录基本是单账号单家庭数据
   - 复杂实时协同不是当前核心问题
   - 用现有 backend 做轻量同步即可覆盖 80%+ 场景

### 结论
- **参考文档的设计原则保留**
- **实现方式改为：本地 SQLite + 自建轻量 sync 模块 + 现有 unified backend**
- 未来如果多个 App 都需要更重的实时同步，再评估升级到 PowerSync / ElectricSQL / 自建 CDC 同步服务

---

## 3. 推荐总体方案：Local First + Optional Cloud Sync

### 总体原则
1. **主功能永远先写本地**
2. **云同步是增强，不是主路径依赖**
3. **用户可以不开启云同步，只使用本地**
4. **登录后可选择把本地学习数据同步到云端**
5. **后端权威数据与学习内容数据分层处理**

### 数据分层

#### A. 本地优先，可选云同步的数据
这些数据是“学习内容本身”，应以本地可用为第一目标：
- 识别后的句子
- 翻译结果
- 句子来源（拍照/相册/手动）
- 复习卡片
- 复习记录
- 孩子学习轨迹
- 学习时间明细

#### B. 服务端权威数据
这些数据必须以后端为准，本地只能缓存展示，不作为最终权威：
- 账号信息
- 登录身份
- Apple / 邮箱验证状态
- 购买 / 恢复购买结果
- entitlement / childLimit
- 价格与计划目录
- 公告配置
- 法务文档链接

#### C. 派生数据
这些数据可本地算，也可后端聚合：
- 最近 7 天学习时长
- 周报
- streak / 活跃天数
- 已保存卡片数

建议：
- **本地先算，后端可做校验或聚合补充**
- UI 优先展示本地结果，联网后再刷新云端汇总

---

## 4. 通用层与拍拍伴读特有层如何拆

## 4.1 通用基础能力（建议放在 unified backend 的 common/sys 层）

### 后端目录建议
```text
backend/src/main/java/com/apphub/backend/sys/sync/
  controller/
  service/
  model/
  support/
```

### 通用能力包括
1. **Sync Client 管理**
   - 一个设备一个 client_id
   - 记录 app_code / user_id / device_id / last_seen_at

2. **Sync Cursor 管理**
   - 每个用户、每个 app、每个设备记录最后同步游标

3. **通用 Pull / Push 协议**
   - `POST /api/v1/sync/{appCode}/pull`
   - `POST /api/v1/sync/{appCode}/push`
   - `POST /api/v1/sync/{appCode}/bootstrap`

4. **Change Log / Tombstone 机制**
   - 统一记录增量变化
   - 支持软删除

5. **冲突处理框架**
   - 默认 last-write-wins
   - 支持 app-specific override

6. **全量恢复 / 重建同步入口**
   - 用户主动触发重新上传本地数据
   - 服务端重新下发全量快照

7. **审计与监控**
   - 同步成功率
   - 冲突率
   - 云同步开关状态
   - 恢复次数

---

## 4.2 拍拍伴读特有层（放在 apps/reading）

### 后端目录建议
```text
backend/src/main/java/com/apphub/backend/apps/reading/sync/
  ReadingSyncAdapter.java
  ReadingSyncEntityRegistry.java
  ReadingSyncConflictResolver.java
```

### 拍拍伴读特有内容
- 学习内容数据结构
- 复习卡规则
- 句子翻译/朗读关联字段
- 孩子档案与学习轨迹
- 周报聚合逻辑

---

## 5. iOS 端推荐实现方式

## 5.1 本地数据库技术选型
**推荐：GRDB + SQLite**

原因：
- SQLite 稳定、可控、离线能力强
- 对 sync/outbox/tombstone 支持更清晰
- 比 SwiftData / CoreData 更容易做跨 App 通用同步抽象
- 后续如果别的 App 也要复用，同步框架更统一

### 不推荐作为第一期主方案
- 只用 UserDefaults：不适合学习内容数据
- 只用文件 JSON：后续同步和查询会很难维护
- SwiftData/CoreData：可用，但对“通用 sync 层 + 精确冲突控制”没 SQLite/GRDB 直观

---

## 5.2 iOS 目录建议

### 通用同步基础层
```text
paipaiV2/ios/PaipaiReadAlongV2/Core/Sync/
  LocalDatabase.swift
  SyncModels.swift
  SyncQueueStore.swift
  SyncCoordinator.swift
  SyncAPIClient.swift
  SyncConflictResolver.swift
  SyncReachabilityService.swift
  SyncSettingsStore.swift
```

### 通用 Repository 层
```text
paipaiV2/ios/PaipaiReadAlongV2/Core/Repositories/
  ChildRepository.swift
  PreferenceRepository.swift
  LearningRepository.swift
  ReviewRepository.swift
  UsageRepository.swift
```

### 拍拍伴读特有本地模型层
```text
paipaiV2/ios/PaipaiReadAlongV2/Features/ReadingData/
  Models/
    ReadingLearningItemRecord.swift
    ReadingReviewProgressRecord.swift
    ReadingChildRecord.swift
    ReadingUsageRecord.swift
  Mapping/
    ReadingSyncMapper.swift
```

---

## 6. 拍拍伴读建议的数据模型（重点）

当前你要离线保的核心不是“页面状态”，而是“学习资产”。
建议抽象为以下几类：

## 6.1 Learning Item（核心学习内容）
建议新增一条统一模型，名字可为：
- `reading_learning_item`
- 或 `reading_sentence_record`

建议字段：
- `id` UUID
- `owner_user_id`
- `child_id`
- `source_type`（camera / photo_library / manual）
- `source_text`
- `translated_text`
- `source_language_code`
- `target_language_code`
- `learning_track_code`
- `ocr_provider`
- `translation_provider`
- `local_image_path`（仅本地保存）
- `cloud_image_key`（未来需要云图时再扩）
- `status`（active / archived / deleted）
- `created_at`
- `updated_at`
- `deleted_at`

### 为什么建议新增这层
因为“识别语句”和“复习卡”不是完全一回事：
- 有的识别结果只是看一眼，不进复习
- 有的要多次复习
- 有的翻译会被编辑

所以建议：
- **Learning Item = 原始学习资产**
- **Review Progress = 基于学习资产的复习状态**

---

## 6.2 Review Progress / Review Event
### `reading_review_progress`
- `id` UUID
- `learning_item_id`
- `child_id`
- `next_review_at`
- `last_review_at`
- `review_stage`
- `ease_factor`
- `due_status`
- `created_at`
- `updated_at`
- `deleted_at`

### `reading_review_event`
- `id` UUID
- `learning_item_id`
- `child_id`
- `result_level`（forgot / hard / remembered / easy）
- `reviewed_at`
- `device_id`
- `created_at`

> `review_event` 建议继续做 append-only，冲突最少，适合同步。

---

## 6.3 Child Profile
已有 `reading_child_profile`，建议继续复用，但要确保满足同步要求：
- UUID 主键
- `updated_at`
- `deleted_at`
- `owner_user_id`
- entitlement 校验在服务端完成

---

## 6.4 Usage 数据
建议分两层：

### 本地明细
`reading_usage_session_local`
- 进入 capture / learning / review 时本地先记
- 退后台、切孩子、退出时结束

### 云端聚合
- 云端只同步必要 usage session / daily aggregate
- 周报和家庭总览可由服务端做二次聚合

---

## 7. 服务端通用同步模型建议

## 7.1 通用表（所有 App 复用）

### `sys_sync_client`
- `id`
- `app_code`
- `user_id`
- `device_id`
- `client_id`
- `client_version`
- `platform`
- `last_seen_at`
- `created_at`
- `updated_at`

### `sys_sync_cursor`
- `id`
- `app_code`
- `user_id`
- `client_id`
- `last_pulled_seq`
- `last_pushed_at`
- `last_pull_at`
- `created_at`
- `updated_at`

### `sys_sync_change_log`
- `id`（bigserial）
- `app_code`
- `entity_type`
- `entity_id`
- `owner_user_id`
- `operation`（upsert / delete）
- `changed_at`
- `payload_version`
- `trace_id`

### `sys_sync_reject_log`
- `id`
- `app_code`
- `user_id`
- `client_id`
- `entity_type`
- `entity_id`
- `reason_code`
- `reason_message`
- `payload`
- `created_at`

---

## 7.2 拍拍伴读业务表需要满足的同步字段
所有要参与同步的业务表建议统一具备：
- `id`（UUID）
- `owner_user_id`
- `created_at`
- `updated_at`
- `deleted_at`（软删除）

不建议依赖数据库自增 id 作为离线创建主键。

---

## 8. 同步协议设计（推荐）

## 8.1 Pull
### `POST /api/v1/sync/reading/pull`
请求：
- `clientId`
- `lastSeq`
- `entityTypes`（可选）
- `limit`

返回：
- `nextSeq`
- `hasMore`
- `changes[]`
  - `entityType`
  - `operation`
  - `entityId`
  - `serverUpdatedAt`
  - `payload`

## 8.2 Push
### `POST /api/v1/sync/reading/push`
请求：
- `clientId`
- `changes[]`
  - `entityType`
  - `operation`
  - `entityId`
  - `clientUpdatedAt`
  - `payload`

返回：
- `accepted[]`
- `rejected[]`
  - `entityId`
  - `reasonCode`
  - `reasonMessage`

### 幂等要求
- `entityId` + `owner_user_id` 唯一
- 服务端收到重复 push 时必须安全 upsert

---

## 9. 冲突策略

## 默认策略
### 9.1 Learning Item / Child / Preference
- 默认 **last-write-wins**
- 对于 entitlement 相关字段，服务端可以拒绝不合法写入

### 9.2 Review Event
- append-only，不做覆盖冲突
- 重复事件通过 `id` 去重

### 9.3 Usage Session
- 以 session UUID 去重
- 重复上传时忽略已存在记录

### 9.4 服务端拒绝的场景
例如：
- 超出 childLimit 创建孩子
- 已删除账号还继续同步
- payload 不合法
- 设备本地 guest 数据未绑定账号却要求云同步

拒绝时：
- 客户端保留本地数据
- UI 提示“仅本地保存，未同步到云端”
- 用户可后续处理（升级/合并/重试）

---

## 10. 账户与本地数据合并策略（很重要）

因为你的 App 要支持“后端掉线也能用”，所以必须支持：
- 用户先离线使用
- 之后再登录 formal account
- 再决定是否同步本地数据到云端

## 推荐策略
### 本地始终存在一个 `installation_id`
- 每台设备首次安装生成
- 本地学习数据先绑定 `installation_id`

### 登录 formal account 后出现合并选择
弹窗：
- 把这台设备上的本地学习数据同步到当前账号？
- 选项：
  1. 合并并上传
  2. 保留本地但不同步
  3. 丢弃本地，拉云端

### 这样能解决
- 游客模式先用
- 后续升级 formal account
- Apple 审核时也能说清楚数据流转逻辑

---

## 11. 哪些内容不建议现在就云同步
为降低成本和复杂度，第一期不建议同步：
- 原始拍照图片二进制
- 音频缓存
- TTS 临时音频文件
- 公告展示缓存
- 法务 HTML 文件内容

建议第一期只同步：
- 结构化学习数据
- 复习数据
- 孩子资料
- 偏好
- usage 明细/聚合

图片以后如要恢复，再单独加对象存储。

---

## 12. 开发阶段规划（推荐顺序）

## Phase 1：本地存储落地（先不做云同步）
### 目标
让拍拍伴读在“无后端”时主要功能完整可用。

### iOS 开发
1. 引入 SQLite（GRDB）
2. 建本地表：
   - learning_item
   - review_progress
   - review_event
   - child_profile_local
   - usage_session_local
   - sync_queue
   - sync_metadata
3. 改 Repository：
   - Capture 保存到本地
   - LearningDetail 从本地读写
   - Review 从本地读写
   - Parent 使用本地 usage 聚合
4. UI 全部改成本地优先读

### 验收
- 后端断网 / 下线时，拍读、翻译、句卡保存、复习仍可用

---

## Phase 2：统一 backend 通用同步层
### 后端开发
1. 新增 `sys/sync` 模块
2. 新增：
   - sync client
   - sync cursor
   - sync change log
   - reject log
3. 设计通用 pull / push 协议
4. 完成鉴权和幂等逻辑

### 验收
- 后端支持 reading app 的增量同步协议
- 不影响其他 App

---

## Phase 3：拍拍伴读业务实体接入同步
### 后端
- reading/sync adapter
- learning_item / review_progress / review_event / child_profile / preference / usage 接入

### iOS
- SyncCoordinator
- SyncQueueStore
- Reachability + retry
- 用户同意云同步后开始 push/pull

### 验收
- 同账号两台设备间学习内容可以同步
- 一台设备离线创建内容，恢复网络后可同步

---

## Phase 4：账号合并、恢复、冲突处理
### 功能
- guest -> formal account 数据合并
- 强制重新上传本地数据
- 强制全量拉取云端
- rejected items UI 提示

### 验收
- 重装 App / 新设备登录可以恢复学习内容
- entitlement 拒绝类冲突不会导致数据丢失

---

## Phase 5：运营与监控
### 后端
- 同步成功率
- rejection rate
- 恢复次数
- 设备数 / 活跃同步 client 数

### 前端
- 同步开关
- 同步状态页
- 最近同步时间
- 本地待同步数量

---

## 13. 对当前项目的最终建议

### 推荐最终方案
**第一阶段不要直接上 PowerSync。**

### 推荐路线
1. **先做本地 SQLite/GRDB 本地优先**
2. **再做 unified backend 的轻量 sync 通用层**
3. **先接入拍拍伴读，跑通通用能力**
4. **等第二个/第三个 App 接入时，再抽象复用并评估是否需要更重的同步中间件**

### 为什么这是当前最优解
- 更符合“低运维成本”
- 更符合“后端掉线仍可用”
- 更适合 unified backend 逐步演进
- 不会把第一个 App 变成同步中间件试验田

---

## 14. 下一步可以直接进入开发的拆解清单

### 优先级最高（建议先做）
1. iOS 本地 SQLite 层设计
2. 学习数据模型落表（learning_item / review_progress / review_event）
3. Repository 改为本地优先
4. backend 通用 sync 协议设计
5. reading app 的 sync adapter

### 可以随后补
6. guest -> formal 合并
7. 全量恢复入口
8. 同步状态 UI
9. 图片云备份（如未来需要）

---

如果要进入实现，建议下一步直接产出两份内容：
1. **数据库表设计稿（本地表 + 云端表）**
2. **第一期开发任务清单（按文件拆到 iOS / backend）**
