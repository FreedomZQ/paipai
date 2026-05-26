# 拍拍伴读「阅读书架」通用模块产品设计说明

文档日期：2026-05-26  
落地目录：`backend/files`  
适用范围：iOS / iPadOS 首发设计；后续 Android 可复用同一产品和合规口径。  
目标：新增一个通用阅读记录模块，记录书籍、阅读进度和阅读笔记；默认本地保存、不上传、不接入第三方统计；用户界面跟随 App 内选择的展示语言和文字大小。

法律提示：本文是产品、工程和上架风险控制说明，不构成法律意见。任何开发者都不能承诺“没有任何法律风险”。本方案的可执行目标是：让该模块成为真实的通用阅读记录工具，避免新增面向特定年龄的产品信号，并通过本地优先、数据最小化、无第三方 SDK、可删除来降低美国 COPPA、欧盟 GDPR 以及 Apple / Google 审核风险。

## 1. 总结结论

1. 模块对用户展示名建议为「阅读书架」或「我的书架」，不要使用「儿童」「孩子」「宝宝」「绘本」「亲子」「家长中心」等用户可见词。
2. 模块定位必须是真实通用：任何年龄用户都可以记录书籍、进度和个人笔记。不能只改文案、实际仍通过截图、插图、引导、年龄选择或营销素材面向未成年人。
3. 首发只做本地功能：不登录、不同步、不上传封面、书名、页码、笔记、设备标识，不接入第三方广告、统计、A/B、热力图、录屏回放或崩溃 SDK。
4. 拍照仅用于生成本地封面缩略图。原图不落盘；缩略图通过重新绘制 Bitmap 生成，去除 EXIF / GPS；缩略图文件和数据库记录删除时同步清理。
5. 不保存书籍正文扫描件，不做 OCR 识别，不引导用户复制整页内容。记录对象是“阅读进度”和“阅读笔记”，不是图书正文归档。
6. 所有入口、空状态、按钮、弹窗、错误提示都走本地化 key，并按 App 当前展示语言渲染；所有正文、标题、输入框、列表预览按用户在 App 中选择的文字大小和系统 Dynamic Type 适配。
7. 本模块不新增后端 API。若未来加入云同步、AI 总结、云 OCR、账号、多端恢复或统计埋点，必须先重新做 COPPA / GDPR / App Privacy / Google Data Safety 评审。
8. 现有 App 其他页面如果仍有明显未成年人定位、档案、监护人、学习报告等内容，本模块的中性文案不能单独消除整 App 的分类风险。上架前需要做全 App 文案、截图、元数据和隐私标签一致性审计。

## 2. 命名与文案策略

### 2.1 用户可见命名

| 场景 | 推荐文案 | 不建议文案 |
| --- | --- | --- |
| 模块名 | 我的书架 / 阅读书架 | 儿童书架 / 绘本书架 / 亲子书架 |
| 新增按钮 | 添加书籍 | 添加绘本 / 给孩子添加书 |
| 空状态 | 还没有记录，添加第一本书吧 | 还没有记录，拍下第一本绘本吧 |
| 详情页 | 书籍详情 | 绘本详情 |
| 笔记区 | 阅读笔记 | 孩子的想法 / 读后感 |
| 数据入口 | 数据管理 / 隐私与数据 | 家长中心 |
| 清空数据 | 清空书架数据 | 清空孩子书架数据 |

说明：

- `读后感` 本身不是禁用词，但为了通用化，首发统一用「阅读笔记」。
- 「管理中心」可用于承接导出、清空、存储占用等操作，不使用「家长中心」作为本模块入口。
- 合规文档、代码注释和内部需求可以讨论未成年人合规，但 App 内用户可见文案、App Store / Google Play 截图和推广素材应采用通用阅读工具表达。

### 2.2 禁用或高风险用户可见词

首发 UI、截图、商店描述和新模块营销文案中不得出现：

