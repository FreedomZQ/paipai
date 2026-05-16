# paipaiV2 PowerSync 文件级开发任务清单 v1

日期：2026-04-20  
目标：在第一版内落地 PowerSync 同步，且兼容 unified backend 多 App 复用

---

## 0. 开发原则

1. **先打通底座，再改页面数据源**
2. **先接入 5 张核心同步表，再扩范围**
3. **先保证已登录用户离线可用，再考虑体验优化**
4. **不做 guest 合并逻辑**（因为已明确无游客账户）

---

## 1. backend：通用层（多个 App 共用）

## 1.1 新增目录
```text
backend/src/main/java/com/apphub/backend/sys/powersync/
  controller/
  service/
  model/
  support/
```

## 1.2 新增文件
### A. Token / 安装实例
1. `sys/powersync/controller/SysPowerSyncController.java`
   - 提供 PowerSync token 发放接口
   - 提供 installation 注册/刷新接口
   - 提供重建同步入口

2. `sys/powersync/service/SysPowerSyncService.java`
   - 统一生成 PowerSync claims
   - 绑定 `appCode/userId/installationId`
   - 校验当前 session 是否允许发 token

3. `sys/powersync/model/PowerSyncTokenView.java`
4. `sys/powersync/model/PowerSyncBootstrapRequest.java`
5. `sys/powersync/model/PowerSyncBootstrapView.java`
6. `sys/powersync/model/PowerSyncRebuildRequest.java`

### B. 上传批处理
7. `sys/powersync/controller/SysPowerSyncUploadController.java`
   - 统一接收 PowerSync Connector 上传的批量变更
   - 按 `appCode` 转发给对应 adapter

8. `sys/powersync/service/SysPowerSyncUploadService.java`
   - 通用 envelope 解析
   - installation/user/app 权限校验
   - 调用 app-specific adapter

9. `sys/powersync/model/PowerSyncUploadEnvelope.java`
10. `sys/powersync/model/PowerSyncChangeItem.java`
11. `sys/powersync/model/PowerSyncUploadResult.java`
12. `sys/powersync/model/PowerSyncRejectedItem.java`

### C. Adapter 注册
13. `sys/powersync/support/PowerSyncAppAdapter.java`
   - 通用接口：`supports(appCode)`、`applyBatch(...)`

14. `sys/powersync/support/PowerSyncAppAdapterRegistry.java`
   - Spring 注入所有 adapter 并按 appCode 路由

### D. installation / audit
15. `sys/powersync/service/SysSyncInstallationService.java`
16. `sys/powersync/service/SysSyncAuditService.java`

## 1.3 新增实体/Mapper
### 通用表
17. `sys/powersync/entity/SysSyncInstallationEntity.java`
18. `sys/powersync/mapper/SysSyncInstallationMapper.java`
19. `sys/powersync/entity/SysSyncAuditLogEntity.java`
20. `sys/powersync/mapper/SysSyncAuditLogMapper.java`

## 1.4 新增 migration
21. `src/main/resources/db/migration/V4__powersync_installation_and_audit.sql`
   - 建 `sys_sync_installation`
   - 建 `sys_sync_audit_log`

---

## 2. backend：reading app 专属 PowerSync 层

## 2.1 新增目录
```text
backend/src/main/java/com/apphub/backend/apps/reading/powersync/
```

## 2.2 新增文件
1. `apps/reading/powersync/ReadingPowerSyncAdapter.java`
   - 实现 `PowerSyncAppAdapter`
   - 负责 reading 变更批次落库

2. `apps/reading/powersync/ReadingPowerSyncEntityType.java`
   - 定义支持的 entityType：
     - `child_profile`
     - `review_card`
     - `review_event`
     - `usage_session`
     - `user_preference`

3. `apps/reading/powersync/ReadingPowerSyncValidator.java`
   - childLimit 校验
   - 账号状态校验
   - payload 合法性校验

4. `apps/reading/powersync/ReadingPowerSyncMapper.java`
   - PowerSync payload -> 业务实体 DTO

