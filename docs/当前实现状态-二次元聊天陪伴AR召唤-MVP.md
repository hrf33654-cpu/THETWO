# 当前实现状态：二次元聊天陪伴 + AR 召唤 App MVP

## 文档信息

- 文档版本：`v0.7`
- 文档状态：`In Progress`
- 更新时间：`2026-05-09`
- 关联 PRD：`PRD-二次元空间陪伴App-MVP.md`
- 关联开发流程：`开发流程文档-二次元聊天陪伴AR召唤-MVP.md`
- 关联技术设计：`技术设计文档-二次元聊天陪伴AR召唤-MVP.md`
- 当前代码基线：`com.thetwo.app`

## 1. 文档目的

本文档只回答三类问题：

- 当前版本已经做到了什么
- 当前版本还没有做到什么
- 当前版本的验证结果是什么

本文档不代替 PRD、开发流程或技术设计。它是当前工程实现状态的快照。

## 2. 当前总体结论

当前工程已经达到：

- Android 工程基线、最小 CI 与 Debug 构建链路已完成
- 登录、创角、聊天、设置治理入口已接入真后端 Debug 联调
- fallback 召唤、截图保存、最近作品回流已具备可演示能力
- 后端第一阶段服务、服务器部署与 Android 客户端真后端联调已完成
- 真实 LLM 已完成服务器环境配置、公网联调与 Android 实机验证
- `P1` 第一阶段已开始落地：会话本地持久化、冷启动恢复、统一 401 清理、最小埋点骨架已接入代码
- AR 产品与技术路线已从 Google ARCore 调整为 `EasyAR Sense`，但代码层尚未接入 EasyAR SDK

当前工程尚未达到：

- `P1` Alpha 真机回归、设备矩阵与已知问题清单尚未完成
- `P2` EasyAR 真 AR 主路径未完成
- `P2` 真 3D 角色资产接入未完成
- `真实邮件验证码` 未完成
- `真实模型服务` 已跑通单模型联调，但流式输出、摘要/记忆与更强安全策略尚未完成
- `Release 版正式 HTTPS / 域名联调` 未完成

一句话总结：

当前版本是 `聊天主闭环 + fallback 召唤闭环 + 真后端 + 真 LLM Debug 联调版`，并且已进入 `P1 Alpha 稳定化`，但还不是 `PRD 完整目标版`。

## 3. 已经实现的内容

### 3.1 工程基线

已实现：

- Android 应用名统一为 `THETWO`
- 包名、`namespace`、`applicationId` 统一为 `com.thetwo.app`
- 架构为 `单 Activity + Compose Navigation`
- 已建立主功能目录：`auth`、`chat`、`companion`、`summon`、`settings`、`session`、`media`、`network`
- 已建立最小 CI 工作流：Debug 构建 + Android Lint
- 已建立项目内 JDK 运行时目录 `.tools/jdk21`

### 3.2 登录与隐私同意

已实现：

- 登录页包含邮箱输入、验证码输入、隐私同意勾选
- 登录流已改成两步式：
  - `POST /auth/request-code`
  - `POST /auth/verify-code`
- Debug 版会显示开发验证码提示
- 登录成功后拿到 `sessionToken`
- 登录后根据 `profileCompleted` 决定进入创角页或直接进入聊天页

当前实现方式：

- 使用 `AuthViewModel + AuthRepository`
- 客户端通过公网入口 `http://111.231.14.253/` 请求后端
- Debug 版放开对该 IP 的明文 HTTP 访问

当前限制：

- 仍是开发验证码，不是真实邮件验证码
- 登录态已做本地持久化，但仍是开发验证码，不是真实邮件验证码

### 3.3 创角

已实现：

- 昵称输入
- 语气输入
- 人格标签输入
- 兴趣标签输入
- 创角保存到后端 `PUT /me/companion-profile`

当前实现方式：

- 使用 `CompanionSetupViewModel + CompanionRepository`
- 角色资料保存成功后写入会话状态并进入聊天首页

当前限制：

- 无高级角色编辑器
- 只有单角色模型，不支持多角色

### 3.4 聊天主闭环

已实现：

