# saving 报告数据来源排查（2026-04-26）

## 结论

按照当前产品约束：**暂时不做本地数据同步，后端不能存储用户使用数据**，周报/月报/趋势复盘/CSV 导出所需的交易记录数据必须来自 iOS App 本地 CoreData。

本次排查发现：

1. 之前实现的后端报告接口仍然基于后端表 `saving_expense_record` / `saving_saving_record` 聚合，和“后端不存用户使用数据”冲突。
2. 之前新增的 `saving_report_snapshot` 写入接口会把报告 JSON 存到后端，属于派生用户使用数据，也和该约束冲突。
3. 已将 iOS live 依赖切到 `LocalReportRepository`，报告生成改为读取 App 本地 CoreData cache，不再依赖 `/v1/reports/weekly` 或 `/v1/reports/monthly`。
4. 现阶段后端报告接口只能视为历史/调试兼容代码，不应接入正式 App；后续应删除或 feature-gate 禁用写入用户使用数据的记录/报告接口。

## iOS 侧已调整

新增：

```text
SaveMoneyApp/Core/Networking/Repositories/LocalReportRepository.swift
```

`LocalReportRepository` 直接读取：

```text
RecordCacheStoreProtocol.loadRecords(...)
```

生成：

- weekly report
- monthly report
- overview
- previous period comparison
- category breakdown
- category trends
- top saving actions
- high-risk spending window
- locked modules / upgrade suggestion

报告 payload 标识：

```text
generationMode = app_local_aggregation
dataReadiness.aggregationLevel = app_local_records
dataReadiness.note = app_local_only
cached = true
```

live 依赖已从：

```swift
reportRepository: LiveReportRepository(service: reportService)
```

改为：

```swift
reportRepository: LocalReportRepository(cacheStore: recordCacheStore, billingRepository: billingRepository)
```

文件：

```text
SaveMoneyApp/App/AppDependencies.swift
```

因此正式 App 打开周报/月报时，数据来自本机 CoreData，而不是后端聚合。

## 当前仍存在的冲突点

### 1. 后端记录表和 CRUD 仍存在

```text
saving_expense_record
saving_saving_record
SavingFinanceMapper.insertExpense(...)
SavingFinanceMapper.insertSaving(...)
```

如果正式 App 仍调用 `LiveRecordRepository.createExpense/createSaving`，后端仍会存用户记录。这和“后端不能存储用户使用数据”冲突。

建议下一步：把正式 App 的记录创建/编辑/删除也切到本地持久化，后端仅保留登录、订阅校验、配置、paywall、feature flags、App Review Notes。

### 2. 后端报告接口仍基于 DB 聚合

```text
POST /v1/reports/weekly
POST /v1/reports/monthly
SavingFinanceService.report(...)
```

当前正式 iOS 已不再接入它们，但后端接口仍存在。建议下一步 feature-gate 禁用或删除，避免误接入。

### 3. saving_report_snapshot 不应保存到后端

```text
saving_report_snapshot.content_json
POST /v1/reports/{reportType}/snapshots
GET /v1/reports/snapshots
```

报告快照是用户使用数据的派生结果，也不应存后端。建议下一步改为 iOS 本地报告快照，或完全取消首发快照能力。

## 当前测试

已执行：

```bash
cd /home/admin/code/app/saveMoney/mobile/ios
./scripts/p0_static_release_preflight.sh
```

结果：

```text
STATIC_RELEASE_PREFLIGHT_OK
```

本地化 key：三语言均 290，无重复。

## 建议下一步 P0 修正

1. 正式 App 记录写入切为本地-only：新增/改造本地 record store 的 create/update/delete，不再调用后端记录 CRUD。
2. 禁用或删除后端 `/v1/records` 写入接口在 saving 正式环境中的可用性。
3. 禁用或删除后端 `/v1/reports/**` 聚合和 snapshot 写入接口，避免后端存派生用户使用数据。
4. 如果要保留历史报告，做 iOS 本地 `ReportSnapshotStore`，存在 CoreData，不上传。
5. 更新 App Review Notes：明确“记账记录和报告默认仅保存在设备本地；后端仅用于登录、订阅验证和远程配置”。

## P0 修正继续落地：正式路径 local-only（10:11 后）

### iOS 正式路径

新增：

```text
SaveMoneyApp/Core/Networking/Repositories/LocalOnlyRecordRepository.swift
SaveMoneyApp/Core/Networking/Repositories/LocalDashboardRepository.swift
```

`AppDependencies.makeLive` 现在使用：

```swift
recordRepository: LocalOnlyRecordRepository(cacheStore: recordCacheStore)
dashboardRepository: LocalDashboardRepository(cacheStore: recordCacheStore)
reportRepository: LocalReportRepository(cacheStore: recordCacheStore, billingRepository: billingRepository)
```

不再接入：

```swift
LiveRecordRepository
LiveDashboardRepository
LiveReportRepository
```

这意味着正式 App 的记录、看板、报告都从本机 CoreData 读取/写入，不调用后端用户数据接口。

本地记录仍使用 UUID 和现有 DTO 结构，后续如上线云同步，可将本地对象作为待同步对象上传；首发不会自动同步。

### 后端正式入口禁用

以下 controller 已改为 `410 Gone`：

```text
/v1/records/**
/v1/dashboard/overview
/v1/reports/weekly
/v1/reports/monthly
```

中文说明已写入 controller：这些路由保留为未来显式云同步版本的契约占位，但首发禁用，避免后端误存或聚合用户使用数据。