- 孩子、儿童、宝宝、幼儿、少儿、未成年人、家长、亲子、启蒙、睡前、童书、绘本。
- 任何暗示 App 专门供特定年龄段使用的表达。
- 引导上传、分享或云端保存个人阅读内容的表达，除非对应功能已经完成合规评审。

### 2.3 代码命名

建议使用中性英文命名：

- 模块：`ReadingShelf`
- 书籍实体：`BookRecord`
- 笔记实体：`ReadingNote`
- 数据管理：`ShelfDataManager`
- 图片处理：`BookCoverThumbnailProcessor`

不要在新增代码中使用 `ChildBook`、`PictureBook`、`KidsShelf`、`ParentShelf` 等命名。

## 3. 合规边界

### 3.1 美国 COPPA

最低风险落地：

- 本模块不收集儿童个人信息到开发者或第三方服务器。
- 不要求用户输入姓名、生日、年龄、学校、联系方式或家庭成员信息。
- 不创建账号，不生成服务端长期设备 ID。
- 不上传照片、书名、笔记、阅读日期、页码或缩略图。
- 不接入第三方广告、统计或可识别用户的 SDK，避免第三方通过持久标识符收集数据。

如果未来任何数据离开设备，特别是照片、音频、精确位置、设备持久标识、联系方式或可识别个人的笔记内容，就需要重新判断 COPPA operator 责任、直接通知、可验证同意、访问删除和第三方披露限制。

### 3.2 欧盟 GDPR

最低风险落地：

- 数据最小化：只保存完成阅读记录所需字段。
- 目的限制：本地数据仅用于用户在设备上查看、搜索、编辑、导出和删除。
- 默认保护：不联网、不同步、不跨设备、不训练模型、不画像。
- 可删除：单条笔记、整本书、全部书架数据都必须可在 App 内删除。
- 可导出：如提供导出，仅通过系统分享面板由用户主动导出文本文件；默认不包含封面图片。
- 透明说明：隐私说明中写清该模块数据默认只保存在当前设备，开发者无法从服务器读取、恢复或删除这些本地内容。

如果 App 实际面向未成年人或很可能被未成年人使用，应按更严格口径处理：避免默认收集任何个人数据；所有云能力、诊断、同步、分享均需单独评审并默认关闭。

### 3.3 Apple / Google 上架

本模块的上架口径：

- 真实功能是通用阅读记录，不作为 Kids Category / Families 专属功能宣传。
- App Store 隐私标签：只有在该模块没有任何数据离开设备时，才能对本模块声明“未收集”。整 App 的标签仍必须按所有功能真实填写。
- Google Play Data Safety：同理，不能只按本模块填写，必须覆盖整 App。
- 不请求 IDFA，不展示 ATT 弹窗，不做跨 App 跟踪。
- 不在该模块内放广告、外链营销、购买诱导或不必要的 WebView。
- 相机和相册权限必须是可选能力；拒绝权限后仍可手动添加无封面的书籍记录。

注意：如果整 App 的名称、图标、截图、欢迎页、功能名、已有页面或隐私文件仍明显面向未成年人，审核方仍可能按相关类别和更严格儿童政策处理。不能把中性文案作为规避真实定位的手段。

### 3.4 版权与内容风险

- 封面缩略图只保存在用户本地，用于个人书架识别，不上传、不分享、不公开展示。
- 首发不提供整页拍摄归档、OCR 识别正文、自动摘抄、AI 总结图书正文等能力。
- 阅读笔记是用户手动输入的个人记录；输入框不主动鼓励录入大段版权正文。
- 导出功能默认只导出文字字段，不导出封面图片；如未来支持图片导出，需要在导出确认中说明文件内容由用户自行管理。

## 4. 功能范围

### 4.1 首发包含

- 书架首页：网格列表、搜索、空状态、添加入口。
- 添加书籍：拍照或相册选封面、生成缩略图、手动填写基础信息。
- 书籍详情：封面、标题、阅读进度、阅读笔记列表。
- 编辑书籍：修改封面和基础信息，删除整本书。
- 阅读笔记：新增、查看、编辑、删除。
- 数据管理：统计数量、估算占用、导出文本、清空全部本地数据。
- 本地化和文字大小适配。

