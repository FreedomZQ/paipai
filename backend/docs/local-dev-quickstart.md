# Local Dev Quickstart

最小本地联调启动方式，默认同时拉起：

- PostgreSQL（容器，端口 `15432`）
- Redis（容器，端口 `16379`）
- backend（本机 Spring Boot，端口 `18082`）

这样可以避开本机常见的 `5432 / 6379 / 8080` 占用冲突。

## 一条命令启动

前台运行 backend：

```bash
cd /home/admin/code/app/backend
./scripts/local-dev-up.sh
```

后台运行 backend：

```bash
cd /home/admin/code/app/backend
./scripts/local-dev-up.sh --backend-background
```

只拉起 PostgreSQL / Redis：

```bash
cd /home/admin/code/app/backend
./scripts/local-dev-up.sh --deps-only
```

## 默认连接信息

- Backend: `http://127.0.0.1:18082`
- PostgreSQL: `127.0.0.1:15432`
- Redis: `127.0.0.1:16379`
- DB name: `apphub_dev`
- DB user: `postgres`
- DB password: `postgres`
- Ops token: `dev-local-token`

dev profile 下 Flyway 默认开启，因此空数据库会在 backend 启动时自动跑 migration。

`local-dev-up.sh` 还会自动通过 JVM 参数注入 `spring.flyway.placeholders.API_KEY=\${API_KEY}`，避免 baseline migration 里的云厂商 header 模板在冷启动时被 Flyway 误判为缺失占位符。

## 健康检查

```bash
curl -s http://127.0.0.1:18082/api/v1/system/healthz
redis-cli -p 16379 ping
PGPASSWORD=postgres psql -h 127.0.0.1 -p 15432 -U postgres -d apphub_dev -c 'select now();'
```

## release-gate / auth route guard

```bash
cd /home/admin/code/app/backend
./scripts/check-no-auth-compat-routes.sh
BACKEND_BASE_URL=http://127.0.0.1:18082 BACKEND_OPS_TOKEN=dev-local-token ./scripts/release-gate.sh
```

## 状态 / 重置 / 停止

查看当前本地联调状态：

```bash
cd /home/admin/code/app/backend
./scripts/local-dev-status.sh
```

一键 tail backend 日志：

```bash
cd /home/admin/code/app/backend
./scripts/local-dev-tail.sh
```

一次做健康检查 / release-gate / route-guard / db / redis 体检：

```bash
cd /home/admin/code/app/backend
./scripts/local-dev-doctor.sh
```

如果希望 `release-gate=blocked/warning` 也直接算失败：

```bash
cd /home/admin/code/app/backend
./scripts/local-dev-doctor.sh --strict-gate
```

冷启动重置（清空本地 PostgreSQL / Redis volume 后重建，默认后台起 backend）：

```bash
cd /home/admin/code/app/backend
./scripts/local-dev-reset.sh
```

只重建依赖：

```bash
cd /home/admin/code/app/backend
./scripts/local-dev-reset.sh --deps-only
```

前台重建并启动 backend：

```bash
cd /home/admin/code/app/backend
./scripts/local-dev-reset.sh --foreground
```

停止：

```bash
cd /home/admin/code/app/backend
./scripts/local-dev-down.sh
```

## 常用覆盖参数

如果你想改端口或数据库名，可在启动前覆盖：

```bash
SERVER_PORT=19082 \
LOCAL_DB_PORT=25432 \
LOCAL_REDIS_PORT=26379 \
LOCAL_DB_NAME=apphub_dev_alt \
./scripts/local-dev-up.sh
```

如果系统里已经有 `mvn` / `java`，脚本会优先使用系统版本；否则会回退到 OpenClaw 当前环境里可见的 `/tmp/openclaw-tools` 工具链。
