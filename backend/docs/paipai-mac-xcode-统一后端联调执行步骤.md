# Paipai 在 Mac 上使用统一后端进行前后端联调的执行步骤

## 1. 结论先说清楚

当前拍拍前端 **不能使用 HBuilderX 正常运行**。

原因：

- 拍拍前端是 **原生 iOS SwiftUI 项目**
- 工程结构依赖：
  - `Xcode`
  - `XcodeGen`
- 项目目录为：

```text
/home/admin/code/app/paipai/ios
```

其中包含：

```text
ios/project.yml
ios/PaipaiReadAlong/*.swift
```

因此，正确的运行方式是：

```text
Xcode + XcodeGen + iOS Simulator / 真机
```

HBuilderX 最多只能作为普通文本编辑器打开 Swift 文件，**不能替代 Xcode 编译和运行 iOS App**。

---

## 2. 目标

本执行步骤用于在一台 Mac 上完成：

1. 启动 unified backend
2. 配置并运行 Paipai iOS 前端
3. 让前端连接统一后端的 reading 兼容接口
4. 验证当前已经落地的：
   - 账号与 Apple 正式会话
   - 公告通知
   - 孩子档案
   - 句卡 / 复习 / 周报
   - 设备自带 OCR / TTS
   - 阿里百炼云端 OCR / TTS
   - 订阅与删除账号

---

## 3. Mac 环境准备

## 3.1 安装 Xcode

从 App Store 安装 Xcode。

安装后执行：

```bash
sudo xcodebuild -license accept
xcodebuild -version
```

预期输出类似：

```text
Xcode 15.x
Build version ...
```

---

## 3.2 安装 Homebrew

如果没有 Homebrew：

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

检查：

```bash
brew --version
```

---

## 3.3 安装开发工具

```bash
brew install openjdk@17 maven postgresql@16 redis xcodegen
```

配置 Java：

```bash
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
source ~/.zshrc
```

检查：

```bash
java -version
mvn -version
xcodegen --version
```

---

## 4. 启动统一后端

以下假设你的 Mac 上代码路径为：

```text
~/code/app/backend
~/code/app/paipai
```

如果路径不同，请替换命令中的目录。

---

## 4.1 启动 PostgreSQL 与 Redis

```bash
brew services start postgresql@16
brew services start redis
```

检查：

```bash
pg_isready
redis-cli ping
```

Redis 应返回：

```text
PONG
```

---

## 4.2 创建数据库

```bash
createdb apphub_dev || true
```

---

## 4.3 配置 unified backend 环境变量

进入目录：

```bash
cd ~/code/app/backend
```

执行：

```bash
export SPRING_PROFILES_ACTIVE=dev
export DB_URL=jdbc:postgresql://127.0.0.1:5432/apphub_dev
export DB_USERNAME=$(whoami)
export DB_PASSWORD=
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
export BACKEND_ENV=dev
export BACKEND_OPS_TOKEN=dev-local-token
```

如果 PostgreSQL 用户不是当前 macOS 用户，请改成真实值：

```bash
export DB_USERNAME=你的数据库用户名
export DB_PASSWORD=你的数据库密码
```

---

## 4.4 配置阿里百炼 API Key（如需验证云端 OCR / TTS）

默认新加坡：

```bash
export DASHSCOPE_API_KEY=你的新加坡百炼APIKey
```

如果你后续想测美国 / 北京，可预留：

```bash
export DASHSCOPE_API_KEY_US=你的美国百炼APIKey
export DASHSCOPE_API_KEY_CN=你的北京百炼APIKey
```

---

## 4.5 启动 unified backend

```bash
mvn spring-boot:run
```

默认地址：

```text
http://127.0.0.1:8080
```

如果你要用真机 iPhone 直接访问 Mac 上的后端，建议这样启动：

```bash
SERVER_ADDRESS=0.0.0.0 mvn spring-boot:run
```

---

## 4.6 验证 unified backend 是否启动成功

另开一个终端：

```bash
curl -s http://127.0.0.1:8080/api/v1/system/healthz
```

再验证拍拍相关接口：

```bash
curl -s http://127.0.0.1:8080/api/v1/bootstrap/config
curl -s http://127.0.0.1:8080/api/v1/plans
curl -s http://127.0.0.1:8080/api/v1/legal/docs
```