### 4.2 首发不包含

- 账号、云同步、跨设备恢复。
- 云端备份、云 OCR、云 AI、云端内容审核。
- 第三方广告、第三方统计、远程配置、热更新、录屏分析。
- 年龄选择、用户身份识别、个人资料绑定。
- 社区、分享广场、排行榜、推荐流。
- 扫描整本书、保存正文图片或正文文本。

## 5. 页面与交互

### 5.1 书架首页

页面标题：`我的书架`

布局：

- 顶部：标题；右上角搜索图标。
- 主体：自适应网格展示书籍卡片。
- 卡片：封面缩略图、书名、进度摘要。
- 空状态：插图或系统图标，文案为「还没有记录，添加第一本书吧」。
- 底部：悬浮按钮「添加书籍」。

交互：

- 点击卡片进入书籍详情页。
- 长按卡片弹出操作菜单：编辑、删除。
- 下拉刷新仅做本地列表重排和视觉反馈，不发起网络请求。
- 搜索支持书名和阅读笔记内容的本地模糊搜索。

排序：

- 默认按 `updated_at` 倒序，新添加或新编辑的书籍在前。
- 搜索结果保持相关性优先；同等相关性按更新时间倒序。

空状态要求：

- 不出现年龄、家庭、童书、绘本相关暗示。
- 相机权限未授权时，仍提供「手动添加」入口。

### 5.2 添加书籍

入口：

- 书架首页底部「添加书籍」按钮。
- 空状态中的「添加书籍」按钮。

步骤一：选择封面，可跳过

- 选项：拍照、从相册选择、暂不添加封面。
- 拍照调用系统相机；相册调用系统照片选择器。
- 拍摄或选图后只生成本地缩略图，原图不写入 App 沙盒。
- 缩略图建议最大边 320px，JPEG 质量 0.8；如设计坚持极低占用，可使用 200px。
- 缩略图生成必须重新绘制 Bitmap，禁止复制原图文件，确保 EXIF / GPS 元数据被剥离。
- 文件名使用 UUID，例如 `Images/{bookId}/{uuid}.jpg`。

步骤二：填写信息

| 字段 | 类型 | 必填 | 默认值 | 校验 |
| --- | --- | --- | --- | --- |
| 书名 | 文本输入 | 是 | 空 | 1-100 字符，去除首尾空格 |
| 阅读日期 | 日期选择 | 是 | 当天 | 可选择历史日期和当天，不允许未来日期 |
| 阅读天数 | 数字输入 | 否 | 1 | 1-999 |
| 当前页码 | 数字输入 | 否 | 空 | 0-99999，可为空 |
| 是否完成 | 开关 | 否 | 关闭 | 布尔值 |
| 阅读次数 | 数字输入 | 否 | 1 | 1-9999 |
| 备注 | 多行文本 | 否 | 空 | 技术上限 50000 字符，UI 不主动强调上限 |

步骤三：确认添加

- 点击「添加」后写入本地 SQLite，并返回书架首页。
- 新记录显示在书架网格首位。
- 书名为空时提示「请输入书名」。
- 本地数据库写入失败时提示「保存失败，请稍后重试」，不展示技术错误或路径。

权限失败处理：

- 相机权限被拒绝：提示「无法使用相机，可以从相册选择或手动添加」。
- 相册权限被拒绝：提示「无法访问照片，可以拍照或手动添加」。
- 不要求用户必须授权相机或相册才能使用书架。

### 5.3 书籍详情

结构：

```text
返回        书籍详情        编辑

[封面缩略图或占位图]
《书名》

基本信息
阅读日期：2026-05-20
阅读天数：3 天
当前页码：第 24 页
是否完成：未完成
阅读次数：第 2 次

阅读笔记                         添加
2026-05-22
这本书的节奏很好，适合反复阅读...

2026-05-20
今天记录了最喜欢的一段内容...
```

交互：

