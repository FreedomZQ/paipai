# Release Checklist

上线前请至少完成以下检查。

## 1. 必填生产配置

### System / Ops
- `BACKEND_ENV=prod`
- `BACKEND_OPS_TOKEN` 已配置
- prod profile 下 actuator 只暴露 `health`
- prod profile 下 Swagger / OpenAPI 已关闭

### Apple Auth
- `app.auth.apple.clientId`
- `app.auth.apple.teamId`
- `app.auth.apple.keyId`
- `app.auth.apple.privateKey`
- `app.auth.apple.redirectUri`
- `app.auth.apple.tokenEndpoint`
- `app.auth.apple.revokeEndpoint`
- `APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY`

### App Store Billing
- `app.billing.appstore.bundleId`
- `app.billing.appstore.environment`
- `app.billing.appstore.issuerId`
- `app.billing.appstore.keyId`
- `app.billing.appstore.privateKey`
- 生产环境确认 `app.billing.appstore.allowSandbox=false`

## 1.5 公开入口策略（需与 `/api/v1/system/public-surface` 对齐）

以下入口预期保持公网可达，并应同时出现在 release-gate 的 `publicSurface` check 与 `/api/v1/system/public-surface` 返回值中：

| Method | Path | 保护方式 |
| --- | --- | --- |
| GET | `/api/v1/system/healthz` | 最小化健康信息；prod 不返回 app definition 细节 |
| GET | `/actuator/health` | prod profile 只暴露 health，且 `show-details=never` |
| POST | `/api/v1/system/auth/apps/{appCode}/apple/exchange` | Apple identity token 验签 / state / nonce 校验后签发 session；reading 不再暴露旧 `/api/v1/auth/...` auth 入口 |
| POST | `/api/v1/system/auth/apps/{appCode}/sessions/demo` | 仅当某个 app 显式设置 `app.auth.demoSessionEnabled=true` 时才进入公开面清单；默认关闭，Apple-only app 开启会被 ops-gate 阻断 |
| POST | `/api/v1/system/appstore/apps/{appCode}/notifications` | Apple JWS 真验签 + notification UUID 去重 + authoritative reconcile |
| POST | `/api/v1/webhooks/app-store/notifications` | Apple JWS 真验签 + notification UUID 去重 + authoritative reconcile |

## 2. 直接查 gate

单 app：

```bash
curl -H "X-Ops-Token: $BACKEND_OPS_TOKEN" \
  "$BACKEND_BASE_URL/api/v1/system/apps/paipai_readingcompanion/apple/ops-gate"
```

全局 gate：

```bash
curl -H "X-Ops-Token: $BACKEND_OPS_TOKEN" \
  "$BACKEND_BASE_URL/api/v1/system/release-gate"
```

public surface 清单：

```bash
curl -H "X-Ops-Token: $BACKEND_OPS_TOKEN" \
  "$BACKEND_BASE_URL/api/v1/system/public-surface"
```

## 3. 一条命令判断是否可发版

严格模式（warning 也失败）：

```bash
BACKEND_BASE_URL="https://backend.example.com" \
BACKEND_OPS_TOKEN="your-ops-token" \
./scripts/release-gate.sh

./scripts/check-no-auth-compat-routes.sh
```

> 注意：`scripts/release-gate.sh` 默认请求 `http://127.0.0.1:8080`。仅当本机 8080 确实是 backend 时可直接使用默认值；在共享开发机 / 多服务主机上应始终显式设置 `BACKEND_BASE_URL`。

允许 warning 通过：

```bash
BACKEND_BASE_URL="https://backend.example.com" \
BACKEND_OPS_TOKEN="your-ops-token" \
ALLOW_WARNINGS=true \
./scripts/release-gate.sh
```

退出码：
- `0`：可发版（ready，或 warning 且 `ALLOW_WARNINGS=true`）
- `1`：blocked，不可发版
- `2`：warning，默认视为不可发版
- `3/4`：请求失败或返回异常

## 4. 外部公开入口确认

以下入口应与 `GET /api/v1/system/public-surface` 完全一致，且不受 `X-Ops-Token` 保护：
- `GET /api/v1/system/healthz`
- `GET /actuator/health`
- `POST /api/v1/system/auth/apps/{appCode}/apple/exchange`
- `POST /api/v1/system/auth/apps/{appCode}/sessions/demo`（仅在 app 显式启用 `app.auth.demoSessionEnabled=true` 时出现；Paipai reading 必须保持关闭）
- `POST /api/v1/system/appstore/apps/{appCode}/notifications`
- `POST /api/v1/webhooks/app-store/notifications`
- 不应再出现任何不带 appCode 的旧 auth Apple/me/logout 路由

它们的安全边界分别是：
- healthz / actuator health：最小化健康信息，prod 隐藏细节
- Paipai Apple exchange：保持为唯一正式登录入口，并继续由 ops-gate / release-gate 观测其 readiness / token-storage / public surface
- Apple exchange：Apple identity token 真校验 + nonce/state 校验 + session 签发规则
- system demo session：默认关闭；必须由 app definition 显式 opt-in；Apple-only app 启用会进入 ops-gate blocker
- auth compat static guard：执行 `./scripts/check-no-auth-compat-routes.sh`，确保 iOS / backend runtime / runbook 不会重新引入旧 `/api/v1/auth/...` auth 路由
- Apple webhook：Apple JWS 真验签 + `notification_uuid` 去重 + authoritative reconcile
