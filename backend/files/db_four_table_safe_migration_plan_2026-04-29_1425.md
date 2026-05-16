# 四表模型安全迁移计划（保证项目可启动）

> 时间：2026-04-29 14:25 Asia/Shanghai

## 用户约束

三个 App 和统一后端都是第一版。数据库初始化应收口为 4 张表：

1. `sys_common_record`
2. `app_paipai_record`
3. `app_saving_record`
4. `app_fitmystery_record`

同时必须确保项目正常启动和可用。

## 已完成的安全前置工作

### 1. 未破坏当前启动路径

当前 Flyway 激活目录仍是：

`src/main/resources/db/migration`

本轮没有覆盖或删除其中任何现有 migration，因此当前后端启动路径不受影响。

### 2. 新增四表 V1 草案，放在非 Flyway 激活目录

新增：

`src/main/resources/db/first_version_four_table/V1__init.sql`

该文件目前不在 `db/migration` 下，不会被 Spring Boot/Flyway 自动执行，用于后续安全重构目标。

四表草案已通过专用门禁：

```bash
scripts/audit-db-four-table-shape.sh
```

结果：PASS

### 3. 新增四表形态审计脚本

新增：

`scripts/audit-db-four-table-shape.sh`

用途：

- 校验 SQL 中 `CREATE TABLE` 正好 4 个。
- 校验表名白名单：
  - `sys_common_record`
  - `app_paipai_record`
  - `app_saving_record`
  - `app_fitmystery_record`
- 校验通用字段：
  - `record_type`
  - `record_key`
  - `payload_json`
  - `metadata_json`
  - `created_at`
  - `updated_at`
- 阻断 raw Flyway placeholder token `${...}`。

### 4. 生成当前表依赖矩阵

新增：

`files/db_four_table_dependency_matrix.md`

检测结果：当前共有 48 张旧表依赖：

- common/sys：22 张
- paipai/reading：14 张
- saving：2 张
- fitmystery：10 张

所有直接删旧表都会有较高启动风险，因为大量 Java mapper/entity/service 仍引用旧表。

## 为什么不能直接替换 `db/migration/V1__init.sql`

如果现在直接把正式 `V1__init.sql` 改成四表：

- `@TableName("reading_*")` 的 MyBatis Plus entity 会找不到表。
- `SavingFinanceMapper` 里的 SQL 会找不到 `saving_expense_record` / `saving_saving_record`。
- FitMystery mapper 里的 SQL 会找不到 `fit_*` 表。
- 系统登录/计费/配置逻辑会找不到 `sys_remote_config`, `sys_user`, `sys_auth_session`, `sys_purchase_transaction` 等表。

结论：直接替换会破坏启动/运行。

## 保证可启动的迁移路线

### Phase 0：当前已完成

- 四表 schema 草案独立存放。
- 依赖矩阵生成。
- 当前激活 migration 不动，保证现有启动路径不变。

### Phase 1：新增四表通用 Repository，不替换旧逻辑

新增但不接管业务：

- `SysCommonRecordEntity`
- `AppRecordEntity`
- `FourTableRecordRepository` 或分表 repository
- JSON 序列化辅助器

目的：让编译通过，同时不影响现有业务。

### Phase 2：按模块切读写，先从低风险配置开始

迁移顺序建议：

1. `sys_remote_config` → `sys_common_record(record_type='remote_config')`
2. release/version/app definition 配置 → `sys_common_record`
3. saving local-only 相关后端 compat 配置 → `sys_common_record`
4. FitMystery pool/item 静态配置 → `app_fitmystery_record`

每切一小块跑编译/门禁。

### Phase 3：迁移核心业务对象

建议顺序：

1. SaveMoney，因为首版 local-only，后端业务表依赖最少。
2. FitMystery，因为 mapper 多但集中在几个 domain mapper。
3. Paipai，因为 reading 表最多且 PowerSync/Review/Usage 相关联动更多。
4. Sys auth/billing/entitlement，最后切，因为影响登录、购买、权益全局链路。

### Phase 4：激活四表 V1

只有满足以下条件才把四表 `V1__init.sql` 放入正式 `db/migration`：

- Java 编译通过。
- 后端测试通过。
- 所有 mapper 不再引用旧表。
- `scripts/audit-db-four-table-shape.sh src/main/resources/db/migration/V1__init.sql` 通过。
- 旧 `V18-V36` 已合并/删除或归档，不再被 Flyway 激活。

## 当前建议下一步

继续 Phase 1：新增四表通用 Repository 和实体，但保持旧业务逻辑不切换。这样可以逐步让代码具备四表读写能力，同时保持项目可启动。
