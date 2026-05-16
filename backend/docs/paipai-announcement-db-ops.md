# Paipai Reading 公告通知 DB 运维说明

## 1. 目的

本说明用于指导通过 unified backend 的数据库直接管理 Paipai 公告，而无需重新发版 App。

适用目标：

- 在指定时间窗内，用户一登录即弹出公告
- 用户点击“不再展示”后，同一 `announcement_uuid` 不再自动弹出
- 公告内容本地缓存近 30 天，供用户在设置页查看历史公告
- 当权益变化、服务升级、规则调整时，尽量通过后台公告通知，而不是再次走发版审核

---

## 2. 数据表

表名：

```text
reading_announcement
```

由 migration 创建：

```text
src/main/resources/db/migration/V1__baseline_current_schema.sql
```

### 字段说明

| 字段 | 说明 |
| --- | --- |
| `announcement_uuid` | 公告唯一标识，前端用它判断是否“不再展示” |
| `title` | 公告标题 |
| `content` | 公告正文，支持较长文本 |
| `status` | 公告状态，当前仅使用 `published` |
| `visible_start_at` | 开始展示时间 |
| `visible_end_at` | 结束展示时间，可为空 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

## 3. 前端当前行为

前端每次启动并建立 reading 会话后，会请求：

```text
GET /api/v1/announcements?windowDays=30
```

后端返回：

- 近 30 天仍需保留到本地缓存的公告
- 当前是否 `active`

前端行为：

1. 合并本地公告缓存
2. 只保留近 30 天数据
3. 找到“当前时间窗内生效、且没有被用户点过不再展示”的最新公告
4. 弹出滚动公告页
5. 用户可点：
   - `知道了`：本次关闭，下次启动若仍 active 仍可再次弹出
   - `不再展示这条公告`：以后同一 `announcement_uuid` 不再自动弹出

---

## 4. 发布公告的最小 SQL 模板

### 4.1 发布一条立即生效、7 天后失效的公告

```sql
INSERT INTO reading_announcement (
    app_code,
    announcement_uuid,
    title,
    content,
    status,
    visible_start_at,
    visible_end_at
) VALUES (
    'reading',
    '2026-04-membership-upgrade-001',
    '家庭多孩子权益已升级',
    '从今天起，家庭多孩子权益已支持新的陪读回顾与更灵活的云端能力。欢迎前往家长区查看详情。',
    'published',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP + INTERVAL '7 days'
);
```

---

### 4.2 发布一条长期有效公告（无结束时间）

```sql
INSERT INTO reading_announcement (
    app_code,
    announcement_uuid,
    title,
    content,
    status,
    visible_start_at,
    visible_end_at
) VALUES (
    'reading',
    '2026-04-permanent-support-update-001',
    '支持方式更新',
    '如果你遇到账号、购买、识图或朗读问题，现在可以优先通过家长区中的“隐私与支持”页联系支持团队。',
    'published',
    CURRENT_TIMESTAMP,
    NULL
);
```

---

### 4.3 预约一条未来公告

```sql
INSERT INTO reading_announcement (
    app_code,
    announcement_uuid,
    title,
    content,
    status,
    visible_start_at,
    visible_end_at
) VALUES (
    'reading',
    '2026-05-feature-release-001',
    '新能力将于下周开放',
    '下周起将逐步开放新的陪读增强能力，请留意家长区和权益说明。',
    'published',
    TIMESTAMP '2026-05-01 09:00:00',
    TIMESTAMP '2026-05-15 23:59:59'
);
```

---

## 5. 修改公告内容

若需要修改已存在公告内容：

```sql
UPDATE reading_announcement
SET title = '家庭多孩子权益已升级（更新）',
    content = '更新后的公告内容……',
    updated_at = CURRENT_TIMESTAMP
WHERE announcement_uuid = '2026-04-membership-upgrade-001';
```

> 建议：如果业务意义已经变化很大，优先使用新的 `announcement_uuid`，避免前端“不再展示”逻辑把新内容也一起屏蔽掉。

---

## 6. 提前结束公告

```sql
UPDATE reading_announcement
SET visible_end_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE announcement_uuid = '2026-04-membership-upgrade-001';
```

或直接下线：

```sql
UPDATE reading_announcement
SET status = 'archived',
    updated_at = CURRENT_TIMESTAMP
WHERE announcement_uuid = '2026-04-membership-upgrade-001';
```

---

## 7. `announcement_uuid` 使用规则

### 强烈建议
`announcement_uuid` 应当稳定且具有业务含义，例如：

```text
2026-04-membership-upgrade-001
2026-05-announcement-cloud-tts-001
2026-05-privacy-update-001
```

### 不建议
- 每次小改文案都复用同一个 uuid
- 用纯随机值且不保留业务含义

### 原因
前端“不再展示”是按 `announcement_uuid` 比较的：

- 同一个 uuid = 视作同一条公告
- 新 uuid = 视作新公告，会再次弹出

---

## 8. 公告内容编写建议

为了符合个人开发者低风险和 Apple 审核要求，公告建议：

### 适合发的内容
- 权益增加说明
- 新功能开放说明
- 支持方式更新
- 服务维护通知
- 隐私政策 / 条款更新提醒

### 不建议直接发的内容
- 诱导性过强的购买承诺
- 未在 App 内真实可用的功能宣传
- 与 Apple 审核说明冲突的付费描述
- 涉及高风险教育承诺、效果承诺、诊断式结论的内容

---

## 9. 当前接口行为说明

当前接口：

```text
GET /api/v1/announcements?windowDays=30
```

行为：

- 需要 reading 会话
- 返回近 30 天公告
- 后端根据当前时间计算 `active`
- 前端自行决定是否弹出以及是否放进本地历史页

---

## 10. 当前限制

### 10.1 不记录服务端“用户已读”状态

当前实现：

- “不再展示”只存在客户端本地
- 不同步到后端

优点：
- 简单
- 低成本
- 无需额外用户级公告已读表

缺点：
- 用户换设备后，可能再次看到同一公告

这属于当前刻意接受的低成本方案。

---

### 10.2 公告不支持富文本模板系统

当前只是：

- 标题
- 长文本正文
- 时间窗

优点：
- 简单、稳、审核风险低

缺点：
- 不支持复杂卡片样式

---

## 11. 当前结论

公告功能已经具备：

- 后端下发
- 时间窗控制
- 前端滚动弹窗
- 不再展示
- 近 30 天历史缓存
- 无需重新发版即可通知用户

对个人开发者而言，这是一套：

- 低成本
- 低维护复杂度
- 对审核相对友好
- 能支撑权益变更通知

的合理实现。
