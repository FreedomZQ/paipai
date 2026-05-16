# paipaiV2 PowerSync 多 App 同步方案 v1

日期：2026-04-20  
适用范围：统一 backend（多个 App 共用） + 当前首个接入 App `reading/paipaiV2`

---

## 1. 本次重评估后的结论

在你新增的前提下：
- **第一版就包含数据同步功能**
- **使用 PowerSync，不走自建 sync 协议**
- **没有游客账户，所有用户都通过 AppleID / 邮箱验证码正式登录**
- **需要兼容后续多个 App**

新的推荐结论是：

### 结论
**第一版可以上 PowerSync，同步范围控制在“结构化学习数据 + 偏好 + usage session”，不要把图片/音频/复杂衍生数据一起做进去。**

这样做的优点：
1. **减少自建增量同步框架的开发和维护成本**
2. 本地 SQLite + PowerSync 的模式天然适合离线优先
3. 统一 backend 只需要补一层 **PowerSync 通用接入层**，后续其它 App 也能复用
4. 没有游客账户，登录与数据归属简单很多，避免 guest -> formal 合并的复杂度

### 需要接受的现实
- 第一个版本要多一个基础设施：**PowerSync Service**
- 第一个版本要做的不是“只有前端本地缓存”，而是：
  - iOS 本地 SQLite（PowerSync 本地库）
  - PostgreSQL 云端表
  - PowerSync Service
  - unified backend 的 PowerSync 认证与上传接入层

但相比自建 sync：
- **开发量更稳定**
- **维护风险更低**
- **后续第二个、第三个 App 复用成本更低**

---

## 2. 关键产品决策

## 2.1 登录策略
### 采用正式账户前置
没有游客账户。

因此：
- **首次使用必须联网登录**（AppleID 或邮箱验证码）
- 登录成功并完成首轮同步后，App 本地已有数据快照
- 后续即使 backend 暂时不可用，用户仍可继续离线使用主要功能

### 离线可用边界
#### 已登录过的用户
可以离线使用：
- 查看本地已同步/已保存的学习数据
- 本地新增学习内容
- 本地复习
- 本地 usage 记录

#### 从未登录过的新用户
不能离线首次进入，因为没有 guest 账户

这点在产品上要明确：
> **无游客模式下的离线可用，是“已登录用户的离线可用”，不是“首次安装就可离线使用”。**

---

## 2.2 同步范围（第一版）
### 第一版要同步
1. **孩子档案**
2. **学习内容主数据**（句子、翻译、语言方向、来源类型）
3. **复习事件**
4. **复习状态/进度**
5. **用户偏好**（界面语种、默认学习方向等）
6. **usage session**（用于多设备聚合与恢复）

### 第一版不做云同步
1. 原始拍照图片二进制
2. TTS 临时音频缓存
3. OCR 临时原图/裁剪图
4. 公告展示缓存
5. 法务 HTML 文本内容
6. 云端服务消费计数（仍以后端权威为准，不依赖本地同步）

---

## 2.3 权威边界
### 本地优先 + 服务端权威分层
#### 本地优先的数据
- 学习内容
- 复习记录
- usage session
- 偏好

#### 服务端权威的数据
- 账号身份
- 会话合法性
- 删除账号验证
- 购买与 entitlement
- childLimit
- 套餐与价格
- 公告
- 法务文档 URL

### 设计要求
即使同步失败，以下主功能仍要可用：
- 拍读 / 识别后的内容保存
- 句子翻译结果保存
- 句子复习
- 本地 usage 统计

---

## 3. 推荐总体架构

## 3.1 组件
### iOS 客户端
- PowerSync Swift SDK
- PowerSync 本地 SQLite
- 本地 only 表（附件路径 / 运行状态缓存）
- App Repository 层
- PowerSync Connector（获取 token、上传本地改动）

### Sync 基础设施
- PowerSync Service（Docker / K8s 部署）
- Sync Rules（按 app_code + user_id 隔离）

### 统一 backend（Java）
- `sys/powersync` 通用模块
- 负责：
  - PowerSync token 发放
  - installation 注册
  - 写入批处理上传接入
  - App-specific adapter 分发
  - 审计日志