测试 Apple 正式会话：

```text
在 iOS App 内通过 Sign in with Apple 走 `/api/v1/system/auth/apps/{appCode}/apple/exchange`。
```

验证点：

```text
- Apple 登录成功后能拿到正式 accessToken
- `/api/v1/system/auth/apps/{appCode}/me` 能返回当前正式账号上下文
- 未登录时不能继续把 children / review / OCR 当成正式能力使用
```

说明 unified backend reading 会话链路已经可用。

---

## 5. 配置 Paipai iOS 前端

进入 iOS 工程目录：

```bash
cd ~/code/app/paipai/ios
```

---

## 5.1 修改 `project.yml`

打开：

```bash
open project.yml
```

重点确认这些字段。

### 本地 Simulator 联调用

如果 Simulator 和 unified backend 都在同一台 Mac 上：

```yaml
DEVELOPMENT_TEAM: YOUR_TEAM_ID
MARKETING_VERSION: '1.0.0'
CURRENT_PROJECT_VERSION: '1'
INFOPLIST_KEY_PAIPAI_API_BASE_URL: http://127.0.0.1:8080
```

把 `YOUR_TEAM_ID` 换成你的 Apple Team ID。

---

### 真机联调用

如果要在 iPhone 真机上调试，**不能使用 `127.0.0.1`**。

先查 Mac 局域网 IP：

```bash
ipconfig getifaddr en0
```

假设得到：

```text
192.168.1.23
```

那么改成：

```yaml
INFOPLIST_KEY_PAIPAI_API_BASE_URL: http://192.168.1.23:8080
```

同时 unified backend 需要用：

```bash
SERVER_ADDRESS=0.0.0.0 mvn spring-boot:run
```

> 更稳的做法仍然是直接使用 HTTPS staging / prod-like 域名，而不是局域网 HTTP。

---

## 6. 生成 Xcode 工程

在 `ios/` 目录执行：

```bash
xcodegen generate
```

成功后会生成：

```text
PaipaiReadAlong.xcodeproj
```

然后打开：

```bash
open PaipaiReadAlong.xcodeproj
```

---

## 7. 在 Xcode 中运行前端

## 7.1 选择 Scheme

选择：

```text
PaipaiReadAlong
```

## 7.2 选择设备

推荐先用：

```text
iPhone 15 / iPhone 16 Simulator
```

## 7.3 Build

```text
Cmd + B
```

## 7.4 Run

```text
Cmd + R
```

---

## 8. 联调验证清单

## 8.1 启动配置与会话

验证：

- `GET /api/v1/bootstrap/config`
- `GET /api/v1/plans`
- `GET /api/v1/legal/docs`
- `POST /api/v1/system/auth/apps/{appCode}/apple/exchange`
- `GET /api/v1/system/auth/apps/{appCode}/me`

预期：

- App 正常启动
- 能通过 Apple 登录建立正式后端会话
- 不应停留在 guest / demo 语义而拿不到后端状态

---

## 8.2 公告通知

接口：

```text
GET /api/v1/announcements?windowDays=30
```

可以先手动插入一条测试公告。

进入数据库：

```bash
psql apphub_dev
```