- 右上角「编辑」进入编辑模式。
- 点击笔记进入完整查看页。
- 笔记列表按创建时间倒序，最新在上。
- 无笔记时显示「还没有笔记，记录一点想法吧」。

展示规则：

- 日期按当前 App 展示语言对应 Locale 格式化。
- 数字和单位按语言本地化，例如中文「3 天」、英文「3 days」。
- 书名和笔记内容是用户输入内容，不自动翻译，只按用户选择的文字大小显示。

### 5.4 编辑书籍

可编辑字段：

- 书名。
- 封面缩略图：重新拍照、从相册选择、移除封面。
- 阅读日期。
- 阅读天数。
- 当前页码。
- 是否完成。
- 阅读次数。
- 备注。

保存：

- 点击「保存」后更新本地数据库并刷新详情页。
- 替换封面时，先写入新缩略图，再更新数据库；确认更新成功后删除旧缩略图。
- 保存失败时不得删除旧缩略图，避免数据损坏。

删除：

- 编辑页底部提供「删除此书籍」。
- 二次确认文案：「删除后将移除此书籍、封面和全部关联笔记，无法在 App 内恢复。」
- 确认后删除数据库记录和本地缩略图文件，返回书架首页。

### 5.5 阅读笔记

列表：

- 位于书籍详情下半部分。
- 每条展示创建日期和内容预览。
- 预览按当前文字大小计算可见行数，最多 2 行，不按固定 30 字截断；无障碍模式下允许更多行。
- 按 `created_at` 倒序。

添加：

- 入口：详情页笔记区右侧「添加」。
- 编辑页为纯文本输入，支持换行。
- 点击「保存」写入本地数据库并返回详情页。
- 点击「取消」放弃输入；如已有内容，弹出确认。

查看：

- 显示完整内容、创建日期、最后编辑日期。
- 右上角提供「编辑」。
- 删除按钮放在更多菜单或底部危险区，避免误触。

编辑：

- 输入框预填原内容。
- 保存后更新 `updated_at`。
- 取消修改时不写数据库。

删除：

- 支持详情页删除。
- 列表左滑删除可选；如果启用，必须二次确认。
- 确认文案：「删除后将移除此笔记，无法在 App 内恢复。」

## 6. 数据管理

入口建议：设置页 `隐私与数据` 或 `数据管理`，不要使用「家长中心」作为本模块入口。

展示：

- 书籍数量。
- 阅读笔记数量。
- 封面缩略图占用空间。
- 数据库存储占用估算。

操作：

- 导出书架数据：可选功能，生成本地 JSON 或 TXT，通过系统分享面板交给用户。
- 清空书架数据：删除全部书籍、封面缩略图和阅读笔记。

导出规则：

- 默认只导出文字记录，不包含封面图片。
- 导出文件不自动上传，不保存到开发者服务器。
- 分享面板由系统处理；用户选择的目标 App 不受本 App 控制，隐私说明需如实说明。

清空规则：

- 必须二次确认。
- 建议在清空前触发 Face ID / Touch ID / 设备密码验证，入口名为「设备验证」，不要使用年龄或监护人表达。
- 确认文案：「将删除本设备上的全部书架数据、封面和笔记，无法在 App 内恢复。」
- 清理完成后重新统计，确保数据库记录和缩略图文件都不存在。

删除边界：

- SQLite、文件系统和闪存存储不应承诺物理介质级不可恢复。
- 对用户可承诺的是：从 App 可访问的数据表和沙盒文件中删除，App 内不可恢复。

## 7. 本地数据设计

### 7.1 存储位置

最低风险建议：

- iOS：`Application Support/ReadingShelf/`。
- SQLite：`Application Support/ReadingShelf/bookshelf.sqlite`。
- 缩略图：`Application Support/ReadingShelf/Images/`。
- 文件保护：优先 `NSFileProtectionCompleteUntilFirstUserAuthentication`，如果不影响使用可用 `NSFileProtectionComplete`。
- 默认设置 `isExcludedFromBackupKey = true`，避免通过系统备份形成“本地-only”口径冲突。

说明：