### PostgreSQL
- 业务表（reading app）
- 通用 sync/installation/audit 表
- PowerSync 直接读业务表增量变化

---

## 3.2 正确的数据流

### Pull / 下行同步
1. iOS 启动后读取本地 PowerSync 数据库
2. 如果有网络且有合法 session，向 unified backend 请求 PowerSync token
3. PowerSync SDK 使用 token 连接 PowerSync Service
4. PowerSync Service 根据 Sync Rules 从 PostgreSQL 拉取该用户在该 App 范围内的数据
5. PowerSync SDK 将变更应用到本地 SQLite
6. UI 自动读取本地库刷新

### Push / 上行同步
PowerSync **不替代业务写入接口**，而是：
1. App 先写本地 PowerSync SQLite
2. SDK 记录本地 CRUD
3. iOS Connector 收集本地待上传变更
4. Connector 调 unified backend 的批量上传接口
5. unified backend 校验身份、执行业务 upsert / soft delete
6. PostgreSQL 产生变更后，PowerSync Service 再把这些变更同步到所有设备

### 这意味着
PowerSync 负责：
- 本地 SQLite
- 下行增量同步
- 变更跟踪

unified backend 仍负责：
- 权限
- 幂等写入
- 业务规则
- 付费限制
- 删除/审计

---

## 4. 多 App 兼容设计

## 4.1 通用层放哪里
建议新增通用模块：

```text
backend/src/main/java/com/apphub/backend/sys/powersync/
  controller/
  service/
  model/
  support/
```

### 通用模块职责
1. PowerSync token 发放
2. 安装实例 registration / bind
3. 批量上传协议定义
4. App adapter 注册与路由
5. 审计日志
6. 强制重建 / 重新同步入口

---

## 4.2 App 特有层放哪里
以 `reading` 为例：

```text
backend/src/main/java/com/apphub/backend/apps/reading/powersync/
  ReadingPowerSyncAdapter.java
  ReadingPowerSyncMapper.java
  ReadingPowerSyncValidator.java
```

职责：
- reading 的实体类型注册
- payload -> 业务实体转换
- business validation（如 childLimit、删除状态等）
- 业务写入调用现有 service / mapper

后续其它 App：
- 新增 `apps/{appCode}/powersync/` 即可接入
- 通用层不需要重写

---

## 4.3 Sync Rules 组织方式
建议目录：

```text
backend/powersync/
  common/
    sync-rules.base.yaml
  apps/
    reading/
      sync-rules.yaml
```

### 规则基本原则
所有同步实体都至少带：
- `app_code`
- `owner_user_id` / `user_id`
- `updated_at`
- `deleted_at`（或软删除标记）

Sync Rules 按以下 claim 过滤：
- `appCode`
- `userId`
- `installationId`

这样未来多个 App 共用一套 PowerSync Service 仍能隔离清楚。

---

## 5. iOS 本地数据策略

## 5.1 本地数据库
第一版使用：
- **PowerSync SQLite 作为主本地数据源**

不建议第一版再额外引入一套自建 sync SQLite + 另一套缓存 SQLite。

### 建议分两类表
#### A. 同步表（由 PowerSync 管理）
- 孩子档案
- 学习内容
- 复习事件
- 复习进度
- usage session
- 用户偏好

#### B. 本地 only 表（不进入云同步）
- 图片本地路径
- OCR 临时文件引用
- TTS 缓存文件索引
- 本地运行状态缓存
- 已展示公告缓存

---

## 5.2 离线可用策略
### 已登录用户
- 所有主界面优先读本地 SQLite
- 无网时照常写本地 SQLite
- 待网络恢复后由 PowerSync Connector 自动上传

### 会话过期策略
- 若用户已登录过且本地已有数据，不因短时 backend 不可达就立刻强制退出
- 本地继续进入“离线模式”
- 等网络恢复后再刷新 token / sync

---

## 6. 第一版业务实体建议

基于当前代码和最小改动原则，第一版不强行重构成全新领域模型，而是：