---

## 2.3 修改现有 reading domain/entity/mapper
### 需要修改的现有文件
1. `apps/reading/domain/entity/ReadingChildProfileEntity.java`
   - 增 `deletedAt`
   - 增 `lastModifiedByInstallationId`
   - 增 `recordVersion`

2. `apps/reading/domain/entity/ReadingReviewCardEntity.java`
   - 增 `sourceText`
   - 增 `translatedText`
   - 增 `sourceLanguageCode`
   - 增 `targetLanguageCode`
   - 增 `sourceType`
   - 增 `lastReviewedAt`
   - 增 `deletedAt`
   - 增 `lastModifiedByInstallationId`
   - 增 `recordVersion`

3. `apps/reading/domain/entity/ReadingUserPreferenceEntity.java`
   - 增 `cloudSyncEnabled`
   - 增 `lastModifiedByInstallationId`
   - 增 `recordVersion`

### 新增 v2 表对应实体
4. `apps/reading/domain/entity/ReadingReviewEventV2Entity.java`
5. `apps/reading/domain/mapper/ReadingReviewEventV2Mapper.java`
6. `apps/reading/domain/entity/ReadingUsageSessionV2Entity.java`
7. `apps/reading/domain/mapper/ReadingUsageSessionV2Mapper.java`

## 2.4 修改现有 service
1. `apps/reading/compat/service/ReadingCompatService.java`
   - review card 创建/更新逻辑补写新字段
   - preference 逻辑补 `cloudSyncEnabled`
   - 避免直接依赖旧 `reading_review_event`

2. `apps/reading/compat/service/ReadingUsageService.java`
   - 读写改向 `reading_usage_session_v2`
   - 聚合逻辑适配 `id(UUID)` 主键的新表

3. `apps/reading/compat/service/ReadingPreferenceService.java`
   - 适配新偏好字段

## 2.5 新增 migration
2. `src/main/resources/db/migration/V5__reading_sync_ready_schema.sql`
   - 升级 `reading_child_profile`
   - 升级 `reading_review_card`
   - 升级 `reading_user_preference`
   - 新建 `reading_review_event_v2`
   - 新建 `reading_usage_session_v2`

---

## 3. backend：PowerSync 配置与部署文件

## 3.1 新增目录
```text
backend/powersync/
  common/
  apps/reading/
```

## 3.2 新增文件
1. `backend/powersync/common/sync-rules.base.yaml`
   - 公共注释与约定

2. `backend/powersync/apps/reading/sync-rules.yaml`
   - 定义 reading 同步 buckets / row filters

3. `backend/powersync/docker-compose.yml`
   - 本地 / 测试环境跑 PowerSync Service

4. `backend/powersync/.env.example`
   - PostgreSQL / token secret / service URL 模板

5. `backend/docs/powersync-部署说明.md`
   - 部署方式、环境变量、健康检查、常见问题

## 3.3 建议同步规则首批表
- `reading_child_profile`
- `reading_review_card`
- `reading_review_event_v2`
- `reading_usage_session_v2`
- `reading_user_preference`

---

## 4. iOS：PowerSync 底座接入

## 4.1 修改项目依赖
1. `ios/project.yml`
   - 新增 PowerSync Swift SDK package
   - 如需要，再新增配套 SQLite helper package
   - 注意：以接入时官方 Swift SDK 仓库 URL / tag 为准

## 4.2 新增目录
```text
paipaiV2/ios/PaipaiReadAlongV2/Core/Sync/
```

## 4.3 新增文件
1. `Core/Sync/PowerSyncManager.swift`
   - 负责 PowerSync client 初始化、启动、停止、重建

2. `Core/Sync/PowerSyncConnector.swift`
   - 提供 token
   - 上传本地变更到 unified backend

3. `Core/Sync/PowerSyncCredentialsStore.swift`
   - 缓存 PowerSync endpoint / token / expiry

