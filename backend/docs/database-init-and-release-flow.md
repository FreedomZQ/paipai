# Unified Backend 数据库初始化与发布流程

## 1. 当前策略

当前 unified backend 已收敛为首发单文件基线：

- 一个首发初始化 SQL：

```text
src/main/resources/db/first_version/V1__init.sql
```

该文件代表当前项目第一个版本的完整 schema、索引、约束、字段备注和基础种子数据。

---

## 2. 环境策略

### 开发环境（dev）

- 保持 Flyway 自动执行首发基线
- 便于本地快速启动和持续开发

### 测试环境（test）

- 默认关闭应用启动自动迁移
- 测试库应在应用启动前由首发基线准备好

### 生产环境（prod）

- 默认关闭应用启动自动迁移
- 发布前先用首发基线初始化数据库，再启动应用

---

## 3. 推荐执行方式

### 3.1 新建数据库后初始化

方式 A：直接执行首发 SQL

```bash
psql "$DB_NAME" -f /home/admin/code/app/backend/src/main/resources/db/first_version/V1__init.sql
```

方式 B：通过 Flyway 执行 `classpath:db/first_version`

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

当前第一个版本不再保留历史增量迁移脚本。首发前如果继续改 schema，应直接更新 `V1__init.sql`，保持单文件基线可从空库重建完整数据库。

## 6. 当前结论

当前项目适合的模式是：

- **first_version/V1__init.sql 单文件首发基线**
- **prod/test 默认关闭启动自动迁移**
- **dev 可自动执行首发基线**

这是兼顾：

- 低成本运维
- 易理解
- 易审计
- 低线上风险

的一套平衡方案。
