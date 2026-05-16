# 四个初始化 SQL 文件使用说明

> 时间：2026-04-29 14:55 Asia/Shanghai

## 目标

把当前正式 migration 中的初始化 SQL 按责任域拆成 4 个文件，方便首版数据库初始化、审查和后续维护：

```text
src/main/resources/db/first_version_four_table/sys_common_record.sql
src/main/resources/db/first_version_four_table/app_paipai_record.sql
src/main/resources/db/first_version_four_table/app_saving_record.sql
src/main/resources/db/first_version_four_table/app_fitmystery_record.sql
```

这些文件目前不在正式 Flyway 自动执行目录：

```text
src/main/resources/db/migration
```

因此不会改变当前后端启动行为。

## 文件职责

| 文件 | 归属 | 当前表前缀 |
|---|---|---|
| `sys_common_record.sql` | 统一后端/common | `sys_*` |
| `app_paipai_record.sql` | Paipai | `reading_*` |
| `app_saving_record.sql` | SaveMoney | `saving_*` |
| `app_fitmystery_record.sql` | FitMystery | `fit_*` |

## 重新生成

如果后续 `src/main/resources/db/migration/V*.sql` 发生变化，运行：

```bash
cd /home/admin/code/app/backend
scripts/split-db-migrations-into-four-init-files.py
```

该脚本会：

1. 读取当前 `src/main/resources/db/migration/V*.sql`。
2. 按 statement 目标对象分组。
3. 重写四个初始化 SQL 文件。
4. 生成组合版：

```text
src/main/resources/db/first_version_four_table/V1__init.sql
```

## 重新组合

如果只修改了四个初始化 SQL 文件，想重新生成组合版：

```bash
cd /home/admin/code/app/backend
scripts/compose-db-four-table-init.sh
```

## 审计

推荐每次生成后运行：

```bash
cd /home/admin/code/app/backend
scripts/audit-db-four-init-files.sh
```

通过条件：

- 四个初始化 SQL 文件都存在且非空。
- 四个文件中的 `CREATE TABLE` 集合与正式 migration 中的 `CREATE TABLE` 集合完全一致。
- 没有遗漏、没有多出、没有重复。
- 每个文件只包含自己归属前缀的表：
  - common: `sys_*`
  - Paipai: `reading_*`
  - SaveMoney: `saving_*`
  - FitMystery: `fit_*`

当前审计结果：

```text
sys_common_record.sql: 22 CREATE TABLE statements
app_paipai_record.sql: 14 CREATE TABLE statements
app_saving_record.sql: 2 CREATE TABLE statements
app_fitmystery_record.sql: 10 CREATE TABLE statements
source CREATE TABLE count: 48
split CREATE TABLE count: 48
BLOCKERS: none
audit result: PASS
```

## Manifest

当前表清单和来源 migration 已记录在：

```text
files/db_four_init_files_manifest_2026-04-29.json
```

## 激活注意事项

不要直接把 `first_version_four_table` 目录当成 Flyway active migration 目录。

如果后续要作为正式首版 baseline 激活，需要先决定以下方式之一：

1. 部署初始化时按四个文件顺序手动/脚本执行。
2. 使用组合版 `V1__init.sql` 作为 Flyway baseline。

无论采用哪种方式，激活前都需要完整验证：

```bash
scripts/audit-db-four-init-files.sh
scripts/audit-multi-app-isolation.sh
scripts/audit-frontend-backend-contract.sh
scripts/check-no-auth-compat-routes.sh
mvn -q test
mvn -q clean verify
```

以及 iOS/Xcode/StoreKit/Apple Sign-In 相关环境验证。