- 聊天首页
- 远端聊天历史拉取 `GET /chat/history`
- 远端消息发送 `POST /chat/send`
- 本地消息状态：
  - `SENDING`
  - `SENT`
  - `FAILED`
- 失败消息可重试
- 最近作品回流卡片展示

当前实现方式：

- 使用 `ChatViewModel + ChatRepository`
- 进入聊天页时恢复：
  - 远端角色资料
  - 远端聊天历史
  - 远端最近作品回流
- 发送消息时先插入本地 `SENDING` 消息，再等待后端回复
- 后端 `/chat/send` 已改为真实 LLM 调用链路：
  - 读取角色资料
  - 读取最近作品回流
  - 读取最近聊天上下文
  - 组装 OpenAI 兼容 `chat/completions` 请求
- 服务器 `.env` 已配置 `LLM_BASE_URL / LLM_API_KEY / LLM_MODEL / LLM_TIMEOUT_MS`
- 已通过全新测试账号验证：公网 `POST /chat/send` 返回真实模型回复，不再返回旧占位文案

当前限制：

- 未接流式输出
- 未接会话摘要与正式长期记忆

### 3.5 安全模式

已实现：

- 聊天页支持安全/受限模式展示
- 受限模式不再依赖前端本地关键词切换
- 前端根据后端返回的 `mode=RESTRICTED` 显示更保守的状态文案

当前实现方式：

- 后端 `POST /chat/send` 决定 `NORMAL / RESTRICTED`
- 前端消费后端返回的 `mode`
- 受限模式继续走模型回复，但使用更强的安全 prompt 与更低温度参数

当前限制：

- 安全模式的入口判定仍以关键词规则为主
- 未接正式审核策略和真实多轮安全模式

### 3.6 会话共享状态

已实现：

- 当前认证会话 `authSession`
- 当前角色资料
- 最近作品回流引用
- AR/相机隐私说明确认状态
- 启动恢复页 `Launch`
- `Preferences DataStore` 本地持久化

当前实现方式：

- 使用 `AppSessionViewModel`
- 全局共享：
  - `authSession`
  - `companionProfile`
  - `recentCaptureReference`
  - `arPrivacyAccepted`
- 使用 `SessionLocalStore + PersistedSessionState`
- 冷启动时先读取本地状态，再调用 `GET /me` 做远端强校验
- 若遇到 `401 / UNAUTHORIZED`，统一清本地账号态并回登录页

当前限制：

- 聊天历史仍不落本地，重启后依旧从远端重新拉取
- `arPrivacyAccepted` 会保留本地状态，不跟随账号清理

### 3.7 召唤页 fallback 主路径

已实现：

- 召唤页入口
- 相机权限请求
- 相机预览 fallback
- 纯屏 fallback
- 可拖动、缩放、旋转的角色卡片
- 官方锚点图展示
- Google Play Services for AR 安装页入口仍存在于当前代码中，属于旧 ARCore 路线遗留占位，不代表后续主线

当前实现方式：

- `SummonViewModel` 管理入口状态
- `CameraX PreviewView` 提供相机预览
- 无权限时自动回退到纯屏模式
- 角色目前为 2D 卡片式占位表现
- 当前产品与技术路线已确定改为 `EasyAR Sense 图像跟踪 + 平面识别`，但代码尚未接入 EasyAR SDK

当前限制：

- 未进入真实 AR 会话
- 未接入 EasyAR Sense SDK
- 未做 EasyAR 官方锚点图识别
- 未做锚点召唤成功后的平面稳定

### 3.8 截图保存与聊天回流

已实现：

- 生成召唤截图
- 保存截图到系统相册或应用图片目录
- 截图保存后调用 `PUT /me/recent-capture`
- 同步成功后回到聊天页并生成最近作品回流
- 同步失败时：
  - 保留本地截图
  - 不生成伪回流
  - 页面提示可重试同步

当前实现方式：

- 相机模式下使用 `PreviewView.bitmap`
- 纯屏模式下生成渐变背景 bitmap
- 再用 Canvas 叠加角色卡片
- Android 10 及以上通过 `MediaStore` 保存
- 旧版 Android 保存到应用图片目录

当前限制：

- 不是 AR 锚定后的真实角色截图
- 不是 3D 模型渲染截图

### 3.9 设置与治理入口

已实现：

