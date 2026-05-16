# 两项目后端兼容矩阵 v0.1

> ⚠️ 历史说明（2026-04-22）：本文件反映早期兼容矩阵阶段结论，不代表当前 reading 收口口径。当前 reading 已收口为 **Apple 登录唯一正式入口**；删除账号仍可使用**临时输入邮箱验证码确认**，但邮箱不再作为长期登录方式。

日期：2026-04-15

适用范围：
- `/home/admin/code/app/paipai/backend`
- `/home/admin/code/app/saveMoney/backend`
- 新统一后端：`/home/admin/code/app/backend`

原则：
- 原有两个后端**不修改、不删除、不合并**
- 统一后端在新目录独立建设
- 兼容矩阵用于后续“能力迁移 + 路由兼容 + 表结构标准化”

---

## 1. 技术底座矩阵

| 维度 | paipai | saveMoney | 统一 backend 目标 |
|---|---|---|---|
| Java | 17 | 17 | 17 |
| Spring Boot | 3.3.2 | 3.3.5 | 3.3.x |
| 持久层 | Spring Data JPA | MyBatis Plus | MyBatis Plus |
| Maven | 是 | 是 | 是 |
| PostgreSQL | 是 | 是 | 是 |
| Redis | 是 | 是 | 是 |
| Flyway | 是 | 是 | 是 |
| OpenAPI | 是 | 是 | 是 |
| profile | dev/test/prod | local/dev/prod | dev/test/prod |

### 结论

- **saveMoney 更适合作为统一技术底座**
- **paipai 更适合作为 Apple 能力迁移输入源**

---

## 2. API 路由兼容矩阵

## 2.1 公共能力

| 能力 | paipai 当前 | saveMoney 当前 | 统一目标 |
|---|---|---|---|
| 健康检查 | `/api/v1/bootstrap/healthz` | 无统一 health API（依赖 actuator） | `/api/v1/system/healthz` + 保留旧兼容层 |
| bootstrap 配置 | `/api/v1/bootstrap/config` | `/v1/config/bootstrap` | 内核统一，外部保留双路由兼容 |
| Paywall 配置 | `/api/v1/plans`、`/api/v1/billing/products` | `/v1/config/paywall` | 统一到 sys configcenter，再做兼容适配 |
| entitlement | `/api/v1/billing/entitlement`、`/api/v1/subscriptions/status` | `/v1/entitlements` | 统一到 sys entitlement + billing adapter；现已补主动 refresh 接口 |

## 2.2 认证能力

| 能力 | paipai 当前 | saveMoney 当前 | 统一目标 |
|---|---|---|---|
| 匿名/演示登录 | 已下线；不再保留 reading guest/demo auth 入口 | `POST /v1/users/bootstrap` | reading 统一改为正式 app-scoped auth；saving 仍保留 bootstrap |
| 当前用户 | `GET /api/v1/system/auth/apps/{appCode}/me` | 通过 bearer + RequestContext | 统一为 app-scoped sys auth/me |
| 登出 | `POST /api/v1/system/auth/apps/{appCode}/logout` | 暂无明确独立接口 | 统一为 app-scoped sys auth/logout |
| Sign in with Apple exchange | `POST /api/v1/system/auth/apps/{appCode}/apple/exchange` | 当前未成型 | 统一内核使用 app-scoped sys auth exchange |
| Apple session refresh | `POST /api/v1/system/auth/apps/{appCode}/apple/refresh` | 暂无 | 统一到 app-scoped sys auth refresh；不再保留 reading `/api/v1/auth/...` compat |
| Apple token revoke | `POST /api/v1/system/auth/apps/{appCode}/apple/revoke` | 暂无 | 统一到 app-scoped sys auth revoke；不再保留 reading `/api/v1/auth/...` compat |

## 2.3 Apple Billing 能力

