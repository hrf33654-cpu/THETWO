# API 接口契约：THETWO MVP

## 文档信息

- 文档版本：`v0.1`
- 文档状态：`Draft`
- 更新时间：`2026-05-08`
- 事实来源：`backend/src/index.ts`、`backend/src/types.ts`、`app/src/main/java/com/thetwo/app/network/BackendModels.kt`
- 适用范围：Android Debug 客户端与当前最小后端 REST API

## 1. 目标

本文档固化当前 THETWO MVP 的 API 边界，避免后续接入真实 LLM、streaming、memory 时破坏客户端和服务端之间的基本契约。

当前原则：

- Android 客户端只调用 THETWO 后端，不直连 LLM 或外部模型服务。
- 所有业务接口统一使用 JSON。
- 除登录验证码接口外，用户态接口统一使用 `Authorization: Bearer <sessionToken>`。
- 当前接口以非流式 REST 为主；未来 streaming 必须兼容当前非流式能力。

## 2. 通用响应格式

成功响应：

```json
{
  "success": true,
  "data": {},
  "errorCode": null,
  "message": "ok"
}
```

失败响应：

```json
{
  "success": false,
  "data": null,
  "errorCode": "ERROR_CODE",
  "message": "错误说明"
}
```

客户端处理规则：

- `success=true` 且 `data` 非空时，客户端按对应模型解析。
- `success=false` 时，客户端必须优先展示 `message`，并根据 `errorCode` 决定是否重登或重试。
- `errorCode=UNAUTHORIZED` 或 HTTP `401` 表示会话失效，客户端必须清理认证态并回到登录页。

## 3. 通用鉴权

需要登录态的接口必须带：

```http
Authorization: Bearer <sessionToken>
```

当前 `sessionToken` 来源：

- `POST /auth/verify-code` 成功返回。
- 当前客户端内存保存；下一阶段会做本地持久化。

当前会话限制：

- 后端没有显式 session 过期时间。
- 删除账号后，对应用户下的 session 会被级联删除。

## 4. Health

### `GET /health`

用途：服务健康检查。

认证：不需要。

响应 `data`：

```json
{
  "status": "ok",
  "appEnv": "development",
  "port": 8787
}
```

## 5. Auth

### `POST /auth/request-code`

用途：请求邮箱验证码。

认证：不需要。

请求体：

```json
{
  "email": "user@example.com"
}
```

成功响应 `data`：

```json
{
  "email": "user@example.com",
  "devCode": "123456"
}
```

当前说明：

- 当前是开发验证码，不是真实邮件发送。
- Debug 客户端可以展示 `devCode`。
- Release 环境不得向用户展示开发验证码。

可能错误：

- `INVALID_EMAIL`：邮箱格式无效。

### `POST /auth/verify-code`

用途：校验验证码并创建 session。

认证：不需要。

请求体：

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

成功响应 `data`：

```json
{
  "userId": "uuid",
  "email": "user@example.com",
  "sessionToken": "token",
  "profileCompleted": false
}
```

可能错误：

- `INVALID_EMAIL`：邮箱格式无效。
- `INVALID_CODE`：验证码为空或错误。

## 6. Me 与 Profile

### `GET /me`

用途：读取当前 session 对应用户。

认证：需要。

成功响应 `data`：

```json
{
  "userId": "uuid",
  "email": "user@example.com",
  "sessionToken": "token",
  "profileCompleted": true
}
```

可能错误：

- `UNAUTHORIZED`：缺少 token 或 token 无效。

### `PUT /me/companion-profile`

用途：创建或更新当前用户的单角色资料。

认证：需要。

请求体：

```json
{
  "nickname": "飞樱",
  "tone": "克制温柔",
  "personalityTags": ["温柔", "细腻"],
  "interestTags": ["二次元", "拍照"]
}
```

成功响应 `data`：

```json
{
  "nickname": "飞樱",
  "tone": "克制温柔",
  "personalityTags": ["温柔", "细腻"],
  "interestTags": ["二次元", "拍照"]
}
```

可能错误：

- `UNAUTHORIZED`：会话失效。
- `PROFILE_REQUIRED`：昵称、语气或标签格式不完整。

### `GET /me/companion-profile`

用途：读取当前用户的角色资料。

认证：需要。

成功响应 `data`：同 `PUT /me/companion-profile`。

可能错误：

- `UNAUTHORIZED`：会话失效。
- `PROFILE_REQUIRED`：当前用户还没有角色资料。

### `DELETE /me`

用途：删除当前账号数据。

认证：需要。

成功响应 `data`：

```json
{
  "deleted": true
}
```

删除语义：

- 删除后端用户记录。
- 通过数据库级联删除 session、角色资料、聊天记录、最近作品回流元信息。
- 不删除用户系统相册中的截图文件。

可能错误：

- `UNAUTHORIZED`：会话失效。

## 7. Chat

### `POST /chat/send`

