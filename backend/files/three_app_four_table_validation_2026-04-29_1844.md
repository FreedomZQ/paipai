# 三个 App + first_version_four_table Docker 联调验证

时间：2026-04-29 18:44 Asia/Shanghai

## 验证环境

- 后端目录：`/home/admin/code/app/backend`
- 初始化 SQL 目录：`src/main/resources/db/first_version_four_table`
- Spring/Flyway 指定加载：`SPRING_FLYWAY_LOCATIONS=classpath:db/first_version_four_table`
- Docker 容器：
  - Postgres：`apphub-four-init-postgres3`
  - Redis：`apphub-four-init-redis3`
  - Backend：`apphub-four-init-backend3`
- Base URL：`http://127.0.0.1:18085`
- 完整接口日志：`files/three_app_four_table_api_validation_20260429_184446.log`

## 修复内容

1. **补齐 FitMystery app 注册数据**
   - 新增正式迁移：`src/main/resources/db/migration/V37__fitmystery_sys_app_registration.sql`
   - 重新生成 `src/main/resources/db/first_version_four_table/sys_common_record.sql`
   - 重新组合 `src/main/resources/db/first_version_four_table/V1__init.sql`
   - 修复后 `sys_app=fitmystery,paipai_readingcompanion,saving`

2. **修复 App 侧系统鉴权接口被 Ops token 拦截**
   - 文件：`src/main/java/com/apphub/backend/common/filter/OpsTokenFilter.java`
   - `/api/v1/system/auth/**` 是多 App 登录/会话接口，本身有 session 策略；不应被 `BACKEND_OPS_TOKEN` 拦截。
   - 修复后三个 app 的 `/api/v1/system/auth/apps/{appCode}/me` 均可用。

3. **修复 PowerSync audit 写 jsonb 失败**
   - 文件：
     - `src/main/java/com/apphub/backend/sys/powersync/mapper/SysSyncAuditLogMapper.java`
     - `src/main/java/com/apphub/backend/sys/powersync/service/SysSyncAuditService.java`
   - 原因：MyBatis-Plus 默认 insert 把 `detail_json` 按 varchar 传入 Postgres `jsonb` 列，导致 `column "detail_json" is of type jsonb but expression is of type character varying`。
   - 修复：新增 `insertJsonb`，SQL 中 `CAST(#{detailJson} AS jsonb)`。

## 数据库验证

- Flyway：`Successfully applied 1 migration`，`flyway=1:true`
- 业务表数量：`app_tables=48`
- `sys_app`：`fitmystery,paipai_readingcompanion,saving`
- `sys_remote_config`：`154`
- app 表分布：
  - `reading_tables=14`
  - `saving_tables=2`
  - `fit_tables=10`

## 接口覆盖结果

### 公共/系统

- `GET /actuator/health` → 200
- `GET /api/v1/system/healthz` → 200
- `GET /api/v1/system/apps`（带 Ops token）→ 200

### 拍拍伴读 `/home/admin/code/app/paipai`

已覆盖前端实际调用的主要 public/auth/business 面：

- `GET /api/v1/bootstrap/config` → 200
- `GET /api/v1/plans` → 200
- `GET /api/v1/legal/docs` → 200
- `GET /api/v1/apps/paipai_readingcompanion/release/app-version` → 200
- `GET /api/v1/system/auth/me` → 200
- `GET /api/v1/system/auth/apps/paipai_readingcompanion/me` → 200
- `GET /api/v1/account/me/state` → 200
- `GET /api/v1/account/me/home-summary` → 200
- `GET /api/v1/learning/daily-task` → 200
- `GET /api/v1/reports/weekly/current` → 200
- `GET /api/v1/reports/weekly/history` → 200
- `GET /api/v1/subscriptions/status` → 200
- `GET /api/v1/billing/entitlement` → 200
- `GET /api/v1/announcements` → 200
- `POST /api/v1/powersync/paipai_readingcompanion/bootstrap`（cloudSyncEnabled=false）→ 200

说明：免费用户请求 PowerSync token / 开启云同步会按权益策略拒绝，这是业务策略，不是数据库初始化问题。

### 省钱 `/home/admin/code/app/saveMoney`

已覆盖前端 live 依赖的远程配置、版本、登录态、权益接口：

- `GET /api/v1/apps/saving/release/app-version` → 200
- `GET /v1/config/paywall` → 200
- `GET /v1/config/categories` → 200
- `GET /v1/config/entitlement-matrix` → 200
- `GET /v1/config/feature-flags` → 200
- `GET /v1/config/report-access` → 200
- `GET /v1/config/report-history-policy` → 200
- `GET /v1/config/onboarding` → 200
- `GET /api/v1/system/auth/apps/saving/me` → 200
- `GET /v1/entitlements` → 200

占位接口按设计返回 410：

- `GET /v1/dashboard/overview` → 410
- `GET /v1/records` → 410

说明：`saveMoney/mobile/ios/SaveMoneyApp/App/AppDependencies.swift` 当前 live 依赖用 `LocalDashboardRepository` / `LocalOnlyRecordRepository`，记录和看板是本机 CoreData 优先；后端 410 是首发 local-only 策略，不是 SQL 缺表。

### 健康记录抽卡 `/home/admin/code/app/fitMysteryFront`

已覆盖前端实际调用的 public/auth/business 面：

- `GET /api/v1/apps/fitmystery/release/app-version` → 200
- `GET /api/v1/fitmystery/config/bootstrap?locale=zh-Hans` → 200
- `GET /api/v1/fitmystery/config/odds` → 200
- `GET /api/v1/system/auth/apps/fitmystery/me` → 200
- `GET /api/v1/fitmystery/me/today` → 200
- `GET /api/v1/fitmystery/box/state` → 200
- `GET /api/v1/fitmystery/collection/items` → 200
- `GET /api/v1/fitmystery/reports/access` → 200
- `POST /api/v1/fitmystery/reports/generations/authorize` → 200

## 结论

使用 `src/main/resources/db/first_version_four_table` 作为唯一 Flyway 初始化来源启动 Docker 后端，三个 App 的主要联调接口已验证可正常使用。

本轮发现并修复的问题中，`fitmystery` 缺少 `sys_app` 注册属于初始化数据问题；Ops token 拦截和 PowerSync jsonb 写入属于后端代码问题。修复后验证通过。

## 备注

- 直接把 `first_version_four_table` 整目录挂到 Postgres `/docker-entrypoint-initdb.d` 仍不建议，因为 Docker entrypoint 会按文件名排序，可能先执行依赖 common 表的 app SQL。当前正确验证路径是 Spring Boot + Flyway 指定 `SPRING_FLYWAY_LOCATIONS=classpath:db/first_version_four_table`，执行目录里的 `V1__init.sql`。
- 尝试运行 Maven targeted tests 时，测试编译被已有 stale test 阻塞：`ReadingCompatServiceTest` 和 `SysBillingServiceTest` 构造器参数未跟上 `SysEntitlementCenterService` 依赖变化。后端主程序已通过 Docker `spring-boot:run` 编译并启动成功；该测试编译问题和本次 SQL 初始化验证无直接关系。
