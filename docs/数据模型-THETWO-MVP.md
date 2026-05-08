# 数据模型：THETWO MVP

## 文档信息

- 文档版本：`v0.1`
- 文档状态：`Draft`
- 更新时间：`2026-05-08`
- 事实来源：`backend/src/db.ts`、`backend/src/types.ts`、`app/src/main/java/com/thetwo/app/network/BackendModels.kt`
- 适用范围：MVP 后端 SQLite 数据模型与下一阶段逻辑模型

## 1. 目标

本文档定义 THETWO MVP 的核心数据模型、所有权、生命周期、保留、删除和迁移规则。目标是避免后续接入真实 LLM、记忆、streaming 和 PostgreSQL 时出现数据语义分裂。

当前数据原则：

- 后端是账号、角色、聊天历史、最近作品回流元信息的权威来源。
- Android 客户端可以缓存认证态和展示态，但不能成为长期数据权威来源。
- 系统相册截图文件不归后端管理，后端只记录最近一次作品回流元信息。
- 删除账号必须删除后端可识别的用户相关数据。

## 2. 当前真实模型

### 2.1 User

实现表：`users`

字段：

- `id`
- `email`
- `created_at`

所有权：

- 归后端所有。
- 一个 email 对应一个用户。

创建时机：

- `POST /auth/verify-code` 首次验证成功时创建。

更新时机：

- 当前不支持更新 email。

读取方：

- 后端鉴权逻辑。
- Android 通过 `GET /me` 间接读取用户身份。

删除规则：

- `DELETE /me` 删除用户。
- 数据库通过 `ON DELETE CASCADE` 级联删除 session、角色资料、聊天记录和最近作品回流。

保留周期：

- 用户未删除账号前保留。

迁移风险：

- 迁移 PostgreSQL 时必须保持 email 唯一约束。

### 2.2 AuthCode

实现表：`auth_codes`

字段：

- `email`
- `code`
- `created_at`

所有权：

- 归后端所有。
- 当前是开发验证码，不是真实邮件验证码。

创建时机：

- `POST /auth/request-code` 生成或覆盖。

更新时机：

- 同一 email 再次请求验证码时覆盖旧验证码。

读取方：

- `POST /auth/verify-code` 校验。

删除规则：

- 当前没有主动删除或过期清理。

保留周期：

- 当前开发阶段长期保留最新验证码。
- 正式邮件服务接入后必须增加过期时间和清理策略。

迁移风险：

- 正式化时不能继续暴露 `devCode` 给 Release 客户端。

### 2.3 Session

实现表：`sessions`

字段：

- `token`
- `user_id`
- `created_at`

客户端模型：

- `AuthSession.userId`
- `AuthSession.email`
- `AuthSession.sessionToken`
- `AuthSession.profileCompleted`

所有权：

- token 由后端生成和校验。
- Android 只保存 token 并通过 Bearer Header 使用。

创建时机：

- `POST /auth/verify-code` 验证成功后创建。

更新时机：

- 当前 session 不更新。

读取方：

- 所有需要登录态的 API。
- Android 当前以内存态保存；下一阶段需要本地持久化。

删除规则：

- `DELETE /me` 通过用户级联删除。
- 当前没有单独登出或 session 过期接口。

保留周期：

- 当前没有显式过期时间。
- Alpha 前应至少定义客户端本地清理和服务端失效处理。

迁移风险：

- PostgreSQL 迁移时必须保留 token 唯一约束。
- 引入过期时间时要兼容旧 token。

### 2.4 CompanionProfile

实现表：`companion_profiles`

字段：

- `user_id`
- `nickname`
- `tone`
- `personality_tags`
- `interest_tags`
- `updated_at`

客户端模型：

- `RemoteCompanionProfile`
- `CompanionProfile`

所有权：

- 每个用户当前只有一个角色资料。
- 后端是角色资料权威来源。

创建时机：

- `PUT /me/companion-profile` 首次保存。

更新时机：

- `PUT /me/companion-profile` 覆盖当前角色资料。

读取方：

- Android 创角后写入会话状态。
- 聊天页启动时从后端恢复。
- 后续真实 LLM prompt 会读取该资料。

删除规则：

- `DELETE /me` 级联删除。
- 当前没有单独删除角色资料接口。

保留周期：

- 用户未删除账号前保留。

迁移风险：

- 当前标签以 JSON 字符串保存；迁移 PostgreSQL 时可继续 JSON 存储，也可拆表，但需要保持 API 不变。

### 2.5 ChatMessage

实现表：`chat_messages`

字段：

- `id`
- `user_id`
- `role`
- `content`
- `mode`
- `client_message_id`
- `created_at`

客户端模型：

- `RemoteChatMessage`
- `ChatMessage`

所有权：

- 后端保存聊天历史。
- Android 只维护当前屏幕消息状态和失败重试状态。

创建时机：