- 需求草案中提到 `Documents`。从隐私和上架口径看，`Application Support` 更适合 App 私有数据；`Documents` 更容易被文件共享、备份和用户文件管理语义影响。
- 如果项目为了兼容既有本地库必须放在 `Documents`，也需要关闭文件共享、设置不备份标记，并在隐私说明中避免承诺“永远不会通过系统备份离开设备”。

### 7.2 SQLite 表

建议新增本地表，不走后端迁移：

```sql
CREATE TABLE IF NOT EXISTS reading_shelf_book (
    id TEXT PRIMARY KEY NOT NULL,
    app_code TEXT NOT NULL,
    title TEXT NOT NULL,
    cover_thumbnail_path TEXT,
    reading_date TEXT NOT NULL,
    reading_days INTEGER NOT NULL DEFAULT 1,
    current_page INTEGER,
    is_completed INTEGER NOT NULL DEFAULT 0,
    reading_count INTEGER NOT NULL DEFAULT 1,
    remark TEXT,
    sort_time TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS reading_shelf_note (
    id TEXT PRIMARY KEY NOT NULL,
    app_code TEXT NOT NULL,
    book_id TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT,
    FOREIGN KEY(book_id) REFERENCES reading_shelf_book(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_reading_shelf_book_updated
    ON reading_shelf_book(updated_at);

CREATE INDEX IF NOT EXISTS idx_reading_shelf_note_book_created
    ON reading_shelf_note(book_id, created_at);
```

字段规则：

- `app_code` 沿用项目多 App 隔离策略。
- `title` 必须去除首尾空格后保存。
- `cover_thumbnail_path` 只保存相对路径，不保存图片二进制。
- `reading_date` 存 `YYYY-MM-DD`。
- `created_at`、`updated_at`、`sort_time` 存 ISO8601 字符串。
- 删除整本书时使用事务：删除笔记、删除书籍记录、删除缩略图文件。

### 7.3 图片处理

处理流程：

1. 用户拍照或选择图片。
2. App 在内存中解码图片。
3. 按封面区域裁剪或等比缩放到最大边 320px。
4. 重新绘制到新的 Bitmap。
5. 以 JPEG 写入沙盒缩略图目录。
6. 不复制原始文件，不保存原图，不保留 EXIF / GPS / 相机型号 / 拍摄时间。
7. 写库成功后释放内存中的原始图片引用。

异常处理：

- 缩略图生成失败时允许无封面保存。
- 文件写入成功但数据库失败时，立即删除刚写入的缩略图。
- 数据库成功但旧文件删除失败时，记录本地低敏错误并在下次启动执行孤儿文件清理，不上传日志。

### 7.4 搜索

首发使用本地 SQLite `LIKE` 或 FTS5：

- 小数据量：`title LIKE ? OR note.content LIKE ?` 即可。
- 预期超过 1000 本书或大量笔记时，可启用 FTS5 虚拟表。
- 搜索在本地执行，不上传关键词。
- 搜索结果命中笔记时，详情页定位到对应笔记区。

## 8. 展示语言与文字大小

### 8.1 展示语言

接入现有 `UserPreference.uiLocale` 和 `AppLocaleCatalog.supportedInterfaceLocales`。当前项目已支持：

- `zh-Hans`
- `en`
- `ja`
- `ko`
- `es`

落地规则：

- 模块所有用户可见字符串都必须使用 localization key，不允许硬编码只写中文。
- 未支持语言回退到英文；英文缺失再回退到简体中文。
- 日期、数字、复数、单位按当前展示语言格式化。
- 用户输入的书名、备注和笔记保持原文，不自动翻译。
- 导出文件的字段名使用导出时的 App 展示语言；用户输入内容保持原文。

建议文案 key：