| 能力 | paipai 当前 | saveMoney 当前 | 统一目标 |
|---|---|---|---|
| verify/intake | `/api/v1/subscriptions/app-store/purchases/intake` | `/v1/purchases/verify` | 统一到 sys billing，保留兼容控制器 |
| restore | `/api/v1/subscriptions/app-store/restores/intake` | `/v1/purchases/restore` | 统一到 sys billing |
| entitlement refresh | 暂无显式独立接口 | 暂无显式独立接口 | 统一到 `POST /api/v1/system/billing/apps/{appCode}/entitlements/refresh`，reading 兼容 `/api/v1/billing/entitlement/refresh`，saving 兼容 `/v1/entitlements/refresh` |
| entitlement observability | 暂无 | 暂无 | `GET /api/v1/system/apps/{appCode}/billing/entitlements/observability`，用于查看 effective mapping、refresh policy、refresh 执行统计与 recentRefreshes |
| notification observability | 暂无 | 暂无 | `GET /api/v1/system/appstore/apps/{appCode}/notifications/observability`，用于查看通知 verified/failed/accepted/reconciled/rejected 与最近通知摘要 |
| webhook/notification | `/api/v1/webhooks/app-store/notifications` | `/v1/appstore/notifications` | 统一到 sys appstore notifications |
| authoritative projection | 已有 | 已有基础版 | 统一用 sys subscription + entitlement snapshot |
| Apple readiness | 已有 `/api/v1/ops/apple/readiness` | `scripts/apple-readiness.sh` | 统一保留 API + script 双入口 |
| Apple ops gate | 暂无统一聚合入口 | 暂无 | `GET /api/v1/system/apps/{appCode}/apple/ops-gate` 与 `GET /api/v1/system/apple/ops-gates`，聚合 readiness / token storage / entitlement observability / notification observability |
| System ops token | 暂无 | 暂无 | `/api/v1/system/**` 除 healthz 外支持 `X-Ops-Token`，prod 未配置 token 时 fail-closed；外部 Apple webhook 兼容路由保持公开 |
| Release gate | 暂无统一总览 | 暂无 | `GET /api/v1/system/release-gate`，汇总各 app 的 Apple 上线门禁状态 |
| Public surface inventory | 暂无 | 暂无 | `GET /api/v1/system/public-surface`，列出故意公开的接口与保护方式 |

## 2.4 业务域能力

| 业务域 | paipai 当前 | saveMoney 当前 | 统一目标 |
|---|---|---|---|
| children/profile | 有 | 无 | `apps/reading` |
| review/learning | 有 | 无 | `apps/reading` |
| OCR | 有 | 无 | `apps/reading` |
| records CRUD | 无 | 有 | `apps/saving` |
| dashboard | 无 | 有 | `apps/saving` |
| weekly/monthly reports | 有 weekly | 有 weekly/monthly | 分别留在 `apps/reading`、`apps/saving` |

---

## 3. 表结构兼容矩阵

> 说明：以下是“未来目标映射”，不是立即执行迁移。当前阶段老表保留，新表按标准创建。

## 3.1 系统公共表

| 现有 paipai | 现有 saveMoney | 统一目标表 |
|---|---|---|
| `user_account` | `users` | `sys_user` |
| `auth_session` | `auth_sessions` | `sys_auth_session` |
| `identity_link` | `user_identities` | `sys_user_identity` |
| `apple_refresh_token_vault` | 暂无 | 现阶段先并入 `sys_auth_provider_token` 加密字段，后续再视需要拆独立 vault 表 |
| `app_store_transaction_intake` | `purchase_transactions` | `sys_purchase_transaction` |
| `app_store_subscription_projection` | `subscriptions` | `sys_subscription` |
| `app_store_subscription_event` | `app_store_notifications` + event traces | `sys_app_store_notification` / `sys_subscription_event` |
| `entitlement_snapshot` | `entitlement_snapshots` | `sys_entitlement_snapshot` |
| `remote_config_item` | `app_remote_config` | `sys_remote_config` |
| `analytics_event` | 暂无同级独立事件表 | `sys_analytics_event` |
| `apple_refresh_token_vault` | 暂无 | `sys_apple_refresh_token_vault` |
| `account_deletion_request` | 暂无 | `sys_account_deletion_request` |
| `integration_call_audit` | `audit_logs` | `sys_audit_log` |