4. `Core/Sync/PowerSyncUploadAPI.swift`
   - 对接 backend 批量上传接口

5. `Core/Sync/PowerSyncBootstrapAPI.swift`
   - 对接 backend bootstrap / rebuild 接口

6. `Core/Sync/PowerSyncSyncState.swift`
   - 本地同步状态模型

7. `Core/Sync/PowerSyncInstallationStore.swift`
   - 管理 `installation_id`

8. `Core/Sync/LocalOnlyTables.swift`
   - 本地 only 表定义：附件路径 / runtime kv

9. `Core/Sync/SyncSettingsStore.swift`
   - 云同步开关、本地状态缓存

---

## 5. iOS：Repository 层改造

## 5.1 新增目录
```text
paipaiV2/ios/PaipaiReadAlongV2/Core/Repositories/
```

## 5.2 新增文件
1. `Core/Repositories/ChildRepository.swift`
2. `Core/Repositories/ReviewCardRepository.swift`
3. `Core/Repositories/ReviewEventRepository.swift`
4. `Core/Repositories/UsageSessionRepository.swift`
5. `Core/Repositories/UserPreferenceRepository.swift`

### Repository 职责
- 所有页面优先读本地 PowerSync SQLite
- 写入时先本地写，再由 PowerSync 上传
- 仅对服务端权威能力继续走 BackendClient（登录、购买、删除等）

---

## 6. iOS：模型层改造

## 6.1 需要新增/修改的模型文件
1. `Core/Models/ReviewCardModels.swift`
   - 增 `sourceText`
   - 增 `translatedText`
   - 增 `sourceLanguageCode`
   - 增 `targetLanguageCode`
   - 增 `sourceType`
   - 增 `deletedAt`

2. `Core/Models/UsageModels.swift`
   - 与 `reading_usage_session_v2` 对齐（UUID/string 主键）

3. `Core/Models/PreferenceModels.swift`
   - 增 `cloudSyncEnabled`

4. `Core/Models/ChildModels.swift`
   - 补 `deletedAt` / `recordVersion`（如 UI 需要）

5. 新增 `Core/Models/SyncModels.swift`
   - installation / sync status / rebuild request 等模型

---

## 7. iOS：现有服务层改造

## 7.1 修改现有文件
1. `Core/Services/BackendClient.swift`
   - 新增 PowerSync token 接口
   - 新增 installation bootstrap 接口
   - 新增 rebuild 接口
   - 保留：登录、购买、删除、法务、公告等权威接口

2. `Core/Services/SecureSessionStore.swift`
   - 补 PowerSync token/credential 缓存策略（如不单独分 store）

3. `App/PaipaiReadAlongV2App.swift`
   - 启动流程增加：
     - 安装实例初始化
     - PowerSync bootstrap
     - 本地数据加载
     - 同步状态监听
   - 不再把 BackendClient 当成主数据源

4. `Core/Services/DeviceInfoService.swift`
   - 给 PowerSync bootstrap / installation 注册提供设备信息

---

## 8. iOS：页面级改造

## 8.1 首页 / Capture / Learning / Review / Parent
### 需要改的文件
1. `Features/Home/HomeView.swift`
   - 改为读本地 Repository 数据

2. `Features/Capture/CaptureView.swift`
   - 识别后保存时写本地 `review_card`
   - 图片路径写 `local_asset_file_ref`

3. `Features/Learning/LearningDetailView.swift`
   - 保存句卡改为本地写入 Repository
   - 同步不阻塞 UI

4. `Features/Review/ReviewView.swift`
   - 复习事件写本地 `review_event_v2`
   - 复习状态更新本地 `review_card`

5. `Features/Parent/ParentAreaView.swift`
   - 读本地 usage / child / preference
   - 展示同步状态入口

6. `Features/Parent/ManageChildrenView.swift`
   - 孩子创建/编辑优先走本地库，再同步上云
   - 若后端 entitlement 拒绝，则显示冲突/拒绝提示

