# 四个初始化 SQL 文件整理记录

> 时间：2026-04-29 14:46–14:58 Asia/Shanghai

## 用户最终澄清

用户要的不是“数据库物理上立刻只有 4 张 generic record 表”，而是为了方便初始化，把当前所有初始化 SQL 按 4 个文件组织：

- `sys_common_record.sql`：统一后端 / sys / common 初始化
- `app_paipai_record.sql`：Paipai / reading 初始化
- `app_saving_record.sql`：SaveMoney / saving 初始化
- `app_fitmystery_record.sql`：FitMystery / fit 初始化

因此，四个文件是“初始化文件分组”，不是强制每个文件只有一张数据库表。

## 已完成文件

生成目录：

```text
backend/src/main/resources/db/first_version_four_table/
```

生成文件：

```text
sys_common_record.sql
app_paipai_record.sql
app_saving_record.sql
app_fitmystery_record.sql
V1__init.sql
```

说明：该目录目前仍然不在正式 Flyway 激活路径 `src/main/resources/db/migration` 下，因此不会破坏当前后端启动。

## 分组结果

从当前正式 migration 目录：

```text
backend/src/main/resources/db/migration/V*.sql
```

拆分得到：

| 文件 | 分组 | CREATE TABLE 数量 |
|---|---:|---:|
| `sys_common_record.sql` | `sys_*` common/backend | 22 |
| `app_paipai_record.sql` | `reading_*` Paipai | 14 |
| `app_saving_record.sql` | `saving_*` SaveMoney | 2 |
| `app_fitmystery_record.sql` | `fit_*` FitMystery | 10 |
| 合计 |  | 48 |

与当前 active migrations 中的 `CREATE TABLE` 总数一致：48。

## 文件示例

`sys_common_record.sql` 开头包含：

```sql
CREATE TABLE public.sys_app (...)
CREATE TABLE public.sys_app_store_notification (...)
CREATE TABLE public.sys_audit_log (...)
```

`app_paipai_record.sql` 开头包含：

```sql
CREATE TABLE public.reading_announcement (...)
CREATE TABLE public.reading_child_profile (...)
CREATE TABLE public.reading_child_usage_daily (...)
```

`app_saving_record.sql` 包含：

```sql
CREATE TABLE IF NOT EXISTS public.saving_expense_record (...)
CREATE TABLE IF NOT EXISTS public.saving_saving_record (...)
```

`app_fitmystery_record.sql` 开头包含：

```sql
CREATE TABLE IF NOT EXISTS public.fit_activity_event (...)
CREATE TABLE IF NOT EXISTS public.fit_points_ledger (...)
CREATE TABLE IF NOT EXISTS public.fit_daily_score_snapshot (...)
```

## 新增/调整脚本

### 1. 生成脚本

```bash
backend/scripts/split-db-migrations-into-four-init-files.py
```

用途：

- 读取当前 `src/main/resources/db/migration/V*.sql`。
- 按 SQL statement 的目标表/序列/索引/更新对象进行分组。
- 输出四个初始化 SQL 文件。
- 同时生成便捷组合文件：

```text
src/main/resources/db/first_version_four_table/V1__init.sql
```

### 2. 审计脚本

```bash
backend/scripts/audit-db-four-init-files.sh
```

用途：

- 对比 active migrations 和四个拆分文件中的 `CREATE TABLE` 集合。
- 确保没有遗漏表、没有多出表、没有重复表。
- 确保四个文件按前缀归属：
  - common 文件只放 `sys_*`
  - Paipai 文件只放 `reading_*`
  - SaveMoney 文件只放 `saving_*`
  - FitMystery 文件只放 `fit_*`

### 3. 兼容旧脚本名

以下脚本已改成 wrapper，避免继续沿用错误的“只能有 4 张物理表”理解：

```bash
backend/scripts/audit-db-four-table-init-files.sh
backend/scripts/audit-db-four-table-shape.sh
```

现在它们都会转调：

```bash
backend/scripts/audit-db-four-init-files.sh
```

### 4. 组合脚本

```bash
backend/scripts/compose-db-four-table-init.sh
```

用途：

- 按固定顺序拼接四个初始化文件，生成组合版 `V1__init.sql`。
- 方便后续如果需要把四文件初始化转换成单文件 Flyway baseline。

## 已执行验证

```bash
scripts/split-db-migrations-into-four-init-files.py
scripts/compose-db-four-table-init.sh
scripts/audit-db-four-init-files.sh
scripts/audit-db-four-table-init-files.sh
scripts/audit-db-four-table-shape.sh
python3 -m py_compile scripts/split-db-migrations-into-four-init-files.py
bash -n scripts/compose-db-four-table-init.sh scripts/audit-db-four-init-files.sh scripts/audit-db-four-table-init-files.sh scripts/audit-db-four-table-shape.sh
```

结果：通过。

审计摘要：

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

## 启动安全性

当前没有改动正式 Flyway 目录：

```text
backend/src/main/resources/db/migration
```

因此当前项目启动路径仍保持原样。

后续如果要启用四文件初始化，有两个选择：

1. 继续作为手动/部署初始化材料使用四个文件。
2. 用 `compose-db-four-table-init.sh` 生成单文件 baseline 后，再经完整后端验证后替换/调整正式 Flyway baseline。
