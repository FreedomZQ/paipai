# Unified Backend 数据库初始化与发布流程

## 1. 当前策略

当前 unified backend 已收敛为：

- 一个基线 migration：

```text
src/main/resources/db/migration/V1__baseline_current_schema.sql
```

- 一份文档型全量初始化 SQL：

```text
docs/unified-backend-postgresql-init.sql
```

二者内容应保持一致，均代表当前项目的完整 schema + 必要索引 + 字段备注 + 基础种子数据。

---

## 2. 环境策略

### 开发环境（dev）

- 保持 Flyway 自动迁移开启
- 便于本地快速启动和持续开发

### 测试环境（test）

- 默认关闭应用启动自动迁移
- 测试库应在应用启动前由初始化 SQL 或基线 migration 准备好

### 生产环境（prod）

- 默认关闭应用启动自动迁移
- 发布前先初始化或迁移数据库，再启动应用

---

## 3. 推荐执行方式

### 3.1 新建数据库后初始化

方式 A：直接执行文档 SQL

```bash
psql "$DB_NAME" -f /home/admin/code/app/backend/docs/unified-backend-postgresql-init.sql
```

方式 B：通过 Flyway 基线 migration 初始化

> 适合后续仍保留 Flyway 机制的场景。

---

## 4. 当前配置说明

### production / test

```yaml
spring.flyway.enabled: ${FLYWAY_ENABLED:false}
```

即：默认关闭自动迁移，但可通过环境变量临时打开。

### dev

继续保持自动迁移开启。

---

## 5. 后续新增表结构的建议

当前已经完成 baseline 收敛。后续如果再改 schema，不建议再次回到“很多历史文件 + docs 全量 SQL 脱节”的状态。

建议：

1. 小改动：继续新增 `V2__...`、`V3__...` 这类新的 migration
2. 在较大版本节点，再把全量 SQL 同步刷新一次
3. 始终保持：
   - `docs/unified-backend-postgresql-init.sql`
   - `src/main/resources/db/migration/` 中最新 baseline/增量链
   口径一致

---

## 6. 为什么不建议彻底删除 migration 机制

即使当前采用 baseline，也不建议完全移除 Flyway 机制本身。

原因：

- 后续 schema 继续变化时仍需要版本化控制
- 生产/测试库仍然需要可重复、可审计的演进方式
- 对个人开发者而言，保留 migration 机制比长期手工维护大 SQL 更省心

---

## 7. 当前结论

当前项目适合的模式是：

- **baseline + 文档型全量初始化 SQL 并存**
- **prod/test 默认关闭启动自动迁移**
- **dev 保留自动迁移**

这是兼顾：

- 低成本运维
- 易理解
- 易审计
- 低线上风险

的一套平衡方案。