用途：发送用户消息并获取角色回复。

认证：需要。

请求体：

```json
{
  "message": "今天想聊聊召唤",
  "clientMessageId": "client-generated-id"
}
```

成功响应 `data`：

```json
{
  "assistantMessage": "回复文本",
  "mode": "NORMAL",
  "timestamp": "2026-05-08T12:00:00.000Z"
}
```

字段说明：

- `message`：用户输入，服务端会 trim，不能为空。
- `clientMessageId`：客户端生成的消息 ID，用于客户端侧发送态关联。
- `mode`：`NORMAL` 或 `RESTRICTED`，由后端决定。
- `assistantMessage`：当前由后端统一调用 OpenAI 兼容 `chat/completions` 生成。

可能错误：

- `UNAUTHORIZED`：会话失效。
- `CHAT_SEND_FAILED`：消息为空。
- `PROFILE_REQUIRED`：当前用户还没有角色资料，无法生成回复。
- `LLM_NOT_CONFIGURED`：模型环境变量未配置完成。
- `LLM_TIMEOUT`：模型响应超时。
- `LLM_UPSTREAM_FAILED`：模型上游返回非 2xx。
- `LLM_EMPTY_REPLY`：模型未返回有效文本。
- `INTERNAL_SERVER_ERROR`：后端内部错误。

下一阶段约束：

- `POST /chat/send` 的非流式响应结构必须继续可用。
- 如果增加 streaming，应新增兼容方案或可选能力，不得破坏当前客户端。
- 安全模式仍由服务端返回 `mode`，客户端不自行决定安全等级。
- 服务端失败时不得伪造成功回复；客户端继续走现有失败与重试路径。

### `GET /chat/history`

用途：读取当前用户聊天历史。

认证：需要。

成功响应 `data`：

```json
{
  "messages": [
    {
      "id": "uuid",
      "role": "USER",
      "content": "消息内容",
      "mode": "NORMAL",
      "clientMessageId": "client-generated-id",
      "timestamp": "2026-05-08T12:00:00.000Z"
    }
  ]
}
```

字段说明：

- `role`：`USER` 或 `ASSISTANT`。
- `mode`：该条消息对应的安全模式。
- `clientMessageId`：仅用户消息可能有值，助手消息通常为 `null`。

### `DELETE /chat/history`

用途：清空当前用户聊天记录。

认证：需要。

成功响应 `data`：

```json
{
  "cleared": true
}
```

删除语义：

- 删除后端 `chat_messages` 中当前用户的所有消息。
- 不删除用户账号、角色资料或最近作品回流。

## 8. Capture

### `PUT /me/recent-capture`

用途：保存最近一次作品回流元信息。

认证：需要。

请求体：

```json
{
  "title": "最近一次召唤 #1",
  "summary": "你把角色放进了现实画面里",
  "storageLocation": "已保存到系统相册"
}
```

成功响应 `data`：

```json
{
  "title": "最近一次召唤 #1",
  "summary": "你把角色放进了现实画面里",
  "storageLocation": "已保存到系统相册",
  "updatedAt": "2026-05-08T12:00:00.000Z"
}
```

说明：

- 后端只保存最近一次作品元信息。
- 后端不保存截图文件本体。
- 系统相册文件由用户在系统相册中自行管理。

可能错误：

- `UNAUTHORIZED`：会话失效。
- `CAPTURE_UPDATE_FAILED`：标题、摘要或存储位置不完整。

### `GET /me/recent-capture`

用途：读取最近一次作品回流元信息。

认证：需要。

成功响应 `data`：同 `PUT /me/recent-capture`。

可能错误：

- `UNAUTHORIZED`：会话失效。
- `CAPTURE_UPDATE_FAILED`：当前没有最近作品回流记录。

### `DELETE /me/recent-capture`

用途：清除 App 内最近作品回流元信息。

认证：需要。

成功响应 `data`：

```json
{
  "cleared": true
}
```

删除语义：

- 只删除 App 后端中的最近作品回流元信息。
- 不删除系统相册截图文件。

## 9. Memory 预留

当前没有独立 memory API。

下一阶段建议：

- 先在 `POST /chat/send` 内部使用最近聊天历史、角色资料和最近作品回流组装上下文。
- 如需持久会话摘要，可新增 `ChatSummary` 或 `MemoryState` 服务端模型。
- 不建议 Android 客户端直接写长期记忆，避免前后端状态分裂。

## 10. Streaming 预留

当前聊天接口是非流式：

```text
Android -> POST /chat/send -> 后端一次性返回 assistantMessage
```

未来接入 streaming 时必须满足：

- 保留当前非流式 `POST /chat/send` 作为兼容路径。
- streaming 只影响回复传输方式，不改变安全模式由服务端决定的原则。
- 客户端失败重试仍以 `clientMessageId` 或服务端返回消息为基础，避免重复写入不可区分数据。