## 3.2 套餐 / 权益 / 配置中心

| paipai 当前 | saveMoney 当前 | 统一目标 |
|---|---|---|
| `subscription_plan` | `plan_catalog` | `sys_plan_catalog` |
| `app_store_product` | `plan_store_mapping` | `sys_plan_store_mapping` |
| `product_price` | paywall/template + StoreKit | `sys_plan_store_price_cache`（可选） |
| entitlement 字段散落于 plan/snapshot | `entitlement_catalog` / `plan_entitlement` / `feature_limit_config` | `sys_entitlement_catalog` / `sys_plan_entitlement` / `sys_feature_limit_config`；当前先用 `sys_remote_config.billing_entitlements` 承载 productId→entitlementCode 映射 |
| 无完整模板化 paywall 表 | `paywall_template` / `paywall_template_i18n` | `sys_paywall_template` / `sys_paywall_template_i18n` |

## 3.3 App 专属表

| paipai 当前 | 统一新表名 |
|---|---|
| `child_profile` | `reading_child_profile` |
| `learning_track` | `reading_learning_track` |
| `review_card_local_ref` | `reading_review_card` |
| `review_event` | `reading_review_event` |
| `daily_learning_task` | `reading_daily_learning_task` |
| 周报快照（当前更多即时计算） | `reading_weekly_report_snapshot`（如后续需要持久化） |

| saveMoney 当前 | 统一新表名 |
|---|---|
| `expense_records` | `saving_expense_record` |
| `saving_records` | `saving_saving_record` |
| `report_snapshots` | `saving_report_snapshot` |

---

## 4. 配置与环境变量兼容矩阵

## 4.1 paipai 当前配置特点

- App 级配置大量走 `APP_*`
- Apple auth / billing 配置更完整
- 已开始做 DB override：`remote_config_item`
- profile 已有 `dev/test/prod`

### paipai 重点配置族

- `APP_AUTH_APPLE_*`
- `APP_BILLING_APPSTORE_*`
- `APP_ENDPOINT_*`
- `APP_SUPPORT_* / APP_PRIVACY_* / APP_TERMS_*`
- `APP_ANALYTICS_HASH_SALT`

## 4.2 saveMoney 当前配置特点

- DB / Redis 环境变量更简洁：`DB_*`、`REDIS_*`
- Apple billing 主要配置集中在 application.yml 内的 `app.billing.apple`
- profile 目前是 `local/dev/prod`

## 4.3 统一目标

| 配置层 | 统一目标 |
|---|---|
| 敏感配置 | 环境变量 |
| 套餐/权益/Paywall | 数据库配置 |
| App 静态定义 | `resources/apps/{appCode}/app-definition.yml`（现已承载 `app.billing.entitlements.productMappings.*` 与 `app.billing.entitlements.refreshPolicy.*`） |
| 运行时环境 | `application-dev.yml` / `application-test.yml` / `application-prod.yml` |

### 统一建议保留的环境变量命名

- `DB_URL / DB_USERNAME / DB_PASSWORD`
- `REDIS_HOST / REDIS_PORT / REDIS_PASSWORD`
- `APPLE_AUTH_*`（未来建议统一前缀）
- `APPLE_BILLING_*`（未来建议统一前缀）
- `SPRING_PROFILES_ACTIVE`

---

## 5. Redis 能力兼容矩阵

