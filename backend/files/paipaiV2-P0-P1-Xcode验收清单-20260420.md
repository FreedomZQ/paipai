# paipaiV2 P0 / P1 Xcode 验收清单（2026-04-20）

> ⚠️ 历史说明（2026-04-22）：本清单包含 2026-04-20 的旧验收口径。当前 reading 已改为 **Apple 登录唯一正式入口**；删除账号仍保留**临时输入邮箱验证码确认**，但不再支持 demo session / 邮箱登录作为现行方案。

> 目的：在 Mac / Xcode 环境完成当前 Linux 无法执行的最终验收。

## 一、编译与安装
- [ ] 打开 `paipaiV2/ios` 工程并完成 resolve package / dependencies
- [ ] Debug 编译通过（iPhone Simulator）
- [ ] Release 编译通过（iPhone Simulator）
- [ ] 真机安装通过（至少 1 台 iPhone）
- [ ] iPad 编译 / 安装通过
- [ ] 如果支持 macOS Catalyst，验证是否可编译（如不支持则明确关闭）

## 二、P0 核心验收

### 1. App 启动与 session
- [ ] 首次启动：可拉取 bootstrap/config
- [ ] 无正式 session 时：不会自动创建 demo session，需通过 Apple 登录建立正式会话
- [ ] 有本地 formal session 时：启动会校验 `auth/me`
- [ ] session 失效时：本地 token 自动清除，不会卡死在错误状态

### 2. 拍读主链路
- [ ] 相机拍照进入 Capture
- [ ] 相册选图进入 Capture
- [ ] 设备 OCR 可识别一句/一小段
- [ ] 云端 OCR 在 formal account + quota 足够时可成功
- [ ] OCR 识别后进入 LearningDetail
- [ ] Translation 结果正常展示
- [ ] 设备朗读可用
- [ ] 保存句卡后可进入 review list

### 3. Review 主链路
- [ ] 今日 review 卡片能拉到真实数据
- [ ] again / hard / good / easy 上报成功
- [ ] 不再出现“提交后跳过下一张卡”的 bug
- [ ] 完成页正常展示

### 4. 家长区主链路
- [ ] 进入家长区优先触发 Face ID / Touch ID / 设备密码
- [ ] 系统验证失败时，才进入数学题 fallback
- [ ] Apple 登录成功
- [ ] Apple 登录成功
- [ ] 孩子列表真实展示
- [ ] 今日 / 累计 usage 能展示
- [ ] 最近 7 天 usage 能展示

### 5. 删除账号
- [ ] formal account 删除前必须先发送验证码
- [ ] 删除验证码可送达邮箱 / Apple private relay
- [ ] 输入验证码后才允许删除
- [ ] 删除后 session 被清理
- [ ] 删除结果页可展示状态与 note

### 6. 价格/权益
- [ ] plans 返回多个计划时，Paywall 正常展示
- [ ] childLimit / supportedLocales / historyEnabled 等字段可见
- [ ] 发起购买时会走 App Store + backend 校验链路
- [ ] restore purchases 正常

### 7. 公告与法务
- [ ] 公告弹窗遵循 maxDisplayCount / minIntervalSeconds
- [ ] 法务链接可以打开
- [ ] `/api/v1/legal/docs` 返回的链接在真机上可访问

## 三、P1 重点验收

### 1. 语言偏好
- [ ] 家长区可进入 LanguagePreferenceView
- [ ] 修改界面语种后主要页面立即切换
- [ ] 修改学习方向后可写回 preferences
- [ ] 重启 App 后偏好仍然生效

### 2. Usage 生命周期
对以下页面分别验证：Capture / LearningDetail / Review
- [ ] 进入页面时 startUsageSession
- [ ] 返回上级页面时 endUsageSession
- [ ] 切换 child 时旧 session 结束，新 session 重开
- [ ] App 退后台时结束 session
- [ ] App 回前台后可重新开启 session
- [ ] 家长区 usage 汇总有更新

### 3. 云端 TTS 链路（当前未挂按钮）
建议用临时调试入口或断点验证：
- [ ] `BackendClient.synthesizeCloudSpeech(...)` 返回音频
- [ ] `TTSService` 云端播放成功
- [ ] quota_blocked 时能得到正确返回
- [ ] provider 失败时可回退设备朗读

### 4. 隐私与审核资料
- [ ] `Info.plist` 中权限文案与实际行为一致
- [ ] `PrivacyInfo.xcprivacy` 与真实采集一致
- [ ] 无无效 tracking 文案
- [ ] App Store Connect 问卷按当前实现填写

## 四、建议抓日志点
- 登录 / 删除 / 购买 / restore
- OCR quota_blocked
- TTS quota_blocked / provider_failed
- usage session start / end
- legal docs URL 打开失败

## 五、验收输出建议
完成后产出：
1. 编译结果（Debug/Release）
2. 真机截图 / 录屏
3. P0/P1 通过/失败表
4. 若发现问题，回填到：
   - `paipaiV2-P0-P1审计总结-20260420.md`
   - `paipaiV2-P1审计-progress-20260420.md`