- 隐私与相机说明卡片
- 权限边界说明卡片
- 最近作品回流展示
- 清除最近作品回流 `DELETE /me/recent-capture`
- 清空聊天记录 `DELETE /chat/history`
- 账号数据删除入口 `DELETE /me`

当前实现方式：

- 使用 `SettingsViewModel`
- 真正调用后端接口，而不是本地占位
- 清除最近作品回流只删除 App 内引用，不删除系统相册文件

当前限制：

- 删除账号后仍以“返回登录页 + 清空内存态”为主
- 未接更细粒度的数据导出或软删除策略

### 3.10 网络层与调试联调配置

已实现：

- `Retrofit + OkHttp + Gson`
- `INTERNET` 权限
- Debug 专用 `network_security_config`
- `BuildConfig.API_BASE_URL`
- `AppContainer` 手动依赖注入
- `LaunchViewModel` 冷启动恢复
- `AnalyticsTracker + DebugAnalyticsTracker`

当前实现方式：

- Debug 版 `API_BASE_URL = http://111.231.14.253/`
- 已通过 Android 实机验证该公网入口可完成登录、创角与聊天主链路
- Release 版保留 HTTPS 占位，不允许当前明文 IP 策略进入正式发布链路

当前限制：

- 还没有正式域名
- 还没有 Release 可用的 HTTPS 接口

### 3.11 最小埋点骨架

已实现：

- `AnalyticsTracker` 统一接口
- Debug 下 `DebugAnalyticsTracker` 输出本地日志
- 已接入的关键事件：
  - `login_request_code_success`
  - `login_verify_success`
  - `companion_profile_saved`
  - `chat_send_success`
  - `chat_send_failed`
  - `chat_restricted_mode_entered`
  - `summon_opened`
  - `capture_saved_local`
  - `capture_sync_success`
  - `capture_sync_failed`
  - `chat_history_cleared`
  - `recent_capture_cleared`
  - `account_deleted`
  - `session_restore_started`
  - `session_restore_succeeded`
  - `session_restore_failed`
  - `session_unauthorized_cleared`

当前限制：

- 仅输出 Debug 本地日志
- 未接正式分析平台
- 尚未形成完整 Alpha 漏斗报表

### 3.12 后端第一阶段

已实现：

- `backend/` 最小服务目录
- `Node + TypeScript + Express + SQLite`
- 接口：
  - `GET /health`
  - `POST /auth/request-code`
  - `POST /auth/verify-code`
  - `GET /me`
  - `PUT /me/companion-profile`
  - `GET /me/companion-profile`
  - `POST /chat/send`
  - `GET /chat/history`
  - `DELETE /chat/history`
  - `PUT /me/recent-capture`
  - `GET /me/recent-capture`
  - `DELETE /me/recent-capture`
  - `DELETE /me`

当前实现方式：

- `Express` 提供 REST API
- `node:sqlite` 提供 SQLite 本地库
- 服务端当前采用开发验证码
- `/chat/send` 已接入 OpenAI 兼容 `chat/completions` provider
- 当前服务器已完成模型环境变量配置；未配置时，`/chat/send` 会返回 `503 LLM_NOT_CONFIGURED`

当前限制：

- 未接真实邮件服务
- 尚未接入流式输出、会话摘要与正式长期记忆

### 3.13 服务器部署状态

已实现：

- Ubuntu 22.04 服务器初始化完成
- Node 24 已安装
- PostgreSQL 14 已安装
- 已创建 `thetwo` 数据库与用户
- `pm2` 已托管 `thetwo-backend`
- `Nginx` 已完成反向代理
- 下列地址已验证通过：
  - `http://127.0.0.1:8787/health`
  - `http://127.0.0.1/health`
  - `http://111.231.14.253/health`
  - `http://111.231.14.253/`

当前说明：

- 当前服务器上 PostgreSQL 已安装并可用
- 当前后端代码实际使用的仍是 `SQLite`
- PostgreSQL 是下一阶段可切换的正式数据库基础，不是当前运行中的主存储
- 当前 LLM 走 OpenAI 兼容 provider，服务器已完成单模型公网联调
- 当前 `http://111.231.14.253/health` 与公网 `/chat/send` 已验证通过

当前公网联调地址：