| Key | zh-Hans | en |
| --- | --- | --- |
| `shelf.title` | 我的书架 | My Shelf |
| `shelf.addBook` | 添加书籍 | Add Book |
| `shelf.empty.title` | 还没有记录 | No records yet |
| `shelf.empty.action` | 添加第一本书 | Add your first book |
| `book.detail.title` | 书籍详情 | Book Details |
| `book.field.title` | 书名 | Title |
| `book.field.readingDate` | 阅读日期 | Reading Date |
| `book.field.readingDays` | 阅读天数 | Reading Days |
| `book.field.currentPage` | 当前页码 | Current Page |
| `book.field.completed` | 是否完成 | Completed |
| `book.field.readingCount` | 阅读次数 | Reading Count |
| `note.section.title` | 阅读笔记 | Reading Notes |
| `note.empty` | 还没有笔记，记录一点想法吧 | No notes yet. Add a thought. |
| `data.title` | 数据管理 | Data Management |
| `data.clearAll` | 清空书架数据 | Clear Shelf Data |

### 8.2 文字大小

目标：用户在 App 中选择的文字大小必须影响本模块所有正文、标签、按钮和输入框，同时兼容系统 Dynamic Type。

建议策略：

- 新增或复用本地偏好：`reading_display_text_size`。
- 值：`small`、`standard`、`large`、`extraLarge`。
- 文字大小以 App 偏好作为基础倍率，再叠加系统 Dynamic Type。
- SwiftUI 使用 `.font(.body)`、`.font(.headline)`、`@ScaledMetric`、`.dynamicTypeSize(...)`，避免固定字号和固定高度。
- 书架网格在大字模式下减少列数，保证标题不互相覆盖。
- 详情页和笔记页允许滚动，不截断正文。
- 按钮最小点击区域 44x44 pt。

验收标准：

- 中文、英文、西班牙文、日文、韩文在最大文字大小下不互相遮挡。
- 书名超过 100 字无法保存；列表中长书名最多显示 2 行，详情页完整显示。
- 笔记详情在最大文字大小下可完整滚动阅读和编辑。
- 搜索框、日期选择器、删除确认弹窗在最大文字大小下按钮可见且可点击。

## 9. 隐私说明与 App Store 表述

### 9.1 隐私政策需要增加的内容

建议增加一段中性说明：

```text
阅读书架数据默认保存在当前设备上，包括用户手动输入的书名、阅读进度、阅读笔记，以及用户主动添加的封面缩略图。开发者不会把这些内容上传到自有服务器，也不会把这些内容提供给第三方广告或分析服务。用户可以在 App 内删除单条记录、删除一本书的记录，或清空全部书架数据。
```

如果保持 `isExcludedFromBackupKey = true`：

```text
为减少不必要的数据传输，阅读书架的本地数据库和封面缩略图默认不参与 App 自己的云端同步。删除 App、系统重置或更换设备后，这些本地数据可能无法恢复。
```

如果未来开启系统备份或 iCloud 同步，必须修改上述文案。

### 9.2 商店隐私标签

本模块本身只有在以下条件全部满足时，才可按“不收集”口径处理：

- 数据没有离开设备。
- 没有第三方 SDK 读取或上报这些数据。
- 没有崩溃日志、诊断日志、客服附件自动包含这些数据。
- 没有云同步、云备份、远程配置或服务端搜索。

整 App 的 App Privacy / Data Safety 仍必须按所有模块真实填写。已有账号、购买、客服、诊断、支付或后端能力不能被本模块的本地-only 口径覆盖。

### 9.3 商店元数据建议

推荐描述：

- “记录书籍、进度和个人阅读笔记。”
- “本地保存，随时编辑和删除。”
- “支持多语言界面和可调文字大小。”

避免描述：

- “专为儿童打造。”
- “亲子共读。”
- “绘本伴读。”
- “宝宝阅读成长记录。”
- “家长必备。”

## 10. 工程实施任务

### 10.1 iOS 前端

建议新增：

- `Features/ReadingShelf/ReadingShelfView.swift`
- `Features/ReadingShelf/BookFormView.swift`
- `Features/ReadingShelf/BookDetailView.swift`
- `Features/ReadingShelf/ReadingNoteEditorView.swift`
- `Features/ReadingShelf/ShelfDataManagementView.swift`
- `Core/Models/ReadingShelfModels.swift`
- `Core/Repositories/ReadingShelfRepository.swift`
- `Core/Services/BookCoverThumbnailProcessor.swift`
- `Core/Services/ReadingShelfExportService.swift`

