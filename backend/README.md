# backend

统一 Apple Store 多应用后端目录。

## 目录说明

- `src/`：新的统一后端代码
- `docs/`：兼容矩阵、迁移计划、诊断文档
- `patches/`：对原项目的建议补丁（不直接修改原仓库）
- `sandboxes/`：隔离验证环境，用于复现/验证原仓库问题，不影响原目录

## 当前原则

1. 不修改、不删除、不合并原有：
   - `/home/admin/code/app/paipai/backend`
   - `/home/admin/code/app/saveMoney/backend`
2. 新统一后端在本目录独立推进
3. 新建表从现在开始严格遵循：
   - `sys_`：通用系统表
   - `reading_`：拍拍伴读业务表
   - `saving_`：省钱业务表
4. 统一技术栈：Java 17 + Spring Boot 3 + MyBatis Plus + Maven + PostgreSQL + Redis + YAML + dev/test/prod
5. 统一后端持久层必须使用 **MyBatis Plus**；paipai 的 Spring Data JPA 代码仅作为 Apple 业务能力参考，不作为统一持久层方案。

## 当前已完成

- paipai 编译失败已在沙箱中完成隔离修复并验证 `mvn test` 通过
- 两项目兼容矩阵已输出到 `docs/compatibility-matrix-v0.1.md`
- 统一后端第一版骨架已创建
- `sys_auth` 第一版已落地并测试通过：
  - `sys_user`
  - `sys_auth_session`
  - Apple formal session 签发与刷新
  - `/api/v1/system/auth/me`
  - `/api/v1/system/auth/logout`
- `sys_billing` / `sys_appstore` 第一版公共骨架已落地并测试通过：
  - `sys_purchase_transaction`
  - `sys_entitlement_snapshot`
  - `sys_app_store_notification`
  - `/api/v1/system/billing/apps/{appCode}/entitlements`
  - `/api/v1/system/billing/apps/{appCode}/entitlements/refresh`
  - `/api/v1/system/billing/apps/{appCode}/purchases/verify`
  - `/api/v1/system/billing/apps/{appCode}/purchases/restore`
  - `/api/v1/system/appstore/apps/{appCode}/notifications`
- Apple auth lifecycle 第一版已落地并测试通过：
  - system 路由：
    - `/api/v1/system/auth/apps/{appCode}/apple/exchange`
    - `/api/v1/system/auth/apps/{appCode}/apple/refresh`
    - `/api/v1/system/auth/apps/{appCode}/apple/revoke`
    - `/api/v1/system/auth/apps/{appCode}/me`
    - `/api/v1/system/auth/apps/{appCode}/logout`
  - 已移除 reading 的旧 `/api/v1/auth/...` auth compat public surface；新 App 只能走 appCode-scoped system auth 路由
  - 当前已完成：identityToken 基础解码、clientId/audience 校验、issuer/exp/nonce 校验与 Apple JWKS 签名验签通路；Apple `/auth/token` authorization-code exchange；远端 id_token 再验签；Apple identity persistence；正式 session issuance；provider token 持久化；refresh_token grant；upstream revoke
  - 当前安全策略：refresh_token 已迁到应用层加密存储第一版；缺少 `APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY` 时，formal exchange / refresh 不再签发正式 session，但 revoke 仍允许用于清理
- App Store JWS 初步解码已接入 `sys_billing` / `sys_appstore`：
  - purchase verify/restore 会先解析 `signedTransactionInfo` payload 并生成 verification status
  - notification ingestion 会先解析 `signedPayload` payload 并尽量提取 notificationUUID/type/subtype
  - 当前已接入 App Store x5c 证书链 + ES256 真签名验签
  - App Store Server API lookup / reconcile client 已接入：支持 transaction lookup 与 subscription status lookup；配置齐全时可把 transaction 从 `pending_server_api_reconciliation` 推进到 `verified`
  - verified lookup 会投影到 `sys_entitlement_snapshot`，统一 entitlement 接口开始具备真实数据来源
  - verified App Store notification 会触发 authoritative lookup，并通过 originalTransactionId / appAccountToken 解析 user，进而落 purchase transaction 与 entitlement snapshot