| 能力 | paipai 当前 | saveMoney 当前 | 统一目标 |
|---|---|---|---|
| entitlement cache | 有使用 Redis，但 key 体系未充分显式化 | `sp:entitlement/user/report/config` 已有 | 统一前缀建议：`apphub:` |
| verify 幂等 | 有链路能力 | `sp:idempotency:purchase_verify:*` | `apphub:idempotency:purchase_verify:{appCode}:{txId}` |
| restore 幂等 | 有链路能力 | `sp:idempotency:purchase_restore:*` | `apphub:idempotency:purchase_restore:{appCode}:{originalTxId}` |
| notification 去重 | 有 webhook 事件去重 | `sp:appstore:notification:*` | `apphub:appstore:notification:{appCode}:{notificationId}` |
| 配置缓存 | 有 DB override 方向 | `sp:config:bootstrap:*` / `sp:config:paywall:*` | `apphub:config:{appCode}:{namespace}:{locale}` |

---

## 6. Apple 能力迁移矩阵

| 能力 | 来源优先级 | 理由 |
|---|---|---|
| Sign in with Apple exchange | paipai | paipai 已有更完整 server-side exchange |
| Apple identity token 验签 | paipai | paipai 已有现成实现与测试 |
| App Store Server API reconcile | paipai + saveMoney 交叉对照 | paipai 更成熟，saveMoney 更贴近 MP 架构 |
| App Store Notifications V2 | paipai + saveMoney 交叉对照 | 两边都有实现，统一时取更稳的状态机 |
| entitlement snapshot | saveMoney 结构 + paipai 规则 | saveMoney 的表结构更适合统一，paipai 的规则更适合 reading |
| readiness / 审核门禁 | paipai | paipai 更贴近 Apple 提审实践 |

---

## 7. 当前实施顺序

1. **已完成**：隔离修复 paipai 编译失败并在沙箱中 `mvn test` 通过
2. **已完成**：新建 `/home/admin/code/app/backend` 统一后端目录
3. **已完成**：输出本兼容矩阵
4. **进行中**：搭建统一后端第一版骨架（MyBatis Plus + dev/test/prod + `sys_` 初始化）
5. **已完成（2026-04-16）**：统一 `sys_auth` 第一版（`sys_user` / `sys_auth_session` + demo session + me + logout）
6. **已完成（2026-04-16）**：统一 `sys_billing` / `sys_appstore` 第一版公共骨架（purchase intake / restore intake / entitlement query / notification ingestion）
7. **已完成（2026-04-16）**：reading / saving 旧路由兼容控制器第一版，旧接口已可落到统一内核骨架
8. **已完成（2026-04-16）**：Apple auth skeleton 已接入 Apple JWKS identity token 签名验签通路；App Store JWS 已接入 x5c 证书链 + ES256 真签名验签
9. **已完成（2026-04-16）**：Sign in with Apple `/auth/token` exchange、正式 session issuance、provider token 持久化、Apple refresh grant、Apple revoke、refresh_token 加密存储第一版、严格 gating（无加密 key 时 formal exchange / refresh 不签发 session）
10. **已完成（2026-04-16）**：entitlement projection 支持 productId→entitlementCode 可配置映射，优先级为 `sys_remote_config.billing_entitlements` > app-definition/env > productId fallback
11. **已完成（2026-04-16）**：entitlement refresh 已接 system / reading / saving 路由，并补可配置 cooldown / candidateLimit；system 已可查看 entitlement observability 与 refresh policy
12. **已完成（2026-04-16）**：新增 Apple ops gate 聚合入口，system 已可统一查看 readiness / token storage / entitlement observability / notification observability / blockers / warnings；prod allowSandbox 已纳入 blocker
13. **下一步**：继续补 App Store Server API verify / reconcile / projection / entitlement refresh 的更多策略、apps/reading 与 apps/saving 业务域迁移、历史明文 refresh token 轮转/清理策略

---

## 8. 当前结论

- 技术底座：**以 saveMoney 为基线**
- Apple 能力：**以 paipai 为主要输入源**
- 路由策略：**旧接口先兼容，新内核统一收口**；2026-04-16 已落 reading `/api/v1/...` 与 saving `/v1/...` 第一版兼容控制器
- 表结构策略：**老表保留，新表严格 `sys_ / reading_ / saving_`**
- 持久层策略：**统一后端固定使用 MyBatis Plus**
- 统一代码位置：**`/home/admin/code/app/backend`**