已删除编译入口：

```text
SavingReportSnapshotMapper.java
SavingReportSnapshotService.java
```

`SavingReportControllerWebMvcTest` 改为验证报告聚合接口首发禁用；新增 `SavingLocalOnlyControllerWebMvcTest` 覆盖 records/dashboard/report 均返回 410。

### 数据库/配置

`V23__saving_report_access_and_export_foundation.sql` 已移除 `saving_report_snapshot` 与 `saving_export_audit` 建表，只保留报告访问矩阵和 CSV 本地导出开关。

新增：

```text
V24__saving_local_only_user_data_policy.sql
```

内容：

- 写入 `saving_sync_policy.ios_v1`
- `mode=local_only`
- `serverRecordStorageEnabled=false`
- `serverReportAggregationEnabled=false`
- `serverReportSnapshotEnabled=false`
- `futureCloudSyncCompatible=true`
- 更新 App Review Notes，明确记录和报告默认仅保存在设备本地
- 如果之前本地环境已创建 `saving_report_snapshot` / `saving_export_audit`，迁移会 drop 掉这两张派生数据表

保留既有后端业务表和 mapper 作为未来云同步的兼容基础，但首发正式 API 不会写入它们。

### 测试

已执行：

```bash
python3 校验 V22/V23/V24 migration JSON
```

结果：通过。

```bash
docker run --rm -v /home/admin/code/app/backend:/workspace -w /workspace maven:3.9.9-eclipse-temurin-17 mvn -q -DskipTests compile
```

结果：通过。

```bash
docker run --rm -v /home/admin/code/app/backend:/workspace -w /workspace maven:3.9.9-eclipse-temurin-17 mvn -q -Dtest=SavingConfigControllerWebMvcTest,SavingReportControllerWebMvcTest,SavingLocalOnlyControllerWebMvcTest test
```

结果：通过。

```bash
cd /home/admin/code/app/saveMoney/mobile/ios
./scripts/p0_static_release_preflight.sh
```

结果：`STATIC_RELEASE_PREFLIGHT_OK`，本地化三语言 290 keys，无重复。

### 仍需 Mac/Xcode 真编译

Linux 静态预检不能替代 Swift 编译。需要在 Mac 上验证新增的 `LocalOnlyRecordRepository`、`LocalDashboardRepository`、`LocalReportRepository` 真编译和真机数据流。

## 2026-04-26 补充排查：中文解释说明与 local-only 口径收口

### 本轮发现

继续排查后，发现还有几处可以完善的“说明口径”问题：

1. `V22__saving_config_driven_launch_content.sql` 的 onboarding 文案中仍有“网络恢复后再同步”的旧表达，容易被理解为 saving V1 已承诺云同步。
2. 后端 410 禁用接口的异常 reason 仍是英文，虽然 controller 已有中文注释，但接口返回给排查人员时不够直观。
3. iOS `LocalOnlyRecordRepository`、`LocalDashboardRepository`、`LocalReportRepository` 顶部说明是英文，和本项目“后端中文说明/低风险边界清晰”的要求不完全一致。
4. App 内本地状态条仍使用“本地缓存/同步记录”等表达，和当前“记录默认保存在本机，不上传明细”的正式口径不够一致。

### 已补充内容

新增 migration：

```text
src/main/resources/db/migration/V25__saving_local_only_copy_clarifications.sql
```

用途：

- 把 onboarding 的“网络恢复后再同步”修正为“首发版本不上传记账明细；记录、草稿、看板和报告仍可在本机使用”。
- 把 App Review Notes 的 `offline`、`accountDeletion`、`privacy`、`reviewerNotes` 进一步收口到 local-only 口径。
- 明确：后端只做登录、订阅校验、远程配置和法务/审核文案；记账明细、报告聚合和 CSV 导出默认留在设备本机。

已更新后端 fallback：

```text
SavingConfigService.defaultOnboardingCopy()
SavingConfigService.defaultAppReviewNotes()
```

即使 DB 配置异常，后端兜底文案也不会重新出现“首发已同步”的误导表达。

已更新后端 controller 410 reason：

```text
SavingFinancialRecordController
SavingDashboardController
SavingReportController
```

现在接口禁用原因直接用中文说明，便于发布排查、QA 和后续维护者理解。

已更新 iOS 中文注释：

```text
LocalOnlyRecordRepository.swift
LocalDashboardRepository.swift
LocalReportRepository.swift
```

说明首发数据由本机 CoreData 生成，未来云同步必须通过新的 sync adapter 显式开启，不能悄悄复用首发接口上传数据。

已更新 App 内本地数据状态文案：

```text
Resources/zh-Hans.lproj/Localizable.strings
Resources/en.lproj/Localizable.strings
Resources/es.lproj/Localizable.strings
```

将“本地缓存/同步成功”类表达弱化为“本机数据/数据刷新”，避免用户误以为记录应上传到云端。

### 后续仍建议补充

- 在 Mac/Xcode 真机验收时，重点检查状态条是否还会出现“同步失败/服务端刷新”等旧文案；如果正式 local-only 页面不需要展示状态条，可进一步隐藏 `LocalDataStatusBanner`。
- 隐私政策和 App Store Connect 隐私问卷需要同步写清：saving V1 记账明细默认留在设备本机；后端不存储记账明细、不生成报告快照。
- 如果未来开启云同步，应新增用户可见开关、迁移、同步审计和隐私政策更新，不能通过现有 410 占位接口静默开启。
