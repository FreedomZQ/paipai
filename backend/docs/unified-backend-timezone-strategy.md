# Unified Backend 全球时区与时间字段策略

## 1. 结论

当前统一后端采用以下时间策略：

1. 表示“绝对时间点”的字段使用 PostgreSQL `TIMESTAMPTZ`
2. 表示“用户本地日历日”的字段保留 `DATE`
3. 后端内部继续以 UTC 作为计算基准
4. 前端展示时转换为用户设备本地时区

---

## 2. 为什么使用 TIMESTAMPTZ

全球用户会处于不同地区和时区。对于以下场景，必须表示同一个绝对时刻：

- 权益过期
- session 过期
- 公告开始 / 结束
- Apple 通知接收
- OCR / TTS 调用审计
- 账号删除
- 句卡下次复习时间

因此这些字段应使用：

```sql
TIMESTAMPTZ
```

这样可以避免因为服务器时区、部署地域或夏令时导致判断漂移。

---

## 3. 为什么不是所有字段都用 TIMESTAMPTZ

例如：

```text
reading_daily_task_completion.task_date
```

这个字段表达的是“某一天”，不是“某个绝对时刻”。

因此它继续使用：

```sql
DATE
```

后续如果出现类似：

- quota_date
- report_week_start
- report_week_end
- 用户本地签到日期

也应优先考虑 `DATE`，并结合用户时区计算。

---

## 4. 本轮已同步的文件

本轮已同步：

- Flyway migrations 中所有绝对时间字段
- `docs/unified-backend-postgresql-init.sql`
- Paipai iOS 公告时间展示逻辑

前端新增：

```text
ServerTimeFormatter
```

用于将后端返回的 ISO8601 / offset timestamp 转为用户设备本地时间显示。

---

## 5. 后续建议

后续如果需要更精确的“用户本地日历日”体验，建议增加用户时区字段，例如：

```text
sys_user.preferred_timezone
```

保存 IANA 时区名，例如：

- `Asia/Shanghai`
- `America/Los_Angeles`
- `Europe/Berlin`

不要只保存 `+08:00` 这类固定偏移，因为夏令时和地区规则会变化。