接入：

- 首页或伴读乐园增加「我的书架」入口，入口文案中性。
- 设置页增加「数据管理」入口，复用本模块统计和清空能力。
- 本地数据库 schema 在 `SQLiteSchema.bootstrapStatements` 增加两张表和索引。
- 语言文案接入现有 `uiText` / localization 机制。
- 文字大小接入现有用户偏好；如现有偏好没有文字大小字段，首发可先跟随系统 Dynamic Type，并预留 `reading_display_text_size`。

### 10.2 后端

首发不新增后端接口、表或定时任务。

需要配合的只有文档和隐私材料：

- 更新 `backend/files/privacy-policy.html` 或对应源文件时，加入阅读书架本地数据说明。
- 如果生产包仍有云同步或设备诊断接口，确保该模块不调用。
- Release gate 增加静态检查：新模块不得调用 OCR/TTS 云接口、账号同步接口或统计接口。

### 10.3 本地清理任务

App 启动或进入书架时执行低频清理：

- 扫描 `Images/` 下没有数据库引用的缩略图，删除。
- 检查数据库记录引用但文件不存在的情况，显示占位封面，不报错。
- 统计占用空间供数据管理页展示。

## 11. 验收清单

产品验收：

- 书架首页、添加、详情、编辑、笔记、数据管理全流程可用。
- 相机和相册权限拒绝后仍可手动添加书籍。
- 删除单条笔记、整本书、全部数据都需要二次确认。
- 清空后数据库表为空，缩略图目录无引用文件。
- 空状态、按钮、弹窗均不出现高风险年龄定位词。

隐私验收：

- 使用代理或网络监控验证：浏览、添加、编辑、删除、搜索、导出过程中无自有后端或第三方请求。
- 缩略图用 `exiftool` 或等价工具验证不含 EXIF / GPS。
- 原图未落盘；沙盒中只存在缩略图。
- 数据库和图片目录不参与 App 自己的云同步。
- 不新增第三方 SDK。

语言与字号验收：

- `zh-Hans`、`en`、`ja`、`ko`、`es` 至少完成冒烟测试。
- 最大文字大小下，列表、详情、表单、弹窗无重叠、无不可点击按钮。
- 用户输入的书名和笔记不被自动翻译，不因语言切换丢失。

上架验收：

- 新模块截图和 App Store / Google Play 文案使用通用阅读工具表达。
- App 隐私标签和 Data Safety 与整 App 实际数据流一致。
- 如果整 App 仍包含未成年人定位功能，不能因为本模块中性文案而降低真实合规要求。
- 若未来新增云能力，必须重新评审 COPPA、GDPR、App Privacy、Data Safety 和供应商 DPA。

## 12. 参考来源

以下为本方案使用的官方或平台资料，检查日期为 2026-05-26：

- FTC COPPA FAQ: https://www.ftc.gov/business-guidance/resources/complying-coppa-frequently-asked-questions
- FTC 2025 COPPA Rule 更新公告: https://www.ftc.gov/news-events/news/press-releases/2025/01/ftc-finalizes-changes-childrens-privacy-rule-limiting-companies-ability-monetize-kids-data
- European Commission GDPR children safeguards: https://commission.europa.eu/law/law-topic/data-protection/rules-business-and-organisations/legal-grounds-processing-data/are-there-any-specific-safeguards-data-about-children_en
- European Commission data protection by design and by default: https://commission.europa.eu/law/law-topic/data-protection/rules-business-and-organisations/obligations/what-does-data-protection-design-and-default-mean_en
- Apple App Review Guidelines: https://developer.apple.com/app-store/review/guidelines/
- Google Play Families Policy: https://support.google.com/googleplay/android-developer/answer/9893335
- Google Play target audience and content policy: https://support.google.com/googleplay/android-developer/answer/9867159
