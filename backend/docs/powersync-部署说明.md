# PowerSync 部署说明

## 目标
为 `reading` App 提供本地优先、多设备同步的 PowerSync 服务端配置。

## 目录
- `powersync/common/sync-rules.base.yaml`：公共约定
- `powersync/apps/reading/sync-rules.yaml`：reading 首批 5 张核心表同步规则
- `powersync/.env.example`：环境变量模板
- `powersync/docker-compose.yml`：本地/测试环境启动模板

## 首次启动
1. 复制环境变量模板：
   - `cp powersync/.env.example powersync/.env`
2. 将 `POWERSYNC_REPLICATION_URL` 指向 backend 使用的 PostgreSQL。
3. 将 `POWERSYNC_SERVICE_JWT_SECRET` 改成正式环境随机长密钥。
4. 在 `backend` 目录执行：
   - `cd powersync && docker compose up -d`

## 后端配置
backend 需要配置以下环境变量，供 iOS bootstrap/token 接口返回：
- `BACKEND_POWERSYNC_ENDPOINT`
- `BACKEND_POWERSYNC_TOKEN_ISSUER`
- `BACKEND_POWERSYNC_TOKEN_SECRET`
- `BACKEND_POWERSYNC_TOKEN_TTL_MINUTES`

## 健康检查
- 容器状态：`docker compose ps`
- 服务日志：`docker compose logs -f powersync`
- 若 token 可签发但客户端无法拉流，优先检查：
  1. `endpoint` 是否可公网/测试网访问
  2. JWT issuer / secret 是否与 backend 完全一致
  3. Postgres 连接串是否正确
  4. sync rules 中 `app_code + user_id` 过滤是否与 claims 对齐

## 第一版同步范围
- `reading_child_profile`
- `reading_review_card`
- `reading_review_event_v2`
- `reading_usage_session_v2`
- `reading_user_preference`

## 不同步内容
- entitlement / billing / legal docs / announcements
- 原图、音频等大文件附件
