# 当前实现状态：二次元聊天陪伴 + AR 召唤 MVP

## 文档信息

- 文档版本：`v0.11`
- 文档状态：`In Progress`
- 更新时间：`2026-05-17`
- 关联 PRD：`PRD-二次元空间陪伴App-MVP.md`
- 关联开发流程：`开发流程文档-二次元聊天陪伴AR召唤-MVP.md`
- 关联技术设计：`技术设计文档-二次元聊天陪伴AR召唤-MVP.md`

## 1. 文档目的

这份文档只回答三类问题：

- 当前版本已经做到什么
- 当前版本还没做到什么
- 当前版本为什么还没做到

它是代码现状的快照，不替代 PRD，也不替代技术设计。

## 2. 当前总体结论

当前项目已经达到：

- 聊天主闭环可用
- 真后端联调可用
- 真 LLM 已接入服务端
- 会话持久化、冷启动恢复、401 清理已完成
- fallback 召唤闭环可用
- 截图保存与最近作品回流可用

当前项目尚未达到：

- 真 AR 主路径可用
- 最终 3D 角色资产可用
- 生产级发布状态

一句话总结：

`P0` 与 `P1` 已基本完成，`P2` 已进入实现，但当前被 EasyAR license 阻塞；现阶段唯一稳定可用的召唤能力仍是 `2D fallback`、`Camera preview fallback` 和 `placeholder_cube.glb` 占位 3D 预览链路。

## 3. 已完成内容

### 3.1 工程与客户端基线

已完成：

- Android 应用名统一为 `THETWO`
- 包名、`namespace`、`applicationId` 统一为 `com.thetwo.app`
- 单 `Activity + Compose Navigation` 架构已完成
- 功能目录已拆分为：
  - `auth`
  - `chat`
  - `companion`
  - `summon`
  - `settings`
  - `session`
  - `network`
  - `media`
- Debug 构建、Lint、单测链路可运行

### 3.2 登录、创角与会话恢复

已完成：

- 邮箱验证码登录 UI
- 隐私同意门槛
- 创角页最小资料采集
- 会话本地持久化：
  - `sessionToken`
  - `email`
  - `profileCompleted`
  - `companionProfile`
  - `recentCaptureReference`
  - `arPrivacyAccepted`
- 冷启动恢复与 `GET /me` 远端校验
- 401 统一清理与回登录页

### 3.3 聊天主闭环

已完成：

- `GET /chat/history`
- `POST /chat/send`
- `DELETE /chat/history`
- 发送中 / 成功 / 失败 / 重试状态
- 最近作品回流卡片
- 后端真实 LLM 调用
- 服务端会话摘要
- 服务端基础记忆
- 服务端短时安全状态

当前聊天现状：

- 主链路已可用
- 失败时可重试
- 安全模式由服务端返回 `NORMAL / RESTRICTED`

### 3.4 后端与服务端能力

已完成：

- `Node + TypeScript + Express + SQLite`
- 登录、用户、创角、聊天、最近作品、删除接口
- 公网联调入口
- 真实 LLM 配置与调用链
- 摘要 / 记忆 / 安全状态服务端落地

当前服务端现状：

- 后端功能链完整
- 上游模型服务仍存在额度 / 鉴权 / 套餐匹配问题待排查

### 3.5 召唤 fallback 闭环

已完成：

- 召唤页入口
- CameraX 相机预览 fallback
- 纯屏 fallback
- 2D 卡片拖动 / 缩放 / 旋转
- 截图保存
- 最近作品回流到聊天
- 设置页清理最近作品与聊天记录

### 3.6 3D 占位预览链路

已完成：

- Filament 依赖接入
- `placeholder_cube.glb` 运行时接入
- `CharacterModelViewport` 可加载占位 3D 资源
- `character.glb` 骨骼数预检
- 超限时不再直接 native 崩溃

当前 3D 现状：

- 占位立方体用于验证 3D 渲染链路
- 最终角色资产 `character.glb` 不可直接用于当前手机 Filament 预览

## 4. 当前未完成内容

### 4.1 最终 3D 角色资产

当前未完成：

- `character.glb` 真机可用版本
- 角色动画与待机表现
- 最终角色进入召唤主链路

阻塞事实：

- `character.glb` 当前 `493 bones`
- 当前 Filament 手机预览链路上限为 `256`

### 4.2 EasyAR 真 AR 主路径

当前未完成：

- 真机 EasyAR 初始化通过
- 官方锚点图识别成功
- 立方体叠加到真实相机画面
- 丢锚恢复
- 真 AR 合屏验收

当前阻塞事实：

- `EasyAR.aar` 已接入
- 新旧 license key 都已真实打包验证
- `BuildConfig.EASYAR_LICENSE_KEY` 已确认为最新 key
- 真机日志仍然报：
  - `Initialization Failed: EasyAR Sense (Android-arm64) Invalid Key: {}`

因此当前结论是：

- EasyAR SDK 已接入
- EasyAR 主路径当前被 **license Invalid Key** 阻塞
- 当前 AR 不可用

### 4.3 发布级能力

当前未完成：

- Release 正式域名 / HTTPS
- 真实邮件验证码闭环
- PostgreSQL 迁移
- 生产监控、日志与备份闭环

## 5. 当前召唤页真实状态

当前召唤页不再以 EasyAR 成功为前提。

真实状态如下：

- 进入召唤页后，若 EasyAR 不可用或初始化失败，应退回 fallback
- 当前稳定可用的是：
  - `2D fallback`
  - `Camera preview fallback`
  - `placeholder_cube.glb` 占位 3D 预览链
- 当前不应视为“真 AR 可用”

同时已确认：

- 之前“EasyAR 失败连带 Filament native 崩”的一部分崩溃链已做代码级缓解
- 但召唤页稳定性仍需继续真机复验

## 6. 下一步优先级

当前推荐顺序：

1. 继续等待 / 排查 EasyAR 官方 license 问题
2. 不被 EasyAR 阻塞的前提下，继续推进聊天稳定性与 fallback 召唤体验
3. 并行准备最终角色资产的移动端版本
4. EasyAR 解除阻塞后，再继续锚点识别与真实相机叠加

## 7. 当前结论口径

统一口径如下：

- `P0`：已完成到可用态
- `P1`：已完成到 Alpha 可测态
- `P2`：进行中，但被 EasyAR license 阻塞

因此当前版本应描述为：

**“聊天主链路、真后端、真 LLM、会话持久化、fallback 召唤闭环已完成；P2 已进入 EasyAR + 3D 阶段，但 EasyAR 主路径当前被 license `Invalid Key` 阻塞。”**