执行：

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
    'local-test-announcement-001',
    '本地联调公告',
    '这是一条用于验证启动弹窗、长文本滚动、不再展示和历史公告缓存的测试公告。你可以把这里写得很长，用来验证滚动效果。',
    'published',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP + INTERVAL '7 days'
);
```

预期：

- 启动后自动弹出公告
- 公告长文本可上下滚动
- 点击“知道了”仅关闭本次
- 点击“不再展示这条公告”后，同一 uuid 不再自动弹出
- 设置页可查看近 30 天历史公告

---

## 8.3 设备自带 OCR

在识图页默认选择：

```text
设备自带
```

预期：

- iPhone / iPad iOS 15+ 使用系统 Vision OCR
- backend 停止后，基础识别仍可使用

说明：

- macOS 10.14 不支持本地 OCR
- macOS 10.15+ 才能走 `VNRecognizeTextRequest`

---

## 8.4 云端 OCR

切换到：

```text
云端服务
```

接口：

```text
POST /api/v1/ocr/extract
```

预期：

- 先经过 reading backend session + 云端次数校验
- 成功时走阿里百炼 OCR
- 用尽时返回升级提示
- 不会在失败时误扣次数或假装成功

---

## 8.5 设备自带朗读

在朗读页默认选择：

```text
设备自带
```

预期：

- 使用 `AVSpeechSynthesizer`
- backend 停止后仍可使用

---

## 8.6 云端朗读

切换到：

```text
云端服务
```

接口：

```text
POST /api/v1/tts/speak
```

预期：

- 先做 reading backend 次数校验
- 后端通过 CosyVoice WebSocket 代理调用
- 前端不接触 API Key
- 成功时返回 `audioBase64`
- iOS 能播放
- 没有音频时不误判成功

---

## 8.7 孩子档案

验证：

- `GET /api/v1/children`
- `POST /api/v1/children`
- `PATCH /api/v1/children/{childId}`

重点看：

- 免费用户不能创建超过权益数量的孩子
- 多孩子权益以后端 entitlement 为准

---

## 8.8 句卡 / 复习 / 周报

验证：

- `POST /api/v1/review-cards`
- `GET /api/v1/review-cards/today`
- `POST /api/v1/review-events`
- `GET /api/v1/reports/weekly/current`
- `GET /api/v1/reports/weekly/history`

重点看：

- 句卡和复习记录是否真正写入 unified backend
- family / history 周报是否仍由后端权益控制

---

## 8.9 订阅与权益

验证：

- `GET /api/v1/subscriptions/status`
- `POST /api/v1/subscriptions/entitlements/refresh`
- `POST /api/v1/subscriptions/app-store/purchases/intake`
- `POST /api/v1/subscriptions/app-store/restores/intake`

重点看：

- 不会靠前端本地假装升级
- 购买 / 恢复后仍以后端投影结果为准

---

## 8.10 删除账号

验证：

- `POST /api/v1/account/deletion-requests`

重点看：

- 删除后 session 失效
- 孩子档案 / 句卡对该账号不再开放
- formal Apple account 时会尝试 Apple revoke

---

## 9. 如果编译失败，怎么把信息发回来

请把以下内容发回来：

1. Xcode 报错全文
2. `xcodegen generate` 日志
3. `xcodebuild` 日志
4. 若接口失败，请附：
   - URL
   - status
   - body
   - backend 日志
   - `requestId`

### 命令行构建日志示例

```bash
cd ~/code/app/paipai/ios

xcodebuild \
  -project PaipaiReadAlong.xcodeproj \
  -scheme PaipaiReadAlong \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  clean build 2>&1 | tee ../paipai-xcodebuild.log
```

如果 Simulator 名字不确定：

```bash
xcrun simctl list devices available
```

---

## 10. 再次强调：HBuilderX 不适用

拍拍前端不是 uni-app / H5 / Vue 项目，因此：

- **不能使用 HBuilderX 正常运行前端**
- 正确工具链仍然是：

```text
Xcode + XcodeGen + iOS Simulator / 真机
```

---

## 11. 最短执行版

### 启后端

```bash
cd ~/code/app/backend
brew services start postgresql@16
brew services start redis
createdb apphub_dev || true

export SPRING_PROFILES_ACTIVE=dev
export DB_URL=jdbc:postgresql://127.0.0.1:5432/apphub_dev
export DB_USERNAME=$(whoami)
export DB_PASSWORD=
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
export BACKEND_ENV=dev
export BACKEND_OPS_TOKEN=dev-local-token
export DASHSCOPE_API_KEY=你的新加坡百炼Key

mvn spring-boot:run
```

### 配前端

```bash
cd ~/code/app/paipai/ios
open project.yml
```

修改：

```yaml
DEVELOPMENT_TEAM: YOUR_TEAM_ID
MARKETING_VERSION: '1.0.0'
CURRENT_PROJECT_VERSION: '1'
INFOPLIST_KEY_PAIPAI_API_BASE_URL: http://127.0.0.1:8080
```

### 生成工程并运行

```bash
xcodegen generate
open PaipaiReadAlong.xcodeproj
```

然后在 Xcode 中：

```text
选择 iPhone Simulator -> Cmd + R
```
