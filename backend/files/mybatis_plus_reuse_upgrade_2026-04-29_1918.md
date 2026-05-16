# MyBatis-Plus 可复用数据访问层升级记录

时间：2026-04-29 19:18 Asia/Shanghai

## 目标

后端数据库增删改查严格收敛到 MyBatis-Plus Mapper / Service 接口层，业务服务不直接绑定底层 Mapper 实现，便于未来拆成微服务时把数据访问接口替换成 RPC/HTTP 适配器，且不改变现有业务逻辑。

## 本轮已完成

### 1. 补齐 BaseMapper 继承

审计时发现以下 Mapper 原来没有继承 `BaseMapper`：

- `SavingFinanceMapper`
- `FitMysteryActivityMapper`
- `FitMysteryBoxMapper`
- `FitMysteryPurchaseMapper`
- `FitMysteryReportMapper`
- `FitMysteryAccountMapper`

已全部改为 `extends BaseMapper<...Entity>`。当前审计结果：后端所有 Mapper 均已继承 `BaseMapper`。

### 2. 给实体补齐 MyBatis-Plus 表映射

新增/补齐 `@TableName`、`@TableId(type = IdType.INPUT)`：

- `SavingExpenseRecordEntity` → `saving_expense_record`
- `SavingSavingRecordEntity` → `saving_saving_record`
- `FitActivityEventEntity` → `fit_activity_event`
- `FitBlindBoxDrawEntity` → `fit_blind_box_draw`
- `FitBlindBoxItemEntity` → `fit_blind_box_item`

新增 MyBatis-Plus 实体：

- `FitReportGenerationLedgerEntity` → `fit_report_generation_ledger`
- `FitDrawChanceLedgerEntity` → `fit_draw_chance_ledger`
- `FitAccountDeletionRequestEntity` → `fit_account_deletion_request`

### 3. 新增可复用数据访问接口 + ServiceImpl 实现

Saving：

- `SavingFinanceDataService extends IService<SavingExpenseRecordEntity>`
- `SavingFinanceDataServiceImpl extends ServiceImpl<SavingFinanceMapper, SavingExpenseRecordEntity>`
- `SavingSavingRecordMapper extends BaseMapper<SavingSavingRecordEntity>`

FitMystery：

- `FitMysteryActivityDataService extends IService<FitActivityEventEntity>`
- `FitMysteryActivityDataServiceImpl extends ServiceImpl<FitMysteryActivityMapper, FitActivityEventEntity>`
- `FitMysteryBoxDataService extends IService<FitBlindBoxDrawEntity>`
- `FitMysteryBoxDataServiceImpl extends ServiceImpl<FitMysteryBoxMapper, FitBlindBoxDrawEntity>`
- `FitMysteryReportDataService extends IService<FitReportGenerationLedgerEntity>`
- `FitMysteryReportDataServiceImpl extends ServiceImpl<FitMysteryReportMapper, FitReportGenerationLedgerEntity>`
- `FitMysteryPurchaseDataService extends IService<FitDrawChanceLedgerEntity>`
- `FitMysteryPurchaseDataServiceImpl extends ServiceImpl<FitMysteryPurchaseMapper, FitDrawChanceLedgerEntity>`
- `FitMysteryAccountDataService extends IService<FitAccountDeletionRequestEntity>`
- `FitMysteryAccountDataServiceImpl extends ServiceImpl<FitMysteryAccountMapper, FitAccountDeletionRequestEntity>`

### 4. 业务服务改为依赖数据访问接口

已将下列业务服务从直接依赖 Mapper 改为依赖可复用 DataService 接口：

- `SavingFinanceService`
- `SavingAccountDeletionService`
- `FitMysteryActivityService`
- `FitMysteryBoxService`
- `FitMysteryReportService`
- `FitMysteryPurchaseService`
- `FitMysteryAccountService`

这些改动保留原 SQL 与原返回结构，不改变现有业务逻辑，只是在业务层和 Mapper 之间加了一层可替换接口边界。

## 编译验证

执行：

```bash
docker run --rm -v "$PWD:/workspace" -v "$HOME/.m2:/root/.m2" -w /workspace \
  maven:3.9.9-eclipse-temurin-17 mvn -q -DskipTests compile
```

结果：通过。

## 仍需后续继续收敛的范围

本轮先完成了原本最不符合规范的 saving / fitmystery 数据访问层。后端中 reading 与 sys 模块历史服务仍存在业务服务直接注入 Mapper 的情况，虽然它们的 Mapper 已经是 `BaseMapper`，但如果要完全满足“业务服务只依赖可替换数据接口”的微服务拆分标准，下一步应继续按相同模式拆出：

- `Reading*DataService` / `Reading*DataServiceImpl`
- `SysAuthDataService` / `SysBillingDataService` / `SysEntitlementDataService` / `SysPowerSyncDataService`

建议按模块分批做，避免一次性改动过大影响现有业务。
