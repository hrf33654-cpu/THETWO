# 技术设计文档：二次元聊天陪伴 + AR 召唤 App MVP

## 文档信息

- 文档版本：`v0.7`
- 文档状态：`In Progress`
- 更新时间：`2026-05-17`
- 关联 PRD：`PRD-二次元空间陪伴App-MVP.md`
- 技术阶段：`MVP`
- 客户端平台：`Android 原生`

## 1. 目标

本文档描述 THETWO 当前的实现方案与技术边界，重点说明：

- 聊天主链路如何落地
- fallback 召唤如何落地
- 3D 与 EasyAR 当前做到哪一步
- 哪些部分已完成，哪些部分被外部条件阻塞

## 2. 技术总览

### 2.1 客户端技术栈

- 语言：`Kotlin`
- UI：`Jetpack Compose`
- 架构：`单 Activity + 多 Screen + ViewModel / Repository`
- 相机 fallback：`CameraX`
- AR 主线：`EasyAR Sense Image Tracking / ImageTarget`
- 3D 渲染：`Filament`
- 本地持久化：`Preferences DataStore`
- 网络：`Retrofit + OkHttp`

### 2.2 后端职责

当前后端负责：

- 登录与用户资料
- 聊天转发与服务端 LLM
- 摘要 / 记忆 / 安全状态
- 最近作品回流

当前后端实现：

- `Node + TypeScript + Express + SQLite`
- 国内 Linux 部署
- `pm2` 托管

## 3. 当前关键技术结论

### 3.1 聊天主链路

当前已完成：

- `/chat/send` 走真实 LLM
- 服务端维护：
  - `chat_summaries`
  - `memory_states`
  - `safety_states`
- 客户端只消费 `NORMAL / RESTRICTED`

当前未完成：

- streaming
- 正式长期记忆 UI
- 上游模型服务稳定性完全闭环

### 3.2 3D 运行时链路

当前已完成：

- Filament 接入
- `placeholder_cube.glb` 作为当前占位资产
- `character.glb` 进入预检流程

当前已确认事实：

- `character.glb` 当前 `493 bones`
- Filament 当前手机预览链上限 `256`
- 因此 `character.glb` 不能直接用于当前移动端 3D 预览主链路

当前技术策略：

- `placeholder_cube.glb` 继续作为 3D / AR 管线验证资产
- 最终角色资产移动端版本后续单独处理

### 3.3 EasyAR 主线当前状态

当前已完成：

- `EasyAR.aar` 已接入 `app/libs`
- `BuildConfig.EASYAR_LICENSE_KEY` 已接入构建
- `EasyArSession` 已具备这些职责：
  - 初始化 EasyAR
  - 启动相机输入帧
  - 加载官方锚点图
  - 建立 tracker 管线
  - 输出 tracking pose 状态

当前初始化路径：

- 当前只走：
  - `Engine.initialize(Activity, licenseKey)`
- 不再保留：
  - `initializeKey(...)`
  - `setupActivity(...)`
  - 其它兜底调用路径

当前真机结论：

- SDK 已成功加载
- 新旧 key 都已进入 APK
- 真机日志仍然报：
  - `Initialization Failed: EasyAR Sense (Android-arm64) Invalid Key: {}`

因此当前技术口径必须写成：

- EasyAR **已接入**
- EasyAR **被 license Invalid Key 阻塞**
- 当前真 AR 主路径 **不可用**

## 4. 当前召唤模块设计

### 4.1 fallback 主链路

当前稳定可用：

- Camera preview fallback
- Screen-only fallback
- 2D 卡片拖动 / 缩放 / 旋转
- 截图保存
- 最近作品回流

### 4.2 EasyAR 分支

当前代码里已经有：

- `SummonEntryState.EASYAR_TRACKING`
- `EasyArTrackingState`
  - `IDLE`
  - `TRACKING`
  - `LOST`
  - `FAILED`
- `MarkerPoseState`

当前设计目标：

- 真相机画面做底图
- tracking 成功后，立方体贴到锚点图平面上
- tracking 丢失时隐藏立方体

但当前现实状态是：

- 由于 license 阻塞
- 真机尚未进入可用的 EasyAR tracking 态
- 召唤页应回退到 2D fallback

### 4.3 CharacterModelViewport 当前语义

当前 `CharacterModelViewport`：

- 支持读取 `markerPoseState`
- 目标是作为 AR overlay 层使用
- 当前阶段只服务占位立方体

当前限制：

- EasyAR 不可用时，这层不应被视为真 AR 已完成
- 当前仍需继续验证“初始化失败时不崩”

## 5. AR 章节统一口径

当前关于 AR 的技术口径统一为：

- 当前阻塞不是“设备已确认不支持”
- 当前主根因是：
  - **EasyAR license `Invalid Key`**
- 当前不应把 EasyAR 写成“进行中待验收”
- 当前应写成：
  - **已接入**
  - **被 license 阻塞**
  - **等待外部支持结果**

## 6. 截图与回流

当前保持不变：

- 截图仍走 fallback 合成路径
- 不把真实 3D / AR 渲染结果写进当前保存图片
- 最近作品回流协议不变

因此当前限制应明确：

- 当前截图不代表真实 AR 渲染结果
- 但不影响：
  - 保存截图
  - 最近作品回流
  - 聊天引用

## 7. 当前技术结论

统一技术结论如下：

- 聊天链路、后端、LLM、会话持久化、fallback 召唤已形成稳定主链路
- 3D 运行时验证已接入，但最终角色资产受骨骼数限制阻塞
- EasyAR SDK 与代码集成已完成到“可初始化验证”阶段
- EasyAR 当前真机主阻塞是：
  - **license `Invalid Key`**
- 因此当前技术主线不应再把“真 AR 立即可用”作为已接近完成项
