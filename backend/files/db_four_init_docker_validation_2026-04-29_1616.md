# first_version_four_table Docker validation

> 时间：2026-04-29 15:46–16:20 Asia/Shanghai

## 目标

按用户要求，在 Docker 中启动后端，验证数据库初始化只使用：

```text
src/main/resources/db/first_version_four_table/sys_common_record.sql
src/main/resources/db/first_version_four_table/app_paipai_record.sql
src/main/resources/db/first_version_four_table/app_saving_record.sql
src/main/resources/db/first_version_four_table/app_fitmystery_record.sql
src/main/resources/db/first_version_four_table/V1__init.sql
```

实际 Flyway 启动验证使用：

```text
SPRING_FLYWAY_LOCATIONS=classpath:db/first_version_four_table
```

注意：Flyway 在该目录下实际执行版本化 migration `V1__init.sql`；四个分组 SQL 是维护来源，`V1__init.sql` 由它们组合生成。

## Docker 环境

临时容器：

```text
apphub-four-init-postgres  postgres:16
apphub-four-init-redis     redis:7-alpine
apphub-four-init-backend   maven:3.9.9-eclipse-temurin-17
```

后端启动命令核心参数：

```text
SPRING_PROFILES_ACTIVE=dev
DB_URL=jdbc:postgresql://apphub-four-init-postgres:5432/apphub_four_init
SPRING_FLYWAY_LOCATIONS=classpath:db/first_version_four_table
SPRING_FLYWAY_ENABLED=true
BACKEND_OPS_TOKEN=four-init-test-token
mvn -q -Dmaven.test.skip=true spring-boot:run
```

## 过程中发现并修复的问题

### 1. SQL splitter 误把注释中的分号当成 statement 结束

现象：

```text
ERROR: syntax error at or near "Type"
Statement: ... Type: TABLE
```

原因：原拆分脚本未正确处理 `--` 行注释，pg_dump 注释中的 `;` 被误切开。

修复：

```text
scripts/split-db-migrations-into-four-init-files.py
```

增强 SQL statement splitter，支持跳过：

- 单引号字符串
- 双引号标识符
- dollar quote
- `--` 行注释
- `/* ... */` 块注释

### 2. `pg_catalog.setval(...)` 被错误归到 common 文件

现象：

```text
ERROR: relation "public.reading_announcement_id_seq" does not exist
```

原因：`SELECT pg_catalog.setval('public.reading_announcement_id_seq', ...)` 被归入 common，执行时 Paipai sequence 尚未创建。

修复：根据 `setval()` 里的 sequence 名前缀归属到对应文件。

### 3. `search_path` 为空导致后续增量 SQL 找不到未带 schema 的表

现象：

```text
ERROR: relation "sys_remote_config" does not exist
```

原因：原 pg_dump baseline 包含：

```sql
SELECT pg_catalog.set_config('search_path', '', false);
```

组合后，后续 V18+ SQL 使用未限定 schema 的 `sys_remote_config`。

修复：生成 first-version init 时替换为：

```sql
SET search_path = public;
```

### 4. `COMMENT ON TABLE/COLUMN reading_*` 被错误归到 common 文件

现象：

```text
ERROR: relation "reading_review_card" does not exist
```

原因：`COMMENT ON COLUMN reading_review_card...` 没被按目标表归属。

修复：COMMENT 语句按目标 table/column 前缀归属到对应 app 文件。

## 已重新生成并审计

执行：

```bash
scripts/split-db-migrations-into-four-init-files.py
scripts/compose-db-four-table-init.sh
scripts/audit-db-four-init-files.sh
```

审计通过：

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

## Docker 启动结果

最终后端已成功启动。

健康检查：

```text
GET /actuator/health -> 200
status: UP
components.db.status: UP
components.redis.status: UP
```

数据库验证：

```text
public base table count: 49
flyway_schema_history count: 1
flyway version/success: 1:true
```

说明：48 张业务表 + 1 张 Flyway schema history 表。

## 接口验证结果

公共/配置类接口通过：

```text
GET /actuator/health -> 200
GET /api/v1/system/healthz -> 200
GET /api/v1/system/apps with X-Ops-Token -> 200
GET /api/v1/bootstrap/config -> 200
GET /api/v1/plans -> 200
GET /v1/config/paywall -> 200
GET /v1/config/categories -> 200
GET /api/v1/fitmystery/config/bootstrap -> 200
GET /api/v1/fitmystery/config/odds -> 200
```

符合预期的鉴权/策略响应：

```text
GET /api/v1/announcements without bearer -> 401
GET /api/v1/fitmystery/me/today without bearer -> 401
GET /api/v1/fitmystery/box/state without bearer -> 401
GET /v1/records -> 410
```

说明：SaveMoney 首版 local-only 记录接口返回 410 是当前策略，不是 SQL 初始化失败。

Demo/bootstrap session 验证未通过，但原因是当前 app definition 策略禁用，不是 SQL 初始化失败：

```text
POST /api/v1/system/auth/apps/{appCode}/sessions/demo -> 403
POST /v1/users/bootstrap -> 403
```

## 当前结论

已完成：

1. Docker 空库只使用 `classpath:db/first_version_four_table` 进行 Flyway 初始化。
2. 初始化 SQL 已修复到可被 Flyway 成功执行。
3. 后端已成功启动。
4. DB/Redis health 为 UP。
5. 三个 App 的公共配置/启动配置类接口可用。

未完全覆盖：

- 需要真实 Apple 登录或显式开启 demo/bootstrap session 后，才能继续验证三个 App 的完整登录态业务写接口。
- 当前 demo/bootstrap 返回 403 是配置策略导致，不是 DB schema 初始化失败。

## 清理

验证完成后建议清理临时 Docker 容器：

```bash
docker rm -f apphub-four-init-backend apphub-four-init-postgres apphub-four-init-redis
docker network rm apphub-four-init-test
```