7. `Features/Parent/LanguagePreferenceView.swift`
   - 切换偏好写本地 `reading_user_preference`

8. `Features/Parent/DeleteAccountView.swift`
   - 删除账号前需提醒：删除后本地学习数据和云端学习数据都将受影响
   - 删除成功后触发本地 PowerSync 数据清空

9. `Features/Paywall/PaywallView.swift`
   - 保持读取后端权威 entitlement / 计划数据
   - 不通过 PowerSync 做套餐状态变更

10. `Features/Onboarding/OnboardingView.swift`
   - 新增“云同步说明”与用户同意开关入口（可选）

---

## 9. iOS：新增设置与状态页

## 9.1 新增文件
1. `Features/Parent/SyncSettingsView.swift`
   - 云同步开关
   - 最近同步时间
   - 当前同步状态
   - 重新同步按钮

2. `Features/Parent/SyncConflictListView.swift`（可选，第一版可简化）
   - 展示被服务端拒绝的记录

### 第一版最小要求
即使不做冲突列表，也至少要做：
- 开关
- 最近同步时间
- 失败提示
- 重建同步按钮

---

## 10. 后端 / iOS 接口与流程对齐任务

## 10.1 新增 API（统一 backend）
1. `POST /api/v1/powersync/{appCode}/bootstrap`
2. `POST /api/v1/powersync/{appCode}/token`
3. `POST /api/v1/powersync/{appCode}/upload`
4. `POST /api/v1/powersync/{appCode}/rebuild`

## 10.2 iOS 端要对应接入
- `BackendClient.swift`
- `PowerSyncBootstrapAPI.swift`
- `PowerSyncUploadAPI.swift`
- `PowerSyncConnector.swift`

---

## 11. 测试与验收文件

## 11.1 backend 新增测试
1. `src/test/java/com/apphub/backend/sys/powersync/controller/SysPowerSyncControllerWebMvcTest.java`
2. `src/test/java/com/apphub/backend/sys/powersync/controller/SysPowerSyncUploadControllerWebMvcTest.java`
3. `src/test/java/com/apphub/backend/apps/reading/powersync/ReadingPowerSyncAdapterTest.java`

### 验证重点
- 非法 session 不能拿 token
- installation 只能绑定当前登录用户
- upload 批次幂等
- childLimit / 删除状态 / entitlement 校验正确

## 11.2 iOS 手工验收重点
1. 首次登录后完成初始同步
2. 断网后仍可保存句卡和复习
3. 恢复网络后自动同步
4. 第二台设备可拉到同步内容
5. 删除账号后本地库被清理

---

## 12. 推荐开发顺序（严格按这个走）

### 第一阶段：通用底座
1. `V4` migration
2. `sys/powersync/*`
3. PowerSync Service 本地跑通
4. token / installation / upload 协议通

### 第二阶段：reading schema 升级
5. `V5` migration
6. reading adapter / validator / mapper
7. backend upload 到 PostgreSQL 跑通

### 第三阶段：iOS PowerSync 接入
8. `project.yml` 加依赖
9. `Core/Sync/*`
10. `AppState` 启动接入 PowerSync
11. Repository 层接本地 SQLite

### 第四阶段：页面迁移
12. Capture / Learning / Review
13. Parent / Children / Preference
14. Sync settings UI

### 第五阶段：联调与验收
15. 多设备同步
16. 断网离线
17. 后端短时不可用时的本地可用性
18. 删除账号 / 登录失效等边界条件

---

## 13. 第一版开发范围边界（避免失控）

### 必须完成
- PowerSync 同步 5 张核心表
- 本地优先读写
- 多设备同步恢复
- 已登录用户离线可用

### 暂不做
- 原图/音频大文件云同步
- 复杂冲突人工合并 UI
- 访客数据合并
- 多账号本地隔离高级工具页

---

## 14. 与本文档配套的设计文档
同目录查看：
1. `paipaiV2-PowerSync多APP同步方案-v1.md`
2. `paipaiV2-PowerSync数据库表设计-v1.md`