- Entitlement / product mapping 可配置化已落地并测试通过：
  - 投影优先级：`sys_remote_config` namespace `billing_entitlements` > app-definition / env > `productId` fallback
  - DB runtime 配置格式：
    - namespace_code: `billing_entitlements`
    - config_key: `productMappings.{productId}` 或直接 `{productId}`
    - config_value_json: `{"value":"family_access"}`
  - app-definition / env 配置格式：`app.billing.entitlements.productMappings.{productId}=entitlementCode`
  - 这样同一个 entitlement 可以映射多个 App Store productId，后续多 app / 多 SKU 不再把 productId 当业务权益硬编码
  - system 观测接口：`/api/v1/system/apps/{appCode}/billing/entitlements/observability`，可查看当前生效 mapping、refresh policy、`entitlement_refresh` 执行统计，以及最近 refresh 摘要（recentRefreshes）
  - Apple 聚合门禁接口：`/api/v1/system/apps/{appCode}/apple/ops-gate`，统一汇总 readiness / token-storage / entitlement observability / notification observability，并给出 `ready|warning|blocked` 总状态与 ops-gate checks 明细
  - 全 app 聚合门禁接口：`/api/v1/system/apple/ops-gates`，用于 CI/CD 或上线脚本一次性检查所有 supported apps
  - 上线总览接口：`/api/v1/system/release-gate`，汇总 appCount / blockedAppCount / warningAppCount，并返回每个 app 的 gate 摘要
  - 公网暴露面清单：`/api/v1/system/public-surface`，列出故意保持公开的接口及其保护方式
  - ops-gate 已包含 production 规则：prod 环境 `allowSandbox=true` 会进入 blocker；notification 未观测/未 reconcile/存在 failed 或 rejected 会进入 warning
  - entitlement refresh 策略已配置化：`billing_refresh_policy.candidateLimit`、`billing_refresh_policy.cooldownMinutes`；app-definition/env 可用 `app.billing.entitlements.refreshPolicy.candidateLimit` / `cooldownMinutes`，环境覆盖支持 `backend.apps.{appCode}.app.billing.entitlements.refreshPolicy.*`；默认分别为 20 / 5 分钟
  - entitlement refresh 当前带有 cooldown：同一 user + originalTransactionId 最近刚 refresh 过时，会返回 `skipped_recent_refresh`，避免重复打 Apple Server API
  - 默认 seed 已包含 reading/saving 的 `billing_entitlements` 与 `billing_refresh_policy` 样板，便于新环境起库后直接观测和调整
- reading / saving 兼容路由第一版已落地并测试通过：
  - reading：保留 billing / webhook 等业务兼容入口；auth 统一收敛到 `/api/v1/system/auth/apps/{appCode}/...`（不再暴露旧 `/api/v1/auth/...`）
  - saving: `/v1/users/bootstrap`、`/v1/purchases/verify`、`/v1/purchases/restore`、`/v1/entitlements`、`/v1/entitlements/refresh`、`/v1/appstore/notifications`
- System / ops 接口安全收口已落地并测试通过：
  - `/api/v1/system/healthz` 保持公开，用于负载均衡与健康检查
  - 其他 `/api/v1/system/**` 在配置 `BACKEND_OPS_TOKEN` / `backend.ops.token` 后必须带 `X-Ops-Token`
  - dev / 未配置 token 时兼容放行；prod / 未配置 token 时 fail-closed，返回 `503 Ops token is not configured`
  - 错误 token 返回 `401 Ops token required`
  - prod profile 已将 actuator 暴露面收窄为 `health`，避免 `info/metrics` 在公网默认暴露
  - 故意保持公开的入口已归档到 `GET /api/v1/system/public-surface`：包括 system healthz、actuator health、appCode-scoped Apple exchange、saving bootstrap，以及 reading/saving 的 App Store webhook 兼容路由；system demo session 默认关闭，仅在 app 显式 `app.auth.demoSessionEnabled=true` 时进入公开面清单
- 已提供上线脚本与清单：`scripts/release-gate.sh` + `scripts/check-no-auth-compat-routes.sh` + `docs/release-checklist.md`，可直接判断能否发版。
- prod 启动守卫已落地：`SystemProductionConfigurationGuard` 会在 prod 启动时对 ops token / actuator / swagger / Apple readiness / public auth policy 做 fail-fast 校验。
  - 公开 auth/bootstrap 策略已收口：reading 仅保留 Apple exchange 作为正式登录入口且 `demoSessionEnabled=false`；saving bootstrap 默认允许但可显式关闭；system demo session 默认关闭，Apple-only app 开启会进入 ops-gate blocker；对应状态会进入 ops-gate / release-gate
  - 公开 auth 策略支持环境覆盖：`backend.apps.saving.app.auth.bootstrapSessionEnabled`
- 当前验证状态：2026-04-22 19:51（Asia/Shanghai）已完成多轮定向回归，且 `cd /home/admin/code/app/backend && mvn -q test` 与 `cd /home/admin/code/app/backend && mvn -q clean verify` 均已通过（exit code 0）。

## 当前仍未完成 / 上线前仍需人工完成

- 生产环境真实密钥与环境变量下发：Apple Sign in、App Store Server API、`BACKEND_OPS_TOKEN`、`APP_AUTH_APPLE_CREDENTIAL_ENCRYPTION_KEY`
- 真实 Apple sandbox / production 联调：identity token exchange、App Store Server API lookup、notification webhook、entitlement projection、refresh/revoke
- 历史明文 refresh token 清理：确认 `/api/v1/system/apps/{appCode}/apple/token-storage` 中 `plaintextRefreshTokenFallbackCount=0`
- 生产发布前执行 `scripts/release-gate.sh`，确认 `/api/v1/system/release-gate` 无 blocker；warning 是否允许需人工审批
- `apps/reading` / `apps/saving` 除 auth / billing / appstore 之外的业务域 service / mapper / table 迁移
- CI/CD 接入：将 `scripts/release-gate.sh` 纳入发布流水线，并安全注入 `BACKEND_OPS_TOKEN`