- `http://111.231.14.253`

当前内网后端监听地址：

- `http://127.0.0.1:8787`

当前限制：

- 仍是 HTTP
- 未接域名
- 未接 HTTPS
- `pm2` 当前由 `root` 用户托管，排查状态与日志需使用 `sudo env PM2_HOME=/root/.pm2 pm2 ...`

## 4. 当前未实现的内容

### 4.1 EasyAR 真 AR 主路径

未实现：

- `EasyAR Sense` SDK 接入与初始化
- `AR Optional` 真正分流逻辑
- `ImageTarget / Image Tracking` 官方锚点图识别
- 锚点图召唤成功后的平面识别与平面稳定
- 丢锚保持
- AR 会话失败恢复

说明：

- 当前代码中的 Google Play Services for AR 入口是历史占位，不再作为下一阶段 AR 主路径。
- 下一阶段 AR 主线以 `EasyAR Sense` 为准：先完成官方锚点图召唤，再在支持设备上启用平面稳定；不支持时回退到 CameraX 相机叠加或纯屏召唤。

### 4.2 真 3D 角色资产接入

未实现：

- `FBX -> GLB` 实际转换结果
- `character.glb` 运行时加载
- 3D 模型渲染
- 待机动画播放

### 4.3 真实外部服务

未实现：

- 真实邮件验证码发送
- 真实模型服务的线上环境配置与联调验收
- 真实会话摘要与长期记忆

### 4.4 客户端正式发布链路

未实现：

- `sessionToken` 本地持久化
- Release 版正式 HTTPS 联调
- 正式域名配置
- Debug / Release 环境切换策略的完整生产化

### 4.5 后端正式化

未实现：

- 从 `SQLite` 迁移到 `PostgreSQL`
- 生产级日志整理
- 监控
- 备份
- 域名与 HTTPS 证书

### 4.6 Alpha 数据与埋点

未实现：

- 登录完成率埋点
- 创角完成率埋点
- 进入召唤页率埋点
- 保存截图率埋点
- 聊天回流率埋点
- AR 漏斗与 fallback 漏斗拆分埋点

## 5. 当前构建与验证结果

客户端已验证通过：

- `compileDebugKotlin`
- `assembleDebug --offline`
- `lintDebug --offline`
- `testDebugUnitTest --offline`

后端已验证通过：

- `npm run build`
- `npm test`
- 本地 smoke test：
  - 未配置 LLM 环境时，`POST /chat/send` 返回 `503`
  - 失败请求不会向 `chat_messages` 写入脏数据

客户端已产物：

- Debug 安装包：`app/build/outputs/apk/debug/app-debug.apk`
- Lint 报告：`app/build/reports/lint-results-debug.html`

后端已验证通过：

- `npm install`
- `npm run build`
- `pm2 start dist/index.js --name thetwo-backend`
- `pm2 save`
- `pm2 startup`
- `curl http://127.0.0.1:8787/health`
- `curl http://127.0.0.1/health`
- `curl http://111.231.14.253/health`

联调已验证通过：

- Debug 客户端可请求 `POST /auth/request-code`
- Debug 客户端可请求 `POST /auth/verify-code`
- 客户端可显示开发验证码提示
- 客户端可成功登录并继续走创角/聊天流程

## 6. 目前最接近 PRD 的完成度判断

如果按 PRD 的“聊天主闭环 + AR 增强 + 后端服务”来判断：

- `聊天主闭环`：已基本完成
- `fallback 召唤闭环`：已基本完成
- `设置与治理入口`：已基本完成
- `服务器与最小后端`：已完成
- `Android 客户端真后端接入`：已完成
- `真 AR 主路径`：未完成
- `真 3D 角色入场`：未完成

因此当前状态应定义为：

`可演示的 MVP 真后端联调版`，不是 `PRD 完整目标版`。

## 7. 下一阶段最优先事项

当前最优先的三件事是：

1. 完成 `LLM_BASE_URL / LLM_API_KEY / LLM_MODEL` 的线上配置与公网联调
2. 补齐会话持久化、埋点与 Alpha 稳定性
3. 再推进 `FBX -> GLB -> 客户端加载` 与 `EasyAR Sense 锚点图召唤 + 平面稳定`