- `POST /chat/send` 先调用模型；模型成功后再写用户消息与助手回复。
- 模型失败、超时或空回复时，不写入 `chat_messages`。

更新时机：

- 当前不更新历史消息。

读取方：

- `GET /chat/history`
- 后续 LLM 上下文与会话摘要。

删除规则：

- `DELETE /chat/history` 删除当前用户全部聊天消息。
- `DELETE /me` 级联删除。

保留周期：

- 当前未设置自动过期。
- Alpha 前需要决定是否限制历史条数或引入摘要。

迁移风险：

- 接入 streaming 后要避免同一 `clientMessageId` 重复写入不可区分的用户消息。
- 真实 LLM 接入后，`mode` 必须继续标记安全状态。
- 若后续补本地离线草稿或发送队列，需要继续保证失败重试不重复落脏历史。

### 2.6 Capture

实现表：`recent_captures`

当前 API 名称：`recent-capture`

字段：

- `user_id`
- `title`
- `summary`
- `storage_location`
- `updated_at`

客户端模型：

- `RemoteRecentCapture`
- `RecentCaptureReference`

所有权：

- 后端只拥有最近一次作品回流元信息。
- 系统相册截图文件由用户和系统相册拥有。

创建时机：

- 召唤页截图保存成功后，Android 调用 `PUT /me/recent-capture`。

更新时机：

- 每次新截图回流覆盖旧记录。

读取方：

- 聊天页展示最近作品回流卡片。
- 后续真实 LLM 可作为最近作品上下文。

删除规则：

- `DELETE /me/recent-capture` 只清除 App 内元信息。
- `DELETE /me` 级联删除。
- 不删除系统相册文件。

保留周期：

- 当前只保留最近一次。

迁移风险：

- 如果未来做作品库，不能复用该模型语义直接扩成多作品表；应新增作品模型。

## 3. 下一阶段逻辑模型

以下模型当前尚未正式落库，不能在文档或代码中描述为已实现。

### 3.1 ChatSummary

用途：

- 对较长聊天历史做摘要，降低 LLM 上下文成本。

建议字段：

- `userId`
- `summary`
- `messageRange`
- `updatedAt`

所有权：

- 后端所有。
- Android 不直接编辑。

生命周期：

- 聊天历史达到阈值后由后端生成或更新。

删除规则：

- `DELETE /chat/history` 应同步删除或重置摘要。
- `DELETE /me` 级联删除。

迁移风险：

- 摘要会影响角色长期表现，必须可重建或可丢弃。

### 3.2 MemoryState

用途：

- 保存角色对用户关系、偏好、最近互动主题的轻量记忆。

建议字段：

- `userId`
- `relationshipTags`
- `preferenceTags`
- `recentInteraction`
- `recentCaptureTheme`
- `updatedAt`

所有权：

- 后端所有。
- Android 只展示必要结果，不直接写长期记忆。

生命周期：

- 由聊天、创角、最近作品回流共同更新。

删除规则：

- 清空聊天是否清理记忆需要单独决策。
- 删除账号必须删除记忆。

迁移风险：

- 记忆写入需要避免把敏感内容永久化。

### 3.3 SafetyState

用途：

- 表示当前会话是否进入更保守的安全模式。

当前实现：

- `ChatMode = NORMAL | RESTRICTED`
- 当前按消息返回 `mode`，尚未形成独立状态表。

建议字段：

- `userId`
- `mode`
- `reason`
- `expiresAt`
- `updatedAt`

所有权：

- 后端所有。
- Android 只消费结果，不自行判定最终安全状态。

生命周期：

- 未成年人自述、危机内容或其他安全规则触发时进入更保守模式。
- 可按短时会话窗口自动恢复或由后端重新判定。

删除规则：

- 删除账号必须删除。
- 清空聊天是否重置 SafetyState 需要在正式安全策略中明确。

迁移风险：

- 不能只依赖前端关键词，真实策略必须在服务端闭环。

## 4. 删除与保留总规则

- 删除账号：删除后端可识别的用户相关数据，包括 session、角色资料、聊天记录、最近作品回流元信息。
- 清空聊天：只删除聊天记录，不删除账号、角色资料、最近作品回流。
- 清除最近作品：只删除最近作品回流元信息，不删除系统相册截图。
- 系统相册图片：由用户在系统相册中自行删除，App 后端不拥有也不删除。
- 本地缓存：Android 下一阶段可以缓存 session 和展示态，但远端删除后必须能被清理。

## 5. 迁移总规则

- API 响应字段优先保持向后兼容。
- SQLite 到 PostgreSQL 迁移时，先迁当前真实模型，再引入下一阶段逻辑模型。
- 引入 `ChatSummary`、`MemoryState`、`SafetyState` 时，应新增表或明确字段，不要混入现有 `chat_messages` 导致语义不清。
- 所有迁移必须提供回滚或可重建路径，尤其是聊天摘要和记忆。