### 保留并同步这些现有表/实体
1. `reading_child_profile`
2. `reading_review_card`
3. `reading_review_event`
4. `reading_usage_session`
5. `reading_user_preference`

### 其中要做的结构升级
#### `reading_review_card`
把它升级成“学习内容 + 复习状态合一”的第一版同步主表。

补字段：
- `source_text`
- `translated_text`
- `source_language_code`
- `target_language_code`
- `source_type`
- `deleted_at`
- `last_modified_by_installation_id`
- `last_reviewed_at`

> 这样可以减少第一版大规模重构成本。
> 第二版如果你想进一步演进，再拆成 `learning_item + review_progress`。

#### `reading_review_event`
改成客户端可生成 ID 的同步友好结构：
- `id` 改为 UUID/String 主键
- 事件 append-only

#### `reading_usage_session`
改成客户端生成的 `session_uuid` 为主键或等价同步主键

---

## 7. 正式登录前提下的用户体验

因为没有 guest：

### 首次登录前
- 不能进入真正的学习主流程
- 只能停留在登录/引导页

### 登录成功后
- 完成首轮 bootstrap + PowerSync 初始拉取
- 之后本地数据可持续使用

### 同步开关建议
设置里增加：
- `云同步备份与多设备恢复` 开关
- 默认：**登录后首次说明，再征得用户同意开启**

原因：
- 审核和隐私上更稳
- 用户更清楚“哪些学习数据会进入云端”

---

## 8. PowerSync 接入的第一版开发范围

## 8.1 必做
1. PowerSync Service 部署
2. unified backend 的 token / installation / upload 接口
3. reading 同步实体接入
4. iOS PowerSync SDK 集成
5. 本地 SQLite 改成本地优先读取
6. 同步状态与失败重试

## 8.2 暂缓
1. 图片云同步
2. 音频云同步
3. 复杂冲突人工合并 UI
4. 后台超复杂静默同步优化
5. 跨账号合并（因为无 guest）

---

## 9. 风险与控制策略

## 9.1 风险：PowerSync 接入复杂度被低估
### 控制
第一版同步实体控制在 5 张表以内：
- child_profile
- review_card
- review_event
- usage_session
- user_preference

## 9.2 风险：付费能力被本地绕过
### 控制
- entitlement 不同步为本地主权威
- childLimit / cloud quota / 购买状态仍以后端为准
- 本地只缓存展示，不主导规则

## 9.3 风险：旧代码大量依赖 BackendClient
### 控制
先改 Repository 层，让页面读写走本地库；BackendClient 逐步退到：
- 登录
- 购买
- 删除账号
- PowerSync token
- 批量上传
- 少量权威接口

## 9.4 风险：后端挂了时登录失败
### 控制
产品定义清楚：
- 首次登录必须联网
- 已登录用户才享受离线可用

---

## 10. 推荐开发阶段

### Phase 1：底座与 schema
- PostgreSQL 表升级为 sync-friendly
- PowerSync Service 跑起来
- unified backend 加 token / installation / upload 接口

### Phase 2：iOS 本地数据源切换
- PowerSync SDK 接入
- 本地 SQLite 为主
- review / capture / parent 数据改读本地

### Phase 3：reading 实体同步打通
- child / card / event / usage / preference 进入 Sync Rules
- 批量上传逻辑完成
- 多设备恢复验证

### Phase 4：设置与审计
- 同步开关
- 最近同步时间
- 失败提示
- 重建同步入口

---

## 11. 最终建议

### 对你的项目，PowerSync 第一版是可行的，但必须坚持两条红线：
1. **同步只做结构化学习数据，不碰大文件**
2. **主要功能仍坚持本地优先**

### 这样做的结果是：
- backend 挂了，老用户仍可离线继续学
- 新功能同步可以多设备恢复
- unified backend 的 PowerSync 通用层以后能复用给其他 App

---

## 12. 本文档配套产物
同目录下继续查看：
1. `paipaiV2-PowerSync数据库表设计-v1.md`
2. `paipaiV2-PowerSync文件级开发任务清单-v1.md`
