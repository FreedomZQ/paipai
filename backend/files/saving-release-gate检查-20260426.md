# saving / 省省星球 release-gate 检查记录（2026-04-26）

## 结论

当前代码与配置的静态一致性检查结果：**8/8 PASS**。

但仍有两个必须由发布操作者在生产环境/App Store Connect 中补齐的外部 blocker：

1. Apple Team ID：`project.yml` 仍是 `__FILL_APPLE_TEAM_ID__`，`release_ios.development_team` 仍是 `__FILL_ME__`。
2. App Store Server API：`issuerId`、`keyId`、`privateKey` 在 `app-definition.yml` 中仍为空，必须通过生产环境变量或密钥管理覆盖，不能写入 App Review Notes、DB seed 或 git。

这些 blocker 是有意保留的显式占位，避免误用假值提审。

## 检查明细

| 检查项 | 结果 | 说明 |
|---|---|---|
| Bundle ID 一致 | PASS | iOS `project.yml`、后端 `app-definition.yml`、DB App Review Notes 均指向 `com.savingsplanet.app`。 |
| 商品 ID 一致 | PASS | paywall、billing entitlement mapping、权益矩阵、App Review Notes 均使用 `com.savingsplanet.app.pro.monthly`。 |
| Team ID blocker 显式存在 | PASS | `project.yml` 与 `release_ios` 配置均保持占位，归档前必须填 Apple Team ID。 |
| App Store Server API blocker 显式存在 | PASS | `issuerId` / `keyId` / `privateKey` 仍为空，需生产环境覆盖。 |
| 隐私/条款 URL 已配置 | PASS | `Release.xcconfig` 配置 `https://www.savemoney.app/privacy` 与 `https://www.savemoney.app/terms`。 |
| 隐私/条款静态文件存在 | PASS | 后端 `static/legal/privacy-policy.html` 与 `static/legal/terms-of-service.html` 存在；上线域名内容仍需人工确认。 |
| App Review Notes 存在且不含密钥 | PASS | `saving_app_review_notes.ios_submission_v1` 已补 `secretPolicy`，明确不得包含密钥/密码/私有证书。 |
| 低风险声明存在 | PASS | App Review Notes 与 `release_ios.low_risk_review_notes` 均声明 App 仅做个人记账复盘，不构成财务/投资/税务/法律建议。 |

## 对齐源文件

- iOS Bundle / Team / 版本：`/home/admin/code/app/saveMoney/mobile/ios/project.yml`
- iOS Release URL：`/home/admin/code/app/saveMoney/mobile/ios/Config/Release.xcconfig`
- 后端应用定义：`/home/admin/code/app/backend/src/main/resources/apps/saving/app-definition.yml`
- Release baseline：`/home/admin/code/app/backend/src/main/resources/db/migration/V21__saving_release_gate_config.sql`
- Paywall / 商品映射：`/home/admin/code/app/backend/src/main/resources/db/migration/V20__saving_launch_compat.sql`
- App Review Notes / 权益矩阵：`/home/admin/code/app/backend/src/main/resources/db/migration/V22__saving_config_driven_launch_content.sql`

## 提审前必须人工确认

- Apple Developer Team ID 已写入 release project 或 CI secrets。
- App Store Connect 商品 `com.savingsplanet.app.pro.monthly` 已创建、可 Sandbox 购买、价格与订阅组配置正确。
- App Store Server API Key / Issuer ID / Key ID / Private Key 已在生产环境注入，且不进入 DB、仓库、App Review Notes。
- 隐私政策与服务条款 URL 在公网域名可访问，内容与 App Store Connect 隐私问卷一致。
- Sandbox 验收覆盖 Apple 登录、购买、权益刷新、恢复购买、账号删除、CSV 导出、free/pro 报告差异。
